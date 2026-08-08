package eu.kanade.tachiyomi.data.translation

import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Translation_boxes
import tachiyomi.domain.translation.service.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class TranslationReaderOverlayLoadResult(
    val decision: TranslationReaderOverlayLoadDecision,
    val boxes: List<Translation_boxes>,
    val displayTransform: TranslationOverlayDisplayTransform,
)

class TranslationReaderOverlayLoader(
    private val repository: TranslationRepository = Injekt.get(),
    private val preferences: TranslationPreferences = Injekt.get(),
) {
    suspend fun load(
        overlayVisible: Boolean,
        chapterId: Long?,
        pageIndex: Int,
        refreshSource: String,
        displayTransform: TranslationOverlayDisplayTransform = TranslationOverlayDisplayTransform.Identity,
    ): TranslationReaderOverlayLoadResult {
        val targetLanguage = preferences.resolvedTargetLanguage()
        if (!overlayVisible || chapterId == null) {
            return TranslationReaderOverlayLoadResult(
                decision = TranslationReaderOverlayLoadPolicy.decision(
                    overlayVisible = overlayVisible,
                    chapterId = chapterId,
                    pageIndex = pageIndex,
                    targetLanguage = targetLanguage,
                    refreshSource = refreshSource,
                    savedPageExists = false,
                    savedBoxCount = 0,
                ),
                boxes = emptyList(),
                displayTransform = displayTransform,
            )
        }

        val savedPage = try {
            withIOContext {
                repository.getSavedPage(
                    chapterId = chapterId,
                    pageIndex = pageIndex.toLong(),
                    targetLanguage = targetLanguage,
                )
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e) { "Failed to load saved translation overlay" }
            repository.insertLog(
                jobId = null,
                pageId = null,
                level = TranslationLogLevel.Error,
                tag = "overlay",
                message = "Failed to load reader translation overlay",
                details = loadFailureDetails(
                    chapterId = chapterId,
                    pageIndex = pageIndex,
                    targetLanguage = targetLanguage,
                    refreshSource = refreshSource,
                    error = e,
                ),
            )
            return TranslationReaderOverlayLoadResult(
                decision = TranslationReaderOverlayLoadDecision(
                    action = TranslationReaderOverlayLoadAction.Clear,
                    clearReason = "load_failed",
                    shouldLogMissing = false,
                    targetLanguage = targetLanguage,
                    refreshSource = refreshSource,
                    savedBoxCount = 0,
                ),
                boxes = emptyList(),
                displayTransform = displayTransform,
            )
        }

        val decision = TranslationReaderOverlayLoadPolicy.decision(
            overlayVisible = overlayVisible,
            chapterId = chapterId,
            pageIndex = pageIndex,
            targetLanguage = targetLanguage,
            refreshSource = refreshSource,
            savedPageExists = savedPage != null,
            savedBoxCount = savedPage?.boxes?.size ?: 0,
        )
        if (
            decision.shouldLogMissing &&
            TranslationReaderOverlayMissingLogDeduper.shouldLogMissing(
                chapterId = chapterId,
                pageIndex = pageIndex,
                targetLanguage = targetLanguage,
                refreshSource = refreshSource,
            )
        ) {
            repository.insertLog(
                jobId = null,
                pageId = null,
                level = TranslationLogLevel.Debug,
                tag = "overlay",
                message = "Reader translation overlay missing",
                details = buildString {
                    appendLine("action=reader_overlay_missing")
                    appendLine("chapter_id=$chapterId")
                    appendLine("page_index=$pageIndex")
                    appendLine("target_language=$targetLanguage")
                    appendLine("overlay_visible=$overlayVisible")
                    appendLine("refresh_source=$refreshSource")
                    appendLine("saved_page=false")
                    appendLine("clear_reason=${decision.clearReason ?: "-"}")
                },
            )
        }
        return TranslationReaderOverlayLoadResult(
            decision = decision,
            boxes = savedPage?.boxes.orEmpty(),
            displayTransform = displayTransform,
        )
    }

    private fun loadFailureDetails(
        chapterId: Long,
        pageIndex: Int,
        targetLanguage: String,
        refreshSource: String,
        error: Throwable,
    ): String {
        return buildString {
            appendLine("action=reader_overlay_load_failed")
            appendLine("chapter_id=$chapterId")
            appendLine("page_index=$pageIndex")
            appendLine("target_language=$targetLanguage")
            appendLine("refresh_source=$refreshSource")
            appendLine("exception_class=${error::class.qualifiedName ?: error::class.simpleName.orEmpty()}")
            appendLine("exception_message=${error.message ?: "-"}")
            appendLine("stack_trace=${error.stackTraceToString()}")
        }.trimEnd()
    }
}
