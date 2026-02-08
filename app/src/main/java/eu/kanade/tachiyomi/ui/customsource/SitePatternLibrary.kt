package eu.kanade.tachiyomi.ui.customsource

/**
 * Library of common CSS selector patterns for different site frameworks.
 * Based on analysis of popular multi-src templates from LNReader and Tachiyomi extensions.
 */
object SitePatternLibrary {

    /**
     * Detected site framework type
     */
    enum class SiteFramework(val displayName: String, val keywords: List<String>) {
        MADARA("Madara/WP Manga", listOf("madara", "manga_reader", "wp-manga-", "c-blog-post", "manga-title-badges")),
        LIGHTNOVEL_WP("LightNovel WP", listOf("lightnovel", "listupd", "bsx", "postbody", "eplisterfull")),
        READNOVELFULL("ReadNovelFull", listOf("list-novel", "novel-item", "chr-name", "novel-title", "chapter-list")),
        WORDPRESS_GENERIC("WordPress", listOf("wp-content", "entry-content", "wp-block", "site-content")),
        WEBNOVEL_APP("Webnovel/Qidian", listOf("g_thumb", "g_bb", "_imgbox", "c_title", "j_chapterList")),
        BOXNOVEL("BoxNovel", listOf("listing-chapters_wrap", "version-chap", "wp-manga-chapter")),
        RANOBES("Ranobes", listOf("r-fullstory-chapters", "r-toc", "r-body")),
        NOVELFULL("NovelFull", listOf("list-chapter", "chapter-item", "novel-info")),
        CUSTOM("Custom/Unknown", emptyList());

        companion object {
            fun detect(html: String): SiteFramework {
                val lowerHtml = html.lowercase()
                return entries.filter { it != CUSTOM }
                    .firstOrNull { framework ->
                        framework.keywords.count { keyword -> lowerHtml.contains(keyword) } >= 2
                    } ?: CUSTOM
            }
        }
    }

    /**
     * Selector preset for a specific element type
     */
    data class SelectorPreset(
        val name: String,
        val selector: String,
        val description: String,
        val priority: Int = 0, // Higher = more specific/reliable
    )

    /**
     * Site pattern template with all selector presets
     */
    data class SitePattern(
        val framework: SiteFramework,
        val novelList: List<SelectorPreset>,
        val novelCover: List<SelectorPreset>,
        val novelTitle: List<SelectorPreset>,
        val novelLink: List<SelectorPreset>,
        val detailsTitle: List<SelectorPreset>,
        val detailsDescription: List<SelectorPreset>,
        val detailsCover: List<SelectorPreset>,
        val detailsTags: List<SelectorPreset>,
        val chapterList: List<SelectorPreset>,
        val chapterItem: List<SelectorPreset>,
        val chapterContent: List<SelectorPreset>,
        val pagination: List<SelectorPreset>,
        val searchUrl: List<SelectorPreset>,
    )

