package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.awaitSingle
import okhttp3.Response
import rx.Observable

/**
 * Preference key for enabling full chapter downloads in extensions.
 * Extensions should use this key when adding a boolean preference for full chapter downloads.
 */
const val FULL_CHAPTER_DOWNLOAD_PREF_KEY = "full_chapter_download_enabled"

/**
 * A basic interface for creating a source. It could be an online source, a local source, etc.
 */
interface Source {

    /**
     * ID for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    val lang: String
        get() = ""

    /**
     * Get the updated details for a manga.
     *
     * @since extensions-lib 1.5
     * @param manga the manga to update.
     * @return the updated manga.
     */
    @Suppress("DEPRECATION")
    suspend fun getMangaDetails(manga: SManga): SManga {
        return fetchMangaDetails(manga).awaitSingle()
    }

    /**
     * Get all the available chapters for a manga.
     *
     * @since extensions-lib 1.5
     * @param manga the manga to update.
     * @return the chapters for the manga.
     */
    @Suppress("DEPRECATION")
    suspend fun getChapterList(manga: SManga): List<SChapter> {
        return fetchChapterList(manga).awaitSingle()
    }

    /**
     * Get the list of pages a chapter has. Pages should be returned
     * in the expected order; the index is ignored.
     *
     * @since extensions-lib 1.5
     * @param chapter the chapter.
     * @return the pages for the chapter.
     */
    @Suppress("DEPRECATION")
    suspend fun getPageList(chapter: SChapter): List<Page> {
        return fetchPageList(chapter).awaitSingle()
    }

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getMangaDetails"),
    )
    fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        throw IllegalStateException("Not used")

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getChapterList"),
    )
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        throw IllegalStateException("Not used")

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getPageList"),
    )
    fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        throw IllegalStateException("Not used")
}

/**
 * A source that supports downloading complete chapters as archive files (e.g., CBZ).
 * This is an optional interface that sources can implement to provide more efficient
 * chapter downloads when the source hosts complete chapter files.
 *
 * @since extensions-lib 1.6
 */
interface FullChapterSource : Source {

    /**
     * Indicates whether this source supports downloading complete chapters as archive files.
     * This should return true only if the source can provide complete chapter files
     * (e.g., CBZ, ZIP) instead of individual page images.
     *
     * @since extensions-lib 1.6
     * @return true if the source supports full chapter downloads, false otherwise
     */
    fun supportsFullChapterDownload(): Boolean = false

    /**
     * Downloads a complete chapter as an archive file (e.g., CBZ).
     * This method is only called if [supportsFullChapterDownload] returns true
     * and the user has enabled full chapter downloads in the source's preferences.
     *
     * The returned Response should contain the complete chapter archive file.
     * The Content-Type should be appropriate for the archive format (e.g., application/zip).
     *
     * @since extensions-lib 1.6
     * @param chapter the chapter to download as a complete archive
     * @return Response containing the complete chapter archive file
     * @throws Exception if the chapter cannot be downloaded as a complete archive
     */
    suspend fun getFullChapter(chapter: SChapter): Response
}
