package eu.kanade.domain.manga.interactor

import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.download.service.NovelDownloadPreferences
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
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
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
) {
    /**
     * Result of mass import operation
     */
    data class ImportResult(
        val added: MutableList<ImportedNovel> = mutableListOf(),
        val skipped: MutableList<SkippedNovel> = mutableListOf(),
        val errored: MutableList<ErroredNovel> = mutableListOf(),
        // Prefilter results
        val prefilterInvalid: List<Pair<String, String>> = emptyList(),
        val prefilterDuplicates: List<String> = emptyList(),
        val prefilterAlreadyInLibrary: List<String> = emptyList(),
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
        val isRunning: Boolean = false,
        val activeImports: List<String> = emptyList(), // Track concurrent imports
        val concurrency: Int = 1,
    )

    private val _progress = MutableStateFlow<ImportProgress?>(null)
    val progress = _progress.asStateFlow()

    private val _result = MutableStateFlow<ImportResult?>(null)
    val result = _result.asStateFlow()

    private var isCancelled = false
    private val importScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runningJob: Job? = null

    fun cancel() {
        isCancelled = true
        runningJob?.cancel()
        runningJob = null
        _progress.update { it?.copy(isRunning = false, status = "Cancelled") }
    }

    fun clear() {
        _progress.value = null
        _result.value = null
        isCancelled = false
    }

    fun startImport(
        urls: List<String>,
        addToLibrary: Boolean = true,
        categoryId: Long? = null,
        fetchDetails: Boolean = true,
        fetchChapters: Boolean = false,
    ): Job {
        // Cancel any previous run
        cancel()
        isCancelled = false
        _result.value = null

        val job = importScope.launch {
            importInternal(
                urls = urls,
                addToLibrary = addToLibrary,
                categoryId = categoryId,
                fetchDetails = fetchDetails,
                fetchChapters = fetchChapters,
            )
        }
        runningJob = job
        return job
    }

    /**
     * Import novels from a list of URLs
     *
     * @param urls List of URLs to import
     * @param addToLibrary Whether to add imported novels to library
     */
    suspend fun import(
        urls: List<String>,
        addToLibrary: Boolean = true,
    ) {
        importInternal(
            urls = urls,
            addToLibrary = addToLibrary,
            categoryId = null,
            fetchDetails = true,
            fetchChapters = false,
        )
    }

    private suspend fun importInternal(
        urls: List<String>,
        addToLibrary: Boolean,
        categoryId: Long?,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) {
        isCancelled = false
        _result.value = null

        val novelSources = getNovelSources()

        // Prefilter: build an in-memory index of library entries to avoid per-URL DB hits.
        // Key is (sourceId,urlPath) where urlPath matches the stored manga.url format.
        // Use efficient query that only fetches source_id and url
        val libraryUrlIndex: Set<Pair<Long, String>> = try {
            mangaRepository.getFavoriteSourceAndUrl().toSet()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to build library URL index for mass import" }
            emptySet()
        }

        // Analyze URLs to separate valid ones from invalid/duplicates/already in library
        val rawUrls = urls.map { it.trim() }.filter { it.isNotEmpty() }
        val validUrls = mutableListOf<String>()
        val invalidUrls = mutableListOf<Pair<String, String>>()
        val duplicateUrls = mutableListOf<String>()
        val alreadyInLibrary = mutableListOf<String>()
        val seenKeys = mutableSetOf<String>()

        for (url in rawUrls) {
            // Check deduplication
            val key = urlDedupKey(url)
            if (key in seenKeys) {
                duplicateUrls.add(url)
                continue
            }
            seenKeys.add(key)

            // Check for valid URL
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                invalidUrls.add(url to "Not a valid URL")
                continue
            }

            // Check if source exists
            val source = findMatchingSource(url, novelSources)
            if (source == null) {
                invalidUrls.add(url to "No matching source")
                continue
            }

            // Check if already in library
            val path = extractPathFromUrl(url, source.baseUrl)
            if (libraryUrlIndex.contains(source.id to path)) {
                alreadyInLibrary.add(url)
                continue
            }

            validUrls.add(url)
        }

        // Create result with prefilter data
        val currentResult = ImportResult(
            prefilterInvalid = invalidUrls,
            prefilterDuplicates = duplicateUrls,
            prefilterAlreadyInLibrary = alreadyInLibrary,
        )

        val cleanUrls = validUrls

        if (novelSources.isEmpty()) {
            urls.forEach { url ->
                currentResult.errored.add(ErroredNovel(url, "No novel sources installed"))
            }
            _result.value = currentResult
            return
        }

        val concurrency = novelDownloadPreferences.parallelMassImport().get()
        val semaphore = Semaphore(concurrency)
        val completedCount = AtomicInteger(0)
        val activeImports = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

        _progress.value = ImportProgress(0, cleanUrls.size, "", "Starting...", true, emptyList(), concurrency)

        // Group URLs by source to apply delays per-source instead of globally
        val urlsBySource = cleanUrls.groupBy { url ->
            findMatchingSource(url, novelSources)?.id ?: -1L
        }

        coroutineScope {
            urlsBySource.values.map { urlsForSource ->
                async {
                    for ((index, url) in urlsForSource.withIndex()) {
                        if (isCancelled) return@async

                        // Apply per-source throttling: delay only between imports from SAME source.
                        // Do NOT hold the global semaphore permit while delaying.
                        if (index > 0 && novelDownloadPreferences.enableMassImportThrottling().get()) {
                            val baseDelay = novelDownloadPreferences.massImportDelay().get().toLong()
                            val randomRange = novelDownloadPreferences.randomDelayRange().get()
                            val randomDelay = if (randomRange > 0) Random.nextLong(0, randomRange.toLong()) else 0L
                            delay(baseDelay + randomDelay)
                        }

                        semaphore.withPermit {
                            if (isCancelled) return@withPermit

                            val cleanUrl = url.trim()
                            if (cleanUrl.isEmpty()) return@withPermit

                            // Track this import as active
                            activeImports[cleanUrl] = true
                            _progress.update {
                                it?.copy(
                                    current = (completedCount.get() + 1).coerceAtMost(cleanUrls.size),
                                    currentUrl = cleanUrl,
                                    status = "Processing ${activeImports.size} novel(s)...",
                                    activeImports = activeImports.keys().toList(),
                                )
                            }

                            try {
                                processUrl(
                                    url = cleanUrl,
                                    novelSources = novelSources,
                                    result = currentResult,
                                    addToLibrary = addToLibrary,
                                    categoryId = categoryId,
                                    fetchDetails = fetchDetails,
                                    fetchChapters = fetchChapters,
                                    libraryUrlIndex = libraryUrlIndex,
                                )
                            } catch (e: Exception) {
                                logcat(LogPriority.ERROR, e) { "Error importing $cleanUrl" }
                                synchronized(currentResult) {
                                    currentResult.errored.add(ErroredNovel(cleanUrl, e.message ?: "Unknown error"))
                                }
                            }

                            // Remove from active imports
                            activeImports.remove(cleanUrl)
                            val done = completedCount.incrementAndGet()
                            _progress.update {
                                val statusText = if (activeImports.isEmpty()) {
                                    "Finishing..."
                                } else {
                                    "Processing ${activeImports.size} novel(s)..."
                                }

                                it?.copy(
                                    current = completedCount.get(),
                                    status = statusText,
                                    activeImports = activeImports.keys().toList(),
                                )
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        _progress.update { it?.copy(isRunning = false, status = "Complete") }
        _result.value = currentResult
    }

    /**
     * Process a single URL for import
     */
    private suspend fun processUrl(
        url: String,
        novelSources: List<HttpSource>,
        result: ImportResult,
        addToLibrary: Boolean,
        categoryId: Long?,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
        libraryUrlIndex: Set<Pair<Long, String>>,
    ) {
        // Find matching source based on URL
        val source = findMatchingSource(url, novelSources)
        if (source == null) {
            synchronized(result) {
                result.errored.add(ErroredNovel(url, "No matching source found for URL"))
            }
            return
        }

        // Extract path from URL
        val path = extractPathFromUrl(url, source.baseUrl)
        if (path.isEmpty()) {
            synchronized(result) {
                result.errored.add(ErroredNovel(url, "Could not extract path from URL"))
            }
            return
        }

        // Fast path: already in library (from prefiltered index)
        if (libraryUrlIndex.contains(source.id to path)) {
            synchronized(result) {
                result.skipped.add(SkippedNovel(url, url, "Already in library"))
            }
            return
        }

        // Check if already in DB but not yet in library (unfavorited)
        val existingManga = mangaRepository.getLiteMangaByUrlAndSourceId(path, source.id)
        if (existingManga != null && existingManga.favorite) {
            synchronized(result) {
                result.skipped.add(SkippedNovel(existingManga.title, url, "Already in library"))
            }
            return
        }

        // If the manga exists but isn't in library, we can often avoid refetching details.
        // Only fetch details if requested (or if we need chapters).
        if (existingManga != null && addToLibrary && !fetchDetails && !fetchChapters) {
            mangaRepository.update(
                MangaUpdate(
                    id = existingManga.id,
                    favorite = true,
                    dateAdded = System.currentTimeMillis(),
                ),
            )
            val updatedManga = existingManga.copy(favorite = true)

            if (categoryId != null && categoryId != 0L) {
                setMangaCategories.await(updatedManga.id, listOf(categoryId))
            }

            synchronized(result) {
                result.added.add(ImportedNovel(updatedManga.title, url, updatedManga))
            }
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

            // Validate that essential fields are initialized
            try {
                // Access title to check if it's initialized
                @Suppress("UNUSED_VARIABLE")
                val titleCheck = sManga.title
            } catch (e: UninitializedPropertyAccessException) {
                throw Exception("Extension failed to parse novel title from $url")
            }

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

                // Assign category if requested (0/null means default)
                if (categoryId != null && categoryId != 0L) {
                    setMangaCategories.await(updatedManga.id, listOf(categoryId))
                }

                // Optionally fetch/sync chapter list
                if (fetchChapters) {
                    try {
                        val sChapters = source.getChapterList(updatedManga.toSManga())
                        syncChaptersWithSource.await(sChapters, updatedManga, source)
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) { "Failed to sync chapters for $url" }
                    }
                }

                synchronized(result) {
                    result.added.add(ImportedNovel(sManga.title, url, updatedManga))
                }
            } else {
                synchronized(result) {
                    result.added.add(ImportedNovel(sManga.title, url, manga))
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to fetch novel from $url" }
            synchronized(result) {
                result.errored.add(ErroredNovel(url, "Failed to fetch: ${e.message}"))
            }
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
     * Find source that matches the given URL.
     * Prioritizes Kotlin extensions over JS plugins when multiple sources match the same URL.
     */
    private fun findMatchingSource(url: String, sources: List<HttpSource>): HttpSource? {
        val normalizedUrl = stripScheme(url).removeSuffix("/")
        
        // Find all matching sources
        val matchingSources = sources.filter { source ->
            try {
                val baseUrl = stripScheme(source.baseUrl).removeSuffix("/")
                normalizedUrl.startsWith(baseUrl)
            } catch (_: Exception) {
                false
            }
        }
        
        if (matchingSources.isEmpty()) return null
        if (matchingSources.size == 1) return matchingSources.first()
        
        // Prioritize Kotlin extensions over JS plugins
        // JS plugins are from JsSource class, Kotlin extensions are other HttpSource implementations
        val kotlinSources = matchingSources.filter { 
            it::class.java.name != "eu.kanade.tachiyomi.jsplugin.source.JsSource" 
        }
        
        return kotlinSources.firstOrNull() ?: matchingSources.first()
    }

    /**
     * Extract path from full URL
     */
    private fun extractPathFromUrl(url: String, baseUrl: String): String {
        return try {
            val baseUri = URI(baseUrl)
            val urlUri = URI(url)

            val baseHost = baseUri.host?.lowercase()
            val urlHost = urlUri.host?.lowercase()

            // If hosts match, use path+query regardless of scheme.
            if (baseHost != null && urlHost != null && baseHost == urlHost) {
                buildString {
                    append(urlUri.rawPath ?: "")
                    val q = urlUri.rawQuery
                    if (!q.isNullOrBlank()) {
                        append('?')
                        append(q)
                    }
                }
            } else {
                val normalizedBase = stripScheme(baseUrl).removeSuffix("/")
                val normalizedUrl = stripScheme(url)
                if (normalizedUrl.startsWith(normalizedBase)) {
                    normalizedUrl.removePrefix(normalizedBase)
                } else {
                    normalizedUrl
                }
            }
        } catch (_: Exception) {
            val normalizedBase = stripScheme(baseUrl).removeSuffix("/")
            val normalizedUrl = stripScheme(url)
            if (normalizedUrl.startsWith(normalizedBase)) {
                normalizedUrl.removePrefix(normalizedBase)
            } else {
                normalizedUrl
            }
        }
    }

    private fun stripScheme(url: String): String {
        // Keep it simple: remove any leading scheme:// and lowercase for comparison.
        return url.trim()
            .replace(Regex("^[a-zA-Z][a-zA-Z0-9+\\-.]*://"), "")
            .lowercase()
    }

    private fun urlDedupKey(url: String): String {
        return try {
            val uri = URI(url.trim())
            buildString {
                append(uri.host?.lowercase() ?: "")
                append(uri.rawPath?.trimEnd('/') ?: "")
                val q = uri.rawQuery
                if (!q.isNullOrBlank()) {
                    append('?')
                    append(q)
                }
            }
        } catch (_: Exception) {
            // Fallback: ignore scheme, trim trailing slash
            stripScheme(url).removeSuffix("/")
        }
    }

    /**
     * Parse URLs from text (handles newlines, commas, etc.)
     * Pre-processes text to add line breaks before http/https and normalize double slashes.
     */
    fun parseUrls(text: String): List<String> {
        // Pre-process: add line break before http:// or https:// to split concatenated URLs
        val preprocessed = text
            .replace(Regex("(?<=[^\\s])(?=https?://)"), "\n")  // Add newline before http(s):// if not preceded by whitespace
            .replace(Regex("(?<!https?:)//+"), "/")  // Replace // with / except in protocol (http://, https://)
        
        return preprocessed
            .split("\n", ",", ";", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() && (it.startsWith("http://") || it.startsWith("https://")) }
            .distinctBy { urlDedupKey(it) }
    }

    /**
     * Result of URL analysis/prefiltering
     */
    data class UrlAnalysisResult(
        val validUrls: List<String>,
        val invalidUrls: List<Pair<String, String>>, // URL to reason
        val duplicateUrls: List<String>,
        val alreadyInLibrary: List<String>,
    ) {
        val totalValid get() = validUrls.size
        val totalInvalid get() = invalidUrls.size + duplicateUrls.size + alreadyInLibrary.size
    }

    /**
     * Analyze URLs before import to give user feedback
     */
    suspend fun analyzeUrls(text: String): UrlAnalysisResult = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val novelSources = getNovelSources()
        
        // Use efficient query that only fetches source_id and url
        val libraryUrlIndex: Set<Pair<Long, String>> = try {
            mangaRepository.getFavoriteSourceAndUrl().toSet()
        } catch (e: Exception) {
            emptySet()
        }

        val rawLines = text.split("\n", ",", ";", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val validUrls = mutableListOf<String>()
        val invalidUrls = mutableListOf<Pair<String, String>>()
        val duplicateUrls = mutableListOf<String>()
        val alreadyInLibrary = mutableListOf<String>()

        val seenKeys = mutableSetOf<String>()

        for (line in rawLines) {
            // Check if it's a valid URL
            if (!line.startsWith("http://") && !line.startsWith("https://")) {
                invalidUrls.add(line to "Not a valid URL")
                continue
            }

            // Check for duplicates
            val key = urlDedupKey(line)
            if (key in seenKeys) {
                duplicateUrls.add(line)
                continue
            }
            seenKeys.add(key)

            // Check if source exists
            val source = findMatchingSource(line, novelSources)
            if (source == null) {
                invalidUrls.add(line to "No matching source")
                continue
            }

            // Check if already in library
            val path = extractPathFromUrl(line, source.baseUrl)
            if (libraryUrlIndex.contains(source.id to path)) {
                alreadyInLibrary.add(line)
                continue
            }

            validUrls.add(line)
        }

        UrlAnalysisResult(validUrls, invalidUrls, duplicateUrls, alreadyInLibrary)
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
