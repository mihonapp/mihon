package eu.kanade.tachiyomi.source.online.all

import android.net.Uri
import android.os.Build
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LewdSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.GalleryAddEvent
import exh.HITOMI_SOURCE_ID
import exh.hitomi.HitomiNozomi
import exh.metadata.metadata.HitomiSearchMetadata
import exh.metadata.metadata.HitomiSearchMetadata.Companion.BASE_URL
import exh.metadata.metadata.HitomiSearchMetadata.Companion.LTN_BASE_URL
import exh.metadata.metadata.HitomiSearchMetadata.Companion.TAG_TYPE_DEFAULT
import exh.metadata.metadata.base.RaisedSearchMetadata.Companion.TAG_TYPE_VIRTUAL
import exh.metadata.metadata.base.RaisedTag
import exh.util.urlImportFetchSearchManga
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.vepta.vdm.ByteCursor
import rx.Observable
import rx.Single
import rx.schedulers.Schedulers
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.*

/**
 * Man, I hate this source :(
 */
class Hitomi : HttpSource(), LewdSource<HitomiSearchMetadata, Document>, UrlImportableSource {
    private val prefs: PreferencesHelper by injectLazy()
    private val jsonParser by lazy { JsonParser() }

    override val id = HITOMI_SOURCE_ID

    /**
     * Whether the source has support for latest updates.
     */
    override val supportsLatest = true
    /**
     * Name of the source.
     */
    override val name = "hitomi.la"
    /**
     * The class of the metadata used by this source
     */
    override val metaClass = HitomiSearchMetadata::class

    private var cachedTagIndexVersion: Long? = null
    private var tagIndexVersionCacheTime: Long = 0
    private fun tagIndexVersion(): Single<Long> {
        val sCachedTagIndexVersion = cachedTagIndexVersion
        return if(sCachedTagIndexVersion == null
                || tagIndexVersionCacheTime + INDEX_VERSION_CACHE_TIME_MS < System.currentTimeMillis()) {
            HitomiNozomi.getIndexVersion(client, "tagindex").subscribeOn(Schedulers.io()).doOnNext {
                cachedTagIndexVersion = it
                tagIndexVersionCacheTime = System.currentTimeMillis()
            }.toSingle()
        } else {
            Single.just(sCachedTagIndexVersion)
        }
    }

    private var cachedGalleryIndexVersion: Long? = null
    private var galleryIndexVersionCacheTime: Long = 0
    private fun galleryIndexVersion(): Single<Long> {
        val sCachedGalleryIndexVersion = cachedGalleryIndexVersion
        return if(sCachedGalleryIndexVersion == null
                || galleryIndexVersionCacheTime + INDEX_VERSION_CACHE_TIME_MS < System.currentTimeMillis()) {
            HitomiNozomi.getIndexVersion(client, "galleriesindex").subscribeOn(Schedulers.io()).doOnNext {
                cachedGalleryIndexVersion = it
                galleryIndexVersionCacheTime = System.currentTimeMillis()
            }.toSingle()
        } else {
            Single.just(sCachedGalleryIndexVersion)
        }
    }

