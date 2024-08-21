package mihon.domain.chapter.interactor

import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.model.Manga

/**
 * Interactor responsible for determining which chapters of a manga should be downloaded.
 *
 * @property getChaptersByMangaId Interactor for retrieving chapters by manga ID.
 * @property downloadPreferences User preferences related to chapter downloads.
 * @property getCategories Interactor for retrieving categories associated with a manga.
 */
class FilterChaptersForDownload(
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val downloadPreferences: DownloadPreferences,
    private val getCategories: GetCategories,
) {

    /**
     * Determines which chapters of a manga should be downloaded based on user preferences.
     *
     * @param manga The manga for which chapters may be downloaded.
     * @param newChapters The list of new chapters available for the manga.
     * @return A list of chapters that should be downloaded. If new chapters should not be downloaded,
     * returns an empty list.
     */
    suspend fun await(manga: Manga, newChapters: List<Chapter>): List<Chapter> {
        if (!shouldDownloadNewChapters(manga)) {
            return emptyList()
        }

        val downloadNewUnreadChaptersOnly = downloadPreferences.downloadNewUnreadChaptersOnly().get()
        return if (downloadNewUnreadChaptersOnly) {
            getUnreadChapters(manga, newChapters)
        } else {
            newChapters
        }
    }

    /**
     * Filters out chapters that have already been read.
     *
     * @param manga The manga whose chapters are being checked.
     * @param newChapters The list of new chapters to filter.
     * @return A list of unread chapters that are present in `newChapters`.
     */
    private suspend fun getUnreadChapters(manga: Manga, newChapters: List<Chapter>): List<Chapter> {
        val dbChapters = getChaptersByMangaId.await(manga.id)
        val unreadChapters = dbChapters
            .groupBy { it.chapterNumber }
            .filterValues { chapters -> chapters.none { it.read } }

        return newChapters.filter { it.chapterNumber in unreadChapters }
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
            includedCategories.isEmpty() && excludedCategories.isEmpty() -> true // Default Download from all categories
            categories.any { it in excludedCategories } -> false // In excluded category
            includedCategories.isEmpty() -> true // Included category not selected
            else -> categories.any { it in includedCategories } // In included category
        }
    }

    companion object {
        private const val DEFAULT_CATEGORY_ID = 0L
    }
}