    /**
     * Madara/WP-Manga pattern (most common for manga/novel sites)
     */
    private val madaraPattern = SitePattern(
        framework = SiteFramework.MADARA,
        novelList = listOf(
            SelectorPreset("Madara Novel List", ".page-item-detail", "Standard Madara novel card container", 10),
            SelectorPreset("Madara Novel Grid", ".c-tabs-item__content", "Tab content novel item", 8),
            SelectorPreset("Madara Post Item", ".post-item", "Blog-style post item", 5),
        ),
        novelCover = listOf(
            SelectorPreset("Madara Cover", ".page-item-detail .item-thumb img", "Novel thumbnail image", 10),
            SelectorPreset("Madara Cover Alt", ".c-tabs-item__content .tab-thumb img", "Tab thumbnail", 8),
            SelectorPreset("Madara Cover Grid", ".item-summary img", "Summary section image", 5),
        ),
        novelTitle = listOf(
            SelectorPreset("Madara Title", ".post-title h3 a", "Main title link", 10),
            SelectorPreset("Madara Title Alt", ".item-summary .post-title a", "Summary title", 8),
            SelectorPreset("Madara H4 Title", ".post-title h4 a", "H4 title variant", 5),
        ),
        novelLink = listOf(
            SelectorPreset("Madara Link", ".post-title a", "Title link", 10),
            SelectorPreset("Madara Item Link", ".item-thumb a", "Thumbnail link", 8),
        ),
        detailsTitle = listOf(
            SelectorPreset("Madara Detail Title", ".post-title h1", "Main page title", 10),
            SelectorPreset("Madara Detail H3", ".post-title h3", "H3 title", 8),
        ),
        detailsDescription = listOf(
            SelectorPreset("Madara Description", ".description-summary .summary__content", "Summary content", 10),
            SelectorPreset("Madara Summary", ".summary__content p", "Summary paragraph", 8),
            SelectorPreset("Madara Desc Alt", ".manga-summary p", "Alternative summary", 5),
        ),
        detailsCover = listOf(
            SelectorPreset("Madara Page Cover", ".summary_image img", "Detail page cover", 10),
            SelectorPreset("Madara Tab Thumb", ".tab-summary .summary_image img", "Tab summary cover", 8),
        ),
        detailsTags = listOf(
            SelectorPreset("Madara Genres", ".genres-content a", "Genre links", 10),
            SelectorPreset("Madara Tags", ".tags-content a", "Tag links", 8),
        ),
        chapterList = listOf(
            SelectorPreset("Madara Chapter List", ".listing-chapters_wrap li", "Chapter list items", 10),
            SelectorPreset("Madara Version Chap", ".version-chap li", "Versioned chapters", 8),
            SelectorPreset("Madara WP Chapter", ".wp-manga-chapter", "WordPress manga chapters", 5),
        ),
        chapterItem = listOf(
            SelectorPreset("Madara Chapter Link", ".wp-manga-chapter a", "Chapter link", 10),
            SelectorPreset("Madara Chap Link Alt", ".listing-chapters_wrap li a", "List chapter link", 8),
        ),
        chapterContent = listOf(
            SelectorPreset("Madara Content", ".reading-content .text-left", "Main reading content", 10),
            SelectorPreset("Madara Content Entry", ".entry-content", "Entry content area", 8),
            SelectorPreset("Madara Text Content", ".text-left", "Left-aligned text", 5),
        ),
        pagination = listOf(
            SelectorPreset("Madara Pagination", ".nav-links .page-numbers", "Page numbers", 10),
            SelectorPreset("Madara Next Page", ".nav-previous a, .nav-next a", "Prev/Next links", 8),
        ),
        searchUrl = listOf(
            SelectorPreset("Madara Search", "?s={query}&post_type=wp-manga", "WP Manga search", 10),
        ),
    )

    /**
     * LightNovel WP pattern
     */
    private val lightNovelWpPattern = SitePattern(
        framework = SiteFramework.LIGHTNOVEL_WP,
        novelList = listOf(
            SelectorPreset("LNWP Novel List", "article.maindet", "Main detail article", 10),
            SelectorPreset("LNWP Listupd", ".listupd article", "Update list article", 8),
            SelectorPreset("LNWP BSX", ".bsx", "BSX container", 5),
        ),
        novelCover = listOf(
            SelectorPreset("LNWP Cover", ".limit img", "Limited container image", 10),
            SelectorPreset("LNWP Thumb", ".thumb img", "Thumbnail", 8),
            SelectorPreset("LNWP IMG", "article img.ts-post-image", "TS post image", 5),
        ),
        novelTitle = listOf(
            SelectorPreset("LNWP Title", ".tt", "TT class title", 10),
            SelectorPreset("LNWP Entry Title", ".entry-title a", "Entry title link", 8),
            SelectorPreset("LNWP Series Title", ".series-title a", "Series title", 5),
        ),
        novelLink = listOf(
            SelectorPreset("LNWP Link", ".bsx a", "BSX link", 10),
            SelectorPreset("LNWP Entry Link", ".entry-title a", "Entry link", 8),
        ),
        detailsTitle = listOf(
            SelectorPreset("LNWP Detail Title", ".entry-title", "Entry title", 10),
            SelectorPreset("LNWP Infox Title", ".infox h1", "Infox H1", 8),
        ),
        detailsDescription = listOf(
            SelectorPreset("LNWP Synp", ".synp .entry-content", "Synopsis entry", 10),
            SelectorPreset("LNWP Desc", ".entry-content-wrap p", "Content wrap paragraphs", 8),
        ),
        detailsCover = listOf(
            SelectorPreset("LNWP Detail Cover", ".thumb img", "Detail thumb", 10),
            SelectorPreset("LNWP Bigcover", ".bigcover img", "Big cover", 8),
        ),
        detailsTags = listOf(
            SelectorPreset("LNWP Genres", ".genxed a", "Genre links", 10),
            SelectorPreset("LNWP Info Genre", ".infox .genre a", "Info genre", 8),
        ),
        chapterList = listOf(
            SelectorPreset("LNWP Chapter List", "#chapterlist li", "Chapter list items", 10),
            SelectorPreset("LNWP Eplister", ".eplister li", "Episode lister", 8),
            SelectorPreset("LNWP Eplisterfull", ".eplisterfull li", "Full episode list", 5),
        ),
        chapterItem = listOf(
            SelectorPreset("LNWP Chapter Link", ".chbox a", "Chapter box link", 10),
            SelectorPreset("LNWP Eph Link", ".eph a", "Episode header link", 8),
        ),
        chapterContent = listOf(
            SelectorPreset("LNWP Content", ".epcontent", "Episode content", 10),
            SelectorPreset("LNWP Reader Area", ".reader-area", "Reader area", 8),
            SelectorPreset("LNWP Entry", ".entry-content", "Entry content", 5),
        ),
        pagination = listOf(
            SelectorPreset("LNWP Pagination", ".pagination a", "Pagination links", 10),
            SelectorPreset("LNWP Hpage", ".hpage a", "H page links", 8),
        ),
        searchUrl = listOf(
            SelectorPreset("LNWP Search", "?s={query}", "Standard WP search", 10),
        ),
    )

