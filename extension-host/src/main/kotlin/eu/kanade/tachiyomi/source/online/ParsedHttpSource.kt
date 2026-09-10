package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class ParsedHttpSource : HttpSource() {

    @Deprecated("Helper functions are limiting")
    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }
        val hasNextPage = popularMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null
        return MangasPage(mangas, hasNextPage)
    }

    protected abstract fun popularMangaSelector(): String
    protected abstract fun popularMangaFromElement(element: Element): SManga
    protected abstract fun popularMangaNextPageSelector(): String?

    @Deprecated("Helper functions are limiting")
    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }
        val hasNextPage = searchMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null
        return MangasPage(mangas, hasNextPage)
    }

    protected abstract fun searchMangaSelector(): String
    protected abstract fun searchMangaFromElement(element: Element): SManga
    protected abstract fun searchMangaNextPageSelector(): String?

    @Deprecated("Helper functions are limiting")
    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }
        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null
        return MangasPage(mangas, hasNextPage)
    }

    protected abstract fun latestUpdatesSelector(): String
    protected abstract fun latestUpdatesFromElement(element: Element): SManga
    protected abstract fun latestUpdatesNextPageSelector(): String?

    @Deprecated("Helper functions are limiting")
    override fun mangaDetailsParse(response: Response): SManga {
        return mangaDetailsParse(response.asJsoup())
    }

    protected abstract fun mangaDetailsParse(document: Document): SManga

    @Deprecated("Helper functions are limiting")
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    protected abstract fun chapterListSelector(): String
    protected abstract fun chapterFromElement(element: Element): SChapter

    @Deprecated("Helper functions are limiting")
    override fun pageListParse(response: Response): List<Page> {
        return pageListParse(response.asJsoup())
    }

    protected abstract fun pageListParse(document: Document): List<Page>

    @Deprecated("Helper functions are limiting")
    override fun imageUrlParse(response: Response): String {
        return imageUrlParse(response.asJsoup())
    }

    protected abstract fun imageUrlParse(document: Document): String
}
