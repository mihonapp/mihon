package mihon.core.chapters.utils

import mihon.domain.chapter.interactor.GetReadChapterCountByMangaIdAndChapterNumber
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.model.Manga

/**
 * Filters the list of chapters based on the user's download preferences.
 *
 * This function checks the user's preference for downloading only unread chapters. If the
 * preference is enabled, it filters the chapters to include only those that haven't been read.
 * Otherwise, it returns the original list of chapters.
 *
 * @param manga The manga to which the chapters belong.
 * @param getReadChapterCount A function to get the count of times a chapter has been read.
 * @param downloadPreferences User preferences related to downloading chapters.
 * @return A list of chapters filtered according to the download preferences. If the user prefers to download only
 * unread chapters, the list will contain only unread chapters. Otherwise, it will return the original list of chapters.
 */
suspend fun List<Chapter>.filterChaptersToDownload(
    manga: Manga,
    getReadChapterCount: GetReadChapterCountByMangaIdAndChapterNumber,
    downloadPreferences: DownloadPreferences,
): List<Chapter> {
    val onlyDownloadUnreadChapters = downloadPreferences.downloadUnreadChaptersOnly().get()

    return if (onlyDownloadUnreadChapters) {
        this.filter { getReadChapterCount.await(manga.id, it.chapterNumber) == 0L }
    } else {
        this
    }
}
