package eu.kanade.tachiyomi.ui.manga

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import eu.kanade.domain.chapter.interactor.SetChapterHidden
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

internal suspend fun SnackbarHostState.setChapterHiddenWithRetry(
    interactor: SetChapterHidden,
    chapters: List<Chapter>,
    manga: Manga,
    source: Source,
    hidden: Boolean,
    errorMessage: String,
    retryLabel: String,
) {
    val targets = chapters.toList()
    var retryCleanup = false
    while (true) {
        try {
            if (retryCleanup) {
                interactor.retryCleanup(targets, manga, source)
            } else {
                interactor.await(targets, manga, source, hidden)
            }
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            val canRetryCleanup = hidden && (retryCleanup || e is SetChapterHidden.CleanupFailed)
            val result = showSnackbar(
                message = errorMessage,
                actionLabel = retryLabel.takeIf { canRetryCleanup },
                withDismissAction = canRetryCleanup,
            )
            if (result != SnackbarResult.ActionPerformed || !canRetryCleanup) return
            retryCleanup = true
        }
    }
}
