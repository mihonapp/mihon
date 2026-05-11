package eu.kanade.tachiyomi.data.translation

import tachiyomi.domain.translation.service.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslationBatchEnqueuer(
    private val repository: TranslationRepository = Injekt.get(),
    private val imageResolver: TranslationImageResolver = Injekt.get(),
    private val preferences: TranslationPreferences = Injekt.get(),
) {
    suspend fun enqueueChapters(
        mangaId: Long,
        chapterIds: List<Long>,
        mode: TranslationMode,
    ): TranslationBatchEnqueueResult {
        val targetLanguage = preferences.resolvedTargetLanguage()
        val sourceLanguage = preferences.sourceLanguage.get()
            .takeUnless { it.isBlank() || it == TranslationLanguages.SOURCE_AUTO }
        val pipeline = preferences.pipeline.get()
        val model = preferences.geminiModel.get()
        val overwrite = !preferences.skipExistingOverlays.get()
        val maxImages = preferences.normalizedMaxImagesPerBatch()
        val candidates = mutableListOf<TranslationPageCandidate>()

        chapterIds.distinct().forEach { chapterId ->
            val pageCount = imageResolver.getPageCount(mangaId, chapterId)
            for (pageIndex in 0 until pageCount) {
                candidates += TranslationPageCandidate(
                    chapterId = chapterId,
                    pageIndex = pageIndex,
                    hasOverlay = TranslationSavedOverlayPolicy.shouldSkipExistingOverlay(
                        hasSavedPageRow = repository.getPage(
                            chapterId = chapterId,
                            pageIndex = pageIndex.toLong(),
                            targetLanguage = targetLanguage,
                        ) != null,
                        overwrite = overwrite,
                    ),
                    hasActiveJob = repository.hasActiveMatchingJob(
                        mangaId = mangaId,
                        chapterId = chapterId,
                        pageIndex = pageIndex.toLong(),
                        scope = TranslationScope.Image,
                        pipeline = pipeline,
                        mode = mode,
                        targetLanguage = targetLanguage,
                    ),
                )
            }
        }

        return enqueueCandidates(
            mangaId = mangaId,
            candidates = candidates,
            overwrite = overwrite,
            maxImages = maxImages,
            pipeline = pipeline,
            mode = mode,
            model = model,
            targetLanguage = targetLanguage,
            sourceLanguage = sourceLanguage,
        )
    }

    private suspend fun enqueueCandidates(
        mangaId: Long,
        candidates: List<TranslationPageCandidate>,
        overwrite: Boolean,
        maxImages: Int,
        pipeline: String,
        mode: TranslationMode,
        model: String,
        targetLanguage: String,
        sourceLanguage: String?,
    ): TranslationBatchEnqueueResult {
        val pages = TranslationBatchPlanner.pagesToQueue(
            pages = candidates,
            overwrite = overwrite,
            maxImagesPerBatch = maxImages,
        )
        var queued = 0
        val skippedExisting = candidates.count { !overwrite && it.hasOverlay }
        val skippedActive = candidates.count { it.hasActiveJob }
        var skipped = candidates.size - pages.size
        var skippedRaceDuplicate = 0
        pages.forEach { page ->
            val result = repository.enqueueJob(
                mangaId = mangaId,
                chapterId = page.chapterId,
                pageIndex = page.pageIndex.toLong(),
                scope = TranslationScope.Image,
                pipeline = pipeline,
                mode = mode,
                model = model,
                targetLanguage = targetLanguage,
                sourceLanguage = sourceLanguage,
                overwrite = overwrite,
            )
            if (result.inserted) {
                queued++
            } else {
                skipped++
                skippedRaceDuplicate++
            }
        }
        repository.insertLog(
            jobId = null,
            pageId = null,
            level = TranslationLogLevel.Info,
            tag = "queue",
            message = "Queued chapter translation pages",
            details = TranslationLogDetailsFormatter.queueState(
                action = "enqueue_all_pages",
                jobId = null,
                previousStatus = null,
                nextStatus = TranslationJobStatus.Queued.value,
                extra = mapOf(
                    "manga_id" to mangaId,
                    "considered" to candidates.size,
                    "eligible_pages" to pages.size,
                    "queued" to queued,
                    "skipped" to skipped,
                    "skipped_existing" to skippedExisting,
                    "skipped_active_duplicate" to skippedActive,
                    "skipped_race_duplicate" to skippedRaceDuplicate,
                    "worker_batch_size" to maxImages,
                    "overwrite" to overwrite,
                ),
            ),
        )
        return TranslationBatchEnqueueResult(
            queued = queued,
            skipped = skipped,
            considered = candidates.size,
        )
    }
}

data class TranslationBatchEnqueueResult(
    val queued: Int,
    val skipped: Int,
    val considered: Int,
)
