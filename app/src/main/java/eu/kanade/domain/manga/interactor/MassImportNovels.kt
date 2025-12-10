package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.delay
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.download.service.NovelDownloadPreferences
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random

/**
 * Interactor for mass importing novels from URLs.
 * Handles parsing URLs, finding matching sources, fetching novels, and adding them to library.
 */
class MassImportNovels(
    private val sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId = Injekt.get(),
    private val novelDownloadPreferences: NovelDownloadPreferences = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
) {
    /**
     * Result of mass import operation
     */
    data class ImportResult(
        val added: MutableList<ImportedNovel> = mutableListOf(),
        val skipped: MutableList<SkippedNovel> = mutableListOf(),
        val errored: MutableList<ErroredNovel> = mutableListOf(),
    )

    data class ImportedNovel(val title: String, val url: String, val manga: Manga)
    data class SkippedNovel(val title: String, val url: String, val reason: String)
    data class ErroredNovel(val url: String, val error: String)

    /**
     * Progress callback for mass import
     */
    data class ImportProgress(
        val current: Int,
        val total: Int,
        val currentUrl: String,
        val status: String,
    )

    /**
     * Import novels from a list of URLs
     *
     * @param urls List of URLs to import
     * @param addToLibrary Whether to add imported novels to library
     * @param onProgress Callback for progress updates
     * @param isCancelled Function to check if operation should be cancelled
     * @return ImportResult with added, skipped, and errored novels
     */
    suspend fun import(
        urls: List<String>,
        addToLibrary: Boolean = true,
        onProgress: (ImportProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): ImportResult {
        val result = ImportResult()
        val novelSources = getNovelSources()

        if (novelSources.isEmpty()) {
            urls.forEach { url ->
                result.errored.add(ErroredNovel(url, "No novel sources installed"))
            }
            return result
        }

        var lastImportTime = 0L

        urls.forEachIndexed { index, url ->
            if (isCancelled()) {
                return result
            }

            val cleanUrl = url.trim()
            if (cleanUrl.isEmpty()) {
                return@forEachIndexed
            }

            onProgress(ImportProgress(index + 1, urls.size, cleanUrl, "Processing..."))

            // Apply throttling
            if (novelDownloadPreferences.enableMassImportThrottling().get()) {
                val now = System.currentTimeMillis()
                val baseDelay = novelDownloadPreferences.massImportDelay().get().toLong()
                val randomRange = novelDownloadPreferences.randomDelayRange().get()
                val randomDelay = if (randomRange > 0) Random.nextLong(0, randomRange.toLong()) else 0L
                val totalDelay = baseDelay + randomDelay

                val timeSinceLastImport = now - lastImportTime
                if (timeSinceLastImport < totalDelay && lastImportTime > 0) {
                    delay(totalDelay - timeSinceLastImport)
                }
                lastImportTime = System.currentTimeMillis()
            }

            try {
                processUrl(cleanUrl, novelSources, result, addToLibrary)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Error importing $cleanUrl" }
                result.errored.add(ErroredNovel(cleanUrl, e.message ?: "Unknown error"))
            }
        }

        return result
    }

    /**
     * Process a single URL for import
     */
    private suspend fun processUrl(
        url: String,
        novelSources: List<HttpSource>,
        result: ImportResult,
        addToLibrary: Boolean,
    ) {
        // Find matching source based on URL
        val source = findMatchingSource(url, novelSources)
        if (source == null) {
            result.errored.add(ErroredNovel(url, "No matching source found for URL"))
            return
        }

        // Extract path from URL
        val path = extractPathFromUrl(url, source.baseUrl)
        if (path.isEmpty()) {
            result.errored.add(ErroredNovel(url, "Could not extract path from URL"))
            return
        }

        // Check if already in library
        val existingManga = getMangaByUrlAndSourceId.await(path, source.id)
        if (existingManga != null && existingManga.favorite) {
            result.skipped.add(SkippedNovel(existingManga.title, url, "Already in library"))
            return
        }

        // Fetch novel details from source
        try {
            val sManga = source.getMangaDetails(
                eu.kanade.tachiyomi.source.model.SManga.create().apply {
                    this.url = path
                },
            )
            sManga.url = path

            // Convert to local manga and add to library
            val manga = networkToLocalManga(sManga.toDomainManga(source.id))

            if (addToLibrary) {
                // Update manga to be in library - persist to database
                mangaRepository.update(
                    MangaUpdate(
                        id = manga.id,
                        favorite = true,
                        dateAdded = System.currentTimeMillis(),
                    ),
                )
                val updatedManga = manga.copy(favorite = true)
                result.added.add(ImportedNovel(sManga.title, url, updatedManga))
            } else {
                result.added.add(ImportedNovel(sManga.title, url, manga))
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to fetch novel from $url" }
            result.errored.add(ErroredNovel(url, "Failed to fetch: ${e.message}"))
        }
    }

    /**
     * Get all novel sources
     */
    private fun getNovelSources(): List<HttpSource> {
        return sourceManager.getCatalogueSources()
            .filterIsInstance<HttpSource>()
            .filter { it.isNovelSource() }
    }

    /**
     * Find source that matches the given URL
     */
    private fun findMatchingSource(url: String, sources: List<HttpSource>): HttpSource? {
        return sources.find { source ->
            try {
                val baseUrl = source.baseUrl.removeSuffix("/")
                url.startsWith(baseUrl)
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Extract path from full URL
     */
    private fun extractPathFromUrl(url: String, baseUrl: String): String {
        val normalizedBase = baseUrl.removeSuffix("/")
        return if (url.startsWith(normalizedBase)) {
            url.removePrefix(normalizedBase)
        } else {
            url
        }
    }

    /**
     * Parse URLs from text (handles newlines, commas, etc.)
     */
    fun parseUrls(text: String): List<String> {
        return text
            .split("\n", ",", ";", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() && (it.startsWith("http://") || it.startsWith("https://")) }
            .distinct()
    }
}

/**
 * Extension to convert SManga to domain Manga
 */
private fun eu.kanade.tachiyomi.source.model.SManga.toDomainManga(sourceId: Long): Manga {
    return Manga.create().copy(
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = genre?.split(", ") ?: emptyList(),
        status = status.toLong(),
        thumbnailUrl = thumbnail_url,
        initialized = initialized,
        source = sourceId,
    )
}
