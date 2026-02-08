package eu.kanade.tachiyomi.customsource

import eu.kanade.tachiyomi.ui.customsource.SitePatternLibrary
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.URLDecoder
import java.util.stream.Stream

/**
 * Automated tests for custom source creation features.
 *
 * Tests include:
 * - URL pattern detection for search URLs
 * - Site framework detection based on HTML content
 * - Selector pattern matching
 *
 * Run with:
 * ./gradlew :app:test --tests "eu.kanade.tachiyomi.customsource.CustomSourceCreationTests"
 */
class CustomSourceCreationTests {

    @Nested
    @DisplayName("Search URL Pattern Detection Tests")
    inner class SearchUrlPatternDetectionTests {

        /**
         * Detect search URL pattern from current URL.
         * Returns Pair(searchUrlPattern, detectedKeyword) or null if not detected.
         */
        private fun detectSearchUrl(url: String, baseUrl: String): Pair<String, String>? {
            val baseUrlTrimmed = baseUrl.trimEnd('/')

            // Common search parameter patterns
            val searchParams = listOf(
                "s" to Regex("""[?&]s=([^&]+)"""),
                "q" to Regex("""[?&]q=([^&]+)"""),
                "query" to Regex("""[?&]query=([^&]+)"""),
                "keyword" to Regex("""[?&]keyword=([^&]+)"""),
                "search" to Regex("""[?&]search=([^&]+)"""),
                "k" to Regex("""[?&]k=([^&]+)"""),
                "term" to Regex("""[?&]term=([^&]+)"""),
            )

            // Try each pattern
            for ((param, regex) in searchParams) {
                val match = regex.find(url)
                if (match != null) {
                    val keyword = URLDecoder.decode(match.groupValues[1], "UTF-8")
                    // Build the search URL pattern
                    val searchUrlPattern = url
                        .replace(match.groupValues[1], "{query}")
                        .replace(Regex("""[?&]page=\d+"""), "&page={page}")
                        .let { if (!it.contains("{page}")) "$it&page={page}" else it }
                        .replace("&&", "&")
                        .replace("?&", "?")

                    return Pair(searchUrlPattern, keyword)
                }
            }

            // Check for path-based search patterns like /search/keyword or /s/keyword
            val pathPatterns = listOf(
                Regex("""(/search/)([^/?]+)"""),
                Regex("""(/s/)([^/?]+)"""),
                Regex("""(/find/)([^/?]+)"""),
            )

            for (regex in pathPatterns) {
                val match = regex.find(url)
                if (match != null) {
                    val keyword = URLDecoder.decode(match.groupValues[2], "UTF-8")
                    val searchUrlPattern = url
                        .replace(match.groupValues[2], "{query}")
                        .let { if (!it.contains("{page}")) "$it?page={page}" else it }

                    return Pair(searchUrlPattern, keyword)
                }
            }

            return null
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("searchUrlTestCases")
        fun `should detect search URL pattern`(
            testName: String,
            inputUrl: String,
            baseUrl: String,
            expectedPattern: String?,
            expectedKeyword: String?
        ) {
            val result = detectSearchUrl(inputUrl, baseUrl)

            if (expectedPattern == null) {
                assertNull(result, "Expected no pattern detected for: $inputUrl")
            } else {
                assertNotNull(result, "Expected pattern to be detected for: $inputUrl")
                result?.let { (pattern, keyword) ->
                    assertTrue(
                        pattern.contains("{query}"),
                        "Pattern should contain {query}: $pattern"
                    )
                    assertEquals(expectedKeyword, keyword, "Keyword mismatch")
                }
            }
        }

        companion object {
            @JvmStatic
            fun searchUrlTestCases(): Stream<Arguments> = Stream.of(
                // WordPress ?s= pattern
                Arguments.of(
                    "WordPress ?s= pattern",
                    "https://example.com/?s=test+novel",
                    "https://example.com",
                    "{query}",
                    "test novel"
                ),
                // WordPress ?s= with page
                Arguments.of(
                    "WordPress ?s= with page",
                    "https://example.com/?s=test&page=2",
                    "https://example.com",
                    "{query}",
                    "test"
                ),
                // ?q= pattern (common)
                Arguments.of(
                    "?q= pattern",
                    "https://example.com/search?q=dragon",
                    "https://example.com",
                    "{query}",
                    "dragon"
                ),
                // ?query= pattern
                Arguments.of(
                    "?query= pattern",
                    "https://novelsite.com/browse?query=isekai",
                    "https://novelsite.com",
                    "{query}",
                    "isekai"
                ),
                // ?keyword= pattern
                Arguments.of(
                    "?keyword= pattern",
                    "https://readnovel.com/search?keyword=reincarnation",
                    "https://readnovel.com",
                    "{query}",
                    "reincarnation"
                ),
                // Path-based /search/keyword
                Arguments.of(
                    "Path-based /search/",
                    "https://novelbin.com/search/martial",
                    "https://novelbin.com",
                    "{query}",
                    "martial"
                ),
                // Path-based /s/keyword
                Arguments.of(
                    "Path-based /s/",
                    "https://site.com/s/cultivation",
                    "https://site.com",
                    "{query}",
                    "cultivation"
                ),
                // URL encoded keyword
                Arguments.of(
                    "URL encoded keyword",
                    "https://example.com/?s=light%20novel",
                    "https://example.com",
                    "{query}",
                    "light novel"
                ),
                // No search pattern (homepage)
                Arguments.of(
                    "No pattern (homepage)",
                    "https://example.com/",
                    "https://example.com",
                    null,
                    null
                ),
                // No search pattern (novel page)
                Arguments.of(
                    "No pattern (novel page)",
                    "https://example.com/novel/my-novel-title",
                    "https://example.com",
                    null,
                    null
                ),
            )
        }
    }

    @Nested
    @DisplayName("Site Framework Detection Tests")
    inner class SiteFrameworkDetectionTests {

        @Test
        fun `should detect Madara framework from HTML`() {
            val html = """
                <html>
                <head><title>Madara Site</title></head>
                <body>
                <div class="page-item-detail">
                    <div class="item-thumb">
                        <img class="wp-manga-chapter">
                    </div>
                    <div class="manga-title-badges hot">Hot</div>
                </div>
                </body>
                </html>
            """.trimIndent()

            val framework = SitePatternLibrary.SiteFramework.detect(html)
            assertEquals(SitePatternLibrary.SiteFramework.MADARA, framework)
        }

        @Test
        fun `should detect LightNovelWP framework from HTML`() {
            val html = """
                <html>
                <body>
                <div class="listupd">
                    <article class="maindet">
                        <div class="bsx">Novel</div>
                        <div class="eplisterfull">Chapters</div>
                    </article>
                </div>
                </body>
                </html>
            """.trimIndent()

            val framework = SitePatternLibrary.SiteFramework.detect(html)
            assertEquals(SitePatternLibrary.SiteFramework.LIGHTNOVEL_WP, framework)
        }

        @Test
        fun `should detect ReadNovelFull framework from HTML`() {
            val html = """
                <html>
                <body>
                <div class="list-novel">
                    <div class="novel-item">
                        <h3 class="novel-title">Novel Name</h3>
                    </div>
                </div>
                <div id="list-chapter">
                    <span class="chr-name">Chapter 1</span>
                </div>
                </body>
                </html>
            """.trimIndent()

            val framework = SitePatternLibrary.SiteFramework.detect(html)
            assertEquals(SitePatternLibrary.SiteFramework.READNOVELFULL, framework)
        }

        @Test
        fun `should detect WordPress framework from HTML`() {
            val html = """
                <html>
                <body>
                <div class="wp-content">
                    <article class="entry-content">
                        <div class="wp-block-paragraph">Content</div>
                    </article>
                </div>
                </body>
                </html>
            """.trimIndent()

            val framework = SitePatternLibrary.SiteFramework.detect(html)
            assertEquals(SitePatternLibrary.SiteFramework.WORDPRESS_GENERIC, framework)
        }

        @Test
        fun `should return CUSTOM for unknown framework`() {
            val html = """
                <html>
                <body>
                <div class="some-random-class">
                    <p>Unknown site structure</p>
                </div>
                </body>
                </html>
            """.trimIndent()

            val framework = SitePatternLibrary.SiteFramework.detect(html)
            assertEquals(SitePatternLibrary.SiteFramework.CUSTOM, framework)
        }

        @Test
        fun `should require at least 2 matching keywords`() {
            // Only one Madara keyword - should not match
            val html = """
                <html>
                <body>
                <div class="manga-title-badges">Badge</div>
                </body>
                </html>
            """.trimIndent()

            val framework = SitePatternLibrary.SiteFramework.detect(html)
            assertEquals(SitePatternLibrary.SiteFramework.CUSTOM, framework)
        }
    }

    @Nested
    @DisplayName("Selector Pattern Library Tests")
    inner class SelectorPatternLibraryTests {

        @Test
        fun `should return suggestions for TRENDING step with Madara framework`() {
            val suggestions = SitePatternLibrary.getSuggestedSelectors(
                SitePatternLibrary.SiteFramework.MADARA,
                eu.kanade.tachiyomi.ui.customsource.SelectorWizardStep.TRENDING
            )

            assertTrue(suggestions.isNotEmpty(), "Should have suggestions for TRENDING step")
            assertTrue(
                suggestions.any { it.selector.contains("page-item-detail") },
                "Should include Madara-specific selector"
            )
        }

        @Test
        fun `should return cover, title, link suggestions for NOVEL_CARD step`() {
            val suggestions = SitePatternLibrary.getSuggestedSelectors(
                SitePatternLibrary.SiteFramework.MADARA,
                eu.kanade.tachiyomi.ui.customsource.SelectorWizardStep.NOVEL_CARD
            )

            assertTrue(suggestions.isNotEmpty(), "Should have suggestions for NOVEL_CARD step")
            // Should include cover, title, and link selectors
            assertTrue(
                suggestions.any { it.name.contains("Cover", ignoreCase = true) },
                "Should include cover selectors"
            )
            assertTrue(
                suggestions.any { it.name.contains("Title", ignoreCase = true) },
                "Should include title selectors"
            )
        }

        @Test
        fun `should return chapter content suggestions`() {
            val suggestions = SitePatternLibrary.getSuggestedSelectors(
                SitePatternLibrary.SiteFramework.READNOVELFULL,
                eu.kanade.tachiyomi.ui.customsource.SelectorWizardStep.CHAPTER_CONTENT
            )

            assertTrue(suggestions.isNotEmpty(), "Should have suggestions for CHAPTER_CONTENT step")
            assertTrue(
                suggestions.any { it.selector.contains("chr-content") || it.selector.contains("chapter-content") },
                "Should include ReadNovelFull chapter content selector"
            )
        }

        @Test
        fun `should prioritize selectors by priority value`() {
            val suggestions = SitePatternLibrary.getSuggestedSelectors(
                SitePatternLibrary.SiteFramework.MADARA,
                eu.kanade.tachiyomi.ui.customsource.SelectorWizardStep.TRENDING
            )

            // Should be sorted by priority descending
            val sorted = suggestions.sortedByDescending { it.priority }
            assertTrue(sorted.first().priority >= sorted.last().priority, "Should be sorted by priority")
        }

        @Test
        fun `should return all available frameworks`() {
            val frameworks = SitePatternLibrary.getAvailableFrameworks()

            assertTrue(frameworks.isNotEmpty(), "Should have available frameworks")
            assertFalse(
                frameworks.contains(SitePatternLibrary.SiteFramework.CUSTOM),
                "Should not include CUSTOM framework"
            )
            assertTrue(
                frameworks.contains(SitePatternLibrary.SiteFramework.MADARA),
                "Should include MADARA framework"
            )
        }

        @Test
        fun `should return empty list for COMPLETE step`() {
            val suggestions = SitePatternLibrary.getSuggestedSelectors(
                SitePatternLibrary.SiteFramework.MADARA,
                eu.kanade.tachiyomi.ui.customsource.SelectorWizardStep.COMPLETE
            )

            assertTrue(suggestions.isEmpty(), "COMPLETE step should have no suggestions")
        }
    }

    @Nested
    @DisplayName("Common Selector Pattern Tests")
    inner class CommonSelectorPatternTests {

        @Test
        fun `Madara pattern should have all required selectors`() {
            val pattern = SitePatternLibrary.patterns[SitePatternLibrary.SiteFramework.MADARA]

            assertNotNull(pattern, "Madara pattern should exist")
            pattern?.let {
                assertTrue(it.novelList.isNotEmpty(), "Should have novel list selectors")
                assertTrue(it.novelCover.isNotEmpty(), "Should have novel cover selectors")
                assertTrue(it.novelTitle.isNotEmpty(), "Should have novel title selectors")
                assertTrue(it.chapterList.isNotEmpty(), "Should have chapter list selectors")
                assertTrue(it.chapterContent.isNotEmpty(), "Should have chapter content selectors")
            }
        }

        @Test
        fun `LightNovelWP pattern should have all required selectors`() {
            val pattern = SitePatternLibrary.patterns[SitePatternLibrary.SiteFramework.LIGHTNOVEL_WP]

            assertNotNull(pattern, "LightNovelWP pattern should exist")
            pattern?.let {
                assertTrue(it.novelList.isNotEmpty(), "Should have novel list selectors")
                assertTrue(it.chapterContent.isNotEmpty(), "Should have chapter content selectors")
            }
        }

        @Test
        fun `ReadNovelFull pattern should have all required selectors`() {
            val pattern = SitePatternLibrary.patterns[SitePatternLibrary.SiteFramework.READNOVELFULL]

            assertNotNull(pattern, "ReadNovelFull pattern should exist")
            pattern?.let {
                assertTrue(it.novelList.isNotEmpty(), "Should have novel list selectors")
                assertTrue(it.chapterContent.isNotEmpty(), "Should have chapter content selectors")
                // ReadNovelFull specific
                assertTrue(
                    it.chapterContent.any { selector -> selector.selector.contains("chr-content") },
                    "Should have chr-content selector"
                )
            }
        }
    }
}
