package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.metadata.*
import exh.metadata.models.ExGalleryMetadata
import exh.metadata.models.Tag
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder
import java.util.*
import exh.ui.login.LoginActivity
import exh.util.UriFilter
import exh.util.UriGroup
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.Request
import org.jsoup.nodes.Document

class EHentai(override val id: Long,
              val exh: Boolean,
              val context: Context) : HttpSource() {

    val schema: String
        get() = if(prefs.secureEXH().getOrDefault())
            "https"
        else
            "http"

    override val baseUrl: String
        get() = if(exh)
            "$schema://exhentai.org"
        else
            "$schema://e-hentai.org"

    override val lang = "all"
    override val supportsLatest = true

    val prefs: PreferencesHelper by injectLazy()

    val metadataHelper = MetadataHelper()

    /**
     * Gallery list entry
     */
    data class ParsedManga(val fav: String?, val manga: Manga)

    fun extendedGenericMangaParse(doc: Document)
            = with(doc) {
        //Parse mangas
        val parsedMangas = select(".gtr0,.gtr1").map {
            ParsedManga(
                    fav = it.select(".itd .it3 > .i[id]").first()?.attr("title"),
                    manga = Manga.create(id).apply {
                        //Get title
                        it.select(".itd .it5 a").first()?.apply {
                            title = text()
                            setUrlWithoutDomain(addParam(attr("href"), "nw", "always"))
                        }
                        //Get image
                        it.select(".itd .it2").first()?.apply {
                            children().first()?.let {
                                thumbnail_url = it.attr("src")
                            } ?: let {
                                text().split("~").apply {
                                    thumbnail_url = "http://${this[1]}/${this[2]}"
                                }
                            }
                        }
                    })

        }
        //Add to page if required
        val hasNextPage = select("a[onclick=return false]").last()?.let {
            it.text() == ">"
        } ?: false
        Pair(parsedMangas, hasNextPage)
    }

    /**
     * Parse a list of galleries
     */
    fun genericMangaParse(response: Response)
            = extendedGenericMangaParse(response.asJsoup()).let {
        MangasPage(it.first.map { it.manga }, it.second)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>>
            = Observable.just(listOf(SChapter.create().apply {
        url = manga.url
        name = "Chapter"
        chapter_number = 1f
    }))

    override fun fetchPageList(chapter: SChapter)
            = fetchChapterPage(chapter, "$baseUrl/${chapter.url}").map {
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
        exGet("$baseUrl/toplist.php?tl=15", page)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl$QUERY_PREFIX").buildUpon()
        uri.appendQueryParameter("f_search", query)
        filters.forEach {
            if(it is UriFilter) it.addToUri(uri)
        }
        return exGet(uri.toString(), page)
    }

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
     * Parse gallery page to metadata model
     */
    override fun mangaDetailsParse(response: Response) = with(response.asJsoup()) {
        val metdata = ExGalleryMetadata()
        with(metdata) {
            url = response.request().url().encodedPath()
            exh = this@EHentai.exh
            title = select("#gn").text().nullIfBlank()?.trim()

            altTitle = select("#gj").text().nullIfBlank()?.trim()

            thumbnailUrl = select("#gd1 div").attr("style").nullIfBlank()?.let {
                it.substring(it.indexOf('(') + 1 until it.lastIndexOf(')'))
            }

            genre = select(".ic").parents().attr("href").nullIfBlank()?.trim()?.substringAfterLast('/')

            uploader = select("#gdn").text().nullIfBlank()?.trim()

            //Parse the table
            select("#gdd tr").forEach {
                it.select(".gdt1")
                        .text()
                        .nullIfBlank()
                        ?.trim()
                        ?.let { left ->
                            it.select(".gdt2")
                                    .text()
                                    .nullIfBlank()
                                    ?.trim()
                                    ?.let { right ->
                                        ignore {
                                            when (left.removeSuffix(":")
                                                    .toLowerCase()) {
                                                "posted" -> datePosted = EX_DATE_FORMAT.parse(right).time
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
                val currentTags = it.select("div").map {
                    Tag(it.text().trim(),
                            it.hasClass("gtl"))
                }
                tags.put(namespace, ArrayList(currentTags))
            }

            //Save metadata
            metadataHelper.writeGallery(this, id)

            //Copy metadata to manga
            SManga.create().let {
                copyTo(it)
                it
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
                page.url = addParam(page.url, "nl", it.substring(it.indexOf('\'') + 1 .. it.lastIndexOf('\'') - 1))
            }
            return currentImage
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException("Unused method was called somehow!")
    }

    //Too lazy to write return type
    fun fetchFavorites() = {
        //Used to get "s" cookie
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
                favNames = doc.getElementsByClass("nosel").first().children().filter {
                    it.children().size >= 3
                }.map { it.child(2).text() }.filterNotNull()

            //Next page
            page++
        } while (parsed.second)
        Pair(result as List<ParsedManga>, favNames!!)
    }()

    val cookiesHeader by lazy {
        val cookies: MutableMap<String, String> = mutableMapOf()
        if(prefs.enableExhentai().getOrDefault()) {
            cookies.put(LoginActivity.MEMBER_ID_COOKIE, prefs.memberIdVal().get()!!)
            cookies.put(LoginActivity.PASS_HASH_COOKIE, prefs.passHashVal().get()!!)
            cookies.put(LoginActivity.IGNEOUS_COOKIE, prefs.igneousVal().get()!!)
        }

        //Setup settings
        val settings = mutableListOf<String?>()
        //Image quality
        settings.add(when(prefs.imageQuality()
                .getOrDefault()
                .toLowerCase()) {
            "ovrs_2400" -> "xr_2400"
            "ovrs_1600" -> "xr_1600"
            "high" -> "xr_1280"
            "med" -> "xr_980"
            "low" -> "xr_780"
            "auto" -> null
            else -> null
        })
        //Use Hentai@Home
        settings.add(if(prefs.useHentaiAtHome().getOrDefault())
            null
        else
            "uh_n")
        //Japanese titles
        settings.add(if(prefs.useJapaneseTitle().getOrDefault())
            "tl_j"
        else
            null)
        //Do not show popular right now pane as we can't parse it
        settings.add("prn_n")
        //Paging size
        settings.add(prefs.ehSearchSize().getOrDefault())
        //Thumbnail rows
        settings.add(prefs.thumbnailRows().getOrDefault())

        cookies.put("uconfig", buildSettings(settings))

        buildCookies(cookies)
    }

    //Headers
    override fun headersBuilder()
            = super.headersBuilder().add("Cookie", cookiesHeader)!!

    fun buildSettings(settings: List<String?>): String {
        return settings.filterNotNull().joinToString(separator = "-")
    }

    fun buildCookies(cookies: Map<String, String>)
            = cookies.entries.map {
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }.joinToString(separator = "; ", postfix = ";")

    fun addParam(url: String, param: String, value: String)
            = Uri.parse(url)
            .buildUpon()
            .appendQueryParameter(param, value)
            .toString()

    override val client = network.client.newBuilder()
            .addInterceptor { chain ->
                val newReq = chain
                        .request()
                        .newBuilder()
                        .addHeader("Cookie", cookiesHeader)
                        .build()

                chain.proceed(newReq)
            }.build()!!

    //Filters
    override fun getFilterList() = FilterList(
            GenreGroup(),
            AdvancedGroup()
    )

    class GenreOption(name: String, val genreId: String): Filter.CheckBox(name, false), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            builder.appendQueryParameter("f_" + genreId, if(state) "1" else "0")
        }
    }
    class GenreGroup : UriGroup<GenreOption>("Genres", listOf(
            GenreOption("Dōjinshi", "doujinshi"),
            GenreOption("Manga", "manga"),
            GenreOption("Artist CG", "artistcg"),
            GenreOption("Game CG", "gamecg"),
            GenreOption("Western", "western"),
            GenreOption("Non-H", "non-h"),
            GenreOption("Image Set", "imageset"),
            GenreOption("Cosplay", "cosplay"),
            GenreOption("Asian Porn", "asianporn"),
            GenreOption("Misc", "misc")
    ))

    class AdvancedOption(name: String, val param: String, defValue: Boolean = false): Filter.CheckBox(name, defValue), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if(state)
                builder.appendQueryParameter(param, "on")
        }
    }
    class RatingOption : Filter.Select<String>("Minimum Rating", arrayOf(
            "Any",
            "2 stars",
            "3 stars",
            "4 stars",
            "5 stars"
    )), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if(state > 0) builder.appendQueryParameter("f_srdd", Integer.toString(state + 1))
        }
    }

    //Explicit type arg for listOf() to workaround this: KT-16570
    class AdvancedGroup : UriGroup<Filter<*>>("Advanced Options", listOf<Filter<*>>(
            AdvancedOption("Search Gallery Name", "f_sname", true),
            AdvancedOption("Search Gallery Tags", "f_stags", true),
            AdvancedOption("Search Gallery Description", "f_sdesc"),
            AdvancedOption("Search Torrent Filenames", "f_storr"),
            AdvancedOption("Only Show Galleries With Torrents", "f_sto"),
            AdvancedOption("Search Low-Power Tags", "f_sdt1"),
            AdvancedOption("Search Downvoted Tags", "f_sdt2"),
            AdvancedOption("Show Expunged Galleries", "f_sh"),
            RatingOption()
    ))

    override val name = if(exh)
        "ExHentai"
    else
        "E-Hentai"

    companion object {
        val QUERY_PREFIX = "?f_apply=Apply+Filter"
        val TR_SUFFIX = "TR"

        fun getCookies(cookies: String): Map<String, String>? {
            val foundCookies = HashMap<String, String>()
            for (cookie in cookies.split(";".toRegex()).dropLastWhile(String::isEmpty).toTypedArray()) {
                val splitCookie = cookie.split("=".toRegex()).dropLastWhile(String::isEmpty).toTypedArray()
                if (splitCookie.size < 2) {
                    return null
                }
                val trimmedKey = splitCookie[0].trim { it <= ' ' }
                if (!foundCookies.containsKey(trimmedKey)) {
                    foundCookies.put(trimmedKey, splitCookie[1].trim { it <= ' ' })
                }
            }
            return foundCookies
        }
    }
}
