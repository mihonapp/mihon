package eu.kanade.tachiyomi.extension

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import java.io.File

/**
 * Jsoup parsing tests using saved HTML files.
 *
 * These tests validate that our CSS selectors correctly parse HTML without making network requests.
 *
 * To add new test cases:
 * 1. Save HTML response to app/src/test/resources/html/{extension}/{page}.html
 * 2. Add selector configuration to the testConfigs map
 *
 * Run with:
 * ./gradlew :app:test --tests "eu.kanade.tachiyomi.extension.JsoupParsingTest"
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JsoupParsingTest {

    private val resourcesDir = File("src/test/resources/html")

    /**
     * Selector configuration for each extension
     */
    data class SelectorConfig(
        val name: String,
        val baseUrl: String,
        val popularSelectors: PopularSelectors,
        val detailsSelectors: DetailsSelectors,
        val chapterSelectors: ChapterSelectors,
        val contentSelectors: ContentSelectors,
    )

    data class PopularSelectors(
        val list: String,
        val title: String,
        val url: String,
        val cover: String? = null,
    )

    data class DetailsSelectors(
        val title: String,
        val author: String? = null,
        val description: String? = null,
        val cover: String? = null,
        val status: String? = null,
        val genres: String? = null,
    )

    data class ChapterSelectors(
        val list: String,
        val title: String,
        val url: String,
        val date: String? = null,
    )

    data class ContentSelectors(
        val primary: String,
        val fallbacks: List<String> = emptyList(),
        val removeSelectors: List<String> = emptyList(),
    )

    /**
     * Extension selector configurations
     * These match the selectors used in actual extensions
     */
    private val testConfigs = mapOf(
        "novelbuddy" to SelectorConfig(
            name = "NovelBuddy",
            baseUrl = "https://novelbuddy.com",
            popularSelectors = PopularSelectors(
                list = ".book-item",
                title = ".title a",
                url = ".title a[href]",
                cover = ".thumb img",
            ),
            detailsSelectors = DetailsSelectors(
                title = ".post-title h1, .name",
                author = ".author-content a, .info-item:contains(Author) a",
                description = ".summary__content, .desc-text, #editdescription",
                cover = ".summary_image img",
                status = ".summary-content:contains(Status), .info-item:contains(Status)",
                genres = ".genres-content a, .info-item:contains(Genre) a",
            ),
            chapterSelectors = ChapterSelectors(
                list = ".wp-manga-chapter, .chapter-item, li.chapter",
                title = "a",
                url = "a[href]",
                date = ".chapter-release-date, .chapter-time",
            ),
            contentSelectors = ContentSelectors(
                primary = ".reading-content, .chapter__content, .text-left",
                fallbacks = listOf(".entry-content", "#chapter-content", ".chapter-content"),
                removeSelectors = listOf(".ads", "script", "style", ".ad-container"),
            ),
        ),

        "webnovel" to SelectorConfig(
            name = "WebNovel",
            baseUrl = "https://www.webnovel.com",
            popularSelectors = PopularSelectors(
                list = "li.g_col_6, .j_list_item",
                title = "a.g_txt_over, .g_info h3 a",
                url = "a[href]",
                cover = "img.g_thumb",
            ),
            detailsSelectors = DetailsSelectors(
                title = "h1.g_title, .det-info h1",
                author = ".ell.dib.vam, .det-info .c_s",
                description = ".g_txt_over.oh, .det-info .g_txt_over",
                cover = ".g_thumb img",
            ),
            chapterSelectors = ChapterSelectors(
                list = "li a[href*=chapter], .volume-item a",
                title = "span, a",
                url = "a[href]",
            ),
            contentSelectors = ContentSelectors(
                primary = ".cha-words, .chapter-content",
                fallbacks = listOf(".cha-paragraph", "p"),
                removeSelectors = listOf(".pirate", "script", "style"),
            ),
        ),

        "foxaholic" to SelectorConfig(
            name = "Foxaholic",
            baseUrl = "https://foxaholic.com",
            popularSelectors = PopularSelectors(
                list = ".page-item-detail",
                title = ".h5 a",
                url = ".h5 a[href]",
                cover = ".item-thumb img",
            ),
            detailsSelectors = DetailsSelectors(
                title = ".post-title h1",
                author = ".author-content a",
                description = ".summary__content",
                cover = ".summary_image img",
            ),
            chapterSelectors = ChapterSelectors(
                list = ".wp-manga-chapter",
                title = "a",
                url = "a[href]",
                date = ".chapter-release-date",
            ),
            contentSelectors = ContentSelectors(
                primary = ".reading-content",
                fallbacks = listOf(".entry-content", ".text-left"),
                removeSelectors = listOf(".code-block", "script", "style"),
            ),
        ),

        "novelshub" to SelectorConfig(
            name = "NovelsHub",
            baseUrl = "https://novelshub.org",
            popularSelectors = PopularSelectors(
                list = "figure.relative, div.wrapper",
                title = "a.text-sm, a.font-bold, h1",
                url = "a[href*=/series/]",
                cover = "img",
            ),
            detailsSelectors = DetailsSelectors(
                title = "h1[class*=title]",
                author = "a[href*=/author/]",
                description = "div[class*=description], p.text-sm",
            ),
            chapterSelectors = ChapterSelectors(
                list = "a[href*=/read/]",
                title = "span, a",
                url = "a[href*=/read/]",
            ),
            contentSelectors = ContentSelectors(
                primary = "div.prose, article div[class*=text]",
                fallbacks = listOf("article p", ".chapter-content"),
                removeSelectors = listOf("script", "style", ".ad"),
            ),
        ),

        "mtlreader" to SelectorConfig(
            name = "MTLReader",
            baseUrl = "https://mtlreader.com",
            popularSelectors = PopularSelectors(
                list = ".novel-item, .book-item",
                title = ".novel-title a, .title a",
                url = "a[href*=/novel/]",
                cover = ".novel-cover img, .thumb img",
            ),
            detailsSelectors = DetailsSelectors(
                title = "h1.novel-title, .entry-title",
                author = ".novel-author a",
                description = ".novel-summary, .description",
            ),
            chapterSelectors = ChapterSelectors(
                list = ".chapter-list li a, .chapter-item a",
                title = "a",
                url = "a[href]",
            ),
            contentSelectors = ContentSelectors(
                primary = ".chapter-content, #chapter-content",
                fallbacks = listOf(".entry-content", ".reading-content"),
                removeSelectors = listOf("script", "style", ".ads"),
            ),
        ),
    )

    /**
     * Sample HTML content for testing when real files aren't available
     */
    private val sampleHtml = mapOf(
        "novelbuddy_popular" to """
            <html>
            <body>
                <div class="book-item">
                    <div class="thumb"><img src="https://example.com/cover1.jpg" /></div>
                    <div class="title"><a href="/novel/the-beginning-after-the-end">The Beginning After The End</a></div>
                </div>
                <div class="book-item">
                    <div class="thumb"><img src="https://example.com/cover2.jpg" /></div>
                    <div class="title"><a href="/novel/shadow-slave">Shadow Slave</a></div>
                </div>
                <div class="book-item">
                    <div class="thumb"><img src="https://example.com/cover3.jpg" /></div>
                    <div class="title"><a href="/novel/solo-leveling">Solo Leveling</a></div>
                </div>
            </body>
            </html>
        """.trimIndent(),

        "novelbuddy_details" to """
            <html>
            <body>
                <div class="post-title"><h1>The Beginning After The End</h1></div>
                <div class="summary_image"><img src="https://example.com/cover1.jpg" /></div>
                <div class="author-content"><a href="/author/turtleme">TurtleMe</a></div>
                <div class="summary__content">
                    <p>King Grey has unrivaled strength, wealth, and prestige in a world governed by martial ability.</p>
                </div>
                <div class="genres-content">
                    <a href="/genre/action">Action</a>
                    <a href="/genre/adventure">Adventure</a>
                    <a href="/genre/fantasy">Fantasy</a>
                </div>
            </body>
            </html>
        """.trimIndent(),

        "novelbuddy_chapters" to """
            <html>
            <body>
                <ul class="chapter-list">
                    <li class="wp-manga-chapter">
                        <a href="/novel/the-beginning-after-the-end/chapter-1">Chapter 1: The End</a>
                        <span class="chapter-release-date">Dec 1, 2023</span>
                    </li>
                    <li class="wp-manga-chapter">
                        <a href="/novel/the-beginning-after-the-end/chapter-2">Chapter 2: New Beginning</a>
                        <span class="chapter-release-date">Dec 2, 2023</span>
                    </li>
                    <li class="wp-manga-chapter">
                        <a href="/novel/the-beginning-after-the-end/chapter-3">Chapter 3: Arthur Leywin</a>
                        <span class="chapter-release-date">Dec 3, 2023</span>
                    </li>
                </ul>
            </body>
            </html>
        """.trimIndent(),

        "novelbuddy_content" to """
            <html>
            <body>
                <div class="reading-content">
                    <p>King Grey was awakened by a sudden pain.</p>
                    <p>His eyes fluttered open to see a ceiling he didn't recognize.</p>
                    <p>"Where am I?" he muttered, trying to sit up.</p>
                    <p>The last thing he remembered was the dagger plunging into his chest.</p>
                </div>
            </body>
            </html>
        """.trimIndent(),

        "webnovel_popular" to """
            <html>
            <body>
                <ul>
                    <li class="g_col_6 j_list_item">
                        <a href="/book/martial-peak_123"><img class="g_thumb" src="cover1.jpg" /></a>
                        <div class="g_info"><h3><a class="g_txt_over" href="/book/martial-peak_123">Martial Peak</a></h3></div>
                    </li>
                    <li class="g_col_6 j_list_item">
                        <a href="/book/supreme-magus_456"><img class="g_thumb" src="cover2.jpg" /></a>
                        <div class="g_info"><h3><a class="g_txt_over" href="/book/supreme-magus_456">Supreme Magus</a></h3></div>
                    </li>
                </ul>
            </body>
            </html>
        """.trimIndent(),

        "foxaholic_popular" to """
            <html>
            <body>
                <div class="page-item-detail">
                    <div class="item-thumb"><img src="thumb1.jpg" /></div>
                    <div class="h5"><a href="/novel/my-vampire-system">My Vampire System</a></div>
                </div>
                <div class="page-item-detail">
                    <div class="item-thumb"><img src="thumb2.jpg" /></div>
                    <div class="h5"><a href="/novel/legendary-mechanic">Legendary Mechanic</a></div>
                </div>
            </body>
            </html>
        """.trimIndent(),

        "novelshub_popular" to """
            <html>
            <body>
                <figure class="relative">
                    <a href="/series/cultivation-chat-group"><img src="cover1.jpg" /></a>
                    <a class="text-sm font-bold" href="/series/cultivation-chat-group">Cultivation Chat Group</a>
                </figure>
                <div class="wrapper">
                    <img class="cover-image" src="cover2.jpg" />
                    <h1>Lord of the Mysteries</h1>
                    <a href="/series/lord-of-the-mysteries">Read</a>
                </div>
            </body>
            </html>
        """.trimIndent(),

        "mtlreader_popular" to """
            <html>
            <body>
                <div class="novel-item">
                    <div class="novel-cover"><img src="cover1.jpg" /></div>
                    <div class="novel-title"><a href="/novel/reverend-insanity">Reverend Insanity</a></div>
                </div>
                <div class="book-item">
                    <div class="thumb"><img src="cover2.jpg" /></div>
                    <div class="title"><a href="/novel/i-have-countless-legendary-swords">Legendary Swords</a></div>
                </div>
            </body>
            </html>
        """.trimIndent(),
    )

    @TestFactory
    fun `test all extension selectors`(): List<DynamicNode> {
        return testConfigs.map { (key, config) ->
            DynamicContainer.dynamicContainer(
                config.name,
                listOf(
                    createPopularTest(key, config),
                    createDetailsTest(key, config),
                    createChaptersTest(key, config),
                    createContentTest(key, config),
                ),
            )
        }
    }

    private fun createPopularTest(key: String, config: SelectorConfig): DynamicTest {
        return DynamicTest.dynamicTest("Popular parsing") {
            val html = loadHtml(key, "popular") ?: sampleHtml["${key}_popular"]
            if (html == null) {
                println("⚠ No HTML available for ${config.name} popular - skipping")
                return@dynamicTest
            }

            val doc = Jsoup.parse(html)
            val items = doc.select(config.popularSelectors.list)

            println("Testing ${config.name} popular selectors:")
            println("  List selector: ${config.popularSelectors.list}")
            println("  Found items: ${items.size}")

            assert(items.isNotEmpty()) {
                "No items found with selector '${config.popularSelectors.list}'"
            }

            items.take(3).forEachIndexed { index, item ->
                val title = item.selectFirst(config.popularSelectors.title)?.text()
                val url = item.selectFirst(config.popularSelectors.url)?.attr("href")
                val cover = config.popularSelectors.cover?.let {
                    item.selectFirst(it)?.attr("src")
                }

                println("  Item $index:")
                println("    Title: $title")
                println("    URL: $url")
                println("    Cover: $cover")

                assert(!title.isNullOrBlank()) { "Title is empty for item $index" }
                assert(!url.isNullOrBlank()) { "URL is empty for item $index" }
            }
        }
    }

    private fun createDetailsTest(key: String, config: SelectorConfig): DynamicTest {
        return DynamicTest.dynamicTest("Details parsing") {
            val html = loadHtml(key, "details") ?: sampleHtml["${key}_details"]
            if (html == null) {
                println("⚠ No HTML available for ${config.name} details - skipping")
                return@dynamicTest
            }

            val doc = Jsoup.parse(html)

            println("Testing ${config.name} details selectors:")

            val title = doc.selectFirst(config.detailsSelectors.title)?.text()
            println("  Title selector: ${config.detailsSelectors.title}")
            println("  Title: $title")
            assert(!title.isNullOrBlank()) { "Title not found" }

            config.detailsSelectors.author?.let { selector ->
                val author = doc.selectFirst(selector)?.text()
                println("  Author selector: $selector")
                println("  Author: $author")
            }

            config.detailsSelectors.description?.let { selector ->
                val desc = doc.selectFirst(selector)?.text()
                println("  Description selector: $selector")
                println("  Description: ${desc?.take(100)}...")
            }
        }
    }

    private fun createChaptersTest(key: String, config: SelectorConfig): DynamicTest {
        return DynamicTest.dynamicTest("Chapters parsing") {
            val html = loadHtml(key, "chapters") ?: sampleHtml["${key}_chapters"]
            if (html == null) {
                println("⚠ No HTML available for ${config.name} chapters - skipping")
                return@dynamicTest
            }

            val doc = Jsoup.parse(html)
            val chapters = doc.select(config.chapterSelectors.list)

            println("Testing ${config.name} chapter selectors:")
            println("  List selector: ${config.chapterSelectors.list}")
            println("  Found chapters: ${chapters.size}")

            assert(chapters.isNotEmpty()) {
                "No chapters found with selector '${config.chapterSelectors.list}'"
            }

            chapters.take(3).forEachIndexed { index, item ->
                val title = item.selectFirst(config.chapterSelectors.title)?.text()
                    ?: item.text()
                val url = item.selectFirst(config.chapterSelectors.url)?.attr("href")
                    ?: item.attr("href")

                println("  Chapter $index:")
                println("    Title: $title")
                println("    URL: $url")

                assert(title.isNotBlank()) { "Chapter title is empty for item $index" }
                assert(url.isNotBlank()) { "Chapter URL is empty for item $index" }
            }
        }
    }

    private fun createContentTest(key: String, config: SelectorConfig): DynamicTest {
        return DynamicTest.dynamicTest("Content parsing") {
            val html = loadHtml(key, "content") ?: sampleHtml["${key}_content"]
            if (html == null) {
                println("⚠ No HTML available for ${config.name} content - skipping")
                return@dynamicTest
            }

            val doc = Jsoup.parse(html)

            println("Testing ${config.name} content selectors:")

            // Try primary selector first
            var content = doc.selectFirst(config.contentSelectors.primary)
            println("  Primary selector: ${config.contentSelectors.primary}")

            // Try fallbacks if primary doesn't work
            if (content == null) {
                for (fallback in config.contentSelectors.fallbacks) {
                    content = doc.selectFirst(fallback)
                    if (content != null) {
                        println("  Used fallback: $fallback")
                        break
                    }
                }
            }

            assert(content != null) {
                "No content found with primary or fallback selectors"
            }

            // Apply remove selectors
            config.contentSelectors.removeSelectors.forEach { selector ->
                content!!.select(selector).remove()
            }

            val text = content!!.text()
            println("  Content length: ${text.length} chars")
            println("  Preview: ${text.take(200)}...")

            assert(text.isNotBlank()) { "Content text is empty after parsing" }
        }
    }

    /**
     * Load HTML from test resources
     */
    private fun loadHtml(extension: String, page: String): String? {
        val file = File(resourcesDir, "$extension/$page.html")
        return if (file.exists()) {
            file.readText()
        } else {
            null
        }
    }

    @Test
    fun `test basic Jsoup operations`() {
        val html = """
            <html>
            <body>
                <div class="container">
                    <h1>Test Title</h1>
                    <p class="content">Test content paragraph</p>
                    <a href="/link" class="btn">Click me</a>
                </div>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html)

        // Test basic selectors
        assert(doc.selectFirst("h1")?.text() == "Test Title")
        assert(doc.selectFirst(".content")?.text() == "Test content paragraph")
        assert(doc.selectFirst("a.btn")?.attr("href") == "/link")
        assert(doc.selectFirst("a.btn")?.text() == "Click me")

        println("✓ Basic Jsoup operations working correctly")
    }

    @Test
    fun `test CSS selector combinations`() {
        val html = """
            <div class="item first">
                <span class="title">Item 1</span>
                <a href="/item/1">Link 1</a>
            </div>
            <div class="item second">
                <span class="title">Item 2</span>
                <a href="/item/2">Link 2</a>
            </div>
        """.trimIndent()

        val doc = Jsoup.parse(html)

        // Test descendant selector
        val titles = doc.select(".item .title")
        assert(titles.size == 2) { "Expected 2 titles, got ${titles.size}" }

        // Test compound selector
        val firstItem = doc.selectFirst(".item.first")
        assert(firstItem != null) { "Compound selector failed" }

        // Test contains selector
        val itemWith2 = doc.selectFirst(".item:has(.title:contains(Item 2))")
        assert(itemWith2?.selectFirst("a")?.attr("href") == "/item/2")

        println("✓ CSS selector combinations working correctly")
    }

    @Test
    fun `test attribute extraction`() {
        val html = """
            <img src="image.jpg" data-lazy-src="lazy.jpg" alt="Test image" />
            <a href="/path" title="Link title">Text</a>
            <div data-id="123" data-name="test">Content</div>
        """.trimIndent()

        val doc = Jsoup.parse(html)

        // Test various attribute extractions
        val img = doc.selectFirst("img")!!
        assert(img.attr("src") == "image.jpg")
        assert(img.attr("data-lazy-src") == "lazy.jpg")
        assert(img.attr("alt") == "Test image")

        // Test fallback pattern (used in extensions)
        val imgSrc = img.attr("src").ifEmpty { img.attr("data-lazy-src") }
        assert(imgSrc == "image.jpg")

        val link = doc.selectFirst("a")!!
        assert(link.attr("href") == "/path")
        assert(link.attr("title") == "Link title")

        println("✓ Attribute extraction working correctly")
    }
}
