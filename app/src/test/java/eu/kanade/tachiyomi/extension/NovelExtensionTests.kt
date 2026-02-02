package eu.kanade.tachiyomi.extension

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.NovelSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.reflect.full.createInstance

/**
 * Comprehensive test suite for all novel extensions.
 *
 * Tests include:
 * - Popular listing (first attempt to get novel)
 * - Latest updates (fallback if popular fails)
 * - Search with various queries: "a", "", " " (fallback if latest fails)
 * - Manga/Novel details
 * - Chapter list
 * - Chapter content parsing
 *
 * All inputs and outputs are saved to build/extension-tests/{extension}/
 *
 * Run with:
 * ./gradlew :app:test --tests "eu.kanade.tachiyomi.extension.NovelExtensionTests"
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NovelExtensionTests {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val outputDir = File("build/extension-tests")

    init {
        outputDir.mkdirs()
    }

    /**
     * Target extensions from instructions.html
     */
    private val targetExtensions = listOf(
        "novelshub",
        "readfromnet",
        "requiemtranslations",
        "scribblehub",
        "sonicmtl",
        "srankmanga",
        "webnovel",
        "storyseedling",
        "zerotranslations",
        "ippotranslation",
        "boxnovel",
        "fanstranslations",
        "foxaholic",
        "hireaththranslation",
        "mtlreader",
        "novelhub",
        // ReadNovelFull multisrc extensions - all use same base parsing logic
        // ported from TypeScript/htmlparser2 to Kotlin/Jsoup
        "freewebnovel",
        "allnovel",
        "allnovelfull",
        "readnovelfull",
        "novelbin",
        "novelbuddy",
        "novelupdates",
    )

    @Serializable
    data class ExtensionTestReport(
        val extensionName: String,
        val extensionClass: String,
        val timestamp: String,
        val novelObtainedFrom: String?,
        val testNovel: SerializableManga?,
        val results: List<TestEndpointResult>,
        val overallStatus: String,
        val errorSummary: String?,
    )

    @Serializable
    data class TestEndpointResult(
        val endpoint: String,
        val passed: Boolean,
        val inputSaved: String?,
        val outputSaved: String?,
        val details: Map<String, String>,
        val error: String?,
    )

    @Serializable
    data class SerializableManga(
        val url: String,
        val title: String,
        val author: String?,
        val artist: String?,
        val description: String?,
        val genre: String?,
        val status: Int,
        val thumbnailUrl: String?,
        val initialized: Boolean,
    )

    @Serializable
    data class SerializableChapter(
        val url: String,
        val name: String,
        val dateUpload: Long,
        val chapterNumber: Float,
        val scanlator: String?,
    )

    @Serializable
    data class SerializableMangasPage(
        val mangas: List<SerializableManga>,
        val hasNextPage: Boolean,
    )

    /**
     * Run full test suite for a single source with fallback strategy
     */
    fun testExtensionFully(source: CatalogueSource): ExtensionTestReport {
        val extDir = File(outputDir, sanitizeFilename(source.name))
        extDir.mkdirs()

        val results = mutableListOf<TestEndpointResult>()
        var testManga: SManga? = null
        var novelObtainedFrom: String? = null

        // Step 1: Try Popular
        val popularResult = testPopular(source, extDir)
        results.add(popularResult)

        if (popularResult.passed) {
            testManga = runBlocking {
                try {
                    source.getPopularManga(1).mangas.firstOrNull()
                } catch (e: Exception) {
                    null
                }
            }
            if (testManga != null) novelObtainedFrom = "popular"
        }

        // Step 2: Try Latest if no manga yet
        if (source.supportsLatest) {
            val latestResult = testLatest(source, extDir)
            results.add(latestResult)

            if (testManga == null && latestResult.passed) {
                testManga = runBlocking {
                    try {
                        source.getLatestUpdates(1).mangas.firstOrNull()
                    } catch (e: Exception) {
                        null
                    }
                }
                if (testManga != null) novelObtainedFrom = "latest"
            }
        } else {
            results.add(
                TestEndpointResult(
                    endpoint = "Latest",
                    passed = true,
                    inputSaved = null,
                    outputSaved = null,
                    details = mapOf("reason" to "supportsLatest=false"),
                    error = null,
                ),
            )
        }

        // Step 3: Try Search with different queries if still no manga
        val searchQueries = listOf("a", "", " ")
        for (query in searchQueries) {
            val queryName = when {
                query.isEmpty() -> "empty"
                query.isBlank() -> "space"
                else -> query
            }
            val searchResult = testSearch(source, extDir, query, "search_$queryName")
            results.add(searchResult)

            if (testManga == null && searchResult.passed) {
                testManga = runBlocking {
                    try {
                        source.getSearchManga(1, query, FilterList()).mangas.firstOrNull()
                    } catch (
                        e: Exception,
                    ) {
                        null
                    }
                }
                if (testManga != null) novelObtainedFrom = "search_$queryName"
            }
        }

        // Step 4: Test Filters
        results.add(testFilters(source, extDir))

        // Step 5: Test Details, Chapters, Content if we have a manga
        if (testManga != null) {
            // Get detailed manga
            val detailedManga = if (source is HttpSource) {
                runBlocking {
                    try {
                        source.getMangaDetails(testManga)
                    } catch (e: Exception) {
                        testManga
                    }
                }
            } else {
                testManga
            }

            results.add(testDetails(source, extDir, detailedManga))
            results.add(testChapterList(source, extDir, detailedManga))

            if (source is NovelSource) {
                results.add(testChapterContent(source, extDir, detailedManga))
            } else {
                results.add(
                    TestEndpointResult(
                        endpoint = "ChapterContent",
                        passed = true,
                        inputSaved = null,
                        outputSaved = null,
                        details = mapOf("reason" to "Not a NovelSource"),
                        error = null,
                    ),
                )
            }
        } else {
            // No manga obtained - add failure results
            results.add(
                TestEndpointResult(
                    endpoint = "Details",
                    passed = false,
                    inputSaved = null,
                    outputSaved = null,
                    details = emptyMap(),
                    error = "No manga available - all listing methods failed",
                ),
            )
            results.add(
                TestEndpointResult(
                    endpoint = "ChapterList",
                    passed = false,
                    inputSaved = null,
                    outputSaved = null,
                    details = emptyMap(),
                    error = "No manga available - all listing methods failed",
                ),
            )
            results.add(
                TestEndpointResult(
                    endpoint = "ChapterContent",
                    passed = false,
                    inputSaved = null,
                    outputSaved = null,
                    details = emptyMap(),
                    error = "No manga available - all listing methods failed",
                ),
            )
        }

        val passedCount = results.count { it.passed }
        val failedCount = results.count { !it.passed }
        val overallStatus = if (failedCount == 0) {
            "PASSED"
        } else if (passedCount == 0) {
            "FAILED"
        } else {
            "PARTIAL"
        }

        val report = ExtensionTestReport(
            extensionName = source.name,
            extensionClass = source::class.qualifiedName ?: source::class.simpleName ?: "Unknown",
            timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()),
            novelObtainedFrom = novelObtainedFrom,
            testNovel = testManga?.toSerializable(),
            results = results,
            overallStatus = overallStatus,
            errorSummary = if (failedCount > 0) {
                results.filter { !it.passed }.mapNotNull { it.error }.joinToString("; ")
            } else {
                null
            },
        )

        // Save report
        File(extDir, "test-report.json").writeText(json.encodeToString(report))

        return report
    }

    private fun testPopular(source: CatalogueSource, extDir: File): TestEndpointResult {
        return try {
            val result = runBlocking { source.getPopularManga(1) }

            // Save output
            val outputFile = "popular_output.json"
            File(extDir, outputFile).writeText(json.encodeToString(result.toSerializable()))

            if (result.mangas.isEmpty()) {
                TestEndpointResult(
                    endpoint = "Popular",
                    passed = false,
                    inputSaved = null,
                    outputSaved = outputFile,
                    details = mapOf("count" to "0"),
                    error = "No mangas returned",
                )
            } else {
                TestEndpointResult(
                    endpoint = "Popular",
                    passed = true,
                    inputSaved = null,
                    outputSaved = outputFile,
                    details = mapOf(
                        "count" to result.mangas.size.toString(),
                        "hasNextPage" to result.hasNextPage.toString(),
                        "firstTitle" to (result.mangas.firstOrNull()?.title ?: "N/A"),
                        "firstUrl" to (result.mangas.firstOrNull()?.url ?: "N/A"),
                    ),
                    error = null,
                )
            }
        } catch (e: Exception) {
            TestEndpointResult(
                endpoint = "Popular",
                passed = false,
                inputSaved = null,
                outputSaved = null,
                details = emptyMap(),
                error = "${e::class.simpleName}: ${e.message}",
            )
        }
    }

    private fun testLatest(source: CatalogueSource, extDir: File): TestEndpointResult {
        return try {
            val result = runBlocking { source.getLatestUpdates(1) }

            val outputFile = "latest_output.json"
            File(extDir, outputFile).writeText(json.encodeToString(result.toSerializable()))

            if (result.mangas.isEmpty()) {
                TestEndpointResult(
                    endpoint = "Latest",
                    passed = false,
                    inputSaved = null,
                    outputSaved = outputFile,
                    details = mapOf("count" to "0"),
                    error = "No mangas returned",
                )
            } else {
                TestEndpointResult(
                    endpoint = "Latest",
                    passed = true,
                    inputSaved = null,
                    outputSaved = outputFile,
                    details = mapOf(
                        "count" to result.mangas.size.toString(),
                        "hasNextPage" to result.hasNextPage.toString(),
                        "firstTitle" to (result.mangas.firstOrNull()?.title ?: "N/A"),
                    ),
                    error = null,
                )
            }
        } catch (e: Exception) {
            TestEndpointResult(
                endpoint = "Latest",
                passed = false,
                inputSaved = null,
                outputSaved = null,
                details = emptyMap(),
                error = "${e::class.simpleName}: ${e.message}",
            )
        }
    }

    private fun testSearch(source: CatalogueSource, extDir: File, query: String, prefix: String): TestEndpointResult {
        val displayQuery = when {
            query.isEmpty() -> "(empty)"
            query.isBlank() -> "(space)"
            else -> "\"$query\""
        }

        return try {
            // Save input
            val inputFile = "${prefix}_input.json"
            File(extDir, inputFile).writeText(json.encodeToString(mapOf("query" to query)))

            val result = runBlocking { source.getSearchManga(1, query, FilterList()) }

            val outputFile = "${prefix}_output.json"
            File(extDir, outputFile).writeText(json.encodeToString(result.toSerializable()))

            if (result.mangas.isEmpty()) {
                TestEndpointResult(
                    endpoint = "Search $displayQuery",
                    passed = false,
                    inputSaved = inputFile,
                    outputSaved = outputFile,
                    details = mapOf("query" to query, "count" to "0"),
                    error = "No mangas returned",
                )
            } else {
                TestEndpointResult(
                    endpoint = "Search $displayQuery",
                    passed = true,
                    inputSaved = inputFile,
                    outputSaved = outputFile,
                    details = mapOf(
                        "query" to query,
                        "count" to result.mangas.size.toString(),
                        "firstTitle" to (result.mangas.firstOrNull()?.title ?: "N/A"),
                    ),
                    error = null,
                )
            }
        } catch (e: Exception) {
            TestEndpointResult(
                endpoint = "Search $displayQuery",
                passed = false,
                inputSaved = null,
                outputSaved = null,
                details = mapOf("query" to query),
                error = "${e::class.simpleName}: ${e.message}",
            )
        }
    }

    private fun testFilters(source: CatalogueSource, extDir: File): TestEndpointResult {
        return try {
            val filters = source.getFilterList()

            val filterInfo = filters.map { filter ->
                mapOf(
                    "name" to filter.name,
                    "type" to (filter::class.simpleName ?: "Unknown"),
                )
            }

            val outputFile = "filters_output.json"
            File(extDir, outputFile).writeText(json.encodeToString(filterInfo))

            TestEndpointResult(
                endpoint = "Filters",
                passed = true,
                inputSaved = null,
                outputSaved = outputFile,
                details = mapOf("count" to filters.size.toString()),
                error = null,
            )
        } catch (e: Exception) {
            TestEndpointResult(
                endpoint = "Filters",
                passed = false,
                inputSaved = null,
                outputSaved = null,
                details = emptyMap(),
                error = "${e::class.simpleName}: ${e.message}",
            )
        }
    }

    private fun testDetails(source: CatalogueSource, extDir: File, manga: SManga): TestEndpointResult {
        return try {
            // Save input
            val inputFile = "details_input.json"
            File(extDir, inputFile).writeText(json.encodeToString(manga.toSerializable()))

            val result = if (source is HttpSource) {
                runBlocking { source.getMangaDetails(manga) }
            } else {
                manga
            }

            val outputFile = "details_output.json"
            File(extDir, outputFile).writeText(json.encodeToString(result.toSerializable()))

            TestEndpointResult(
                endpoint = "Details",
                passed = true,
                inputSaved = inputFile,
                outputSaved = outputFile,
                details = mapOf(
                    "title" to result.title,
                    "hasDescription" to (!result.description.isNullOrBlank()).toString(),
                    "hasThumbnail" to (!result.thumbnail_url.isNullOrBlank()).toString(),
                    "author" to (result.author ?: "N/A"),
                    "status" to result.status.toString(),
                ),
                error = null,
            )
        } catch (e: Exception) {
            TestEndpointResult(
                endpoint = "Details",
                passed = false,
                inputSaved = null,
                outputSaved = null,
                details = emptyMap(),
                error = "${e::class.simpleName}: ${e.message}",
            )
        }
    }

    private fun testChapterList(source: CatalogueSource, extDir: File, manga: SManga): TestEndpointResult {
        return try {
            // Save input
            val inputFile = "chapters_input.json"
            File(extDir, inputFile).writeText(json.encodeToString(manga.toSerializable()))

            val result = if (source is HttpSource) {
                runBlocking { source.getChapterList(manga) }
            } else {
                emptyList()
            }

            val outputFile = "chapters_output.json"
            File(extDir, outputFile).writeText(json.encodeToString(result.map { it.toSerializable() }))

            if (result.isEmpty()) {
                TestEndpointResult(
                    endpoint = "ChapterList",
                    passed = false,
                    inputSaved = inputFile,
                    outputSaved = outputFile,
                    details = mapOf("count" to "0"),
                    error = "No chapters returned",
                )
            } else {
                TestEndpointResult(
                    endpoint = "ChapterList",
                    passed = true,
                    inputSaved = inputFile,
                    outputSaved = outputFile,
                    details = mapOf(
                        "count" to result.size.toString(),
                        "firstChapter" to (result.firstOrNull()?.name ?: "N/A"),
                        "lastChapter" to (result.lastOrNull()?.name ?: "N/A"),
                    ),
                    error = null,
                )
            }
        } catch (e: Exception) {
            TestEndpointResult(
                endpoint = "ChapterList",
                passed = false,
                inputSaved = null,
                outputSaved = null,
                details = emptyMap(),
                error = "${e::class.simpleName}: ${e.message}",
            )
        }
    }

    private fun testChapterContent(source: CatalogueSource, extDir: File, manga: SManga): TestEndpointResult {
        if (source !is NovelSource || source !is HttpSource) {
            return TestEndpointResult(
                endpoint = "ChapterContent",
                passed = true,
                inputSaved = null,
                outputSaved = null,
                details = mapOf("reason" to "Source is not a HttpSource+NovelSource"),
                error = null,
            )
        }

        return try {
            // Get chapters
            val chapters = runBlocking { source.getChapterList(manga) }

            if (chapters.isEmpty()) {
                return TestEndpointResult(
                    endpoint = "ChapterContent",
                    passed = false,
                    inputSaved = null,
                    outputSaved = null,
                    details = emptyMap(),
                    error = "No chapters available",
                )
            }

            // Use last chapter (usually first in story order)
            val chapter = chapters.last()

            // Save input
            val inputFile = "content_input.json"
            File(extDir, inputFile).writeText(json.encodeToString(chapter.toSerializable()))

            // Get page list
            val pages = runBlocking { source.getPageList(chapter) }

            if (pages.isEmpty()) {
                return TestEndpointResult(
                    endpoint = "ChapterContent",
                    passed = false,
                    inputSaved = inputFile,
                    outputSaved = null,
                    details = mapOf("chapterName" to chapter.name),
                    error = "No pages returned",
                )
            }

            // Get text content
            val textContent = runBlocking { source.fetchPageText(pages.first()) }

            // Save raw content
            val rawFile = "content_raw.html"
            File(extDir, rawFile).writeText(textContent)

            // Save preview (first 3000 chars)
            val previewFile = "content_preview.txt"
            val preview = if (textContent.length > 3000) textContent.take(3000) + "\n...[truncated]" else textContent
            File(extDir, previewFile).writeText(preview)

            if (textContent.isBlank()) {
                TestEndpointResult(
                    endpoint = "ChapterContent",
                    passed = false,
                    inputSaved = inputFile,
                    outputSaved = rawFile,
                    details = mapOf("chapterName" to chapter.name),
                    error = "Empty content returned",
                )
            } else {
                TestEndpointResult(
                    endpoint = "ChapterContent",
                    passed = true,
                    inputSaved = inputFile,
                    outputSaved = rawFile,
                    details = mapOf(
                        "chapterName" to chapter.name,
                        "contentLength" to textContent.length.toString(),
                        "wordCount" to textContent.split(Regex("\\s+")).size.toString(),
                        "isHtml" to textContent.contains("<").toString(),
                    ),
                    error = null,
                )
            }
        } catch (e: Exception) {
            TestEndpointResult(
                endpoint = "ChapterContent",
                passed = false,
                inputSaved = null,
                outputSaved = null,
                details = emptyMap(),
                error = "${e::class.simpleName}: ${e.message}",
            )
        }
    }

    // Serialization helpers
    private fun SManga.toSerializable() = SerializableManga(
        url = url,
        title = title,
        author = author,
        artist = artist,
        description = description,
        genre = genre,
        status = status,
        thumbnailUrl = thumbnail_url,
        initialized = initialized,
    )

    private fun SChapter.toSerializable() = SerializableChapter(
        url = url,
        name = name,
        dateUpload = date_upload,
        chapterNumber = chapter_number,
        scanlator = scanlator,
    )

    private fun MangasPage.toSerializable() = SerializableMangasPage(
        mangas = mangas.map { it.toSerializable() },
        hasNextPage = hasNextPage,
    )

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    /**
     * Test with a mock source to validate the test framework
     */
    @Test
    fun `test framework with mock source`() {
        val mockSource = object : CatalogueSource {
            override val name = "MockTestSource"
            override val lang = "en"
            override val id = 999L
            override val supportsLatest = true

            override suspend fun getPopularManga(page: Int) = MangasPage(
                listOf(
                    SManga.create().apply {
                        url = "/mock/novel/1"
                        title = "Mock Novel Title"
                        description = "A mock novel for testing"
                        thumbnail_url = "https://example.com/thumb.jpg"
                    },
                ),
                hasNextPage = false,
            )

            override suspend fun getLatestUpdates(page: Int) = getPopularManga(page)
            override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) = getPopularManga(page)
            override fun getFilterList() = FilterList()
        }

        val report = testExtensionFully(mockSource)

        println("\n" + "=".repeat(60))
        println("Mock Source Test Report")
        println("=".repeat(60))
        println("Extension: ${report.extensionName}")
        println("Novel obtained from: ${report.novelObtainedFrom}")
        println("Overall status: ${report.overallStatus}")
        println("\nResults:")
        report.results.forEach { result ->
            val icon = if (result.passed) "✓" else "✗"
            println("  $icon ${result.endpoint}")
            result.details.forEach { (k, v) -> println("      $k: $v") }
            if (result.error != null) println("      Error: ${result.error}")
        }
        println("=".repeat(60))

        assert(report.overallStatus != "FAILED") { "Mock source test should pass" }
    }

    /**
     * Print a summary of the test report
     */
    fun printReport(report: ExtensionTestReport) {
        println("\n" + "=".repeat(60))
        println("Extension Test Report: ${report.extensionName}")
        println("=".repeat(60))
        println("Class: ${report.extensionClass}")
        println("Timestamp: ${report.timestamp}")
        println("Novel obtained from: ${report.novelObtainedFrom ?: "N/A"}")
        if (report.testNovel != null) {
            println("Test novel: ${report.testNovel.title}")
            println("  URL: ${report.testNovel.url}")
        }
        println("\nResults (${report.overallStatus}):")
        report.results.forEach { result ->
            val icon = if (result.passed) "✓" else "✗"
            println("  $icon ${result.endpoint}")
            result.details.forEach { (k, v) -> println("      $k: $v") }
            if (result.error != null) println("      Error: ${result.error}")
        }
        if (report.errorSummary != null) {
            println("\nError Summary: ${report.errorSummary}")
        }
        println("=".repeat(60))
    }
}