    /**
     * ReadNovelFull pattern
     */
    private val readNovelFullPattern = SitePattern(
        framework = SiteFramework.READNOVELFULL,
        novelList = listOf(
            SelectorPreset("RNF Novel List", ".list-novel .row", "Novel list row", 10),
            SelectorPreset("RNF Novel Item", ".novel-item", "Novel item", 8),
            SelectorPreset("RNF Col", ".col-novel-main", "Novel column", 5),
        ),
        novelCover = listOf(
            SelectorPreset("RNF Cover", ".cover img", "Cover image", 10),
            SelectorPreset("RNF Lazy", ".lazy", "Lazy-loaded image", 8),
        ),
        novelTitle = listOf(
            SelectorPreset("RNF Title", ".novel-title a", "Novel title link", 10),
            SelectorPreset("RNF Title H3", "h3.novel-title a", "H3 title", 8),
        ),
        novelLink = listOf(
            SelectorPreset("RNF Link", ".novel-title a", "Title link", 10),
            SelectorPreset("RNF Cover Link", ".cover a", "Cover link", 8),
        ),
        detailsTitle = listOf(
            SelectorPreset("RNF Detail Title", ".title", "Title element", 10),
            SelectorPreset("RNF H3 Title", "h3.title", "H3 title", 8),
        ),
        detailsDescription = listOf(
            SelectorPreset("RNF Description", ".desc-text", "Description text", 10),
            SelectorPreset("RNF Summary", ".summary .content", "Summary content", 8),
        ),
        detailsCover = listOf(
            SelectorPreset("RNF Page Cover", ".book img", "Book cover", 10),
            SelectorPreset("RNF Info Img", ".info-holder img", "Info holder image", 8),
        ),
        detailsTags = listOf(
            SelectorPreset("RNF Genres", ".info a[href*=genre]", "Genre links", 10),
            SelectorPreset("RNF Tags", ".tag a", "Tag links", 8),
        ),
        chapterList = listOf(
            SelectorPreset("RNF Chapter List", "#list-chapter li", "Chapter list items", 10),
            SelectorPreset("RNF Row Chapter", ".row-content-chapter li", "Row content chapters", 8),
        ),
        chapterItem = listOf(
            SelectorPreset("RNF Chapter Link", "#list-chapter li a", "Chapter link", 10),
            SelectorPreset("RNF Chr Name", ".chr-name", "Chapter name", 8),
        ),
        chapterContent = listOf(
            SelectorPreset("RNF Content", "#chr-content", "Chapter content", 10),
            SelectorPreset("RNF Chapter Content", ".chapter-content", "Chapter content alt", 8),
            SelectorPreset("RNF Container", "#chapter-container", "Container", 5),
        ),
        pagination = listOf(
            SelectorPreset("RNF Pagination", ".pagination li a", "Pagination links", 10),
            SelectorPreset("RNF Last", ".last a", "Last page link", 8),
        ),
        searchUrl = listOf(
            SelectorPreset("RNF Search", "/search?keyword={query}", "Keyword search", 10),
        ),
    )

