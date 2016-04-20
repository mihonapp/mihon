package eu.kanade.tachiyomi.data.source.base

import android.content.Context
import com.bumptech.glide.load.model.LazyHeaders
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.network.NetworkHelper
import eu.kanade.tachiyomi.data.network.get
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.source.model.MangasPage
import eu.kanade.tachiyomi.data.source.model.Page
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import rx.schedulers.Schedulers
import java.util.*
import javax.inject.Inject

abstract class Source(context: Context) : BaseSource() {

    @Inject protected lateinit var networkService: NetworkHelper
    @Inject protected lateinit var chapterCache: ChapterCache
    @Inject protected lateinit var prefs: PreferencesHelper

    val requestHeaders by lazy { headersBuilder().build() }

    val glideHeaders by lazy { glideHeadersBuilder().build() }

    init {
        App.get(context).component.inject(this)
    }

    override fun isLoginRequired(): Boolean {
        return false
    }

    protected fun popularMangaRequest(page: MangasPage): Request {
        if (page.page == 1) {
            page.url = initialPopularMangasUrl
        }

        return get(page.url, requestHeaders)
    }

    protected open fun searchMangaRequest(page: MangasPage, query: String): Request {
        if (page.page == 1) {
            page.url = getInitialSearchUrl(query)
        }

        return get(page.url, requestHeaders)
    }

    protected open fun mangaDetailsRequest(mangaUrl: String): Request {
        return get(baseUrl + mangaUrl, requestHeaders)
    }

    protected fun chapterListRequest(mangaUrl: String): Request {
        return get(baseUrl + mangaUrl, requestHeaders)
    }

    protected open fun pageListRequest(chapterUrl: String): Request {
        return get(baseUrl + chapterUrl, requestHeaders)
    }

    protected open fun imageUrlRequest(page: Page): Request {
        return get(page.url, requestHeaders)
    }

    protected open fun imageRequest(page: Page): Request {
        return get(page.imageUrl, requestHeaders)
    }

    // Get the most popular mangas from the source
    open fun pullPopularMangasFromNetwork(page: MangasPage): Observable<MangasPage> {
        return networkService.requestBody(popularMangaRequest(page), true)
                .map { Jsoup.parse(it) }
                .doOnNext { doc -> page.mangas = parsePopularMangasFromHtml(doc) }
                .doOnNext { doc -> page.nextPageUrl = parseNextPopularMangasUrl(doc, page) }
                .map { response -> page }
    }

    // Get mangas from the source with a query
    open fun searchMangasFromNetwork(page: MangasPage, query: String): Observable<MangasPage> {
        return networkService.requestBody(searchMangaRequest(page, query), true)
                .map { Jsoup.parse(it) }
                .doOnNext { doc -> page.mangas = parseSearchFromHtml(doc) }
                .doOnNext { doc -> page.nextPageUrl = parseNextSearchUrl(doc, page, query) }
                .map { response -> page }
    }

    // Get manga details from the source
    open fun pullMangaFromNetwork(mangaUrl: String): Observable<Manga> {
        return networkService.requestBody(mangaDetailsRequest(mangaUrl))
                .flatMap { Observable.just(parseHtmlToManga(mangaUrl, it)) }
    }

    // Get chapter list of a manga from the source
    open fun pullChaptersFromNetwork(mangaUrl: String): Observable<List<Chapter>> {
        return networkService.requestBody(chapterListRequest(mangaUrl))
                .flatMap { unparsedHtml ->
                    val chapters = parseHtmlToChapters(unparsedHtml)
                    if (!chapters.isEmpty())
                        Observable.just(chapters)
                    else
                        Observable.error(Exception("No chapters found"))
                }
    }

    open fun getCachedPageListOrPullFromNetwork(chapterUrl: String): Observable<List<Page>> {
        return chapterCache.getPageListFromCache(getChapterCacheKey(chapterUrl))
                .onErrorResumeNext { pullPageListFromNetwork(chapterUrl) }
                .onBackpressureBuffer()
    }

