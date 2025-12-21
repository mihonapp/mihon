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
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Extension Test Framework
 *
 * Provides comprehensive testing capabilities for novel/manga source extensions.
 * Tests cover: popular listing, latest updates, search, filters, novel details,
 * chapter list, and chapter content parsing.
 *
 * Testing strategy:
 * 1. Try popular endpoint first
 * 2. If popular fails/empty, try latest (if supported)
 * 3. If both fail, try search with queries: "a", "", " "
 * 4. If we get a novel, test details, chapter list, and chapter parse
 * 5. Also test search and filters separately
 *
 * All raw responses and parsed outputs are saved for debugging and validation.
 */
class ExtensionTestFramework(
    private val outputDir: File = File("build/extension-tests"),
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    },
) {

    init {
        outputDir.mkdirs()
    }

    /**
     * Result of an extension test suite
     */
    @Serializable
    data class TestSuiteResult(
        val extensionName: String,
        val extensionId: Long,
        val language: String,
        val timestamp: String,
        val tests: List<TestResult>,
        val summary: TestSummary,
        val novelObtainedFrom: String? = null, // "popular", "latest", "search_a", "search_empty", "search_space"
        val testNovel: SerializableManga? = null,
    )

    @Serializable
    data class TestSummary(
        val total: Int,
        val passed: Int,
        val failed: Int,
        val skipped: Int,
        val durationMs: Long,
    )

    @Serializable
    data class TestResult(
        val name: String,
        val status: TestStatus,
        val durationMs: Long,
        val error: String? = null,
        val rawResponseFile: String? = null,
        val parsedOutputFile: String? = null,
        val details: Map<String, String> = emptyMap(),
    )

    @Serializable
    enum class TestStatus {
        PASSED,
        FAILED,
        SKIPPED,
    }

    /**
     * Run all tests for a CatalogueSource with comprehensive fallback strategy
     */
    fun runTestSuite(source: CatalogueSource): TestSuiteResult {
        val startTime = System.currentTimeMillis()
        val extensionDir = File(outputDir, sanitizeFilename(source.name))
        extensionDir.mkdirs()

        val results = mutableListOf<TestResult>()
        var testManga: SManga? = null
        var novelObtainedFrom: String? = null

        // Step 1: Try to get a novel for testing using fallback strategy
        // Try Popular first
        val popularResult = testPopular(source, extensionDir)
        results.add(popularResult)

        if (popularResult.status == TestStatus.PASSED) {
            testManga = runBlocking {
                try {
                    source.getPopularManga(1).mangas.firstOrNull()
                } catch (e: Exception) {
                    null
                }
            }
            if (testManga != null) novelObtainedFrom = "popular"
        }

        // If popular failed or empty, try Latest
        if (testManga == null && source.supportsLatest) {
            val latestResult = testLatest(source, extensionDir)
            results.add(latestResult)

            if (latestResult.status == TestStatus.PASSED) {
                testManga = runBlocking {
                    try {
                        source.getLatestUpdates(1).mangas.firstOrNull()
                    } catch (e: Exception) {
                        null
                    }
                }
                if (testManga != null) novelObtainedFrom = "latest"
            }
        } else if (source.supportsLatest) {
            // Still test latest even if we got a novel from popular
            results.add(testLatest(source, extensionDir))
        } else {
            results.add(
                TestResult(
                    name = "Latest Updates",
                    status = TestStatus.SKIPPED,
                    durationMs = 0,
                    error = "Source does not support latest updates",
                ),
            )
        }

        // If still no novel, try search with different queries
        if (testManga == null) {
            val searchQueries = listOf("a", "", " ")
            for (query in searchQueries) {
                val searchResult = testSearchWithQuery(source, extensionDir, query, "search_${queryToFilename(query)}")
                results.add(searchResult)

                if (searchResult.status == TestStatus.PASSED) {
                    testManga = runBlocking {
                        try {
                            source.getSearchManga(1, query, FilterList()).mangas.firstOrNull()
                        } catch (
                            e: Exception,
                        ) {
                            null
                        }
                    }
                    if (testManga != null) {
                        novelObtainedFrom = "search_${queryToFilename(query)}"
                        break
                    }
                }
            }
        } else {
            // Still test search even if we already have a novel
            results.add(testSearch(source, extensionDir, "a"))
        }

        // Test Filters
        results.add(testFilters(source, extensionDir))

        // If we have a test novel, run detail tests
        if (testManga != null) {
            // Test Manga Details
            results.add(testMangaDetails(source, extensionDir, testManga))

            // Get detailed manga for chapter testing
            val detailedManga = runBlocking {
                try {
                    if (source is HttpSource) source.getMangaDetails(testManga) else testManga
                } catch (e: Exception) {
                    testManga
                }
            }

            // Test Chapter List
            val chapterResult = testChapterList(source, extensionDir, detailedManga)
            results.add(chapterResult)

            // Test Chapter Content (for novels)
            if (source is NovelSource) {
                results.add(testChapterContent(source, extensionDir, detailedManga))
            } else {
                results.add(
                    TestResult(
                        name = "Chapter Content (Novel)",
                        status = TestStatus.SKIPPED,
                        durationMs = 0,
                        error = "Source is not a NovelSource",
                    ),
                )
            }
        } else {
            // No novel obtained - all listing methods failed
            results.add(
                TestResult(
                    name = "Manga Details",
                    status = TestStatus.FAILED,
                    durationMs = 0,
                    error = "No manga available - all listing methods (popular, latest, search) failed",
                ),
            )
            results.add(
                TestResult(
                    name = "Chapter List",
                    status = TestStatus.FAILED,
                    durationMs = 0,
                    error = "No manga available - all listing methods (popular, latest, search) failed",
                ),
            )
            results.add(
                TestResult(
                    name = "Chapter Content (Novel)",
                    status = TestStatus.FAILED,
                    durationMs = 0,
                    error = "No manga available - all listing methods (popular, latest, search) failed",
                ),
            )
        }

        val endTime = System.currentTimeMillis()
        val totalDuration = endTime - startTime

        val summary = TestSummary(
            total = results.size,
            passed = results.count { it.status == TestStatus.PASSED },
            failed = results.count { it.status == TestStatus.FAILED },
            skipped = results.count { it.status == TestStatus.SKIPPED },
            durationMs = totalDuration,
        )

        val suiteResult = TestSuiteResult(
            extensionName = source.name,
            extensionId = source.id,
            language = source.lang,
            timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()),
            tests = results,
            summary = summary,
            novelObtainedFrom = novelObtainedFrom,
            testNovel = testManga?.toSerializable(),
        )

        // Save suite result
        File(extensionDir, "test-results.json").writeText(json.encodeToString(suiteResult))

        return suiteResult
    }

    private fun queryToFilename(query: String): String = when {
        query.isEmpty() -> "empty"
        query.isBlank() -> "space"
        else -> query
    }

    private fun testPopular(source: CatalogueSource, outputDir: File): TestResult {
        val testName = "Popular Manga"
        val startTime = System.currentTimeMillis()

        return try {
            val result = runBlocking { source.getPopularManga(1) }
            val duration = System.currentTimeMillis() - startTime

            // Save parsed output
            val parsedFile = "popular_parsed.json"
            File(outputDir, parsedFile).writeText(json.encodeToString(result.toSerializable()))

            if (result.mangas.isEmpty()) {
                TestResult(
                    name = testName,
                    status = TestStatus.FAILED,
                    durationMs = duration,
                    error = "No mangas returned",
                    parsedOutputFile = parsedFile,
                )
            } else {
                TestResult(
                    name = testName,
                    status = TestStatus.PASSED,
                    durationMs = duration,
                    parsedOutputFile = parsedFile,
                    details = mapOf(
                        "mangaCount" to result.mangas.size.toString(),
                        "hasNextPage" to result.hasNextPage.toString(),
                        "firstMangaTitle" to (result.mangas.firstOrNull()?.title ?: "N/A"),
                        "firstMangaUrl" to (result.mangas.firstOrNull()?.url ?: "N/A"),
                    ),
                )
            }
        } catch (e: Exception) {
            TestResult(
                name = testName,
                status = TestStatus.FAILED,
                durationMs = System.currentTimeMillis() - startTime,
                error = "${e::class.simpleName}: ${e.message}",
            )
        }
    }

    private fun testLatest(source: CatalogueSource, outputDir: File): TestResult {
        val testName = "Latest Updates"
        val startTime = System.currentTimeMillis()

        return try {
            val result = runBlocking { source.getLatestUpdates(1) }
            val duration = System.currentTimeMillis() - startTime

            val parsedFile = "latest_parsed.json"
            File(outputDir, parsedFile).writeText(json.encodeToString(result.toSerializable()))

            if (result.mangas.isEmpty()) {
                TestResult(
                    name = testName,
                    status = TestStatus.FAILED,
                    durationMs = duration,
                    error = "No mangas returned",
                    parsedOutputFile = parsedFile,
                )
            } else {
                TestResult(
                    name = testName,
                    status = TestStatus.PASSED,
                    durationMs = duration,
                    parsedOutputFile = parsedFile,
                    details = mapOf(
                        "mangaCount" to result.mangas.size.toString(),
                        "hasNextPage" to result.hasNextPage.toString(),
                        "firstMangaTitle" to (result.mangas.firstOrNull()?.title ?: "N/A"),
                        "firstMangaUrl" to (result.mangas.firstOrNull()?.url ?: "N/A"),
                    ),
                )
            }
        } catch (e: Exception) {
            TestResult(
                name = testName,
                status = TestStatus.FAILED,
                durationMs = System.currentTimeMillis() - startTime,
                error = "${e::class.simpleName}: ${e.message}",
            )
        }
    }

    private fun testSearch(source: CatalogueSource, outputDir: File, query: String): TestResult {
        return testSearchWithQuery(source, outputDir, query, "search")
    }

    private fun testSearchWithQuery(
        source: CatalogueSource,
        outputDir: File,
        query: String,
        testPrefix: String,
    ): TestResult {
        val queryDisplay = when {
            query.isEmpty() -> "(empty)"
            query.isBlank() -> "(space)"
            else -> "\"$query\""
        }
        val testName = "Search $queryDisplay"
        val startTime = System.currentTimeMillis()

        return try {
            val result = runBlocking { source.getSearchManga(1, query, FilterList()) }
            val duration = System.currentTimeMillis() - startTime

            val parsedFile = "${testPrefix}_parsed.json"
            val parsedJson = json.encodeToString(result.toSerializable())
            File(outputDir, parsedFile).writeText(parsedJson)

            if (result.mangas.isEmpty()) {
                TestResult(
                    name = testName,
                    status = TestStatus.FAILED,
                    durationMs = duration,
                    error = "No mangas returned",
                    parsedOutputFile = parsedFile,
                    details = mapOf(
                        "query" to query,
                        "queryType" to queryToFilename(query),
                    ),
                )
            } else {
                TestResult(
                    name = testName,
                    status = TestStatus.PASSED,
                    durationMs = duration,
                    parsedOutputFile = parsedFile,
                    details = mapOf(
                        "query" to query,
                        "queryType" to queryToFilename(query),
                        "mangaCount" to result.mangas.size.toString(),
                        "hasNextPage" to result.hasNextPage.toString(),
                        "firstMangaTitle" to (result.mangas.firstOrNull()?.title ?: "N/A"),
                        "firstMangaUrl" to (result.mangas.firstOrNull()?.url ?: "N/A"),
                    ),
                )
            }
        } catch (e: Exception) {
            TestResult(
                name = testName,
                status = TestStatus.FAILED,
                durationMs = System.currentTimeMillis() - startTime,
                error = "${e::class.simpleName}: ${e.message}",
                details = mapOf(
                    "query" to query,
                    "queryType" to queryToFilename(query),
                ),
            )
        }
    }

    private fun testFilters(source: CatalogueSource, outputDir: File): TestResult {
        val testName = "Filters"
        val startTime = System.currentTimeMillis()

        return try {
            val filters = source.getFilterList()
            val duration = System.currentTimeMillis() - startTime

            val filterInfo = filters.map { filter ->
                mapOf(
                    "name" to filter.name,
                    "type" to filter::class.simpleName.orEmpty(),
                )
            }

            val parsedFile = "filters_parsed.json"
            File(outputDir, parsedFile).writeText(json.encodeToString(filterInfo))

            TestResult(
                name = testName,
                status = TestStatus.PASSED,
                durationMs = duration,
                parsedOutputFile = parsedFile,
                details = mapOf(
                    "filterCount" to filters.size.toString(),
                ),
            )
        } catch (e: Exception) {
            TestResult(
                name = testName,
                status = TestStatus.FAILED,
                durationMs = System.currentTimeMillis() - startTime,
                error = "${e::class.simpleName}: ${e.message}",
            )
        }
    }

    private fun testMangaDetails(source: CatalogueSource, outputDir: File, manga: SManga): TestResult {
        val testName = "Manga Details"
        val startTime = System.currentTimeMillis()

        return try {
            // Save input manga
            val inputFile = "manga_details_input.json"
            File(outputDir, inputFile).writeText(json.encodeToString(manga.toSerializable()))

            val result = if (source is HttpSource) {
                runBlocking { source.getMangaDetails(manga) }
            } else {
                manga
            }
            val duration = System.currentTimeMillis() - startTime

            val parsedFile = "manga_details_parsed.json"
            File(outputDir, parsedFile).writeText(json.encodeToString(result.toSerializable()))

            val missingFields = mutableListOf<String>()
            if (result.title.isBlank()) missingFields.add("title")
            if (result.thumbnail_url.isNullOrBlank()) missingFields.add("thumbnail_url")
            if (result.description.isNullOrBlank()) missingFields.add("description")

            TestResult(
                name = testName,
                status = TestStatus.PASSED,
                durationMs = duration,
                parsedOutputFile = parsedFile,
                details = mapOf(
                    "title" to result.title,
                    "author" to (result.author ?: "N/A"),
                    "artist" to (result.artist ?: "N/A"),
                    "status" to result.status.toString(),
                    "hasDescription" to (!result.description.isNullOrBlank()).toString(),
                    "hasThumbnail" to (!result.thumbnail_url.isNullOrBlank()).toString(),
                    "genre" to (result.genre ?: "N/A"),
                    "missingFields" to if (missingFields.isEmpty()) "none" else missingFields.joinToString(", "),
                ),
            )
        } catch (e: Exception) {
            TestResult(
                name = testName,
                status = TestStatus.FAILED,
                durationMs = System.currentTimeMillis() - startTime,
                error = "${e::class.simpleName}: ${e.message}",
            )
        }
    }

    private fun testChapterList(source: CatalogueSource, outputDir: File, manga: SManga): TestResult {
        val testName = "Chapter List"
        val startTime = System.currentTimeMillis()

        return try {
            // Save input manga
            val inputFile = "chapters_input.json"
            File(outputDir, inputFile).writeText(json.encodeToString(manga.toSerializable()))

            val result = if (source is HttpSource) {
                runBlocking { source.getChapterList(manga) }
            } else {
                emptyList()
            }
            val duration = System.currentTimeMillis() - startTime

            val parsedFile = "chapters_parsed.json"
            File(outputDir, parsedFile).writeText(json.encodeToString(result.map { it.toSerializable() }))

            if (result.isEmpty()) {
                TestResult(
                    name = testName,
                    status = TestStatus.FAILED,
                    durationMs = duration,
                    error = "No chapters returned",
                    parsedOutputFile = parsedFile,
                )
            } else {
                TestResult(
                    name = testName,
                    status = TestStatus.PASSED,
                    durationMs = duration,
                    parsedOutputFile = parsedFile,
                    details = mapOf(
                        "chapterCount" to result.size.toString(),
                        "firstChapter" to result.firstOrNull()?.name.orEmpty(),
                        "firstChapterUrl" to result.firstOrNull()?.url.orEmpty(),
                        "lastChapter" to result.lastOrNull()?.name.orEmpty(),
                        "lastChapterUrl" to result.lastOrNull()?.url.orEmpty(),
                    ),
                )
            }
        } catch (e: Exception) {
            TestResult(
                name = testName,
                status = TestStatus.FAILED,
                durationMs = System.currentTimeMillis() - startTime,
                error = "${e::class.simpleName}: ${e.message}",
            )
        }
    }

    private fun testChapterContent(source: CatalogueSource, outputDir: File, manga: SManga): TestResult {
        val testName = "Chapter Content (Novel)"
        val startTime = System.currentTimeMillis()

        if (source !is NovelSource) {
            return TestResult(
                name = testName,
                status = TestStatus.SKIPPED,
                durationMs = 0,
                error = "Source is not a NovelSource",
            )
        }

        return try {
            // Get first chapter
            val chapters = if (source is HttpSource) {
                runBlocking { source.getChapterList(manga) }
            } else {
                emptyList()
            }

            if (chapters.isEmpty()) {
                return TestResult(
                    name = testName,
                    status = TestStatus.SKIPPED,
                    durationMs = System.currentTimeMillis() - startTime,
                    error = "No chapters available",
                )
            }

            val firstChapter = chapters.last() // Usually first chapter is at end of list

            // Save chapter input
            val chapterInputFile = "chapter_content_input.json"
            File(outputDir, chapterInputFile).writeText(json.encodeToString(firstChapter.toSerializable()))

            // Get page list
            val pages = if (source is HttpSource) {
                runBlocking { source.getPageList(firstChapter) }
            } else {
                emptyList()
            }

            if (pages.isEmpty()) {
                return TestResult(
                    name = testName,
                    status = TestStatus.FAILED,
                    durationMs = System.currentTimeMillis() - startTime,
                    error = "No pages returned for chapter",
                )
            }

            // For novels, get the text content
            val textContent = runBlocking { source.fetchPageText(pages.first()) }
            val duration = System.currentTimeMillis() - startTime

            // Save raw content
            val rawFile = "chapter_content_raw.html"
            File(outputDir, rawFile).writeText(textContent)

            // Also save a preview (first 2000 chars)
            val previewFile = "chapter_content_preview.txt"
            val preview = if (textContent.length > 2000) textContent.take(2000) + "..." else textContent
            File(outputDir, previewFile).writeText(preview)

            if (textContent.isBlank()) {
                TestResult(
                    name = testName,
                    status = TestStatus.FAILED,
                    durationMs = duration,
                    error = "Empty chapter content",
                    rawResponseFile = rawFile,
                )
            } else {
                TestResult(
                    name = testName,
                    status = TestStatus.PASSED,
                    durationMs = duration,
                    rawResponseFile = rawFile,
                    details = mapOf(
                        "chapterName" to firstChapter.name,
                        "chapterUrl" to firstChapter.url,
                        "contentLength" to textContent.length.toString(),
                        "isHtml" to textContent.contains("<").toString(),
                        "wordCount" to textContent.split(Regex("\\s+")).size.toString(),
                    ),
                )
            }
        } catch (e: Exception) {
            TestResult(
                name = testName,
                status = TestStatus.FAILED,
                durationMs = System.currentTimeMillis() - startTime,
                error = "${e::class.simpleName}: ${e.message}",
            )
        }
    }

    // Serializable versions of model classes
    @Serializable
    data class SerializableMangasPage(
        val mangas: List<SerializableManga>,
        val hasNextPage: Boolean,
    )

    @Serializable
    data class SerializableManga(
        val url: String,
        val title: String,
        val artist: String?,
        val author: String?,
        val description: String?,
        val genre: String?,
        val status: Int,
        val thumbnail_url: String?,
        val initialized: Boolean,
    )

    @Serializable
    data class SerializableChapter(
        val url: String,
        val name: String,
        val date_upload: Long,
        val chapter_number: Float,
        val scanlator: String?,
    )

    private fun MangasPage.toSerializable() = SerializableMangasPage(
        mangas = mangas.map { it.toSerializable() },
        hasNextPage = hasNextPage,
    )

    private fun SManga.toSerializable() = SerializableManga(
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = genre,
        status = status,
        thumbnail_url = thumbnail_url,
        initialized = initialized,
    )

    private fun SChapter.toSerializable() = SerializableChapter(
        url = url,
        name = name,
        date_upload = date_upload,
        chapter_number = chapter_number,
        scanlator = scanlator,
    )

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
