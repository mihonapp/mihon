package eu.kanade.tachiyomi.data.translation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import mihon.app.di.appGraph
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.manga.interactor.GetManga

class TranslationJob(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val mangaId = inputData.getLong(KEY_MANGA_ID, -1L)
        val chapterId = inputData.getLong(KEY_CHAPTER_ID, -1L)
        if (mangaId == -1L || chapterId == -1L) return Result.failure()

        val appGraph = applicationContext.appGraph
        val getManga = appGraph.getManga
        val getChapter = appGraph.getChapter
        val sourceManager = appGraph.sourceManager
        val translationManager = appGraph.translationManager

        val manga = getManga.await(mangaId) ?: return Result.failure()
        val chapter = getChapter.await(chapterId) ?: return Result.failure()
        val source = sourceManager.get(manga.source) ?: return Result.failure()

        val success = translationManager.translateChapter(source, manga, chapter)
        return if (success) Result.success() else Result.retry()
    }

    companion object {
        const val KEY_MANGA_ID = "manga_id"
        const val KEY_CHAPTER_ID = "chapter_id"
    }
}
