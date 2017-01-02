package eu.kanade.tachiyomi.data.source.online.all

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.network.GET
import eu.kanade.tachiyomi.data.network.asObservableSuccess
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.source.model.MangasPage
import eu.kanade.tachiyomi.data.source.model.Page
import eu.kanade.tachiyomi.data.source.online.OnlineSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.metadata.*
import exh.metadata.models.ExGalleryMetadata
import exh.metadata.models.Tag
import exh.plusAssign
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder
import java.util.*
import exh.ui.login.LoginActivity

class EHentai(override val id: Int,
              val exh: Boolean,
              val context: Context) : OnlineSource() {


    val schema: String
        get() = if(prefs.secureEXH().getOrDefault())
            "https"
        else
            "http"

    override val baseUrl: String
        get() = if(exh)
            "$schema://exhentai.org"
        else
            "http://g.e-hentai.org"

    override val lang = "all"
    override val supportsLatest = true

    val prefs: PreferencesHelper by injectLazy()

    val metadataHelper = MetadataHelper()

    /**
     * Gallery list entry
     */
    data class ParsedManga(val fav: String?, val manga: Manga)

    /**
     * Parse a list of galleries
     */
    fun genericMangaParse(response: Response, page: MangasPage? = null)
            = with(response.asJsoup()) {
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
            page?.let { page ->
                page.mangas += parsedMangas.map { it.manga }
                select("a[onclick=return false]").last()?.let {
                    if(it.text() == ">") page.nextPageUrl = it.attr("href")
                }
            }
        //Return parsed mangas anyways
        parsedMangas
    }

    override fun fetchChapterList(manga: Manga): Observable<List<Chapter>>
            = Observable.just(listOf(Chapter.create().apply {
        manga_id = manga.id
        url = manga.url
        name = "Chapter"
        chapter_number = 1f
    }))

    override fun fetchPageListFromNetwork(chapter: Chapter)
            = fetchChapterPage(chapter, 0).map {
        it.mapIndexed { i, s ->
            Page(i, s)
        }
    }!!

    private fun fetchChapterPage(chapter: Chapter, id: Int): Observable<List<String>> {
        val urls = mutableListOf<String>()
        return chapterPageCall(chapter, id).flatMap {
            val jsoup = it.asJsoup()
            urls += parseChapterPage(jsoup)
            if(nextPageUrl(jsoup) != null) {
                fetchChapterPage(chapter, id + 1)
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
    private fun chapterPageCall(chapter: Chapter, pn: Int) = client.newCall(chapterPageRequest(chapter, pn)).asObservableSuccess()
    private fun chapterPageRequest(chapter: Chapter, pn: Int) = GET("$baseUrl${chapter.url}?p=$pn", headers)

    private fun nextPageUrl(element: Element): String?
            = element.select("a[onclick=return false]").last()?.let {
        return if (it.text() == ">") it.attr("href") else null
    }

    private fun buildGenreString(filters: List<OnlineSource.Filter>): String {
        val genreString = StringBuilder()
        for (genre in GENRE_LIST) {
            genreString += "&f_"
            genreString += genre
            genreString += "="
            genreString += if (filters.isEmpty()
                    || !filters
                    .map { it.id }
                    .find { it == genre }
                    .isNullOrEmpty())
                "1"
            else
                "0"
        }
        return genreString.toString()
    }

    override fun popularMangaInitialUrl() = if(exh)
        latestUpdatesInitialUrl()
    else
        "$baseUrl/toplist.php?tl=15"

    override fun popularMangaParse(response: Response, page: MangasPage) {
        genericMangaParse(response, page)
    }

    override fun searchMangaInitialUrl(query: String, filters: List<Filter>)
            = "$baseUrl$QUERY_PREFIX${buildGenreString(filters)}&f_search=${URLEncoder.encode(query, "UTF-8")}"

    override fun searchMangaParse(response: Response, page: MangasPage, query: String, filters: List<Filter>) {
        genericMangaParse(response, page)
    }

    override fun latestUpdatesInitialUrl() = baseUrl

    override fun latestUpdatesParse(response: Response, page: MangasPage) {
        genericMangaParse(response, page)
    }

    /**
     * Parse gallery page to metadata model
     */
    override fun mangaDetailsParse(response: Response, manga: Manga) = with(response.asJsoup()) {
        val metdata = ExGalleryMetadata()
        with(metdata) {
            url = manga.url
            exh = this@EHentai.exh
            title = select("#gn").text().nullIfBlank()
            altTitle = select("#gj").text().nullIfBlank()

            thumbnailUrl = select("#gd1 img").attr("src").nullIfBlank()

            genre = select(".ic").attr("alt").nullIfBlank()

            uploader = select("#gdn").text().nullIfBlank()

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
            metadataHelper.writeGallery(this)

            //Copy metadata to manga
            copyTo(manga)
        }
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

    //Copy and paste from OnlineSource as we need the page argument
    override public fun fetchImageUrl(page: Page): Observable<Page> {
        page.status = Page.LOAD_PAGE
        return client
                .newCall(imageUrlRequest(page))
                .asObservableSuccess()
                .map { imageUrlParse(it, page) }
                .doOnError { page.status = Page.ERROR }
                .onErrorReturn { null }
                .doOnNext { page.imageUrl = it }
                .map { page }
    }

    fun imageUrlParse(response: Response, page: Page): String {
        with(response.asJsoup()) {
            val currentImage = select("img[onerror]").attr("src")
            //Each press of the retry button will choose another server
            select("#loadfail").attr("onclick").nullIfBlank()?.let {
                page.url = addParam(page.url, "nl", it.substring(it.indexOf('\'') + 1 .. it.lastIndexOf('\'') - 1))
            }
            return currentImage
        }
    }

    val cookiesHeader by lazy {
        val cookies: MutableMap<String, String> = HashMap()
        if(prefs.enableExhentai().getOrDefault()) {
            cookies.put(LoginActivity.MEMBER_ID_COOKIE, prefs.memberIdVal().getOrDefault())
            cookies.put(LoginActivity.PASS_HASH_COOKIE, prefs.passHashVal().getOrDefault())
            cookies.put(LoginActivity.IGNEOUS_COOKIE, prefs.igneousVal().getOrDefault())
        }
        buildCookies(cookies)
    }

    //Headers
    override fun headersBuilder()
        = super.headersBuilder().add("Cookie", cookiesHeader)!!

    fun buildCookies(cookies: Map<String, String>)
            = cookies.entries.map {
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }.joinToString(separator = "; ", postfix = ";")

    fun addParam(url: String, param: String, value: String)
            = Uri.parse(url)
            .buildUpon()
            .appendQueryParameter(param, value)
            .toString()

    override val client = super.client.newBuilder()
            .addInterceptor { chain ->
                val newReq = chain
                        .request()
                        .newBuilder()
                        .addHeader("Cookie", cookiesHeader)
                        .build()

                chain.proceed(newReq)
            }.build()!!

    //Filters
    val generatedFilters = GENRE_LIST.map { Filter(it, it) }
    override fun getFilterList() = generatedFilters

    override val name = if(exh)
        "ExHentai"
    else
        "E-Hentai"

    companion object {
        val QUERY_PREFIX = "?f_apply=Apply+Filter"
        val GENRE_LIST = arrayOf("doujinshi", "manga", "artistcg", "gamecg", "western", "non-h", "imageset", "cosplay", "asianporn", "misc")
        val TR_SUFFIX = "TR"
    }
}