    /**
     * Generic WordPress pattern
     */
    private val wordpressPattern = SitePattern(
        framework = SiteFramework.WORDPRESS_GENERIC,
        novelList = listOf(
            SelectorPreset("WP Post", "article.post", "Article post", 10),
            SelectorPreset("WP Entry", ".entry", "Entry element", 8),
            SelectorPreset("WP Hentry", ".hentry", "H-entry", 5),
        ),
        novelCover = listOf(
            SelectorPreset("WP Featured", ".wp-post-image", "Featured image", 10),
            SelectorPreset("WP Thumb", ".post-thumbnail img", "Post thumbnail", 8),
            SelectorPreset("WP Attachment", ".attachment-post-thumbnail", "Attachment", 5),
        ),
        novelTitle = listOf(
            SelectorPreset("WP Entry Title", ".entry-title a", "Entry title", 10),
            SelectorPreset("WP Post Title", ".post-title a", "Post title", 8),
        ),
        novelLink = listOf(
            SelectorPreset("WP Entry Link", ".entry-title a", "Title link", 10),
            SelectorPreset("WP Read More", ".read-more a", "Read more link", 8),
        ),
        detailsTitle = listOf(
            SelectorPreset("WP Page Title", ".entry-title", "Entry title", 10),
            SelectorPreset("WP H1", "h1.title", "H1 title", 8),
        ),
        detailsDescription = listOf(
            SelectorPreset("WP Content", ".entry-content p", "Entry paragraphs", 10),
            SelectorPreset("WP Excerpt", ".entry-excerpt", "Excerpt", 8),
        ),
        detailsCover = listOf(
            SelectorPreset("WP Content Image", ".entry-content img", "Content image", 10),
            SelectorPreset("WP Single Thumb", ".single-post-thumbnail img", "Single thumbnail", 8),
        ),
        detailsTags = listOf(
            SelectorPreset("WP Categories", ".cat-links a", "Category links", 10),
            SelectorPreset("WP Tags", ".tags-links a", "Tag links", 8),
        ),
        chapterList = listOf(
            SelectorPreset("WP Chapters", ".entry-content ul li a", "Content list links", 10),
            SelectorPreset("WP TOC", ".toc li a", "TOC links", 8),
        ),
        chapterItem = listOf(
            SelectorPreset("WP Chapter Link", ".entry-content a", "Content link", 10),
        ),
        chapterContent = listOf(
            SelectorPreset("WP Chapter Content", ".entry-content", "Entry content", 10),
            SelectorPreset("WP Article", "article .content", "Article content", 8),
        ),
        pagination = listOf(
            SelectorPreset("WP Nav Links", ".nav-links a", "Navigation links", 10),
            SelectorPreset("WP Page Numbers", ".page-numbers", "Page numbers", 8),
        ),
        searchUrl = listOf(
            SelectorPreset("WP Search", "?s={query}", "Standard search", 10),
        ),
    )

    /**
     * All available patterns by framework
     */
    val patterns: Map<SiteFramework, SitePattern> = mapOf(
        SiteFramework.MADARA to madaraPattern,
        SiteFramework.LIGHTNOVEL_WP to lightNovelWpPattern,
        SiteFramework.READNOVELFULL to readNovelFullPattern,
        SiteFramework.WORDPRESS_GENERIC to wordpressPattern,
    )

    /**
     * Get suggested selectors for a step based on detected framework
     */
    fun getSuggestedSelectors(
        framework: SiteFramework,
        step: SelectorWizardStep,
    ): List<SelectorPreset> {
        val pattern = patterns[framework] ?: patterns[SiteFramework.WORDPRESS_GENERIC]!!

        return when (step) {
            SelectorWizardStep.TRENDING, SelectorWizardStep.TRENDING_NOVELS -> pattern?.novelList ?: emptyList()
            SelectorWizardStep.NOVEL_CARD -> {
                // Combine cover, title, link suggestions
                (pattern?.novelCover ?: emptyList()) +
                    (pattern?.novelTitle ?: emptyList()) +
                    (pattern?.novelLink ?: emptyList())
            }
            SelectorWizardStep.NEW_NOVELS_SECTION, SelectorWizardStep.NEW_NOVELS -> pattern?.novelList ?: emptyList()
            SelectorWizardStep.NOVEL_PAGE, SelectorWizardStep.NOVEL_DETAILS -> {
                (pattern?.detailsTitle ?: emptyList()) +
                    (pattern?.detailsDescription ?: emptyList()) +
                    (pattern?.detailsCover ?: emptyList()) +
                    (pattern?.detailsTags ?: emptyList())
            }
            SelectorWizardStep.CHAPTER_LIST -> pattern?.chapterList ?: emptyList()
            SelectorWizardStep.CHAPTER_PAGE, SelectorWizardStep.CHAPTER_CONTENT -> pattern?.chapterContent ?: emptyList()
            SelectorWizardStep.PAGINATION -> pattern?.pagination ?: emptyList()
            SelectorWizardStep.SEARCH, SelectorWizardStep.SEARCH_URL_PATTERN -> pattern?.searchUrl ?: emptyList()
            else -> emptyList()
        }
    }

    /**
     * Get all available frameworks for display in UI
     */
    fun getAvailableFrameworks(): List<SiteFramework> {
        return SiteFramework.entries.filter { it != SiteFramework.CUSTOM }
    }
}
