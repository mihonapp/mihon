package eu.kanade.tachiyomi.extension

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.NovelSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Instrumented Test for Auto-Discovering and Testing ALL Installed Extensions
 *
 * This test runs on an Android device/emulator and automatically discovers
 * ALL installed extensions without requiring prior configuration.
 *
 * RUN THIS TEST:
 * ./gradlew :app:connectedDebugAndroidTest --tests "eu.kanade.tachiyomi.extension.ExtensionAutoDiscoveryTest"
 *
 * Or from Android Studio: Right-click on this class and select "Run"
 */
@RunWith(AndroidJUnit4::class)
class ExtensionAutoDiscoveryTest {

    private lateinit var context: Context
    private lateinit var extensionManager: ExtensionManager

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    // Test results tracking
    private val testResults = mutableMapOf<String, ExtensionTestReport>()
    private var totalSources = 0
    private var passedSources = 0
    private var failedSources = 0

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        extensionManager = Injekt.get<ExtensionManager>()

        // Wait for extension manager to initialize
        runBlocking {
            var attempts = 0
            while (!extensionManager.isInitialized.value && attempts < 50) {
                kotlinx.coroutines.delay(100)
                attempts++
            }
        }
    }

    @After
    fun printSummary() {
        println("\n" + "=".repeat(80))
        println("EXTENSION AUTO-DISCOVERY TEST SUMMARY")
        println("=".repeat(80))
        println("Total sources tested: $totalSources")
        println("Passed: $passedSources")
        println("Failed: $failedSources")
        println("=".repeat(80))

        testResults.forEach { (name, report) ->
            val failedCount = report.results.count { !it.success }
            val status = if (failedCount == 0) "✓ PASSED" else "✗ FAILED ($failedCount issues)"
            println("\n$name: $status")

            report.results.forEach { result ->
                val icon = if (result.success) "  ✓" else "  ✗"
                val extra = when {
                    result.resultCount != null -> "(${result.resultCount} items)"
                    result.details != null -> "(${result.details.size} steps)"
                    else -> ""
                }
                val errorMsg = if (!result.success && result.error != null) " - ${result.error}" else ""
                println("$icon ${result.testName} $extra$errorMsg")
            }
        }
    }

    @Test
    fun testAllInstalledExtensions() {
        val installedExtensions = extensionManager.installedExtensionsFlow.value

        println("\n" + "=".repeat(80))
        println("AUTO-DISCOVERED ${installedExtensions.size} INSTALLED EXTENSIONS")
        println("=".repeat(80) + "\n")

        installedExtensions.forEachIndexed { index, ext ->
            println("Extension #${index + 1}: ${ext.name} (${ext.pkgName})")
            println("  Lang: ${ext.lang}, IsNovel: ${ext.isNovel}, IsNsfw: ${ext.isNsfw}")
            println("  Sources: ${ext.sources.size}")

            ext.sources.forEach { source ->
                testSingleSource(source as? CatalogueSource ?: return@forEach)
            }
        }
    }

    @Test
    fun testNovelExtensionsOnly() {
        val novelExtensions = extensionManager.installedExtensionsFlow.value
            .filter { it.isNovel }

        println("\n" + "=".repeat(80))
        println("AUTO-DISCOVERED ${novelExtensions.size} NOVEL EXTENSIONS")
        println("=".repeat(80) + "\n")

        novelExtensions.forEach { ext ->
            println("Novel Extension: ${ext.name}")
            ext.sources.forEach { source ->
                testSingleSource(source as? CatalogueSource ?: return@forEach)
            }
        }
    }

    @Test
    fun testMangaExtensionsOnly() {
        val mangaExtensions = extensionManager.installedExtensionsFlow.value
            .filter { !it.isNovel }

        println("\n" + "=".repeat(80))
        println("AUTO-DISCOVERED ${mangaExtensions.size} MANGA EXTENSIONS")
        println("=".repeat(80) + "\n")

        mangaExtensions.forEach { ext ->
            println("Manga Extension: ${ext.name}")
            ext.sources.forEach { source ->
                testSingleSource(source as? CatalogueSource ?: return@forEach)
            }
        }
    }

    private fun testSingleSource(source: CatalogueSource) {
        totalSources++
        val report = ExtensionTestReport(source.name)

        println("\n--- Testing: ${source.name} (id: ${source.id}) ---")

        // Test 1: Properties
        val propsResult = testProperties(source)
        report.results.add(propsResult)

        // Test 2: Popular endpoint
        val popularResult = testPopular(source)
        report.results.add(popularResult)

        // Test 3: Latest endpoint
        if (source.supportsLatest) {
            val latestResult = testLatest(source)
            report.results.add(latestResult)
        } else {
            report.results.add(TestResult("latest", true, null, listOf("Skipped - not supported")))
        }

        // Test 4: Search
        val searchResult = testSearch(source)
        report.results.add(searchResult)

        // Test 5: Filters
        val filtersResult = testFilters(source)
        report.results.add(filtersResult)

        // Test 6: Full integration
        val integrationResult = testFullIntegration(source)
        report.results.add(integrationResult)

        // Record results
        testResults[source.name] = report

        val hasFailures = report.results.any { !it.success }
        if (hasFailures) {
            failedSources++
        } else {
            passedSources++
        }
    }

    private fun testProperties(source: CatalogueSource): TestResult {
        return try {
            assert(source.name.isNotBlank()) { "Name should not be blank" }
            assert(source.lang.isNotBlank()) { "Lang should not be blank" }
            assert(source.id != 0L) { "ID should be set" }
            TestResult("properties", true, null)
        } catch (e: Exception) {
            TestResult("properties", false, e.message)
        }
    }

    private fun testPopular(source: CatalogueSource): TestResult {
        return try {
            val result = runBlocking { source.getPopularManga(1) }
            TestResult("popular", true, null, resultCount = result.mangas.size)
        } catch (e: Exception) {
            TestResult("popular", false, "${e::class.simpleName}: ${e.message}")
        }
    }

    private fun testLatest(source: CatalogueSource): TestResult {
        return try {
            val result = runBlocking { source.getLatestUpdates(1) }
            TestResult("latest", true, null, resultCount = result.mangas.size)
        } catch (e: Exception) {
            TestResult("latest", false, "${e::class.simpleName}: ${e.message}")
        }
    }

    private fun testSearch(source: CatalogueSource): TestResult {
        val queries = listOf("a", "the")
        val results = queries.map { query ->
            try {
                val result = runBlocking { source.getSearchManga(1, query, FilterList()) }
                "query='$query': ${result.mangas.size} results"
            } catch (e: Exception) {
                "query='$query': FAILED - ${e.message}"
            }
        }

        val hasFailures = results.any { it.contains("FAILED") }
        return TestResult(
            "search",
            !hasFailures,
            if (hasFailures) results.filter { it.contains("FAILED") }.joinToString("; ") else null,
            results,
        )
    }

    private fun testFilters(source: CatalogueSource): TestResult {
        return try {
            val filters = source.getFilterList()
            TestResult("filters", true, null, resultCount = filters.size)
        } catch (e: Exception) {
            TestResult("filters", false, "${e::class.simpleName}: ${e.message}")
        }
    }

    private fun testFullIntegration(source: CatalogueSource): TestResult {
        val steps = mutableListOf<String>()

        return try {
            // Step 1: Get a manga
            var manga: SManga? = null
            var obtainedFrom = ""

            try {
                val popular = runBlocking { source.getPopularManga(1) }
                manga = popular.mangas.firstOrNull()
                if (manga != null) obtainedFrom = "popular"
                steps.add("Popular: ${popular.mangas.size} results")
            } catch (e: Exception) {
                steps.add("Popular failed: ${e.message}")
            }

            if (manga == null && source.supportsLatest) {
                try {
                    val latest = runBlocking { source.getLatestUpdates(1) }
                    manga = latest.mangas.firstOrNull()
                    if (manga != null) obtainedFrom = "latest"
                    steps.add("Latest: ${latest.mangas.size} results")
                } catch (e: Exception) {
                    steps.add("Latest failed: ${e.message}")
                }
            }

            if (manga == null) {
                try {
                    val search = runBlocking { source.getSearchManga(1, "a", FilterList()) }
                    manga = search.mangas.firstOrNull()
                    if (manga != null) obtainedFrom = "search"
                    steps.add("Search: ${search.mangas.size} results")
                } catch (e: Exception) {
                    steps.add("Search failed: ${e.message}")
                }
            }

            if (manga == null) {
                return TestResult("integration", false, "Could not obtain any manga", steps)
            }

            steps.add("Got manga '${manga.title}' from $obtainedFrom")

            // Step 2: Get details if HttpSource
            if (source is HttpSource) {
                try {
                    val details = runBlocking { source.getMangaDetails(manga) }
                    steps.add("Details: title=${details.title}")
                } catch (e: Exception) {
                    steps.add("Details failed: ${e.message}")
                }

                // Step 3: Get chapters
                try {
                    val chapters = runBlocking { source.getChapterList(manga) }
                    steps.add("Chapters: ${chapters.size}")

                    // Step 4: Get content if NovelSource
                    if (source is NovelSource && chapters.isNotEmpty()) {
                        try {
                            val chapter = chapters.last()
                            val pages = runBlocking { source.getPageList(chapter) }
                            if (pages.isNotEmpty()) {
                                val content = runBlocking { source.fetchPageText(pages.first()) }
                                steps.add("Content: ${content.length} chars")
                            } else {
                                steps.add("Content: No pages")
                            }
                        } catch (e: Exception) {
                            steps.add("Content failed: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    steps.add("Chapters failed: ${e.message}")
                }
            }

            TestResult("integration", true, null, steps)
        } catch (e: Exception) {
            steps.add("Error: ${e::class.simpleName}: ${e.message}")
            TestResult("integration", false, e.message, steps)
        }
    }

    // Data classes
    data class TestResult(
        val testName: String,
        val success: Boolean,
        val error: String?,
        val details: List<String>? = null,
        val resultCount: Int? = null,
    )

    data class ExtensionTestReport(
        val sourceName: String,
        val results: MutableList<TestResult> = mutableListOf(),
    )
}
