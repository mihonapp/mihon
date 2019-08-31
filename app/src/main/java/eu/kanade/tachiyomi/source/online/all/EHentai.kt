package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.net.Uri
import com.elvishew.xlog.XLog
import com.github.salomonbrys.kotson.*
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.asObservableWithAsyncStacktrace
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LewdSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.eh.EHentaiUpdateHelper
import exh.metadata.EX_DATE_FORMAT
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.EHentaiSearchMetadata.Companion.EH_GENRE_NAMESPACE
import exh.metadata.metadata.EHentaiSearchMetadata.Companion.TAG_TYPE_LIGHT
import exh.metadata.metadata.EHentaiSearchMetadata.Companion.TAG_TYPE_NORMAL
import exh.metadata.metadata.base.RaisedSearchMetadata.Companion.TAG_TYPE_VIRTUAL
import exh.metadata.nullIfBlank
import exh.metadata.parseHumanReadableByteCount
import exh.debug.DebugToggles
import exh.ui.login.LoginController
import exh.util.UriFilter
import exh.util.UriGroup
import exh.util.ignore
import exh.util.urlImportFetchSearchManga
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder
import java.util.*
import exh.metadata.metadata.base.RaisedTag
import exh.eh.EHentaiUpdateWorkerConstants
import exh.eh.GalleryEntry
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.TextNode
import rx.Single
import java.lang.RuntimeException

