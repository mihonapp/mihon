package eu.kanade.tachiyomi.source.custom

import android.content.Context
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File

/**
 * Manager for custom user-defined novel sources
 *
 * Handles:
 * - Loading/saving custom source configurations
 * - Creating CustomNovelSource instances from configs
 * - Validating source configurations
 * - Providing templates for common site structures
 */
class CustomSourceManager(
    private val context: Context,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val customSourcesDir: File by lazy {
        File(context.filesDir, "custom_sources").apply { mkdirs() }
    }

    private val _customSources = MutableStateFlow<List<CustomNovelSource>>(emptyList())
    val customSources: Flow<List<CustomNovelSource>> = _customSources.asStateFlow()

    init {
        loadAllSources()
    }

    /**
     * Load all saved custom sources from disk
     */
    fun loadAllSources() {
        val sources = mutableListOf<CustomNovelSource>()

        customSourcesDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
            try {
                val config = json.decodeFromString<CustomSourceConfig>(file.readText())
                sources.add(CustomNovelSource(config))
                logcat(LogPriority.DEBUG) { "Loaded custom source: ${config.name}" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load custom source: ${file.name}" }
            }
        }

        _customSources.value = sources
    }

    /**
     * Get all custom sources as CatalogueSource list
     */
    fun getSources(): List<CatalogueSource> = _customSources.value

    /**
     * Create a new custom source from configuration
     */
    fun createSource(config: CustomSourceConfig): Result<CustomNovelSource> {
        return try {
            // Validate config
            validateConfig(config)

            // Create source
            val source = CustomNovelSource(config)

            // Save to disk
            saveSourceConfig(config)

            // Add to list
            _customSources.value = _customSources.value + source

            Result.success(source)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update an existing custom source
     */
    fun updateSource(oldId: Long, newConfig: CustomSourceConfig): Result<CustomNovelSource> {
        return try {
            validateConfig(newConfig)

            // Remove old source
            deleteSource(oldId)

            // Create new source
            createSource(newConfig)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a custom source
     */
    fun deleteSource(sourceId: Long): Boolean {
        val source = _customSources.value.find { it.id == sourceId } ?: return false

        // Remove from list
        _customSources.value = _customSources.value.filter { it.id != sourceId }

        // Delete file
        val file = File(customSourcesDir, "${sanitizeFilename(source.name)}.json")
        return file.delete()
    }

    /**
     * Export a source configuration as JSON
     */
    fun exportSource(sourceId: Long): String? {
        val source = _customSources.value.find { it.id == sourceId } ?: return null
        val file = File(customSourcesDir, "${sanitizeFilename(source.name)}.json")
        return if (file.exists()) file.readText() else null
    }

    /**
     * Import a source from JSON string
     */
    fun importSource(jsonString: String): Result<CustomNovelSource> {
        return try {
            val config = json.decodeFromString<CustomSourceConfig>(jsonString)
            createSource(config)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get available templates
     */
    fun getTemplates(): Map<String, CustomSourceConfig> = CustomSourceTemplates.getAll()

    /**
     * Create a config from a template
     */
    fun fromTemplate(templateName: String, name: String, baseUrl: String): CustomSourceConfig {
        val template = CustomSourceTemplates.getAll()[templateName]
            ?: CustomSourceTemplates.GENERIC

        return template.copy(
            name = name,
            baseUrl = baseUrl,
            popularUrl = template.popularUrl.replace("https://example.com", baseUrl),
            latestUrl = template.latestUrl?.replace("https://example.com", baseUrl),
            searchUrl = template.searchUrl.replace("https://example.com", baseUrl),
            chapterAjax = template.chapterAjax?.replace("https://example.com", baseUrl),
        )
    }

    /**
     * Validate a source configuration
     */
    fun validateConfig(config: CustomSourceConfig): List<String> {
        val errors = mutableListOf<String>()

        if (config.name.isBlank()) {
            errors.add("Name is required")
        }

        if (config.baseUrl.isBlank()) {
            errors.add("Base URL is required")
        } else if (!config.baseUrl.startsWith("http://") && !config.baseUrl.startsWith("https://")) {
            errors.add("Base URL must start with http:// or https://")
        }

        if (config.popularUrl.isBlank()) {
            errors.add("Popular URL is required")
        }

        if (config.searchUrl.isBlank()) {
            errors.add("Search URL is required")
        }

        if (config.selectors.popular.list.isBlank()) {
            errors.add("Popular list selector is required")
        }

        if (config.selectors.details.title.isBlank()) {
            errors.add("Details title selector is required")
        }

        if (config.selectors.chapters.list.isBlank()) {
            errors.add("Chapters list selector is required")
        }

        if (config.selectors.content.primary.isBlank()) {
            errors.add("Content primary selector is required")
        }

        // Check for duplicate name
        if (_customSources.value.any { it.name == config.name && it.id != config.id }) {
            errors.add("A source with this name already exists")
        }

        if (errors.isNotEmpty()) {
            throw IllegalArgumentException(errors.joinToString("; "))
        }

        return errors
    }

    /**
     * Test a source configuration by making actual requests
     * Tests all endpoints: popular, latest, search, details, chapters, content
     */
    suspend fun testSource(config: CustomSourceConfig): SourceTestResult {
        val source = CustomNovelSource(config)
        val results = mutableMapOf<String, TestStepResult>()
        var testManga: eu.kanade.tachiyomi.source.model.SManga? = null
        var testMangaSource: String? = null

        // Step 1: Test Popular
        try {
            val popular = source.getPopularManga(1)
            val success = popular.mangas.isNotEmpty()
            results["popular"] = TestStepResult(
                success = success,
                message = if (success) {
                    "Found ${popular.mangas.size} novels"
                } else {
                    "No novels found (URL may need adjustment - some sites have novels on homepage without page param)"
                },
                data = popular.mangas.firstOrNull()?.let {
                    mapOf(
                        "First Title" to it.title,
                        "First URL" to it.url,
                        "First Cover" to (it.thumbnail_url ?: "None"),
                    )
                },
            )
            if (success && testManga == null) {
                testManga = popular.mangas.first()
                testMangaSource = "popular"
            }
        } catch (e: Exception) {
            results["popular"] = TestStepResult(
                success = false,
                message = "Error: ${e.message}",
            )
        }

        // Step 2: Test Latest (if supported)
        if (config.latestUrl != null) {
            try {
                val latest = source.getLatestUpdates(1)
                val success = latest.mangas.isNotEmpty()
                results["latest"] = TestStepResult(
                    success = success,
                    message = if (success) {
                        "Found ${latest.mangas.size} novels"
                    } else {
                        "No novels found"
                    },
                    data = latest.mangas.firstOrNull()?.let {
                        mapOf(
                            "First Title" to it.title,
                            "First URL" to it.url,
                        )
                    },
                )
                if (success && testManga == null) {
                    testManga = latest.mangas.first()
                    testMangaSource = "latest"
                }
            } catch (e: Exception) {
                results["latest"] = TestStepResult(
                    success = false,
                    message = "Error: ${e.message}",
                )
            }
        }

        // Step 3: Test Search with a longer, more common query
        try {
            // Use "love" as it's common in many novel sites
            val searchQuery = "love"
            val search = source.getSearchManga(1, searchQuery, eu.kanade.tachiyomi.source.model.FilterList())
            val success = search.mangas.isNotEmpty()
            results["search"] = TestStepResult(
                success = success,
                message = if (success) {
                    "Found ${search.mangas.size} results for '$searchQuery'"
                } else {
                    "No results found for '$searchQuery'"
                },
                data = search.mangas.take(3).mapIndexed { index, manga ->
                    "Result ${index + 1}" to manga.title
                }.toMap(),
            )
            if (success && testManga == null) {
                testManga = search.mangas.first()
                testMangaSource = "search"
            }
        } catch (e: Exception) {
            results["search"] = TestStepResult(
                success = false,
                message = "Error: ${e.message}",
            )
        }

        // Step 4: Test Details (using first available manga)
        if (testManga != null) {
            try {
                val details = source.getMangaDetails(testManga!!)
                val success = details.title.isNotBlank()
                results["details"] = TestStepResult(
                    success = success,
                    message = if (success) "Got details for: ${details.title}" else "Failed to get title",
                    data = mapOf(
                        "Title" to details.title,
                        "Author" to (details.author ?: "N/A"),
                        "Description" to (details.description?.take(150)?.let { "$it..." } ?: "None"),
                        "Cover URL" to (details.thumbnail_url ?: "None"),
                        "Status" to when (details.status) {
                            eu.kanade.tachiyomi.source.model.SManga.ONGOING -> "Ongoing"
                            eu.kanade.tachiyomi.source.model.SManga.COMPLETED -> "Completed"
                            else -> "Unknown"
                        },
                        "Source" to (testMangaSource ?: "unknown"),
                    ),
                )
            } catch (e: Exception) {
                results["details"] = TestStepResult(
                    success = false,
                    message = "Error: ${e.message}",
                )
            }

            // Test chapters
            try {
                val chapters = source.getChapterList(testManga)
                results["chapters"] = TestStepResult(
                    success = chapters.isNotEmpty(),
                    message = if (chapters.isNotEmpty()) {
                        "Found ${chapters.size} chapters"
                    } else {
                        "No chapters found"
                    },
                    data = if (chapters.isNotEmpty()) {
                        mapOf(
                            "Total Chapters" to chapters.size.toString(),
                            "First Chapter" to chapters.last().name, // Reversed list usually
                            "First URL" to chapters.last().url,
                            "Last Chapter" to chapters.first().name,
                            "Last URL" to chapters.first().url,
                        )
                    } else {
                        null
                    },
                )

                // Test content (first chapter - which is usually last in list if reversed)
                if (chapters.isNotEmpty()) {
                    try {
                        // Use the first chapter in the list (latest) or last (first)?
                        // Usually we want to test the first chapter of the book.
                        val chapterToTest = chapters.last()
                        val pages = source.getPageList(chapterToTest)
                        if (pages.isNotEmpty()) {
                            val content = source.fetchPageText(pages.first())
                            val cleanContent = content.replace(Regex("<[^>]*>"), "").trim()
                            val preview = if (cleanContent.length > 200) {
                                "${cleanContent.take(100)}...${cleanContent.takeLast(100)}"
                            } else {
                                cleanContent
                            }

                            results["content"] = TestStepResult(
                                success = content.isNotBlank(),
                                message = if (content.isNotBlank()) {
                                    "Content length: ${content.length} chars"
                                } else {
                                    "Empty content"
                                },
                                data = mapOf(
                                    "Preview" to preview,
                                ),
                            )
                        }
                    } catch (e: Exception) {
                        results["content"] = TestStepResult(
                            success = false,
                            message = "Error: ${e.message}",
                        )
                    }
                }
            } catch (e: Exception) {
                results["chapters"] = TestStepResult(
                    success = false,
                    message = "Error: ${e.message}",
                )
            }
        }

        return SourceTestResult(
            sourceName = config.name,
            overallSuccess = results.values.all { it.success },
            steps = results,
        )
    }

    private fun saveSourceConfig(config: CustomSourceConfig) {
        val file = File(customSourcesDir, "${sanitizeFilename(config.name)}.json")
        file.writeText(json.encodeToString(config))
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}

data class TestStepResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, String>? = null,
)

data class SourceTestResult(
    val sourceName: String,
    val overallSuccess: Boolean,
    val steps: Map<String, TestStepResult>,
)
