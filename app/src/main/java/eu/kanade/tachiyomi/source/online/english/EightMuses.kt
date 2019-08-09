package eu.kanade.tachiyomi.source.online.english

import android.net.Uri
import com.kizitonwose.time.hours
import com.lvla.rxjava.interopkt.toV1Single
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LewdSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.EIGHTMUSES_SOURCE_ID
import exh.metadata.metadata.EightMusesSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.util.CachedField
import exh.util.NakedTrie
import exh.util.await
import exh.util.urlImportFetchSearchManga
import kotlinx.coroutines.*
import kotlinx.coroutines.rx2.asSingle
import okhttp3.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import rx.schedulers.Schedulers

typealias SiteMap = NakedTrie<Unit>

class EightMuses: HttpSource(),
        LewdSource<EightMusesSearchMetadata, Document>,
        UrlImportableSource {
    override val id = EIGHTMUSES_SOURCE_ID

    /**
     * Name of the source.
     */
    override val name = "8muses"
    /**
     * Whether the source has support for latest updates.
     */
    override val supportsLatest = true
    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    override val lang: String = "en"

    override val metaClass = EightMusesSearchMetadata::class

    /**
     * Base url of the website without the trailing slash, like: http://mysite.com
     */
    override val baseUrl = EightMusesSearchMetadata.BASE_URL

    private val siteMapCache = CachedField<SiteMap>(1.hours.inMilliseconds.longValue)

    override val client: OkHttpClient
        get() = network.cloudflareClient

    private suspend fun obtainSiteMap() = siteMapCache.obtain {
        withContext(Dispatchers.IO) {
            val result = client.newCall(eightMusesGet("$baseUrl/sitemap/1.xml"))
                    .asObservableSuccess()
                    .toSingle()
                    .await(Schedulers.io())
                    .body()!!.string()

            val parsed = Jsoup.parse(result)

            val seen = NakedTrie<Unit>()

            parsed.getElementsByTag("loc").forEach { item ->
                seen[item.text().substring(22)] = Unit
            }

            seen
        }
    }

    override fun headersBuilder(): Headers.Builder {
        return Headers.Builder()
                .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;")
                .add("Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8")
                .add("Referer", "https://www.8muses.com")
                .add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.142 Safari/537.36")
    }

    private fun eightMusesGet(url: String): Request {
        return GET(url, headers = headersBuilder().build())
    }

    /**
     * Returns the request for the popular manga given the page.
     *
     * @param page the page number to retrieve.
     */
    override fun popularMangaRequest(page: Int) = eightMusesGet("$baseUrl/comics/$page")

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun popularMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException("Should not be called!")
    }

    /**
     * Returns the request for the search manga given the page.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = if(!query.isBlank()) {
            HttpUrl.parse("$baseUrl/search")!!
                    .newBuilder()
                    .addQueryParameter("q", query)
        } else {
            HttpUrl.parse("$baseUrl/comics")!!
                    .newBuilder()
        }

        urlBuilder.addQueryParameter("page", page.toString())

        filters.filterIsInstance<SortFilter>().map {
            it.addToUri(urlBuilder)
        }

        return eightMusesGet(urlBuilder.toString())
    }

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun searchMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException("Should not be called!")
    }

    /**
     * Returns the request for latest manga given the page.
     *
     * @param page the page number to retrieve.
     */
    override fun latestUpdatesRequest(page: Int) = eightMusesGet("$baseUrl/comics/lastupdate?page=$page")

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun latestUpdatesParse(response: Response): MangasPage {
        throw UnsupportedOperationException("Should not be called!")
    }

    override fun fetchLatestUpdates(page: Int)
            = fetchListing(latestUpdatesRequest(page), false)

    override fun fetchPopularManga(page: Int)
            = fetchListing(popularMangaRequest(page), false) // TODO Dig

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return urlImportFetchSearchManga(query) {
            fetchListing(searchMangaRequest(page, query, filters), false)
        }
    }

    private fun fetchListing(request: Request, dig: Boolean): Observable<MangasPage> {
        return client.newCall(request)
                .asObservableSuccess()
                .flatMapSingle { response ->
                    GlobalScope.async(Dispatchers.IO) {
                        parseResultsPage(response, dig)
                    }.asSingle(GlobalScope.coroutineContext).toV1Single()
                }
    }

    private suspend fun parseResultsPage(response: Response, dig: Boolean): MangasPage {
        val doc = response.asJsoup()
        val contents = parseSelf(doc)

        val onLastPage = doc.selectFirst(".current:nth-last-child(2)") != null

        return MangasPage(
                if(dig) {
                    contents.albums.flatMap {
                        val href = it.attr("href")
                        val splitHref = href.split('/')
                        obtainSiteMap().subMap(href).filter {
                            it.key.split('/').size - splitHref.size == 1
                        }.map { (key, _) ->
                            SManga.create().apply {
                                url = key

                                title = key.substringAfterLast('/').replace('-', ' ')
                            }
                        }
                    }
                } else {
                    contents.albums.map {
                        SManga.create().apply {
                            url = it.attr("href")

                            title = it.select(".title-text").text()

                            thumbnail_url = baseUrl + it.select(".lazyload").attr("data-src")
                        }
                    }
                },
                !onLastPage
        )
    }

    /**
     * Parses the response from the site and returns the details of a manga.
     *
     * @param response the response from the site.
     */
    override fun mangaDetailsParse(response: Response): SManga {
        throw UnsupportedOperationException("Should not be called!")
    }

    /**
     * Returns an observable with the updated details for a manga. Normally it's not needed to
     * override this method.
     *
     * @param manga the manga to be updated.
     */
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
                .asObservableSuccess()
                .flatMap {
                    parseToManga(manga, it.asJsoup()).andThen(Observable.just(manga))
                }
    }

    /**
     * Parses the response from the site and returns a list of chapters.
     *
     * @param response the response from the site.
     */
    override fun chapterListParse(response: Response): List<SChapter> {
        throw UnsupportedOperationException("Should not be called!")
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return GlobalScope.async(Dispatchers.IO) {
            fetchAndParseChapterList("", manga.url)
        }.asSingle(GlobalScope.coroutineContext).toV1Single().toObservable()
    }

    private suspend fun fetchAndParseChapterList(prefix: String, url: String): List<SChapter> {
        // Request
        val req = eightMusesGet(baseUrl + url)

        return client.newCall(req).asObservableSuccess().toSingle().await(Schedulers.io()).use { response ->
            val contents = parseSelf(response.asJsoup())

            val out = mutableListOf<SChapter>()
            if(contents.images.isNotEmpty()) {
                out += SChapter.create().apply {
                    this.url = url
                    this.name = if(prefix.isBlank()) ">" else prefix
                }
            }

            val builtPrefix = if(prefix.isBlank()) "> " else "$prefix > "

            out + contents.albums.flatMap { ele ->
                fetchAndParseChapterList(builtPrefix + ele.selectFirst(".title-text").text(), ele.attr("href"))
            }
        }
    }

    data class SelfContents(val albums: List<Element>, val images: List<Element>)
    private fun parseSelf(doc: Document): SelfContents {
        // Parse self
        val gc = doc.select(".gallery .c-tile")

        // Check if any in self
        val selfAlbums = gc.filter { it.attr("href").startsWith("/comics/album") }
        val selfImages = gc.filter { it.attr("href").startsWith("/comics/picture") }

        return SelfContents(selfAlbums, selfImages)
    }

    /**
     * Parses the response from the site and returns a list of pages.
     *
     * @param response the response from the site.
     */
    override fun pageListParse(response: Response): List<Page> {
        val contents = parseSelf(response.asJsoup())
        return contents.images.mapIndexed { index, element ->
            Page(
                    index,
                    element.attr("href"),
                    "$baseUrl/image/fl" + element.select(".lazyload").attr("data-src").substring(9)
            )
        }
    }

    override fun parseIntoMetadata(metadata: EightMusesSearchMetadata, input: Document) {
        with(metadata) {
            path = Uri.parse(input.location()).pathSegments

            val breadcrumbs = input.selectFirst(".top-menu-breadcrumb > ol")

            title = breadcrumbs.selectFirst("li:nth-last-child(1) > a").text()

            thumbnailUrl = parseSelf(input).let { it.albums + it.images }.firstOrNull()
                    ?.selectFirst(".lazyload")
                    ?.attr("data-src")?.let {
                        baseUrl + it
                    }

            tags.clear()
            tags += RaisedTag(
                    EightMusesSearchMetadata.ARTIST_NAMESPACE,
                    breadcrumbs.selectFirst("li:nth-child(2) > a").text(),
                    EightMusesSearchMetadata.TAG_TYPE_DEFAULT
            )
            tags += input.select(".album-tags a").map {
                RaisedTag(
                        EightMusesSearchMetadata.TAGS_NAMESPACE,
                        it.text(),
                        EightMusesSearchMetadata.TAG_TYPE_DEFAULT
                )
            }
        }
    }

    class SortFilter : Filter.Select<String>(
            "Sort",
            SORT_OPTIONS.map { it.second }.toTypedArray()
    ) {
        fun addToUri(url: HttpUrl.Builder) {
            url.addQueryParameter("sort", SORT_OPTIONS[state].first)
        }

        companion object {
            // <Internal, Display>
            private val SORT_OPTIONS = listOf(
                    "" to "Views",
                    "like" to "Likes",
                    "date" to "Date",
                    "az" to "A-Z"
            )
        }
    }

    override fun getFilterList() = FilterList(
            SortFilter()
    )

    /**
     * Parses the response from the site and returns the absolute url to the source image.
     *
     * @param response the response from the site.
     */
    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException("Should not be called!")
    }

    override val matchingHosts = listOf(
            "www.8muses.com",
            "8muses.com"
    )

    override fun mapUrlToMangaUrl(uri: Uri): String? {
        var path = uri.pathSegments.drop(2)
        if(uri.pathSegments[1].toLowerCase() == "picture") {
            path = path.dropLast(1)
        }
        return "/comics/album/${path.joinToString("/")}"
    }
}