package mihon.domain.chapter.interactor

import android.database.sqlite.SQLiteException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.model.Manga

/**
 * Interactor responsible for determining which chapters of a manga should be downloaded.
 *
 * @property chapterRepository The repository for accessing chapter data.
 * @property downloadPreferences User preferences related to chapter downloads.
 * @property getCategories Interactor for retrieving categories associated with a manga.
 */
class GetChaptersToDownload(
    private val chapterRepository: ChapterRepository,
    private val downloadPreferences: DownloadPreferences,
    private val getCategories: GetCategories,
) {

    /**
     * Determines chapters that should be downloaded based on user preferences.
     *
     * @param manga The manga for which chapters may be downloaded.
     * @param newChapters The list of new chapters available for the manga.
     * @return A list of chapters that should be downloaded.
     */
    suspend fun await(manga: Manga, newChapters: List<Chapter>): List<Chapter> {
        if (!shouldDownloadNewChapters(manga)) {
            return emptyList()
        }

        val downloadUnreadChapters = downloadPreferences.downloadUnreadChaptersOnly().get()
        return if (downloadUnreadChapters) {
            newChapters.filter { isUnreadChapter(manga.id, it.chapterNumber) }
        } else {
            newChapters
        }
    }

    /**
     * Checks if a chapter is unread based on its manga ID and chapter number.
     *
     * @param mangaId The ID of the manga to which the chapter belongs.
     * @param chapterNumber The number of the chapter.
     * @return `true` if the chapter is unread; otherwise `false`.
     */
    private suspend fun isUnreadChapter(mangaId: Long, chapterNumber: Double): Boolean {
        return try {
            chapterRepository.getReadChapterCountByMangaIdAndChapterNumber(mangaId, chapterNumber) == 0L
        } catch (e: SQLiteException) {
            logcat(LogPriority.ERROR, e)
            true
        }
    }

    /**
     * Determines whether new chapters should be downloaded for the manga based on user preferences and the
     * categories to which the manga belongs.
     *
     * @param manga The manga to check for download eligibility.
     * @return `true` if new chapters should be downloaded; otherwise `false`.
     */
    private suspend fun shouldDownloadNewChapters(manga: Manga): Boolean {
        if (!manga.favorite) return false

        // Boolean to determine if user wants to automatically download new chapters.
        val downloadNewChapters = downloadPreferences.downloadNewChapters().get()
        if (!downloadNewChapters) return false

        val categories = getCategories.await(manga.id).map { it.id }.ifEmpty { listOf(DEFAULT_CATEGORY_ID) }
        val includedCategories = downloadPreferences.downloadNewChapterCategories().get().map { it.toLong() }
        val excludedCategories = downloadPreferences.downloadNewChapterCategoriesExclude().get().map { it.toLong() }

        return when {
            includedCategories.isEmpty() && excludedCategories.isEmpty() -> true // Default: Download from all categories
            categories.any { it in excludedCategories } -> false // In excluded category
            includedCategories.isEmpty() -> true // Included category not selected
            else -> categories.any { it in includedCategories } // In included category
        }
    }

    companion object {
        private const val DEFAULT_CATEGORY_ID = 0L
    }
}