    /**
     * Parse the supplied input into the supplied metadata object
     */
    override fun parseIntoMetadata(metadata: HitomiSearchMetadata, input: Document) {
        with(metadata) {
            url = input.location()

            tags.clear()

            thumbnailUrl = "https:" + input.selectFirst(".cover img").attr("src")

            val galleryElement = input.selectFirst(".gallery")

            title = galleryElement.selectFirst("h1").text()
            artists = galleryElement.select("h2 a").map { it.text() }
            tags += artists.map { RaisedTag("artist", it, TAG_TYPE_VIRTUAL) }

            input.select(".gallery-info tr").forEach {
                val content = it.child(1)
                when(it.child(0).text().toLowerCase()) {
                    "group" -> {
                        group = content.text()
                        tags += RaisedTag("group", group!!, TAG_TYPE_VIRTUAL)
                    }
                    "type" -> {
                        type = content.text()
                        tags += RaisedTag("type", type!!, TAG_TYPE_VIRTUAL)
                    }
                    "series" -> {
                        series = content.select("a").map { it.text() }
                        tags += series.map {
                            RaisedTag("series", it, TAG_TYPE_VIRTUAL)
                        }
                    }
                    "language" -> {
                        language = content.selectFirst("a")?.attr("href")?.split('-')?.get(1)
                        language?.let {
                            tags += RaisedTag("language", it, TAG_TYPE_VIRTUAL)
                        }
                    }
                    "characters" -> {
                        characters = content.select("a").map { it.text() }
                        tags += characters.map { RaisedTag("character", it, TAG_TYPE_DEFAULT) }
                    }
                    "tags" -> {
                        tags += content.select("a").map {
                            val ns = if(it.attr("href").startsWith("/tag/male")) "male" else "female"
                            RaisedTag(ns, it.text().dropLast(2), TAG_TYPE_DEFAULT)
                        }
                    }
                }
            }

            uploadDate = DATE_FORMAT.parse(input.selectFirst(".gallery-info .date").text()).time
        }
    }

    override val lang = "all"

    /**
     * Base url of the website without the trailing slash, like: http://mysite.com
     */
    override val baseUrl = BASE_URL

    /**
     * Returns the request for the popular manga given the page.
     *
     * @param page the page number to retrieve.
     */
    override fun popularMangaRequest(page: Int) = HitomiNozomi.rangedGet(
            "$LTN_BASE_URL/popular-all.nozomi",
            100L * (page - 1),
            99L + 100 * (page - 1)
    )

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()

