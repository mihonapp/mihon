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
)

enum class TranslationQueueTypeFilter(val statuses: Set<String>) {
    Waiting(setOf(TranslationJobStatus.Queued.value, TranslationJobStatus.Retrying.value)),
    Translating(setOf(TranslationJobStatus.Running.value)),
    Paused(setOf(TranslationJobStatus.PausedAuth.value, TranslationJobStatus.PausedQuota.value)),
    Error(setOf(TranslationJobStatus.Failed.value)),
    Done(setOf(TranslationJobStatus.Completed.value)),
    Cancelled(setOf(TranslationJobStatus.Cancelled.value)),
}

object TranslationQueueUiModel {
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
                TranslationQueueGroup(
                    key = key,
                    mangaTitle = groupItems.first().mangaTitle,
                    items = groupItems,
                )
            }
    }
}

data class TranslationLogUiItem(
    val id: Long,
    val jobId: Long?,
    val pageId: Long?,
    val createdAt: Long,
    val level: String,
    val tag: String,
    val message: String,
    val details: String?,
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
