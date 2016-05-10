package eu.kanade.tachiyomi.data.source.online.english

import android.content.Context
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.network.get
import eu.kanade.tachiyomi.data.network.post
import eu.kanade.tachiyomi.data.source.EN
import eu.kanade.tachiyomi.data.source.base.Source
import eu.kanade.tachiyomi.data.source.model.MangasPage
import eu.kanade.tachiyomi.data.source.model.Page
import eu.kanade.tachiyomi.util.Parser
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class Kissmanga(context: Context) : Source(context) {

    override fun getName() = NAME

    override fun getBaseUrl() = BASE_URL

    override fun getLang() = EN

    override val networkClient: OkHttpClient
        get() = networkService.cloudflareClient

    override fun getInitialPopularMangasUrl(): String {
        return String.format(POPULAR_MANGAS_URL, 1)
    }

    override fun getInitialSearchUrl(query: String): String {
        return SEARCH_URL
    }

    override fun searchMangaRequest(page: MangasPage, query: String): Request {
        if (page.page == 1) {
            page.url = getInitialSearchUrl(query)
        }

        val form = FormBody.Builder()
        form.add("authorArtist", "")
        form.add("mangaName", query)
        form.add("status", "")
        form.add("genres", "")

        return post(page.url, requestHeaders, form.build())
    }

    override fun pageListRequest(chapterUrl: String): Request {
        return post(baseUrl + chapterUrl, requestHeaders)
    }

    override fun imageRequest(page: Page): Request {
        return get(page.imageUrl)
    }

    override fun parsePopularMangasFromHtml(parsedHtml: Document): List<Manga> {
        val mangaList = ArrayList<Manga>()

        for (currentHtmlBlock in parsedHtml.select("table.listing tr:gt(1)")) {
            val manga = constructPopularMangaFromHtml(currentHtmlBlock)
            mangaList.add(manga)
        }

        return mangaList
    }

    private fun constructPopularMangaFromHtml(htmlBlock: Element): Manga {
        val manga = Manga()
        manga.source = id

        val urlElement = Parser.element(htmlBlock, "td a:eq(0)")

        if (urlElement != null) {
            manga.setUrl(urlElement.attr("href"))
            manga.title = urlElement.text()
        }

        return manga
    }

    override fun parseNextPopularMangasUrl(parsedHtml: Document, page: MangasPage): String? {
        val path = Parser.href(parsedHtml, "li > a:contains(› Next)")
        return if (path != null) BASE_URL + path else null
    }

    override fun parseSearchFromHtml(parsedHtml: Document): List<Manga> {
        return parsePopularMangasFromHtml(parsedHtml)
    }

    override fun parseNextSearchUrl(parsedHtml: Document, page: MangasPage, query: String): String? {
        return null
    }

    override fun parseHtmlToManga(mangaUrl: String, unparsedHtml: String): Manga {
        val parsedDocument = Jsoup.parse(unparsedHtml)
        val infoElement = parsedDocument.select("div.barContent").first()

        val manga = Manga.create(mangaUrl)
        manga.title = Parser.text(infoElement, "a.bigChar")
        manga.author = Parser.text(infoElement, "p:has(span:contains(Author:)) > a")
        manga.genre = Parser.allText(infoElement, "p:has(span:contains(Genres:)) > *:gt(0)")
        manga.description = Parser.allText(infoElement, "p:has(span:contains(Summary:)) ~ p")
        manga.status = parseStatus(Parser.text(infoElement, "p:has(span:contains(Status:))")!!)

        val thumbnail = Parser.src(parsedDocument, ".rightBox:eq(0) img")
        if (thumbnail != null) {
            manga.thumbnail_url = thumbnail
        }

        manga.initialized = true
        return manga
    }

    private fun parseStatus(status: String): Int {
        if (status.contains("Ongoing")) {
            return Manga.ONGOING
        }
        if (status.contains("Completed")) {
            return Manga.COMPLETED
        }
        return Manga.UNKNOWN
    }

    override fun parseHtmlToChapters(unparsedHtml: String): List<Chapter> {
        val parsedDocument = Jsoup.parse(unparsedHtml)
        val chapterList = ArrayList<Chapter>()

        for (chapterElement in parsedDocument.select("table.listing tr:gt(1)")) {
            val chapter = constructChapterFromHtmlBlock(chapterElement)
            chapterList.add(chapter)
        }

        return chapterList
    }

    private fun constructChapterFromHtmlBlock(chapterElement: Element): Chapter {
        val chapter = Chapter.create()

        val urlElement = Parser.element(chapterElement, "a")
        val date = Parser.text(chapterElement, "td:eq(1)")

        if (urlElement != null) {
            chapter.setUrl(urlElement.attr("href"))
            chapter.name = urlElement.text()
        }
        if (date != null) {
            try {
                chapter.date_upload = SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH).parse(date).time
            } catch (e: ParseException) { /* Ignore */
            }

        }
        return chapter
    }

    override fun parseHtmlToPageUrls(unparsedHtml: String): List<String> {
        val parsedDocument = Jsoup.parse(unparsedHtml)
        val pageUrlList = ArrayList<String>()

        val numImages = parsedDocument.select("#divImage img").size

        for (i in 0..numImages - 1) {
            pageUrlList.add("")
        }
        return pageUrlList
    }

    override fun parseFirstPage(pages: List<Page>, unparsedHtml: String): List<Page> {
        val p = Pattern.compile("lstImages.push\\(\"(.+?)\"")
        val m = p.matcher(unparsedHtml)

        var i = 0
        while (m.find()) {
            pages[i++].imageUrl = m.group(1)
        }
        return pages
    }

    override fun parseHtmlToImageUrl(unparsedHtml: String): String? {
        return null
    }

    companion object {

        val NAME = "Kissmanga"
        val BASE_URL = "http://kissmanga.com"
        val POPULAR_MANGAS_URL = BASE_URL + "/MangaList/MostPopular?page=%s"
        val SEARCH_URL = BASE_URL + "/AdvanceSearch"
    }

}
