package eu.kanade.tachiyomi.data.download

import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.model.DownloadIndexEntry
import tachiyomi.domain.download.model.DownloadIndexMangaKey
import tachiyomi.domain.download.repository.DownloadIndexRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Cache where we dump the downloads directory from the filesystem. This class is needed because
 * directory checking is expensive and it slows down the app.
 */
@Inject
@SingleIn(AppScope::class)
class DownloadCache(
    @AppCoroutineScope private val scope: CoroutineScope,
    private val provider: DownloadProvider,
    private val sourceManager: SourceManager,
    private val storageManager: StorageManager,
    private val downloadIndexRepository: DownloadIndexRepository,
    private val downloadPreferences: DownloadPreferences,
) {

    private var renewalJob: Job? = null

    private val scanMutex = Mutex()

    // Guarded by scanMutex. Changes made while a scan is walking the filesystem are replayed after it
    private var isScanning = false
    private val pendingOps = mutableListOf<suspend () -> Unit>()
    private var scanToken = 0L
    private val index = Index()

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing = _isInitializing
        .debounce(1.seconds) // Don't notify if it finishes quickly enough
        .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    init {
        scope.launch(Dispatchers.IO) {
            try {
                scanMutex.withLock { index.replaceAll(downloadIndexRepository.getAllChapterDirsAsFlow().first()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "DownloadCache: failed to load download index" }
            }
            if (!downloadPreferences.downloadIndexInitialized.get()) renewCache()
        }
        storageManager.changes.onEach { invalidateCache() }.launchIn(scope)
    }

    fun isChapterInDirs(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        chapterDirs: Set<String>,
    ): Boolean {
        return chapterDirs.isNotEmpty() && provider.getValidChapterDirNames(chapterName, chapterScanlator, chapterUrl)
            .any { it in chapterDirs }
    }

    /**
     * Returns the ids of the downloaded chapters among the given chapters of one manga.
     *
     * @param chapters the chapters to query, all of the same manga.
     * @param mangaTitle the title of the manga to query.
     * @param sourceId the id of the source of the chapters.
     */
    fun getDownloadedChapterIds(chapters: List<Chapter>, mangaTitle: String, sourceId: Long): Set<Long> {
        val chapterDirs = index.chapterDirs(getMangaKey(sourceId, mangaTitle))
        if (chapterDirs.isEmpty()) return emptySet()
        return chapters
            .filter { isChapterInDirs(it.name, it.scanlator, it.url, chapterDirs) }
            .mapTo(HashSet()) { it.id }
    }

    fun getDownloadedChapterIdsAsFlow(chapters: List<Chapter>, mangaTitle: String, sourceId: Long): Flow<Set<Long>> {
        return downloadIndexRepository.getChapterDirsAsFlow(sourceId, provider.getMangaDirName(mangaTitle))
            .map { chapterDirs ->
                if (chapterDirs.isEmpty()) {
                    emptySet()
                } else {
                    chapters.filter { chapter ->
                        isChapterInDirs(chapter.name, chapter.scanlator, chapter.url, chapterDirs)
                    }.mapTo(HashSet()) { it.id }
                }
            }
    }

    /**
     * Returns true if the chapter is downloaded.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     * @param chapterUrl the url of the chapter to query
     * @param mangaTitle the title of the manga to query.
     * @param sourceId the id of the source of the chapter.
     */
    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        mangaTitle: String,
        sourceId: Long,
    ): Boolean {
        return isChapterInDirs(
            chapterName,
            chapterScanlator,
            chapterUrl,
            index.chapterDirs(getMangaKey(sourceId, mangaTitle)),
        )
    }

    /**
     * Returns the amount of downloaded chapters.
     */
    suspend fun getTotalDownloadCount(): Int {
        return downloadIndexRepository.getTotalChapterDirCount().toInt()
    }

    /**
     * Returns the amount of downloaded chapters for a manga.
     *
     * @param manga the manga to check.
     */
    fun getDownloadCount(manga: Manga): Int {
        return index.chapterDirs(keyOf(manga)).size
    }

    fun getAllDownloadCountsFlow(): Flow<Map<DownloadIndexMangaKey, Long>> {
        return downloadIndexRepository.getAllMangaChapterDirCountsAsFlow()
    }

    fun getAllChapterDirsAsFlow(): Flow<Map<DownloadIndexMangaKey, Set<String>>> {
        return downloadIndexRepository.getAllChapterDirsAsFlow()
    }

    fun getMangaKey(sourceId: Long, mangaTitle: String): DownloadIndexMangaKey {
        return DownloadIndexMangaKey(sourceId, provider.getMangaDirName(mangaTitle))
    }

    private fun keyOf(manga: Manga) = getMangaKey(manga.source, manga.title)

    /**
     * Adds a chapter that has just been downloaded to this cache.
     *
     * @param chapterDirName the downloaded chapter's directory name.
     * @param manga the manga of the chapter.
     */
    suspend fun addChapter(chapterDirName: String, manga: Manga) {
        val source = sourceManager.get(manga.source) ?: run {
            logcat(LogPriority.WARN) { "Failed to add ${manga.title} to index. Source ${manga.source} not found." }
            return
        }
        val key = keyOf(manga)
        mutate(
            updateIndex = { index.add(key, chapterDirName) },
            persist = {
                downloadIndexRepository.insertChapterDirs(
                    listOf(
                        DownloadIndexEntry(
                            sourceId = manga.source,
                            sourceDir = provider.getSourceDirName(source),
                            mangaDir = key.mangaDir,
                            chapterDir = chapterDirName,
                        ),
                    ),
                )
            },
        )
    }

    /**
     * Removes a chapter that has been deleted from this cache.
     *
     * @param chapter the chapter to remove.
     * @param manga the manga of the chapter.
     */
    suspend fun removeChapter(chapter: Chapter, manga: Manga) {
        removeChapterDirs(provider.getValidChapterDirNames(chapter.name, chapter.scanlator, chapter.url), manga)
    }

    /**
     * Removes a list of chapters that have been deleted from this cache.
     *
     * @param chapters the list of chapters to remove.
     * @param manga the manga of the chapter.
     */
    suspend fun removeChapters(chapters: List<Chapter>, manga: Manga) {
        removeChapterDirs(chapters.flatMap { provider.getValidChapterDirNames(it.name, it.scanlator, it.url) }, manga)
    }

    private suspend fun removeChapterDirs(chapterDirs: List<String>, manga: Manga) {
        val key = keyOf(manga)
        mutate(
            updateIndex = { index.remove(key, chapterDirs) },
            persist = { downloadIndexRepository.deleteChapterDirs(key.sourceId, key.mangaDir, chapterDirs) },
        )
    }

    /**
     * Removes a manga that has been deleted from this cache.
     *
     * @param manga the manga to remove.
     */
    suspend fun removeManga(manga: Manga) {
        val key = keyOf(manga)
        mutate(
            updateIndex = { index.remove(key) },
            persist = { downloadIndexRepository.deleteMangaDir(key.sourceId, key.mangaDir) },
        )
    }

    /**
     * Renames a manga in this cache.
     *
     * @param manga the manga being renamed.
     * @param newTitle the manga's new title.
     */
    suspend fun renameManga(manga: Manga, newTitle: String) {
        val from = keyOf(manga)
        val to = getMangaKey(manga.source, newTitle)
        mutate(
            updateIndex = { index.rename(from, to) },
            persist = { downloadIndexRepository.renameMangaDir(from.sourceId, from.mangaDir, to.mangaDir) },
        )
    }

    suspend fun removeSource(source: Source) {
        mutate(
            updateIndex = { index.removeSource(source.id) },
            persist = { downloadIndexRepository.deleteBySource(source.id) },
        )
    }

    @Synchronized
    fun invalidateCache() {
        renewCache()
    }

    /**
     * Renews the downloads cache by rescanning the filesystem directory.
     */
    @Synchronized
    private fun renewCache() {
        renewalJob?.cancel()

        renewalJob = scope.launch(Dispatchers.IO) {
            val token = scanMutex.withLock {
                pendingOps.clear()
                isScanning = true
                _isInitializing.emit(true)
                ++scanToken
            }

            try {
                // Try to wait until extensions and sources have loaded
                val sources = withTimeoutOrNull(30.seconds) {
                    getSources()
                } ?: run {
                    logcat(LogPriority.WARN) { "Sources didn't load in time, scan skipped" }
                    return@launch
                }

                // A stored stub can share its name with an installed source, so a folder can belong to several ids
                val sourceIdsByDirName = sources.groupBy({ provider.getSourceDirName(it).lowercase() }, { it.id })

                val downloadsDir = storageManager.getDownloadsDirectory() ?: return@launch
                val sourceDirs =
                    (downloadsDir.listFiles() ?: return@launch).filter { it.isDirectory && !it.name.isNullOrBlank() }

                val sourceIdsByDir = sourceDirs.mapNotNull { dir ->
                    sourceIdsByDirName[dir.name!!.lowercase()]?.let { dir to it }
                }

                // Dirs exist but none resolved to a known source: sources probably aren't loaded, don't wipe the index
                if (sourceDirs.isNotEmpty() && sourceIdsByDir.isEmpty()) return@launch

                // Each folder is listed once, then fanned out to every id that points at it
                val scans = sourceIdsByDir.map { (dir, sourceIds) ->
                    async {
                        scanSourceDir(dir)?.let { mangaDirs -> toEntries(dir.name!!, sourceIds, mangaDirs) }
                    }
                }.awaitAll()
                if (scans.any { it == null }) {
                    logcat(LogPriority.WARN) { "Scan aborted, a directory couldn't be listed" }
                    return@launch
                }
                val entries = scans.filterNotNull().flatten()

                scanMutex.withLock {
                    if (scanToken != token) return@withLock

                    downloadIndexRepository.replaceAll(entries)
                    index.replaceAll(
                        entries
                            .groupBy({ DownloadIndexMangaKey(it.sourceId, it.mangaDir) }, { it.chapterDir })
                            .mapValues { it.value.toSet() },
                    )
                    downloadPreferences.downloadIndexInitialized.set(true)

                    pendingOps.forEach { it() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to create cache" }
            } finally {
                withContext(NonCancellable) {
                    scanMutex.withLock {
                        if (scanToken == token) {
                            isScanning = false
                            pendingOps.clear()
                            _isInitializing.emit(false)
                        }
                    }
                }
            }
        }
    }

    private suspend fun scanSourceDir(sourceDir: UniFile): Map<String, List<String>>? {
        val mangaDirs = sourceDir.listFiles()
            ?.filter { it.isDirectory && !it.name.isNullOrBlank() }
            ?: return null

        val result = HashMap<String, List<String>>(mangaDirs.size)
        for (mangaDir in mangaDirs) {
            currentCoroutineContext().ensureActive()
            result[mangaDir.name!!] = listChapterDirs(mangaDir) ?: return null
        }
        return result
    }

    private fun toEntries(
        sourceDirName: String,
        sourceIds: List<Long>,
        mangaDirs: Map<String, List<String>>,
    ): List<DownloadIndexEntry> {
        return sourceIds.flatMap { sourceId ->
            mangaDirs.flatMap { (mangaDir, chapterDirs) ->
                chapterDirs.map { DownloadIndexEntry(sourceId, sourceDirName, mangaDir, it) }
            }
        }
    }

    private fun listChapterDirs(mangaDir: UniFile): List<String>? {
        return mangaDir.listFiles()?.mapNotNull { file ->
            when {
                // Ignore incomplete downloads
                file.name?.endsWith(Downloader.TMP_DIR_SUFFIX) == true -> null
                // Folder of images
                file.isDirectory -> file.name
                // CBZ files
                file.isFile && file.extension == "cbz" -> file.nameWithoutExtension
                // Anything else is irrelevant
                else -> null
            }
        }
    }

    private suspend fun mutate(updateIndex: () -> Unit, persist: suspend () -> Unit) {
        scanMutex.withLock {
            applyMutation(updateIndex, persist)
            if (isScanning) pendingOps += { applyMutation(updateIndex, persist) }
        }
    }

    private suspend fun applyMutation(updateIndex: () -> Unit, persist: suspend () -> Unit) {
        updateIndex()
        try {
            persist()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to persist download index change" }
        }
    }

    private suspend fun getSources(): List<Source> {
        return sourceManager.getOnlineSources() + sourceManager.getStubSources()
    }
}

private class Index {
    private val map = ConcurrentHashMap<DownloadIndexMangaKey, Set<String>>()

    fun chapterDirs(key: DownloadIndexMangaKey): Set<String> = map[key].orEmpty()

    fun add(key: DownloadIndexMangaKey, chapterDir: String) {
        map.merge(key, setOf(chapterDir)) { old, new -> old + new }
    }

    fun remove(key: DownloadIndexMangaKey, chapterDirs: Collection<String>) {
        map.computeIfPresent(key) { _, old -> (old - chapterDirs.toSet()).ifEmpty { null } }
    }

    fun remove(key: DownloadIndexMangaKey) {
        map.remove(key)
    }

    fun rename(from: DownloadIndexMangaKey, to: DownloadIndexMangaKey) {
        map.remove(from)?.let { moved -> map.merge(to, moved) { old, new -> old + new } }
    }

    fun removeSource(sourceId: Long) {
        map.keys.removeIf { it.sourceId == sourceId }
    }

    fun replaceAll(newMap: Map<DownloadIndexMangaKey, Set<String>>) {
        map.putAll(newMap)
        map.keys.retainAll(newMap.keys)
    }
}
