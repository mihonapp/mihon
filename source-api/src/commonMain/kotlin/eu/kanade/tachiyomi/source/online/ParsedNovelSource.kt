package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.NovelSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import org.jsoup.nodes.Document

/**
 * A simple implementation for novel sources from a website using Jsoup.
 */
abstract class ParsedNovelSource : ParsedHttpSource(), NovelSource {

    override suspend fun fetchPageText(page: Page): String {
        val response = client.newCall(GET(page.url, headers)).awaitSuccess()
        val document = response.asJsoup()
        return novelContentParse(document)
    }

    /**
     * Parses the response from the site and returns the page list.
     * For novels, this usually returns a single page containing the chapter text.
     *
     * @param document the parsed document.
     */
    override fun pageListParse(document: Document): List<Page> {
        val content = novelContentParse(document)
        return listOf(Page(0, text = content))
    }

    /**
     * Returns the content of the chapter from the given document.
     * By default, it selects the element using [novelContentSelector] and returns its HTML.
     *
     * @param document the parsed document.
     */
    open fun novelContentParse(document: Document): String {
        val element = document.select(novelContentSelector()).first() ?: return ""
        
        // Fix relative URLs for images and other media
        element.select("img, video, audio, source").forEach { media ->
            if (media.hasAttr("src")) {
                media.attr("src", media.absUrl("src"))
            }
        }
        
        return element.html()
    }

    /**
     * Returns the Jsoup selector that returns the element containing the chapter content.
     */
    abstract fun novelContentSelector(): String

    /**
     * Returns the absolute url to the source image from the document.
     * Not used for novels as the content is text.
     */
    override fun imageUrlParse(document: Document): String = ""

    override fun chapterPageParse(response: okhttp3.Response): eu.kanade.tachiyomi.source.model.SChapter {
        throw UnsupportedOperationException("Not used")
    }
}