    open fun pullPageListFromNetwork(chapterUrl: String): Observable<List<Page>> {
        return networkService.requestBody(pageListRequest(chapterUrl))
                .flatMap { unparsedHtml ->
                    val pages = convertToPages(parseHtmlToPageUrls(unparsedHtml))
                    if (!pages.isEmpty())
                        Observable.just(parseFirstPage(pages, unparsedHtml))
                    else
                        Observable.error(Exception("Page list is empty"))
                }
    }

    open fun getAllImageUrlsFromPageList(pages: List<Page>): Observable<Page> {
        return Observable.from(pages)
                .filter { page -> page.imageUrl != null }
                .mergeWith(getRemainingImageUrlsFromPageList(pages))
    }

    // Get the URLs of the images of a chapter
    open fun getRemainingImageUrlsFromPageList(pages: List<Page>): Observable<Page> {
        return Observable.from(pages)
                .filter { page -> page.imageUrl == null }
                .concatMap { getImageUrlFromPage(it) }
    }

    open fun getImageUrlFromPage(page: Page): Observable<Page> {
        page.status = Page.LOAD_PAGE
        return networkService.requestBody(imageUrlRequest(page))
                .flatMap { unparsedHtml -> Observable.just(parseHtmlToImageUrl(unparsedHtml)) }
                .onErrorResumeNext { e ->
                    page.status = Page.ERROR
                    Observable.just<String>(null)
                }
                .flatMap { imageUrl ->
                    page.imageUrl = imageUrl
                    Observable.just(page)
                }
                .subscribeOn(Schedulers.io())
    }

    open fun getCachedImage(page: Page): Observable<Page> {
        val pageObservable = Observable.just(page)
        if (page.imageUrl == null)
            return pageObservable

        return pageObservable
                .flatMap { p ->
                    if (!chapterCache.isImageInCache(page.imageUrl)) {
                        return@flatMap cacheImage(page)
                    }
                    Observable.just(page)
                }
                .flatMap { p ->
                    page.imagePath = chapterCache.getImagePath(page.imageUrl)
                    page.status = Page.READY
                    Observable.just(page)
                }
                .onErrorResumeNext { e ->
                    page.status = Page.ERROR
                    Observable.just(page)
                }
    }

    private fun cacheImage(page: Page): Observable<Page> {
        page.status = Page.DOWNLOAD_IMAGE
        return getImageProgressResponse(page)
                .flatMap { resp ->
                    chapterCache.putImageToCache(page.imageUrl, resp)
                    Observable.just(page)
                }
    }

    open fun getImageProgressResponse(page: Page): Observable<Response> {
        return networkService.requestBodyProgress(imageRequest(page), page)
                .doOnNext {
                    if (!it.isSuccessful) {
                        it.body().close()
                        throw RuntimeException("Not a valid response")
                    }
                }
    }

    fun savePageList(chapterUrl: String, pages: List<Page>?) {
        if (pages != null)
            chapterCache.putPageListToCache(getChapterCacheKey(chapterUrl), pages)
    }

    protected open fun convertToPages(pageUrls: List<String>): List<Page> {
        val pages = ArrayList<Page>()
        for (i in pageUrls.indices) {
            pages.add(Page(i, pageUrls[i]))
        }
        return pages
    }

    protected open fun parseFirstPage(pages: List<Page>, unparsedHtml: String): List<Page> {
        val firstImage = parseHtmlToImageUrl(unparsedHtml)
        pages[0].imageUrl = firstImage
        return pages
    }

    protected fun getChapterCacheKey(chapterUrl: String): String {
        return "$id$chapterUrl"
    }

    // Overridable method to allow custom parsing.
    open fun parseChapterNumber(chapter: Chapter) {

    }

    protected open fun glideHeadersBuilder(): LazyHeaders.Builder {
        val builder = LazyHeaders.Builder()
        for ((key, value) in requestHeaders.toMultimap()) {
            builder.addHeader(key, value[0])
        }

        return builder
    }

}
