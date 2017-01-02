package eu.kanade.tachiyomi.data.source.online.all

import android.content.Context
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.source.model.MangasPage
import eu.kanade.tachiyomi.data.source.model.Page
import eu.kanade.tachiyomi.data.source.online.OnlineSource
import exh.metadata.MetadataHelper
import exh.metadata.copyTo
import exh.metadata.models.ExGalleryMetadata
import okhttp3.Response
import rx.Observable

/**
 * Offline metadata store source
 */

class EHentaiMetadata(override val id: Int,
                      val exh: Boolean,
                      val context: Context) : OnlineSource() {

    val metadataHelper = MetadataHelper()

    val internalEx = EHentai(id - 2, exh, context)

    override val baseUrl: String
        get() = throw UnsupportedOperationException()
    override val lang: String
        get() = "advanced"
    override val supportsLatest: Boolean
        get() = true

    override fun popularMangaInitialUrl(): String {
        throw UnsupportedOperationException()
    }

    override fun popularMangaParse(response: Response, page: MangasPage) {
        throw UnsupportedOperationException()
    }

    override fun searchMangaInitialUrl(query: String, filters: List<Filter>): String {
        throw UnsupportedOperationException()
    }

    override fun searchMangaParse(response: Response, page: MangasPage, query: String, filters: List<Filter>) {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesInitialUrl(): String {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesParse(response: Response, page: MangasPage) {
        throw UnsupportedOperationException()
    }

    override fun mangaDetailsParse(response: Response, manga: Manga) {
        throw UnsupportedOperationException()
    }

    override fun chapterListParse(response: Response, chapters: MutableList<Chapter>) {
        throw UnsupportedOperationException()
    }

    override fun pageListParse(response: Response, pages: MutableList<Page>) {
        throw UnsupportedOperationException()
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    override fun fetchChapterList(manga: Manga): Observable<List<Chapter>>
            = Observable.just(listOf(Chapter.create().apply {
        manga_id = manga.id
        url = manga.url
        name = "ONLINE - Chapter"
        chapter_number = 1f
    }))

    override fun fetchPageListFromNetwork(chapter: Chapter) = internalEx.fetchPageListFromNetwork(chapter)

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

    override fun fetchPopularManga(page: MangasPage)
        = Observable.fromCallable {
            page.mangas.addAll(metadataHelper.getAllGalleries().sortedByDescending {
                it.ratingCount ?: 0
            }.mapToManga())
            page
        }!!

    override fun fetchSearchManga(page: MangasPage, query: String, filters: List<Filter>)
    = Observable.fromCallable {
        page.mangas.addAll(sortedByTimeGalleries().filter { manga ->
            filters.isEmpty() || filters.filter { it.id == manga.genre }.isNotEmpty()
        }.mapToManga())
        page
    }!!

    override fun fetchLatestUpdates(page: MangasPage)
    = Observable.fromCallable {
        page.mangas.addAll(sortedByTimeGalleries().mapToManga())
        page
    }!!

    override fun fetchMangaDetails(manga: Manga) = Observable.fromCallable {
        //Hack to convert the gallery into an online gallery when favoriting it or reading it
        metadataHelper.fetchMetadata(manga.url, exh).copyTo(manga)
        manga
    }!!

    override fun getFilterList() = internalEx.getFilterList()

    override val name: String
        get() = if(exh) {
            "ExHentai"
        } else {
            "E-Hentai"
        } + " - METADATA"

}
