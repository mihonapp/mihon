package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.awaitSingle
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import rx.Observable

/**
 * A basic interface for creating a source. It could be an online source, a local source, etc.
 */
interface Source {

    /**
     * Id for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    /**
     * Represents an IETF BCP 47 compliant language tag.
     * Special cases include:
     * - [Language.MULTI]: Indicates multiple languages.
     * - [Language.OTHER]: Refers to a language not explicitly defined by the source (e.g. text less, unofficially supported language).
     * - 'all': Indicates multiple language.
     *
     * Usage of 'all' is highly discouraged and is only supported due to legacy reasons.
     *
     * @since extensions-lib 1.6
     */
    val language: String

    /**
     * Indicates if the source supports search filters
     *
     * @since extensions-lib 1.6
     */
    val hasSearchFilters: Boolean

    /**
     * Whether the source has a list for latest updates
     *
     * @since extensions-lib 1.6
     */
    val hasLatestListing: Boolean

    /**
     * Returns the list of filters for the source.
     *
     * @since extensions-lib 1.6
     */
    suspend fun getSearchFilters(): FilterList

    /**
     * Get a page with a list of manga.
     *
     * @since extensions-lib 1.6
     *
     * @param page the page number to retrieve.
     */
    suspend fun getDefaultMangaList(page: Int): MangasPage

    /**
     * Get a page with a list of latest manga updates.
     *
     * @since extensions-lib 1.6
     *
     * @param page the page number to retrieve.
     */
    suspend fun getLatestMangaList(page: Int): MangasPage = throw UnsupportedOperationException()

    /**
     * Get a page with a list of manga.
     *
     * @since extensions-lib 1.6
     *
     * @param query     the search query.
     * @param filters   the list of filters to apply.
     * @param page      the page number to retrieve.
     */
    suspend fun getMangaList(query: String, filters: FilterList, page: Int): MangasPage

    /**
     * Get the updated details for a manga and its chapters
     *
     * @since extensions-lib 1.6
     *
     * @param manga         manga to get details and chapters for.
     * @param updateManga   whether to update the manga details or not
     * @param fetchChapters whether to fetch chapters or not.
     */
    suspend fun getMangaDetails(
        manga: SManga,
        updateManga: Boolean,
        fetchChapters: Boolean,
    ): Pair<SManga, List<SChapter>> {
        return supervisorScope {
            val mangaAsync = async { if (updateManga) fetchMangaDetails(manga).awaitSingle() else manga }
            val chaptersAsync = async { if (fetchChapters) fetchChapterList(manga).awaitSingle() else emptyList() }
            mangaAsync.await() to chaptersAsync.await()
        }
    }

    /**
     * Get the list of pages a chapter has. Pages should be returned
     * in the expected order; the index is ignored.
     *
     * @since extensions-lib 1.6
     *
     * @param chapter the chapter.
     */
    suspend fun getPageList(chapter: SChapter): List<Page> = throw RuntimeException("Stub!")

    override fun toString(): String

    /**
     * Object for holding the special cases supported by [Source.language].
     *
     * @since extensions-lib 1.6
     */
    object Language {
        /**
         * Indicates multiple languages.
         *
         * @since extensions-lib 1.6
         */
        const val MULTI = "multi"

        /**
         * Refers to a language not explicitly defined by the source (e.g. text less, unofficially supported language)
         *
         * @since extensions-lib 1.6
         */
        const val OTHER = "other"
    }

    @Deprecated("Use the new suspend API instead", ReplaceWith("getMangaDetails(manga, true, false)"))
    fun fetchMangaDetails(manga: SManga): Observable<SManga> = throw UnsupportedOperationException()

    @Deprecated("Use the new suspend API instead", ReplaceWith("getMangaDetails(manga, false, true)"))
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = throw UnsupportedOperationException()

    @Deprecated("Use the new suspend API instead", ReplaceWith("getPageList"))
    fun fetchPageList(chapter: SChapter): Observable<List<Page>> = throw UnsupportedOperationException()

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
}
