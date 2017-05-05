package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.metadata.MetadataHelper
import exh.metadata.copyTo
import exh.metadata.models.ExGalleryMetadata
import exh.search.SearchEngine
import okhttp3.Response
import rx.Observable

/**
 * Offline metadata store source
 *
 * TODO This no longer fakes an online source because of technical reasons.
 * If we still want offline search, we must find out a way to rearchitecture the source system so it supports
 * online source faking again.
 */

class EHentaiMetadata(override val id: Long,
                      val exh: Boolean,
                      val context: Context) : HttpSource() {
    override fun popularMangaRequest(page: Int)
            = throw UnsupportedOperationException("Unused method called!")
    override fun popularMangaParse(response: Response)
            = throw UnsupportedOperationException("Unused method called!")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList)
            = throw UnsupportedOperationException("Unused method called!")
    override fun searchMangaParse(response: Response)
            = throw UnsupportedOperationException("Unused method called!")
    override fun latestUpdatesRequest(page: Int)
            = throw UnsupportedOperationException("Unused method called!")
    override fun latestUpdatesParse(response: Response)
            = throw UnsupportedOperationException("Unused method called!")
    override fun mangaDetailsParse(response: Response)
            = throw UnsupportedOperationException("Unused method called!")
    override fun chapterListParse(response: Response)
            = throw UnsupportedOperationException("Unused method called!")
    override fun pageListParse(response: Response)
            = throw UnsupportedOperationException("Unused method called!")
    override fun imageUrlParse(response: Response)
            = throw UnsupportedOperationException("Unused method called!")

    val metadataHelper = MetadataHelper()

    val internalEx = EHentai(id - 2, exh, context)

    val searchEngine = SearchEngine()

    override val baseUrl: String
        get() = throw UnsupportedOperationException()
    override val lang: String
        get() = "advanced"
    override val supportsLatest: Boolean
        get() = true

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>>
            = Observable.just(listOf(Chapter.create().apply {
        url = manga.url
        name = "ONLINE - Chapter"
        chapter_number = 1f
    }))

    override fun fetchPageList(chapter: SChapter) = internalEx.fetchPageList(chapter)

    override fun fetchImageUrl(page: Page) = internalEx.fetchImageUrl(page)

    fun List<ExGalleryMetadata>.mapToManga() = filter { it.exh == exh }
            .map {
        Manga.create(id).apply {
            it.copyTo(this)
            source = this@EHentaiMetadata.id
        }
    }

    fun sortedByTimeGalleries() = metadataHelper.getAllGalleries().sortedByDescending {
        it.datePosted ?: 0
    }

    override fun fetchPopularManga(page: Int)
        = Observable.fromCallable {
            MangasPage(metadataHelper.getAllGalleries().sortedByDescending {
                it.ratingCount ?: 0
            }.mapToManga(), false)
        }!!

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList)
    = Observable.fromCallable {
        val genreGroup = filters.find {
            it is EHentai.GenreGroup
        }!! as EHentai.GenreGroup
        val disableGenreFilter = genreGroup.state.find(EHentai.GenreOption::state) == null

        val parsed = searchEngine.parseQuery(query)
        MangasPage(sortedByTimeGalleries().filter { manga ->
            disableGenreFilter || genreGroup.state.find {
                it.state && it.genreId == manga.genre
            } != null
        }.filter {
            searchEngine.matches(it, parsed)
        }.mapToManga(), false)
    }!!

    override fun fetchLatestUpdates(page: Int)
    = Observable.fromCallable {
        MangasPage(sortedByTimeGalleries().mapToManga(), false)
    }!!

    override fun fetchMangaDetails(manga: SManga) = Observable.fromCallable {
        //Hack to convert the gallery into an online gallery when favoriting it or reading it
        metadataHelper.fetchEhMetadata(manga.url, exh)?.copyTo(manga)
        manga
    }!!

    override fun getFilterList() = FilterList(EHentai.GenreGroup())

    override val name: String
        get() = if(exh) {
            "ExHentai"
        } else {
            "E-Hentai"
        } + " - METADATA"

}
