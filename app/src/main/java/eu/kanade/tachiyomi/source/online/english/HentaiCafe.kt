package eu.kanade.tachiyomi.source.online.english

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.LewdSource
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.HENTAI_CAFE_SOURCE_ID
import exh.metadata.EMULATED_TAG_NAMESPACE
import exh.metadata.models.HentaiCafeMetadata
import exh.metadata.models.HentaiCafeMetadata.Companion.BASE_URL
import exh.metadata.models.Tag
import exh.util.urlImportFetchSearchManga
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class HentaiCafe : ParsedHttpSource(), LewdSource<HentaiCafeMetadata, Document> {
    override val id = HENTAI_CAFE_SOURCE_ID

    override val lang = "en"

    override val supportsLatest = true

    override fun queryAll() = HentaiCafeMetadata.EmptyQuery()
    override fun queryFromUrl(url: String) = HentaiCafeMetadata.UrlQuery(url)

    override val name = "Hentai Cafe"
    override val baseUrl = "https://hentai.cafe"

    // Defer popular manga -> latest updates
    override fun popularMangaSelector() = throw UnsupportedOperationException("Unused method called!")
    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException("Unused method called!")
    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException("Unused method called!")
    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException("Unused method called!")
    override fun fetchPopularManga(page: Int) = fetchLatestUpdates(page)
    
    //Support direct URL importing
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
            urlImportFetchSearchManga(query, {
                super.fetchSearchManga(page, query, filters)
            })
    override fun searchMangaSelector() = "article.post:not(#post-0)"
    override fun searchMangaFromElement(element: Element): SManga {
        val thumb = element.select(".entry-thumb > img")
        val title = element.select(".entry-title > a")

        return SManga.create().apply {
            setUrlWithoutDomain(title.attr("href"))
            this.title = title.text()

            thumbnail_url = thumb.attr("src")
        }
    }
    override fun searchMangaNextPageSelector() = ".x-pagination > ul > li:last-child > a.prev-next"
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if(query.isNotBlank()) {
            //Filter by query
            "$baseUrl/page/$page/?s=${Uri.encode(query)}"
        } else if(filters.filterIsInstance<ShowBooksOnlyFilter>().any { it.state }) {
            //Filter by book
            "$baseUrl/category/book/page/$page/"
        } else {
            //Filter by tag
            val tagFilter = filters.filterIsInstance<TagFilter>().first()

            if(tagFilter.state == 0) throw IllegalArgumentException("No filters active, no query active! What to filter?")

            val tag = tagFilter.values[tagFilter.state]
            "$baseUrl/tag/${tag.id}/page/$page/"
        }

        return GET(url)
    }

    override fun latestUpdatesSelector() = searchMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()
    override fun latestUpdatesRequest(page: Int) = GET("$BASE_URL/page/$page/")

    override fun mangaDetailsParse(document: Document): SManga {
        return parseToManga(queryFromUrl(document.location()), document)
    }

    override fun chapterListSelector() = throw UnsupportedOperationException("Unused method called!")
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Unused method called!")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return lazyLoadMeta(queryFromUrl(manga.url),
            client.newCall(mangaDetailsRequest(manga)).asObservableSuccess().map { it.asJsoup() }
        ).map {
            listOf(SChapter.create().apply {
                url = "/manga/read/${it.readerId}/en/0/1/"

                name = "Chapter"

                chapter_number = 1f
            })
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pageItems = document.select(".dropdown > li > a")

        return pageItems.mapIndexed { index, element ->
            Page(index, element.attr("href"))
        }
    }

    override fun imageUrlParse(document: Document)
            = document.select("#page img").attr("src")

    override val metaParser: HentaiCafeMetadata.(Document) -> Unit = {
        val content = it.getElementsByClass("content")
        val eTitle = content.select("h3")

        url = Uri.decode(it.location())
        title = eTitle.text()
        
        thumbnailUrl = content.select("img").attr("src")

        tags.clear()
        val eDetails = content.select("p > a[rel=tag]")
        eDetails.forEach {
            val href = it.attr("href")
            val parsed = Uri.parse(href)
            val firstPath = parsed.pathSegments.first()

            when(firstPath) {
                "tag" -> tags.add(Tag(EMULATED_TAG_NAMESPACE, it.text(), false))
                "artist" -> {
                    artist = it.text()
                    tags.add(Tag("artist", it.text(), false))
                }
            }
        }

        readerId = Uri.parse(content.select("a[title=Read]").attr("href")).pathSegments[2]
    }

    override fun getFilterList() = FilterList(
            TagFilter(),
            ShowBooksOnlyFilter()
    )

    class ShowBooksOnlyFilter : Filter.CheckBox("Show books only")

    class TagFilter : Filter.Select<HCTag>("Filter by tag", listOf(
            "???" to "None",

            "ahegao" to "Ahegao",
            "anal" to "Anal",
            "big-ass" to "Big ass",
            "big-breast" to "Big Breast",
            "bondage" to "Bondage",
            "cheating" to "Cheating",
            "chubby" to "Chubby",
            "condom" to "Condom",
            "cosplay" to "Cosplay",
            "cunnilingus" to "Cunnilingus",
            "dark-skin" to "Dark skin",
            "defloration" to "Defloration",
            "exhibitionism" to "Exhibitionism",
            "fellatio" to "Fellatio",
            "femdom" to "Femdom",
            "flat-chest" to "Flat chest",
            "full-color" to "Full color",
            "glasses" to "Glasses",
            "group" to "Group",
            "hairy" to "Hairy",
            "handjob" to "Handjob",
            "harem" to "Harem",
            "housewife" to "Housewife",
            "incest" to "Incest",
            "large-breast" to "Large Breast",
            "lingerie" to "Lingerie",
            "loli" to "Loli",
            "masturbation" to "Masturbation",
            "nakadashi" to "Nakadashi",
            "netorare" to "Netorare",
            "office-lady" to "Office Lady",
            "osananajimi" to "Osananajimi",
            "paizuri" to "Paizuri",
            "pettanko" to "Pettanko",
            "rape" to "Rape",
            "schoolgirl" to "Schoolgirl",
            "sex-toys" to "Sex Toys",
            "shota" to "Shota",
            "stocking" to "Stocking",
            "swimsuit" to "Swimsuit",
            "teacher" to "Teacher",
            "tsundere" to "Tsundere",
            "uncensored" to "uncensored",
            "x-ray" to "X-ray"
    ).map { HCTag(it.first, it.second) }.toTypedArray()
    )

    class HCTag(val id: String, val displayName: String) {
        override fun toString() = displayName
    }
}
