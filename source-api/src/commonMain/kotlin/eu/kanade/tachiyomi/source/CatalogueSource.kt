@file:Suppress("DEPRECATION")

package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable

@Deprecated(
    message = "Use the base Source class instead",
    replaceWith = ReplaceWith(
        expression = "Source",
        imports = ["eu.kanade.tachiyomi.source.Source"],
    ),
)
interface CatalogueSource : Source {

    override val language: String get() = lang

    override val hasSearchFilters: Boolean get() = getFilterList().isNotEmpty()

    override val hasLatestListing: Boolean get() = supportsLatest

    override suspend fun getSearchFilters(): FilterList = getFilterList()

    override suspend fun getDefaultMangaList(page: Int): MangasPage = fetchPopularManga(page).awaitSingle()

    override suspend fun getLatestMangaList(page: Int): MangasPage = fetchLatestUpdates(page).awaitSingle()

    override suspend fun getMangaList(query: String, filters: FilterList, page: Int): MangasPage =
        fetchSearchManga(page, query, filters).awaitSingle()

    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    @Deprecated("Use language instead", ReplaceWith("language"))
    val lang: String get() = throw UnsupportedOperationException()

    /**
     * Whether the source has support for latest updates.
     */
    @Deprecated("Use hasLatestListing instead", ReplaceWith("hasLatestListing"))
    val supportsLatest: Boolean get() = throw UnsupportedOperationException()

    /**
     * Returns the list of filters for the source.
     */
    @Deprecated("Use the new suspend API instead", ReplaceWith("getSearchFilters"))
    fun getFilterList(): FilterList = throw UnsupportedOperationException()

    /**
     * Returns an observable containing a page with a list of manga.
     *
     * @param page the page number to retrieve.
     */
    @Deprecated("Use the new suspend API instead", ReplaceWith("getDefaultMangaList"))
    fun fetchPopularManga(page: Int): Observable<MangasPage> = throw UnsupportedOperationException()

    /**
     * Returns an observable containing a page with a list of latest manga updates.
     *
     * @param page the page number to retrieve.
     */
    @Deprecated("Use the new suspend API instead", ReplaceWith("getLatestMangaList"))
    fun fetchLatestUpdates(page: Int): Observable<MangasPage> = throw UnsupportedOperationException()

    /**
     * Returns an observable containing a page with a list of manga.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    @Deprecated("Use the new suspend API instead", ReplaceWith("getMangaList"))
    fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = throw UnsupportedOperationException()
}
