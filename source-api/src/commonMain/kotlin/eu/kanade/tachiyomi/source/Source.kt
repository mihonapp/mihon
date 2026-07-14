package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import rx.Observable

/**
 * A basic interface for creating a source. It could be an online source, a local source, etc...
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

    /**
     * Primary language of the source.
     *
     * Expected to be a valid IETF BCP 47 language tag, for example:
     * * `"en"` → English
     * * `"en-US"` → English (United States)
     * * `"zh-Hant"` → Traditional Chinese
     * * `"es-419"` → Spanish (Latin America)
     * * `"mul"` → Multiple languages
     * * `"und"` → Undetermined
     *
     * Sources containing multiple languages should use the language tag `"mul"`.
     * Sources with language-independent content or content without readable text
     * should use the language tag `"und"`.
     *
     * For backward compatibility, the default [CatalogueSource] implementation
     * normalizes the legacy values `"all"` and `"other"` returned by
     * [CatalogueSource.lang] to `"mul"` and `"und"`, respectively.
     *
     * @see SManga.language
     * @see SChapter.language
     * @since tachiyomix 1.7
     */
    val language: String

    /**
     * Whether the source has support for latest updates.
     */
    val supportsLatest: Boolean

    /**
     * Returns the list of filters for the source.
     */
    fun getFilterList(): FilterList = throw Exception("Stub!")

    /**
     * Get a page with a list of manga.
     *
     * @since tachiyomix 1.6
     * @param page the page number to retrieve.
     */
    suspend fun getPopularManga(page: Int): MangasPage

    /**
     * Get a page with a list of latest manga updates.
     *
     * @since tachiyomix 1.6
     * @param page the page number to retrieve.
     */
    suspend fun getLatestUpdates(page: Int): MangasPage

    /**
     * Get a page with a list of manga.
     *
     * @since tachiyomix 1.6
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage

    /**
     * Fetches updated information for a manga.
     *
     * Depending on the provided flags or source availability, this may include
     * updated manga metadata, available chapters, or both.
     *
     * If a value is not requested, the existing provided value can be returned as-is.
     * The host app may apply any returned updates regardless of the flags,
     * so it’s best to avoid returning unintended or inaccurate data.
     *
     * @since tachiyomix 1.6
     * @param manga The manga to fetch updates for.
     * @param chapters Existing chapters of the manga
     * @param fetchDetails Whether to include updated manga details.
     * @param fetchChapters Whether to include available chapters.
     */
    suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate

    /**
     * Get the list of pages a chapter has. Pages should be returned
     * in the expected order; the index is ignored.
     *
     * @since tachiyomix 1.6
     * @param chapter the chapter.
     * @return the pages for the chapter.
     */
    suspend fun getPageList(chapter: SChapter): List<Page>

    @Deprecated("Use the combined suspend API instead", ReplaceWith("getMangaUpdate"))
    fun fetchMangaDetails(manga: SManga): Observable<SManga> = throw UnsupportedOperationException()

    @Deprecated("Use the combined suspend API instead", ReplaceWith("getMangaUpdate"))
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = throw UnsupportedOperationException()

    @Deprecated("Use the suspend API instead", ReplaceWith("getPageList"))
    fun fetchPageList(chapter: SChapter): Observable<List<Page>> = throw UnsupportedOperationException()
}
