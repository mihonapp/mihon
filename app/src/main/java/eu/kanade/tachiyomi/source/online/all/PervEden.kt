package eu.kanade.tachiyomi.source.online.all

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.LewdSource
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.ChapterRecognition
import eu.kanade.tachiyomi.util.asJsoup
import exh.metadata.EMULATED_TAG_NAMESPACE
import exh.metadata.models.PervEdenGalleryMetadata
import exh.metadata.models.PervEdenLang
import exh.metadata.models.PervEdenTitle
import exh.metadata.models.Tag
import exh.util.UriFilter
import exh.util.UriGroup
import exh.util.urlImportFetchSearchManga
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.text.SimpleDateFormat
import java.util.*

class PervEden(override val id: Long, val pvLang: PervEdenLang) : ParsedHttpSource(),
        LewdSource<PervEdenGalleryMetadata, Document> {

    override val supportsLatest = true
    override val name = "Perv Eden"
    override val baseUrl = "http://www.perveden.com"
    override val lang = pvLang.name

    override fun popularMangaSelector() = "#topManga > ul > li"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = "http:" + element.select(".hottestImage > img").attr("data-src")

        val titleElement = element.getElementsByClass("hottestInfo").first().child(0)
        manga.url = titleElement.attr("href")
        manga.title = titleElement.text()

        return manga
    }

    override fun popularMangaNextPageSelector(): String? = null

    //Support direct URL importing
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
            urlImportFetchSearchManga(query, {
                super.fetchSearchManga(page, query, filters)
            })

    override fun searchMangaSelector() = "#mangaList > tbody > tr"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val titleElement = element.child(0).child(0)
        manga.url = titleElement.attr("href")
        manga.title = titleElement.text().trim()
        return manga
    }

    override fun searchMangaNextPageSelector() = ".next"

    override fun popularMangaRequest(page: Int): Request {
        val urlLang = if(lang == "en")
            "eng"
        else "it"
        return GET("$baseUrl/$urlLang/")
    }

    override fun latestUpdatesSelector() = ".newsManga"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        val header = element.getElementsByClass("manga_tooltop_header").first()
        val titleElement = header.child(0)
        manga.url = titleElement.attr("href")
        manga.title = titleElement.text().trim()
        manga.thumbnail_url = "http:" + titleElement.getElementsByClass("mangaImage").first().attr("tmpsrc")
        return manga
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }

        return MangasPage(mangas, mangas.isNotEmpty())
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/$lang/$lang-directory/").buildUpon()
        uri.appendQueryParameter("page", page.toString())
        uri.appendQueryParameter("title", query)
        filters.forEach {
            if(it is UriFilter) it.addToUri(uri)
        }
        return GET(uri.toString())
    }

    override fun latestUpdatesNextPageSelector(): String? {
        throw NotImplementedError("Unused method called!")
    }

    override val metaParser: PervEdenGalleryMetadata.(Document) -> Unit = { document ->
        url = Uri.parse(document.location()).path

        pvId = PervEdenGalleryMetadata.pvIdFromUrl(url!!)

        lang = this@PervEden.lang

        title = document.getElementsByClass("manga-title").first()?.text()

        thumbnailUrl = "http:" + document.getElementsByClass("mangaImage2").first()?.child(0)?.attr("src")

        val rightBoxElement = document.select(".rightBox:not(.info)").first()

        altTitles.clear()
        tags.clear()
        var inStatus: String? = null
        rightBoxElement.childNodes().forEach {
            if(it is Element && it.tagName().toLowerCase() == "h4") {
                inStatus = it.text().trim()
            } else {
                when(inStatus) {
                    "Alternative name(s)" -> {
                        if(it is TextNode) {
                            val text = it.text().trim()
                            if(!text.isBlank())
                                altTitles.add(PervEdenTitle(this, text))
                        }
                    }
                    "Artist" -> {
                        if(it is Element && it.tagName() == "a") {
                            artist = it.text()
                            tags.add(Tag("artist", it.text().toLowerCase(), false))
                        }
                    }
                    "Genres" -> {
                        if(it is Element && it.tagName() == "a")
                            tags.add(Tag(EMULATED_TAG_NAMESPACE, it.text().toLowerCase(), false))
                    }
                    "Type" -> {
                        if(it is TextNode) {
                            val text = it.text().trim()
                            if(!text.isBlank())
                                type = text
                        }
                    }
                    "Status" -> {
                        if(it is TextNode) {
                            val text = it.text().trim()
                            if(!text.isBlank())
                                status = text
                        }
                    }
                }
            }
        }

        rating = document.getElementById("rating-score")?.attr("value")?.toFloat()
    }

    override fun mangaDetailsParse(document: Document): SManga
        = parseToManga(queryFromUrl(document.location()), document)

    override fun latestUpdatesRequest(page: Int): Request {
        val num = when (lang) {
            "en" -> "0"
            "it" -> "1"
            else -> throw NotImplementedError("Unimplemented language!")
        }

        return GET("$baseUrl/ajax/news/$page/$num/0/")
    }

    override fun chapterListSelector() = "#leftContent > table > tbody > tr"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val linkElement = element.getElementsByClass("chapterLink").first()

        setUrlWithoutDomain(linkElement.attr("href"))
        name = "Chapter " + linkElement.getElementsByTag("b").text()

        ChapterRecognition.parseChapterNumber(
                this,
                SManga.create().apply {
                    title = ""
                })

        try {
            date_upload = DATE_FORMAT.parse(element.getElementsByClass("chapterDate").first().text().trim()).time
        } catch(ignored: Exception) {}
    }

    override fun pageListParse(document: Document)
            = document.getElementById("pageSelect").getElementsByTag("option").map {
        Page(it.attr("data-page").toInt() - 1, baseUrl + it.attr("value"))
    }

    override fun imageUrlParse(document: Document)
            = "http:" + document.getElementById("mainImg").attr("src")!!

    override fun queryAll() = PervEdenGalleryMetadata.EmptyQuery()
    override fun queryFromUrl(url: String) = PervEdenGalleryMetadata.UrlQuery(url, PervEdenLang.source(id))

    override fun getFilterList() = FilterList (
            AuthorFilter(),
            ArtistFilter(),
            TypeFilterGroup(),
            ReleaseYearGroup(),
            StatusFilterGroup()
    )

    class StatusFilterGroup : UriGroup<StatusFilter>("Status", listOf(
            StatusFilter("Ongoing", 1),
            StatusFilter("Completed", 2),
            StatusFilter("Suspended", 0)
    ))

    class StatusFilter(n: String, val id: Int) : Filter.CheckBox(n, false), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if(state)
                builder.appendQueryParameter("status", id.toString())
        }
    }

    //Explicit type arg for listOf() to workaround this: KT-16570
    class ReleaseYearGroup : UriGroup<Filter<*>>("Release Year", listOf(
            ReleaseYearRangeFilter(),
            ReleaseYearYearFilter()
    ))

    class ReleaseYearRangeFilter : Filter.Select<String>("Range", arrayOf(
            "on",
            "after",
            "before"
    )), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            builder.appendQueryParameter("releasedType", state.toString())
        }
    }

    class ReleaseYearYearFilter : Filter.Text("Year"), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            builder.appendQueryParameter("released", state)
        }
    }

    class AuthorFilter : Filter.Text("Author"), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            builder.appendQueryParameter("author", state)
        }
    }

    class ArtistFilter : Filter.Text("Artist"), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            builder.appendQueryParameter("artist", state)
        }
    }

    class TypeFilterGroup : UriGroup<TypeFilter>("Type", listOf(
            TypeFilter("Japanese Manga", 0),
            TypeFilter("Korean Manhwa", 1),
            TypeFilter("Chinese Manhua", 2),
            TypeFilter("Comic", 3),
            TypeFilter("Doujinshi", 4)
    ))

    class TypeFilter(n: String, val id: Int) : Filter.CheckBox(n, false), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if(state)
                builder.appendQueryParameter("type", id.toString())
        }
    }

    companion object {
        val DATE_FORMAT = SimpleDateFormat("MMM d, yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
    }
}
