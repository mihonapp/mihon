package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import rx.Observable
import tachiyomi.core.common.util.lang.awaitSingle

interface CatalogueSource : Source {

    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    val lang: String get() = throw UnsupportedOperationException()

    override val language: String get() = lang

    /**
     * Whether the source has support for latest updates.
     */
    val supportsLatest: Boolean get() = throw UnsupportedOperationException()

    override val hasLatestListing: Boolean get() = supportsLatest

    /**
     * Get a page with a list of manga.
     *
     * @since extensions-lib 1.5
     * @param page the page number to retrieve.
     */
    @Suppress("DEPRECATION")
    override suspend fun getDefaultMangaList(page: Int): MangasPage {
        return fetchPopularManga(page).awaitSingle()
    }

    /**
     * Get a page with a list of latest manga updates.
     *
     * @since extensions-lib 1.5
     * @param page the page number to retrieve.
     */
    @Suppress("DEPRECATION")
    override suspend fun getLatestMangaList(page: Int): MangasPage {
        return fetchLatestUpdates(page).awaitSingle()
    }

    /**
     * Get a page with a list of manga.
     *
     * @since extensions-lib 1.5
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    @Suppress("DEPRECATION")
    override suspend fun getMangaList(query: String, filters: FilterList, page: Int): MangasPage {
        return fetchSearchManga(page, query, filters).awaitSingle()
    }

    /**
     * Returns the list of filters for the source.
     */
    fun getFilterList(): FilterList

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getPopularManga"),
    )
    fun fetchPopularManga(page: Int): Observable<MangasPage> =
        throw IllegalStateException("Not used")

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getSearchManga"),
    )
    fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        throw IllegalStateException("Not used")

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getLatestUpdates"),
    )
    fun fetchLatestUpdates(page: Int): Observable<MangasPage> =
        throw IllegalStateException("Not used")
}
