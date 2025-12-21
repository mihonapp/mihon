package eu.kanade.tachiyomi.source.custom

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.NovelSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Custom Novel Extension - User-defined novel source
 *
 * This class allows users to create custom novel sources by defining:
 * - CSS selectors for different page elements
 * - URL patterns for navigation
 * - Custom headers if needed
 *
 * The configuration is stored as JSON and can be edited through the app UI
 * or shared with other users.
 */
class CustomNovelSource(
    val config: CustomSourceConfig,
) : HttpSource(), NovelSource {

    // Mark this as a novel source for HttpPageLoader detection
    override val isNovelSource: Boolean = true

    override val name: String = config.name
    override val baseUrl: String = config.baseUrl
    override val lang: String = config.language
    override val id: Long = config.id ?: generateId(config.name, config.baseUrl)
    override val supportsLatest: Boolean = config.latestUrl != null

    override val client = if (config.useCloudflare) network.cloudflareClient else network.client

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().apply {
        config.headers.forEach { (key, value) ->
            add(key, value)
        }
    }

    // ======================== Popular ========================

    override fun popularMangaRequest(page: Int) = GET(
        config.popularUrl.buildUrl(baseUrl, page),
        headers,
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return parseMangaList(document, config.selectors.popular)
    }

    // ======================== Latest ========================

    override fun latestUpdatesRequest(page: Int) = GET(
        (config.latestUrl ?: config.popularUrl).buildUrl(baseUrl, page),
        headers,
    )

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ======================== Search ========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET(
        config.searchUrl.buildSearchUrl(baseUrl, query, page),
        headers,
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return parseMangaList(document, config.selectors.search ?: config.selectors.popular)
    }

    // ======================== Details ========================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val selectors = config.selectors.details

        return SManga.create().apply {
            title = document.selectText(selectors.title) ?: ""
            author = document.selectText(selectors.author)
            artist = document.selectText(selectors.artist)
            description = document.selectText(selectors.description)
            genre = document.selectText(selectors.genre)
            thumbnail_url = document.selectAttr(selectors.cover, "src", "data-src", "data-lazy-src")
            status = parseStatus(document.selectText(selectors.status))
        }
    }

    // ======================== Chapters ========================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val selectors = config.selectors.chapters

        // Check if we need to make an AJAX request for chapters
        if (config.chapterAjax != null) {
            val novelId = extractNovelId(document, response.request.url.toString())
            if (novelId != null) {
                val ajaxResponse = client.newCall(
                    GET(config.chapterAjax!!.buildAjaxUrl(baseUrl, novelId), headers),
                ).execute()
                return parseChapterList(ajaxResponse.asJsoup(), selectors)
            }
        }

        return parseChapterList(document, selectors)
    }

    override fun chapterPageParse(response: Response): SChapter {
        val document = response.asJsoup()
        val selectors = config.selectors.chapters

        val link = document.selectFirst(selectors.link ?: "a")
            ?: document.selectFirst("a[href*=chapter]")

        return SChapter.create().apply {
            url = link?.attr("abs:href")?.removePrefix(baseUrl) ?: ""
            name = document.selectText(selectors.name)
                ?: link?.text()?.trim()
                ?: "Chapter"
            date_upload = parseDate(document.selectText(selectors.date))
        }
    }

    private fun parseChapterList(document: Document, selectors: ChapterSelectors): List<SChapter> {
        return document.select(selectors.list).mapNotNull { element ->
            try {
                val link = element.selectFirst(selectors.link ?: "a") ?: element.selectFirst("a")
                val url = link?.attr("abs:href")?.removePrefix(baseUrl) ?: return@mapNotNull null

                SChapter.create().apply {
                    this.url = url
                    name = element.selectText(selectors.name)
                        ?: link?.text()?.trim()
                        ?: return@mapNotNull null
                    date_upload = parseDate(element.selectText(selectors.date))
                }
            } catch (e: Exception) {
                null
            }
        }.let { chapters ->
            if (config.reverseChapters) chapters.reversed() else chapters
        }
    }

    // ======================== Pages (Novel Content) ========================

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request.url.toString().removePrefix(baseUrl)
        return listOf(Page(0, url))
    }

    override suspend fun fetchPageText(page: Page): String {
        val response = client.newCall(GET(baseUrl + page.url, headers)).awaitSuccess()
        val document = response.asJsoup()

        val selectors = config.selectors.content

        // Try primary selector
        var element = document.selectFirst(selectors.primary)

        // Try fallback selectors
        if (element == null && selectors.fallbacks != null) {
            for (fallback in selectors.fallbacks!!) {
                element = document.selectFirst(fallback)
                if (element != null) break
            }
        }

        if (element == null) return ""

        // Remove unwanted elements
        selectors.removeSelectors?.forEach { selector ->
            element!!.select(selector).remove()
        }

        // Fix relative URLs
        element.select("img, video, audio, source").forEach { media ->
            listOf("src", "data-src", "data-lazy-src").forEach { attr ->
                if (media.hasAttr(attr)) {
                    media.attr(attr, media.absUrl(attr))
                }
            }
        }

        return element.html()
    }

    override fun imageUrlParse(response: Response) = ""

    override fun getFilterList() = FilterList()

    // ======================== Helper Functions ========================

    private fun parseMangaList(document: Document, selectors: MangaListSelectors): MangasPage {
        val mangas = document.select(selectors.list).mapNotNull { element ->
            try {
                SManga.create().apply {
                    val link = element.selectFirst(selectors.link ?: "a[href]")
                        ?: element.selectFirst("a")
                    url = link?.attr("abs:href")?.removePrefix(baseUrl) ?: return@mapNotNull null
                    title = element.selectText(selectors.title)
                        ?: link?.attr("title")?.ifBlank { null }
                        ?: link?.text()?.trim()
                        ?: return@mapNotNull null
                    thumbnail_url = element.selectAttr(selectors.cover, "src", "data-src", "data-lazy-src")
                }
            } catch (e: Exception) {
                null
            }
        }

        val hasNextPage = if (selectors.nextPage != null) {
            document.selectFirst(selectors.nextPage!!) != null
        } else {
            mangas.isNotEmpty()
        }

        return MangasPage(mangas, hasNextPage)
    }

    private fun Document.selectText(selector: String?): String? {
        if (selector.isNullOrBlank()) return null
        return selectFirst(selector)?.text()?.trim()?.ifBlank { null }
    }

    private fun Element.selectText(selector: String?): String? {
        if (selector.isNullOrBlank()) return null
        return selectFirst(selector)?.text()?.trim()?.ifBlank { null }
    }

    private fun Element.selectAttr(selector: String?, vararg attrs: String): String? {
        if (selector.isNullOrBlank()) return null
        val element = selectFirst(selector) ?: return null
        for (attr in attrs) {
            val value = element.attr(attr).ifBlank { null } ?: element.attr("abs:$attr").ifBlank { null }
            if (value != null) return value
        }
        return null
    }

    private fun parseStatus(status: String?): Int {
        if (status == null) return SManga.UNKNOWN
        return when {
            status.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
            status.contains("completed", ignoreCase = true) -> SManga.COMPLETED
            status.contains("hiatus", ignoreCase = true) -> SManga.ON_HIATUS
            status.contains("cancelled", ignoreCase = true) -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        // Basic date parsing - can be extended
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun extractNovelId(document: Document, url: String): String? {
        // Try config-defined ID extraction
        config.novelIdSelector?.let { selector ->
            document.selectFirst(selector)?.let { element ->
                config.novelIdAttr?.let { attr ->
                    return element.attr(attr).ifBlank { null }
                }
                return element.text().trim().ifBlank { null }
            }
        }

        // Fallback: extract from URL
        config.novelIdPattern?.let { pattern ->
            Regex(pattern).find(url)?.groupValues?.getOrNull(1)?.let { return it }
        }

        return null
    }

    private fun String.buildUrl(baseUrl: String, page: Int): String {
        var url = this.replace("{baseUrl}", baseUrl)

        // Handle {page} - only include if page > 1 or URL requires it
        if (url.contains("{page}")) {
            if (page == 1 && !url.contains("page={page}") && !url.contains("pg={page}")) {
                // For URLs like "https://site.com/{page}" on page 1, remove the placeholder entirely
                url = url.replace("/{page}", "")
                    .replace("?page={page}", "")
                    .replace("&page={page}", "")
                    .replace("{page}", "")
            } else {
                url = url.replace("{page}", page.toString())
            }
        }

        return url.trimEnd('/', '?', '&')
    }

    private fun String.buildSearchUrl(baseUrl: String, query: String, page: Int): String {
        var url = this.replace("{baseUrl}", baseUrl)

        // Handle query parameter - detect if using s, q, query, keyword, or search
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        url = url.replace("{query}", encodedQuery)

        // Handle {page} same as buildUrl
        if (url.contains("{page}")) {
            if (page == 1 && !url.contains("page={page}") && !url.contains("pg={page}")) {
                url = url.replace("&page={page}", "")
                    .replace("?page={page}", "")
                    .replace("{page}", "")
            } else {
                url = url.replace("{page}", page.toString())
            }
        }

        return url.trimEnd('/', '?', '&')
    }

    private fun String.buildAjaxUrl(baseUrl: String, novelId: String): String {
        return this.replace("{baseUrl}", baseUrl)
            .replace("{novelId}", novelId)
    }

    companion object {
        private fun generateId(name: String, baseUrl: String): Long {
            return (name + baseUrl).hashCode().toLong() and 0x7FFFFFFF
        }

        /**
         * Create a CustomNovelSource from JSON configuration
         */
        fun fromJson(json: String): CustomNovelSource {
            val config = Json.decodeFromString<CustomSourceConfig>(json)
            return CustomNovelSource(config)
        }
    }
}

// ======================== Configuration Data Classes ========================

/**
 * Source type enum for different multisrc configurations
 */
@Serializable
enum class CustomSourceType {
    GENERIC, // Generic CSS selector based
    MADARA, // Madara WordPress theme (uses AJAX for chapters)
    READNOVELFULL, // ReadNovelFull style sites
    LIGHTNOVELWP, // LightNovelWP theme
    READWN, // ReadWN style sites
}

@Serializable
data class CustomSourceConfig(
    val name: String,
    val baseUrl: String,
    val language: String = "en",
    val id: Long? = null,
    val sourceType: CustomSourceType = CustomSourceType.GENERIC,
    val popularUrl: String,
    val latestUrl: String? = null,
    val searchUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val selectors: SourceSelectors,
    val chapterAjax: String? = null,
    val novelIdSelector: String? = null,
    val novelIdAttr: String? = null,
    val novelIdPattern: String? = null,
    val reverseChapters: Boolean = false,
    val useCloudflare: Boolean = true,
    val useNewChapterEndpoint: Boolean = false,
    val postSearch: Boolean = false,
)

@Serializable
data class SourceSelectors(
    val popular: MangaListSelectors,
    val search: MangaListSelectors? = null,
    val details: DetailSelectors,
    val chapters: ChapterSelectors,
    val content: ContentSelectors,
)

@Serializable
data class MangaListSelectors(
    val list: String,
    val link: String? = null,
    val title: String? = null,
    val cover: String? = null,
    val nextPage: String? = null,
)

@Serializable
data class DetailSelectors(
    val title: String,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val status: String? = null,
    val cover: String? = null,
)

@Serializable
data class ChapterSelectors(
    val list: String,
    val link: String? = null,
    val name: String? = null,
    val date: String? = null,
)

@Serializable
data class ContentSelectors(
    val primary: String,
    val fallbacks: List<String>? = null,
    val removeSelectors: List<String>? = null,
)

// ======================== Config Templates ========================

/**
 * Pre-built configuration templates for common site structures
 */
object CustomSourceTemplates {

    /**
     * Template for Madara WordPress theme (most common novel theme)
     * Uses AJAX for chapter loading
     */
    val MADARA = CustomSourceConfig(
        name = "Madara Theme Site",
        baseUrl = "https://example.com",
        sourceType = CustomSourceType.MADARA,
        popularUrl = "{baseUrl}/page/{page}/?s=&post_type=wp-manga",
        latestUrl = "{baseUrl}/page/{page}/?s=&post_type=wp-manga&m_orderby=latest",
        searchUrl = "{baseUrl}/page/{page}/?s={query}&post_type=wp-manga",
        selectors = SourceSelectors(
            popular = MangaListSelectors(
                list = ".page-item-detail, .c-tabs-item__content, div.col-4, " +
                    "div.col-md-2, div.col-12.col-md-4, div.hover-details",
                link = ".post-title a, a",
                title = ".post-title, a[title]",
                cover = "img",
                nextPage = ".pagination a:contains(next)",
            ),
            details = DetailSelectors(
                title = ".post-title h1, h1.entry-title, #manga-title",
                author = ".manga-authors a, .author-content a",
                description = "div.summary__content, .manga-excerpt, #tab-manga-about",
                genre = ".genres-content a",
                status = ".post-status .summary-content",
                cover = ".summary_image img",
            ),
            chapters = ChapterSelectors(
                list = ".wp-manga-chapter",
                link = "a",
                name = "a",
                date = ".chapter-release-date",
            ),
            content = ContentSelectors(
                primary = ".text-left",
                fallbacks = listOf(
                    ".text-right",
                    ".entry-content",
                    ".c-blog-post > div > div:nth-child(2)",
                    ".reading-content",
                    ".chapter-content",
                ),
                removeSelectors = listOf(
                    "div.ads",
                    ".unlock-buttons",
                    "script",
                    "ins",
                    ".adsbygoogle",
                    ".code-block",
                    "noscript",
                    "iframe",
                ),
            ),
        ),
        useNewChapterEndpoint = false, // Set to true for sites using /ajax/chapters/ endpoint
        reverseChapters = true,
    )

    /**
     * Template for LightNovelWP theme
     */
    val LIGHTNOVELWP = CustomSourceConfig(
        name = "LightNovelWP Theme Site",
        baseUrl = "https://example.com",
        sourceType = CustomSourceType.LIGHTNOVELWP,
        popularUrl = "{baseUrl}/series?page={page}",
        latestUrl = "{baseUrl}/series?page={page}&order=latest",
        searchUrl = "{baseUrl}/series?page={page}&s={query}",
        selectors = SourceSelectors(
            popular = MangaListSelectors(
                list = "article",
                link = "a[title]",
                title = "a[title]",
                cover = ".ts-post-image img, .ts-post-image, img.ts-post-image, img",
                nextPage = ".pagination .next, .pagination a.next",
            ),
            details = DetailSelectors(
                title = ".entry-title",
                author = ".spe span:contains(Author) + span, .serl:contains(Author)",
                description = ".entry-content, [itemprop=description]",
                genre = ".genxed a, .sertogenre a",
                status = ".sertostat, .spe:contains(Status), .serl:contains(Status)",
                cover = ".thumb img, .thumbook img, img.ts-post-image",
            ),
            chapters = ChapterSelectors(
                list = ".eplister li",
                link = "a",
                name = ".epl-title, .epl-num span:first-child",
                date = ".epl-date",
            ),
            content = ContentSelectors(
                primary = ".epcontent.entry-content",
                fallbacks = listOf(
                    ".epcontent",
                    ".entry-content",
                    "#chapter-content",
                    ".reading-content",
                    ".text-left",
                ),
                removeSelectors = listOf(
                    ".unlock-buttons",
                    ".ads",
                    "script",
                    "style",
                    ".sharedaddy",
                    ".code-block",
                    ".su-spoiler-title",
                ),
            ),
        ),
        reverseChapters = true,
    )

    /**
     * Template for ReadNovelFull-style sites
     */
    val READNOVELFULL = CustomSourceConfig(
        name = "ReadNovelFull Style Site",
        baseUrl = "https://example.com",
        sourceType = CustomSourceType.READNOVELFULL,
        popularUrl = "{baseUrl}/most-popular?page={page}",
        latestUrl = "{baseUrl}/latest-release-novel?page={page}",
        searchUrl = "{baseUrl}/search?keyword={query}&page={page}",
        selectors = SourceSelectors(
            popular = MangaListSelectors(
                list = "div.col-novel-main div.list-novel div.row, div.archive div.row, " +
                    "div.index-intro div.item, div.ul-list1 div.li",
                link = "h3.novel-title a, .novel-title a, a.cover, h3.tit a",
                title = "h3.novel-title a, .novel-title a, h3.tit a",
                cover = "img",
                nextPage = "li.next:not(.disabled), ul.pagination li.active + li a",
            ),
            details = DetailSelectors(
                title = "h3.title, h1.tit",
                author = "div.info div:contains(Author) a, ul.info-meta li:contains(Author) a",
                description = "div.desc-text, div.inner, div.desc, div.m-desc div.txt div.inner",
                genre = "div.info div:contains(Genre) a, ul.info-meta li:contains(Genre) a",
                status = "div.info div:contains(Status), ul.info-meta li:contains(Status)",
                cover = "div.books img, div.book img, div.m-imgtxt img",
            ),
            chapters = ChapterSelectors(
                list = "ul.list-chapter li, ul#idData li",
                link = "a",
                name = "a",
            ),
            content = ContentSelectors(
                primary = "div#chr-content",
                fallbacks = listOf(
                    "div#chr-content.chr-c",
                    "div#chapter-content",
                    "div#article",
                    "div.txt",
                    "div.chapter-content",
                    "div.content",
                ),
                removeSelectors = listOf("div.ads", ".unlock-buttons", "script", "ins", ".adsbygoogle"),
            ),
        ),
        chapterAjax = "{baseUrl}/ajax/chapter-archive?novelId={novelId}",
        novelIdPattern = "/novel/([^/]+)",
    )

    /**
     * Template for ReadWN-style sites
     */
    val READWN = CustomSourceConfig(
        name = "ReadWN Style Site",
        baseUrl = "https://example.com",
        sourceType = CustomSourceType.READWN,
        popularUrl = "{baseUrl}/list/all/all-newstime-{page}.html",
        latestUrl = "{baseUrl}/list/all/all-lastdotime-{page}.html",
        searchUrl = "{baseUrl}/e/search/index.php",
        postSearch = true,
        selectors = SourceSelectors(
            popular = MangaListSelectors(
                list = "li.novel-item",
                link = "a[href]",
                title = "h4",
                cover = ".novel-cover img",
                nextPage = ".pagination a.next",
            ),
            details = DetailSelectors(
                title = "h1.novel-title",
                author = "span[itemprop=author]",
                description = ".summary",
                genre = "div.categories ul li",
                status = "div.header-stats span:has(small:contains(Status)) strong",
                cover = "figure.cover img",
            ),
            chapters = ChapterSelectors(
                list = ".chapter-list li",
                link = "a",
                name = "a .chapter-title",
                date = "a .chapter-update",
            ),
            content = ContentSelectors(
                primary = ".chapter-content",
                removeSelectors = listOf(".ads", "script"),
            ),
        ),
    )

    /**
     * Template for WordPress-based novel sites (common structure)
     */
    val WORDPRESS_NOVEL = CustomSourceConfig(
        name = "WordPress Novel Site",
        baseUrl = "https://example.com",
        sourceType = CustomSourceType.GENERIC,
        popularUrl = "{baseUrl}/novel/?m_orderby=rating&page={page}",
        latestUrl = "{baseUrl}/novel/?m_orderby=latest&page={page}",
        searchUrl = "{baseUrl}/?s={query}&post_type=wp-manga&page={page}",
        selectors = SourceSelectors(
            popular = MangaListSelectors(
                list = "div.page-item-detail, div.c-tabs-item__content",
                link = "a[href*=/novel/]",
                title = "h3.h5, .post-title a",
                cover = "img",
                nextPage = "a.nextpostslink, .nav-next a",
            ),
            details = DetailSelectors(
                title = ".post-title h1, h1.entry-title",
                author = ".author-content a, .manga-authors a",
                description = ".description-summary, .summary__content",
                genre = ".genres-content a",
                status = ".post-status .summary-content",
                cover = ".summary_image img",
            ),
            chapters = ChapterSelectors(
                list = "li.wp-manga-chapter, ul.main li",
                link = "a",
                name = "a",
                date = ".chapter-release-date",
            ),
            content = ContentSelectors(
                primary = ".text-left, .reading-content, .entry-content",
                fallbacks = listOf(".chapter-content", "#chapter-content", ".content"),
                removeSelectors = listOf(".ads", ".sharedaddy", ".code-block", "script"),
            ),
        ),
    )

    /**
     * Template for generic novel sites
     */
    val GENERIC = CustomSourceConfig(
        name = "Generic Novel Site",
        baseUrl = "https://example.com",
        sourceType = CustomSourceType.GENERIC,
        popularUrl = "{baseUrl}/novels?page={page}",
        searchUrl = "{baseUrl}/search?q={query}&page={page}",
        selectors = SourceSelectors(
            popular = MangaListSelectors(
                list = ".novel-list .novel-item, .book-list .book-item",
                link = "a[href]",
                title = ".title, .name, h3, h4",
                cover = "img",
                nextPage = ".pagination .next, a[rel=next]",
            ),
            details = DetailSelectors(
                title = "h1, .novel-title",
                author = ".author, .writer",
                description = ".description, .synopsis, .summary",
                genre = ".genres a, .tags a",
                status = ".status",
                cover = ".cover img, .thumbnail img",
            ),
            chapters = ChapterSelectors(
                list = ".chapter-list li, .chapters .chapter",
                link = "a",
                name = "a, .chapter-title",
                date = ".date, .time, time",
            ),
            content = ContentSelectors(
                primary = ".chapter-content, .novel-content, .text-content, article",
                fallbacks = listOf(".content", "#content", "main"),
                removeSelectors = listOf(".ads", "script", "style", ".hidden", ".navigation"),
            ),
        ),
    )

    /**
     * Get all available templates
     */
    fun getAll(): Map<String, CustomSourceConfig> = mapOf(
        "Madara Theme" to MADARA,
        "LightNovelWP Theme" to LIGHTNOVELWP,
        "ReadNovelFull Style" to READNOVELFULL,
        "ReadWN Style" to READWN,
        "WordPress Novel" to WORDPRESS_NOVEL,
        "Generic" to GENERIC,
    )

    /**
     * Convert a template to editable JSON
     */
    fun toJson(config: CustomSourceConfig): String {
        return Json { prettyPrint = true }.encodeToString(config)
    }
}