// TODO Consider gallery updating when doing tabbed browsing
class EHentai(override val id: Long,
              val exh: Boolean,
              val context: Context) : HttpSource(), LewdSource<EHentaiSearchMetadata, Document>, UrlImportableSource {
    override val metaClass = EHentaiSearchMetadata::class

    val schema: String
        get() = if(prefs.secureEXH().getOrDefault())
            "https"
        else
            "http"

    val domain: String
        get() = if(exh)
            "exhentai.org"
        else
            "e-hentai.org"

    override val baseUrl: String
        get() = "$schema://$domain"

    override val lang = "all"
    override val supportsLatest = true

    private val prefs: PreferencesHelper by injectLazy()
    private val updateHelper: EHentaiUpdateHelper by injectLazy()

    /**
     * Gallery list entry
     */
    data class ParsedManga(val fav: Int, val manga: Manga)

    fun extendedGenericMangaParse(doc: Document)
            = with(doc) {
        // Parse mangas (supports compact + extended layout)
        val parsedMangas = select(".itg > tbody > tr").filter {
            // Do not parse header and ads
            it.selectFirst("th") == null && it.selectFirst(".itd") == null
        }.map {
            val thumbnailElement = it.selectFirst(".gl1e img, .gl2c .glthumb img")
            val column2 = it.selectFirst(".gl3e, .gl2c")
            val linkElement = it.selectFirst(".gl3c > a, .gl2e > div > a")

            val favElement = column2.children().find { it.attr("style").startsWith("border-color") }

            ParsedManga(
                    fav = FAVORITES_BORDER_HEX_COLORS.indexOf(
                            favElement?.attr("style")?.substring(14, 17)
                    ),
                    manga = Manga.create(id).apply {
                        //Get title
                        title = thumbnailElement.attr("title")
                        url = EHentaiSearchMetadata.normalizeUrl(linkElement.attr("href"))
                        //Get image
                        thumbnail_url = thumbnailElement.attr("src")

                        // TODO Parse genre + uploader + tags
                    })
        }

        val parsedLocation = HttpUrl.parse(doc.location())

        //Add to page if required
        val hasNextPage = if(parsedLocation == null
                || !parsedLocation.queryParameterNames().contains(REVERSE_PARAM)) {
            select("a[onclick=return false]").last()?.let {
                it.text() == ">"
            } ?: false
        } else {
            parsedLocation.queryParameter(REVERSE_PARAM)!!.toBoolean()
        }
        Pair(parsedMangas, hasNextPage)
    }

    /**
     * Parse a list of galleries
     */
    fun genericMangaParse(response: Response)
            = extendedGenericMangaParse(response.asJsoup()).let {
        MangasPage(it.first.map { it.manga }, it.second)
    }

    override fun fetchChapterList(manga: SManga)
            = fetchChapterList(manga) {}

    fun fetchChapterList(manga: SManga, throttleFunc: () -> Unit): Observable<List<SChapter>> {
        return Single.fromCallable {
            // Pull all the way to the root gallery
            // We can't do this with RxJava or we run into stack overflows on shit like this:
            //   https://exhentai.org/g/1073061/f9345f1c12/
            var url: String = manga.url
            var doc: Document? = null

            runBlocking {
                while (true) {
                    val gid = EHentaiSearchMetadata.galleryId(url).toInt()
                    val cachedParent = updateHelper.parentLookupTable.get(
                            gid
                    )
                    if(cachedParent == null) {
                        throttleFunc()

                        val resp = client.newCall(exGet(baseUrl + url)).execute()
                        if (!resp.isSuccessful) error("HTTP error (${resp.code()})!")
                        doc = resp.asJsoup()

                        val parentLink = doc!!.select("#gdd .gdt1").find { el ->
                            el.text().toLowerCase() == "parent:"
                        }!!.nextElementSibling().selectFirst("a")?.attr("href")

                        if (parentLink != null) {
                            updateHelper.parentLookupTable.put(
                                    gid,
                                    GalleryEntry(
                                            EHentaiSearchMetadata.galleryId(parentLink),
                                            EHentaiSearchMetadata.galleryToken(parentLink)
                                    )
                            )
                            url = EHentaiSearchMetadata.normalizeUrl(parentLink)
                        } else break
                    } else {
                        XLog.d("Parent cache hit: %s!", gid)
                        url = EHentaiSearchMetadata.idAndTokenToUrl(
                                cachedParent.gId,
                                cachedParent.gToken
                        )
                    }
                }
            }

            doc!!
        }.map { d ->
            val newDisplay = d.select("#gnd a")
            // Build chapter for root gallery
            val self = SChapter.create().apply {
                url = EHentaiSearchMetadata.normalizeUrl(d.location())
                name = "v1: " + d.selectFirst("#gn").text()
                chapter_number = 1f
                date_upload = EX_DATE_FORMAT.parse(d.select("#gdd .gdt1").find { el ->
                    el.text().toLowerCase() == "posted:"
                }!!.nextElementSibling().text()).time
            }
            // Build and append the rest of the galleries
            if(DebugToggles.INCLUDE_ONLY_ROOT_WHEN_LOADING_EXH_VERSIONS.enabled) listOf(self)
            else {
                newDisplay.mapIndexed { index, newGallery ->
                    val link = newGallery.attr("href")
                    val name = newGallery.text()
                    val posted = (newGallery.nextSibling() as TextNode).text().removePrefix(", added ")
                    SChapter.create().apply {
                        this.url = EHentaiSearchMetadata.normalizeUrl(link)
                        this.name = "v${index + 2}: $name"
                        this.chapter_number = index + 2f
                        this.date_upload = EX_DATE_FORMAT.parse(posted).time
                    }
                }.reversed() + self
            }
        }.toObservable()
    }

    override fun fetchPageList(chapter: SChapter)
            = fetchChapterPage(chapter, baseUrl + chapter.url).map {
        it.mapIndexed { i, s ->
            Page(i, s)
        }
    }!!

    private fun fetchChapterPage(chapter: SChapter, np: String,
                                 pastUrls: List<String> = emptyList()): Observable<List<String>> {
        val urls = ArrayList(pastUrls)
        return chapterPageCall(np).flatMap {
            val jsoup = it.asJsoup()
            urls += parseChapterPage(jsoup)
            val nextUrl = nextPageUrl(jsoup)
            if(nextUrl != null) {
                fetchChapterPage(chapter, nextUrl, urls)
            } else {
                Observable.just(urls)
            }
        }
    }
    private fun parseChapterPage(response: Element)
            = with(response) {
        select(".gdtm a").map {
            Pair(it.child(0).attr("alt").toInt(), it.attr("href"))
        }.sortedBy(Pair<Int, String>::first).map { it.second }
    }
    private fun chapterPageCall(np: String) = client.newCall(chapterPageRequest(np)).asObservableSuccess()
    private fun chapterPageRequest(np: String) = exGet(np, null, headers)

    private fun nextPageUrl(element: Element): String?
            = element.select("a[onclick=return false]").last()?.let {
        return if (it.text() == ">") it.attr("href") else null
    }

    override fun popularMangaRequest(page: Int) = if(exh)
        latestUpdatesRequest(page)
    else
        exGet("$baseUrl/toplist.php?tl=15&p=${page - 1}", null) // Custom page logic for toplists

    //Support direct URL importing
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
            urlImportFetchSearchManga(query) {
                searchMangaRequestObservable(page, query, filters).flatMap {
                    client.newCall(it).asObservableSuccess()
                } .map { response ->
                    searchMangaParse(response)
                }
            }

    private fun searchMangaRequestObservable(page: Int, query: String, filters: FilterList): Observable<Request> {
        val uri = Uri.parse("$baseUrl$QUERY_PREFIX").buildUpon()
        uri.appendQueryParameter("f_search", query)
        filters.forEach {
            if(it is UriFilter) it.addToUri(uri)
        }

        val request = exGet(uri.toString(), page)

        // Reverse search results on filter
        if(filters.any { it is ReverseFilter && it.state }) {
            return client.newCall(request)
                    .asObservableSuccess()
                    .map {
                        val doc = it.asJsoup()

                        val elements = doc.select(".ptt > tbody > tr > td")

                        val totalElement = elements[elements.size - 2]

                        val thisPage = totalElement.text().toInt() - (page - 1)

                        uri.appendQueryParameter(REVERSE_PARAM, (thisPage > 1).toString())

                        exGet(uri.toString(), thisPage)
                    }
        } else {
            return Observable.just(request)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList)
            = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int) = exGet(baseUrl, page)

    override fun popularMangaParse(response: Response) = genericMangaParse(response)
    override fun searchMangaParse(response: Response) = genericMangaParse(response)
    override fun latestUpdatesParse(response: Response) = genericMangaParse(response)

    fun exGet(url: String, page: Int? = null, additionalHeaders: Headers? = null, cache: Boolean = true)
            = GET(page?.let {
        addParam(url, "page", Integer.toString(page - 1))
    } ?: url, additionalHeaders?.let {
        val headers = headers.newBuilder()
        it.toMultimap().forEach { (t, u) ->
            u.forEach {
                headers.add(t, it)
            }
        }
        headers.build()
    } ?: headers).let {
        if(!cache)
            it.newBuilder().cacheControl(CacheControl.FORCE_NETWORK).build()
        else
            it
    }!!

    /**
     * Returns an observable with the updated details for a manga. Normally it's not needed to
     * override this method.
     *
     * @param manga the manga to be updated.
     */
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
                .asObservableWithAsyncStacktrace()
                .flatMap { (stacktrace, response) ->
                    if(response.isSuccessful) {
                        // Pull to most recent
                        val doc = response.asJsoup()
                        val newerGallery = doc.select("#gnd a").lastOrNull()
                        val pre = if(newerGallery != null && DebugToggles.PULL_TO_ROOT_WHEN_LOADING_EXH_MANGA_DETAILS.enabled) {
                            manga.url = EHentaiSearchMetadata.normalizeUrl(newerGallery.attr("href"))
                            client.newCall(mangaDetailsRequest(manga))
                                    .asObservableSuccess().map { it.asJsoup() }
                        } else Observable.just(doc)

                        pre.flatMap {
                            parseToManga(manga, it).andThen(Observable.just(manga.apply {
                                initialized = true
                            }))
                        }
                    } else {
                        response.close()

                        if(response.code() == 404) {
                            throw GalleryNotFoundException(stacktrace)
                        } else {
                            throw Exception("HTTP error ${response.code()}", stacktrace)
                        }
                    }
                }
    }

    /**
     * Parse gallery page to metadata model
     */
    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    override fun parseIntoMetadata(metadata: EHentaiSearchMetadata, input: Document) {
        with(metadata) {
            with(input) {
                val url = input.location()
                gId = EHentaiSearchMetadata.galleryId(url)
                gToken = EHentaiSearchMetadata.galleryToken(url)

                exh = this@EHentai.exh
                title = select("#gn").text().nullIfBlank()?.trim()

                altTitle = select("#gj").text().nullIfBlank()?.trim()

                thumbnailUrl = select("#gd1 div").attr("style").nullIfBlank()?.let {
                    it.substring(it.indexOf('(') + 1 until it.lastIndexOf(')'))
                }
                genre = select(".cs")
                        .attr("onclick")
                        .nullIfBlank()
                        ?.trim()
                        ?.substringAfterLast('/')
                        ?.removeSuffix("'")

                uploader = select("#gdn").text().nullIfBlank()?.trim()

                //Parse the table
                select("#gdd tr").forEach {
                    val left = it.select(".gdt1").text().nullIfBlank()?.trim()
                    val rightElement = it.selectFirst(".gdt2")
                    val right = rightElement.text().nullIfBlank()?.trim()
                    if(left != null && right != null) {
                        ignore {
                            when (left.removeSuffix(":")
                                    .toLowerCase()) {
                                "posted" -> datePosted = EX_DATE_FORMAT.parse(right).time
                                // Example gallery with parent: https://e-hentai.org/g/1390451/7f181c2426/
                                // Example JP gallery: https://exhentai.org/g/1375385/03519d541b/
                                // Parent is older variation of the gallery
                                "parent" -> parent = if (!right.equals("None", true)) {
                                    rightElement.child(0).attr("href")
                                } else null
                                "visible" -> visible = right.nullIfBlank()
                                "language" -> {
                                    language = right.removeSuffix(TR_SUFFIX).trim().nullIfBlank()
                                    translated = right.endsWith(TR_SUFFIX, true)
                                }
                                "file size" -> size = parseHumanReadableByteCount(right)?.toLong()
                                "length" -> length = right.removeSuffix("pages").trim().nullIfBlank()?.toInt()
                                "favorited" -> favorites = right.removeSuffix("times").trim().nullIfBlank()?.toInt()
                            }
                        }
                    }
                }

                lastUpdateCheck = System.currentTimeMillis()
                if(datePosted != null
                        && lastUpdateCheck - datePosted!! > EHentaiUpdateWorkerConstants.GALLERY_AGE_TIME) {
                    aged = true
                }

                //Parse ratings
                ignore {
                    averageRating = select("#rating_label")
                            .text()
                            .removePrefix("Average:")
                            .trim()
                            .nullIfBlank()
                            ?.toDouble()
                    ratingCount = select("#rating_count")
                            .text()
                            .trim()
                            .nullIfBlank()
                            ?.toInt()
                }

                //Parse tags
                tags.clear()
                select("#taglist tr").forEach {
                    val namespace = it.select(".tc").text().removeSuffix(":")
                    tags.addAll(it.select("div").map { element ->
                        RaisedTag(
                                namespace,
                                element.text().trim(),
                                if(element.hasClass("gtl"))
                                    TAG_TYPE_LIGHT
                                else
                                    TAG_TYPE_NORMAL
                        )
                    })
                }

                // Add genre as virtual tag
                genre?.let {
                    tags.add(RaisedTag(EH_GENRE_NAMESPACE, it, TAG_TYPE_VIRTUAL))
                }
            }
        }
    }

    override fun chapterListParse(response: Response)
            = throw UnsupportedOperationException("Unused method was called somehow!")

    override fun pageListParse(response: Response)
            = throw UnsupportedOperationException("Unused method was called somehow!")

    override fun fetchImageUrl(page: Page): Observable<String> {
        return client.newCall(imageUrlRequest(page))
                .asObservableSuccess()
                .map { realImageUrlParse(it, page) }
    }

    fun realImageUrlParse(response: Response, page: Page): String {
        with(response.asJsoup()) {
            val currentImage = getElementById("img").attr("src")
            //Each press of the retry button will choose another server
            select("#loadfail").attr("onclick").nullIfBlank()?.let {
                page.url = addParam(page.url, "nl", it.substring(it.indexOf('\'') + 1 until it.lastIndexOf('\'')))
            }
            return currentImage
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException("Unused method was called somehow!")
    }

    fun fetchFavorites(): Pair<List<ParsedManga>, List<String>> {
        val favoriteUrl = "$baseUrl/favorites.php"
        val result = mutableListOf<ParsedManga>()
        var page = 1

        var favNames: List<String>? = null

        do {
            val response2 = client.newCall(exGet(favoriteUrl,
                    page = page,
                    cache = false)).execute()
            val doc = response2.asJsoup()

            //Parse favorites
            val parsed = extendedGenericMangaParse(doc)
            result += parsed.first

            //Parse fav names
            if (favNames == null)
                favNames = doc.select(".fp:not(.fps)").mapNotNull {
                    it.child(2).text()
                }

            //Next page
            page++
        } while (parsed.second)

        return Pair(result as List<ParsedManga>, favNames!!)
    }

    fun spPref() = if(exh)
        prefs.eh_exhSettingsProfile()
    else
        prefs.eh_ehSettingsProfile()

    fun rawCookies(sp: Int): Map<String, String> {
        val cookies: MutableMap<String, String> = mutableMapOf()
        if(prefs.enableExhentai().getOrDefault()) {
            cookies[LoginController.MEMBER_ID_COOKIE] = prefs.memberIdVal().get()!!
            cookies[LoginController.PASS_HASH_COOKIE] = prefs.passHashVal().get()!!
            cookies[LoginController.IGNEOUS_COOKIE] = prefs.igneousVal().get()!!
            cookies["sp"] = sp.toString()

            val sessionKey = prefs.eh_settingsKey().getOrDefault()
            if(sessionKey != null)
                cookies["sk"] = sessionKey

            val sessionCookie = prefs.eh_sessionCookie().getOrDefault()
            if(sessionCookie != null)
                cookies["s"] = sessionCookie

            val hathPerksCookie = prefs.eh_hathPerksCookies().getOrDefault()
            if(hathPerksCookie != null)
                cookies["hath_perks"] = hathPerksCookie
        }

        // Session-less extended display mode (for users without ExHentai)
        cookies["sl"] = "dm_2"

        // Ignore all content warnings
        cookies["nw"] = "1"

        return cookies
    }

    fun cookiesHeader(sp: Int = spPref().getOrDefault())
            = buildCookies(rawCookies(sp))

    //Headers
    override fun headersBuilder()
            = super.headersBuilder().add("Cookie", cookiesHeader())!!

    fun addParam(url: String, param: String, value: String)
            = Uri.parse(url)
            .buildUpon()
            .appendQueryParameter(param, value)
            .toString()

    override val client = network.client.newBuilder()
            .cookieJar(CookieJar.NO_COOKIES)
            .addInterceptor { chain ->
                val newReq = chain
                        .request()
                        .newBuilder()
                        .removeHeader("Cookie")
                        .addHeader("Cookie", cookiesHeader())
                        .build()

                chain.proceed(newReq)
            }.build()!!

    //Filters
    override fun getFilterList() = FilterList(
            GenreGroup(),
            AdvancedGroup(),
            ReverseFilter()
    )

    class GenreOption(name: String, val genreId: Int): Filter.CheckBox(name, false)
    class GenreGroup : Filter.Group<GenreOption>("Genres", listOf(
            GenreOption("Dōjinshi", 2),
            GenreOption("Manga", 4),
            GenreOption("Artist CG", 8),
            GenreOption("Game CG", 16),
            GenreOption("Western", 512),
            GenreOption("Non-H", 256),
            GenreOption("Image Set", 32),
            GenreOption("Cosplay", 64),
            GenreOption("Asian Porn", 128),
            GenreOption("Misc", 1)
    )), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            val bits = state.fold(0) { acc, genre ->
                if(!genre.state) acc + genre.genreId else acc
            }
            builder.appendQueryParameter("f_cats", bits.toString())
        }
    }

    class AdvancedOption(name: String, val param: String, defValue: Boolean = false): Filter.CheckBox(name, defValue), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if(state)
                builder.appendQueryParameter(param, "on")
        }
    }

    open class PageOption(name: String, private val queryKey: String) : Filter.Text(name), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if(state.isNotBlank()) {
                if (builder.build().getQueryParameters("f_sp").isEmpty()) {
                    builder.appendQueryParameter("f_sp", "on")
                }

                builder.appendQueryParameter(queryKey, state.trim())
            }
        }
    }
    class MinPagesOption : PageOption("Minimum Pages", "f_spf")
    class MaxPagesOption : PageOption("Maximum Pages", "f_spt")

    class RatingOption : Filter.Select<String>("Minimum Rating", arrayOf(
            "Any",
            "2 stars",
            "3 stars",
            "4 stars",
            "5 stars"
    )), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if(state > 0) {
                builder.appendQueryParameter("f_srdd", Integer.toString(state + 1))
                builder.appendQueryParameter("f_sr", "on")
            }
        }
    }

    class AdvancedGroup : UriGroup<Filter<*>>("Advanced Options", listOf(
            AdvancedOption("Search Gallery Name", "f_sname", true),
            AdvancedOption("Search Gallery Tags", "f_stags", true),
            AdvancedOption("Search Gallery Description", "f_sdesc"),
            AdvancedOption("Search Torrent Filenames", "f_storr"),
            AdvancedOption("Only Show Galleries With Torrents", "f_sto"),
            AdvancedOption("Search Low-Power Tags", "f_sdt1"),
            AdvancedOption("Search Downvoted Tags", "f_sdt2"),
            AdvancedOption("Show Expunged Galleries", "f_sh"),
            RatingOption(),
            MinPagesOption(),
            MaxPagesOption()
    ))

    class ReverseFilter : Filter.CheckBox("Reverse search results")

    override val name = if(exh)
        "ExHentai"
    else
        "E-Hentai"

    class GalleryNotFoundException(cause: Throwable): RuntimeException("Gallery not found!", cause)

     // === URL IMPORT STUFF

    override val matchingHosts: List<String> = if(exh) listOf(
            "exhentai.org"
    ) else listOf(
            "g.e-hentai.org",
            "e-hentai.org"
    )

    override fun mapUrlToMangaUrl(uri: Uri): String? {
        return when (uri.pathSegments.firstOrNull()) {
            "g" -> {
                //Is already gallery page, do nothing
                uri.toString()
            }
            "s" -> {
                //Is page, fetch gallery token and use that
                getGalleryUrlFromPage(uri)
            }
            else -> null
        }
    }

    override fun cleanMangaUrl(url: String): String {
        return EHentaiSearchMetadata.normalizeUrl(super.cleanMangaUrl(url))
    }

    private fun getGalleryUrlFromPage(uri: Uri): String {
        val lastSplit = uri.pathSegments.last().split("-")
        val pageNum = lastSplit.last()
        val gallery = lastSplit.first()
        val pageToken = uri.pathSegments.elementAt(1)

        val json = JsonObject()
        json["method"] = "gtoken"
        json["pagelist"] = JsonArray().apply {
            add(JsonArray().apply {
                add(gallery.toInt())
                add(pageToken)
                add(pageNum.toInt())
            })
        }

        val outJson = JsonParser().parse(client.newCall(Request.Builder()
                .url(EH_API_BASE)
                .post(RequestBody.create(JSON, json.toString()))
                .build()).execute().body()!!.string()).obj

        val obj = outJson["tokenlist"].array.first()
        return "${uri.scheme}://${uri.host}/g/${obj["gid"].int}/${obj["token"].string}/"
    }


    companion object {
        private const val QUERY_PREFIX = "?f_apply=Apply+Filter"
        private const val TR_SUFFIX = "TR"
        private const val REVERSE_PARAM = "TEH_REVERSE"

        private const val EH_API_BASE = "https://api.e-hentai.org/api.php"
        private val JSON = MediaType.parse("application/json; charset=utf-8")!!

        private val FAVORITES_BORDER_HEX_COLORS = listOf(
                "000",
                "f00",
                "fa0",
                "dd0",
                "080",
                "9f4",
                "4bf",
                "00f",
                "508",
                "e8e"
        )

        fun buildCookies(cookies: Map<String, String>)
                = cookies.entries.joinToString(separator = "; ") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }

    }
}