    /**
     * Returns the request for the search manga given the page.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList)
            = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return urlImportFetchSearchManga(query) {
            val splitQuery = query.split(" ")

            val positive = splitQuery.filter { !it.startsWith('-') }.toMutableList()
            val negative = (splitQuery - positive).map { it.removePrefix("-") }

            // TODO Cache the results coming out of HitomiNozomi
            val hn = Single.zip(tagIndexVersion(), galleryIndexVersion()) { tv, gv -> tv to gv }
                    .map { HitomiNozomi(client, it.first, it.second) }

            var base = if(positive.isEmpty()) {
                hn.flatMap { n -> n.getGalleryIdsFromNozomi(null, "index", "all").map { n to it.toSet() } }
            } else {
                val q = positive.removeAt(0)
                hn.flatMap { n -> n.getGalleryIdsForQuery(q).map { n to it.toSet() } }
            }

            base = positive.fold(base) { acc, q ->
                acc.flatMap { (nozomi, mangas) ->
                    nozomi.getGalleryIdsForQuery(q).map {
                        nozomi to mangas.intersect(it)
                    }
                }
            }

            base = negative.fold(base) { acc, q ->
                acc.flatMap { (nozomi, mangas) ->
                    nozomi.getGalleryIdsForQuery(q).map {
                        nozomi to (mangas - it)
                    }
                }
            }

            base.flatMap { (_, ids) ->
                val chunks = ids.chunked(PAGE_SIZE)

                nozomiIdsToMangas(chunks[page - 1]).map { mangas ->
                    MangasPage(mangas, page < chunks.size)
                }
            }.toObservable()
        }
    }

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    /**
     * Returns the request for latest manga given the page.
     *
     * @param page the page number to retrieve.
     */
    override fun latestUpdatesRequest(page: Int) = HitomiNozomi.rangedGet(
            "$LTN_BASE_URL/index-all.nozomi",
            100L * (page - 1),
            99L + 100 * (page - 1)
    )

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
                .asObservableSuccess()
                .flatMap { responseToMangas(it) }
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page))
                .asObservableSuccess()
                .flatMap { responseToMangas(it) }
    }

    fun responseToMangas(response: Response): Observable<MangasPage> {
        val range = response.header("Content-Range")!!
        val total = range.substringAfter('/').toLong()
        val end = range.substringBefore('/').substringAfter('-').toLong()
        val body = response.body()!!
        return parseNozomiPage(body.bytes())
                .map {
                    MangasPage(it, end < total - 1)
                }
    }

    private fun parseNozomiPage(array: ByteArray): Observable<List<SManga>> {
        val cursor = ByteCursor(array)
        val ids = (1 .. array.size / 4).map {
            cursor.nextInt()
        }

        return nozomiIdsToMangas(ids).toObservable()
    }

    private fun nozomiIdsToMangas(ids: List<Int>): Single<List<SManga>> {
        return Single.zip(ids.map {
            client.newCall(GET("$LTN_BASE_URL/galleryblock/$it.html"))
                    .asObservableSuccess()
                    .subscribeOn(Schedulers.io()) // Perform all these requests in parallel
                    .map { parseGalleryBlock(it) }
                    .toSingle()
        }) { it.map { m -> m as SManga } }
    }

    private fun parseGalleryBlock(response: Response): SManga {
        val doc = response.asJsoup()
        return SManga.create().apply {
            val titleElement = doc.selectFirst("h1")
            title = titleElement.text()
            thumbnail_url = "https:" + if(prefs.eh_hl_useHighQualityThumbs().getOrDefault()) {
                doc.selectFirst("img").attr("data-srcset").substringBefore(' ')
            } else {
                doc.selectFirst("img").attr("data-src")
            }
            url = titleElement.child(0).attr("href")

            // TODO Parse tags and stuff
        }
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
                    parseToManga(manga, it.asJsoup()).andThen(Observable.just(manga.apply {
                        initialized = true
                    }))
                }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
                listOf(
                        SChapter.create().apply {
                            url = manga.url
                            name = "Chapter"
                            chapter_number = 0.0f
                        }
                )
        )
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$LTN_BASE_URL/galleries/${HitomiSearchMetadata.hlIdFromUrl(chapter.url)}.js")
    }

    /**
     * Parses the response from the site and returns the details of a manga.
     *
     * @param response the response from the site.
     */
    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    /**
     * Parses the response from the site and returns a list of chapters.
     *
     * @param response the response from the site.
     */
    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    /**
     * Parses the response from the site and returns a list of pages.
     *
     * @param response the response from the site.
     */
    override fun pageListParse(response: Response): List<Page> {
        val hlId = response.request().url().pathSegments().last().removeSuffix(".js").toLong()
        val str = response.body()!!.string()
        val json = jsonParser.parse(str.removePrefix("var galleryinfo ="))
        return json.array.mapIndexed { index, jsonElement ->
            Page(
                    index,
                    "",
            "https://${subdomainFromGalleryId(hlId)}a.hitomi.la/galleries/$hlId/${jsonElement["name"].string}"
            )
        }
    }

    private fun subdomainFromGalleryId(id: Long): Char {
        return (97 + id.rem(NUMBER_OF_FRONTENDS)).toChar()
    }

    /**
     * Parses the response from the site and returns the absolute url to the source image.
     *
     * @param response the response from the site.
     */
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val request = super.imageRequest(page)
        val hlId = request.url().pathSegments().let {
            it[it.lastIndex - 1]
        }
        return request.newBuilder()
                .header("Referer", "$BASE_URL/reader/$hlId.html")
                .build()
    }

    override val matchingHosts = listOf(
            "hitomi.la"
    )

    override fun mapUrlToMangaUrl(uri: Uri): String? {
        val lcFirstPathSegment = uri.pathSegments.firstOrNull()?.toLowerCase() ?: return null

        if(lcFirstPathSegment != "galleries" && lcFirstPathSegment != "reader")
            return null

        return "https://hitomi.la/galleries/${uri.pathSegments[1].substringBefore('.')}.html"
    }

    companion object {
        private val INDEX_VERSION_CACHE_TIME_MS = 1000 * 60 * 10
        private val PAGE_SIZE = 25
        private val NUMBER_OF_FRONTENDS = 2

        private val DATE_FORMAT by lazy {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                SimpleDateFormat("yyyy-MM-dd HH:mm:ssX", Locale.US)
            else
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss'-05'", Locale.US)
        }
    }

}
