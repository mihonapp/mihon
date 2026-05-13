package eu.kanade.tachiyomi.data.translation

data class TranslationQueueItem(
    val id: Long,
    val mangaId: Long,
    val mangaTitle: String,
    val chapterId: Long?,
    val chapterName: String?,
    val chapterNumber: Double?,
    val pageIndex: Long?,
    val scope: String,
    val pipeline: String,
    val mode: String,
    val model: String,
    val targetLanguage: String,
    val sourceLanguage: String?,
    val overwrite: Boolean,
    val status: String,
    val progressCurrent: Long,
    val progressTotal: Long,
    val attempts: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val errorMessage: String?,
)

data class TranslationQueueGroup(
    val key: String,
    val mangaTitle: String,
    val items: List<TranslationQueueItem>,
    val chapterGroups: List<TranslationQueueChapterGroup>,
    val statusCounts: Map<String, Int>,
    val retryableCount: Int,
)

data class TranslationQueueChapterGroup(
    val key: String,
    val title: String,
    val items: List<TranslationQueueItem>,
    val statusCounts: Map<String, Int>,
    val retryableCount: Int,
)

data class TranslationQueueDerivedUiState(
    val activeJobCount: Int,
    val queueGroups: List<TranslationQueueGroup>,
    val groupedLogsByJob: Map<Long, List<GroupedTranslationLog>>,
)

enum class TranslationQueueTypeFilter(val statuses: Set<String>) {
    Waiting(setOf(TranslationJobStatus.Queued.value, TranslationJobStatus.Retrying.value, TranslationJobStatus.ManualRetry.value)),
    Translating(setOf(TranslationJobStatus.Running.value)),
    Paused(setOf(TranslationJobStatus.PausedAuth.value, TranslationJobStatus.PausedQuota.value)),
    Error(setOf(TranslationJobStatus.Failed.value)),
    Done(setOf(TranslationJobStatus.Completed.value)),
    Cancelled(setOf(TranslationJobStatus.Cancelled.value)),
}

object TranslationQueueUiModel {
    fun derive(
        items: List<TranslationQueueItem>,
        logs: List<TranslationLogUiItem>,
        filters: Set<TranslationQueueTypeFilter>,
    ): TranslationQueueDerivedUiState {
        return TranslationQueueDerivedUiState(
            activeJobCount = items.count { it.status !in FINISHED_QUEUE_STATUSES },
            queueGroups = filterAndGroup(items, filters),
            groupedLogsByJob = groupedLogsByJob(logs),
        )
    }

    fun filterAndGroup(
        items: List<TranslationQueueItem>,
        filters: Set<TranslationQueueTypeFilter>,
    ): List<TranslationQueueGroup> {
        val allowedStatuses = filters.flatMapTo(mutableSetOf()) { it.statuses }
        return items
            .asSequence()
            .filter { allowedStatuses.isEmpty() || it.status in allowedStatuses }
            .sortedWith(
                compareBy<TranslationQueueItem> { it.mangaTitle.lowercase() }
                    .thenBy { it.chapterNumber ?: Double.MAX_VALUE }
                    .thenBy { it.chapterName.orEmpty() }
                    .thenBy { it.pageIndex ?: Long.MAX_VALUE }
                    .thenBy { it.createdAt },
            )
            .groupBy { "${it.mangaId}:${it.mangaTitle}" }
            .map { (key, groupItems) ->
                val chapterGroups = groupItems
                    .groupBy { "${it.chapterId ?: -1}:${it.chapterName.orEmpty()}" }
                    .map { (chapterKey, chapterItems) ->
                        TranslationQueueChapterGroup(
                            key = "$key:$chapterKey",
                            title = chapterItems.first().chapterName ?: "Chapter ${chapterItems.first().chapterId ?: "-"}",
                            items = chapterItems,
                            statusCounts = chapterItems.groupingBy { it.status }.eachCount(),
                            retryableCount = chapterItems.count { it.isRetryableQueueItem() },
                        )
                    }
                TranslationQueueGroup(
                    key = key,
                    mangaTitle = groupItems.first().mangaTitle,
                    items = groupItems,
                    chapterGroups = chapterGroups,
                    statusCounts = groupItems.groupingBy { it.status }.eachCount(),
                    retryableCount = groupItems.count { it.isRetryableQueueItem() },
                )
            }
    }

    fun groupedLogsByJob(logs: List<TranslationLogUiItem>): Map<Long, List<GroupedTranslationLog>> {
        return logs
            .filter { it.jobId != null }
            .groupBy { requireNotNull(it.jobId) }
            .mapValues { (_, jobLogs) -> TranslationLogUiModel.groupAdjacent(jobLogs) }
    }

    fun retryableItems(items: List<TranslationQueueItem>): List<TranslationQueueItem> {
        return items.filter { it.isRetryableQueueItem() }
    }

    private fun TranslationQueueItem.isRetryableQueueItem(): Boolean {
        val parsedStatus = TranslationJobStatus.entries.firstOrNull { it.value == status }
        return this.status in FINISHED_QUEUE_STATUSES || parsedStatus?.isRetryableFromQueue() == true
    }
}

private val FINISHED_QUEUE_STATUSES = setOf(
    TranslationJobStatus.Completed.value,
    TranslationJobStatus.Failed.value,
    TranslationJobStatus.Cancelled.value,
)

data class TranslationLogUiItem(
    val id: Long,
    val jobId: Long?,
    val pageId: Long?,
    val createdAt: Long,
    val level: String,
    val tag: String,
    val message: String,
    val details: String?,
    val detailsPreview: String? = TranslationLogUiModel.detailsPreview(details),
)

data class GroupedTranslationLog(
    val first: TranslationLogUiItem,
    val count: Int,
    val firstCreatedAt: Long,
    val latestCreatedAt: Long,
) {
    val id: Long = first.id
}

object TranslationLogUiModel {
    fun detailsPreview(details: String?): String? {
        val value = details?.takeIf { it.isNotBlank() } ?: return null
        if (value.length <= LOG_DETAILS_PREVIEW_CHARS) return value
        return value.take(LOG_DETAILS_PREVIEW_CHARS).trimEnd() + "\n..."
    }

    fun groupAdjacent(logs: List<TranslationLogUiItem>): List<GroupedTranslationLog> {
        if (logs.isEmpty()) return emptyList()

        val result = mutableListOf<GroupedTranslationLog>()
        var current = mutableListOf(logs.first())

        fun flush() {
            val timestamps = current.map { it.createdAt }
            result += GroupedTranslationLog(
                first = current.first(),
                count = current.size,
                firstCreatedAt = timestamps.min(),
                latestCreatedAt = timestamps.max(),
            )
        }

        logs.drop(1).forEach { log ->
            if (sameLogGroup(current.last(), log)) {
                current += log
            } else {
                flush()
                current = mutableListOf(log)
            }
        }
        flush()

        return result
    }

    private fun sameLogGroup(left: TranslationLogUiItem, right: TranslationLogUiItem): Boolean {
        return left.level == right.level &&
            left.tag == right.tag &&
            left.message == right.message &&
            left.jobId == right.jobId &&
            left.pageId == right.pageId &&
            left.details == right.details
    }
}

private const val LOG_DETAILS_PREVIEW_CHARS = 600
