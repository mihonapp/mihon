package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import rx.Observable
import tachiyomi.core.common.util.lang.awaitSingle

interface CatalogueSource : Source {

    override val lang: String

    @Suppress("DEPRECATION")
    override suspend fun getPopularManga(page: Int): MangasPage = fetchPopularManga(page).awaitSingle()

    @Suppress("DEPRECATION")
    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchLatestUpdates(page).awaitSingle()

    @Suppress("DEPRECATION")
    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = fetchSearchManga(page, query, filters).awaitSingle()

    @Suppress("DEPRECATION")
    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = supervisorScope {
        val asyncManga = if (fetchDetails) async { fetchMangaDetails(manga).awaitSingle() } else null
        val asyncChapters = if (fetchChapters) async { fetchChapterList(manga).awaitSingle() } else null
        SMangaUpdate(asyncManga?.await() ?: manga, asyncChapters?.await() ?: chapters)
    }

    @Suppress("DEPRECATION")
    override suspend fun getPageList(chapter: SChapter): List<Page> = fetchPageList(chapter).awaitSingle()

    @Deprecated("Use the suspend API instead", ReplaceWith("getPopularManga"))
    fun fetchPopularManga(page: Int): Observable<MangasPage> = throw UnsupportedOperationException()

    @Deprecated("Use the suspend API instead", ReplaceWith("getSearchManga"))
    fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = throw UnsupportedOperationException()

    @Deprecated("Use the suspend API instead", ReplaceWith("getLatestUpdates"))
    fun fetchLatestUpdates(page: Int): Observable<MangasPage> = throw UnsupportedOperationException()
}
