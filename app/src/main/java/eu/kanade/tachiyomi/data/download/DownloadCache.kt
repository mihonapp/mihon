package eu.kanade.tachiyomi.data.download

import android.app.Application
import android.content.Context
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * Cache where we dump the downloads directory from the filesystem. This class is needed because
 * directory checking is expensive and it slows down the app.
 */
class DownloadCache(
    private val context: Context,
    private val provider: DownloadProvider = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val storageManager: StorageManager = Injekt.get(),
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _changes: Channel<Unit> = Channel(Channel.UNLIMITED)
    val changes = _changes.receiveAsFlow()
        .onStart { emit(Unit) }
        .shareIn(scope, SharingStarted.Lazily, 1)

    private var renewalJob: Job? = null

    private val initialized = MutableStateFlow(false)
    private val _isIndexing = MutableStateFlow(false)
    val isIndexing = _isIndexing
        .debounce(1000L) // Don't notify if it finishes quickly enough
        .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    private val diskCacheFile: File = File(context.filesDir, "download_cache.pb")

    private val rootDownloadsDirMutex = Mutex()
    private var rootDownloadsDir = storageManager.getDownloadsDirectory()?.let { RootDirectory(it) }

    init {
        // Attempt to read cache file
        scope.launch {
            rootDownloadsDirMutex.withLock {
                try {
                    if (diskCacheFile.exists()) {
                        rootDownloadsDir = diskCacheFile.inputStream().use {
                            ProtoBuf.decodeFromByteArray<RootDirectory>(it.readBytes())
                        }
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Failed to initialize from disk cache" }
                    diskCacheFile.delete()
                }
                initialized.value = true
            }
        }

        storageManager.changes
            .onEach { invalidateCache() }
            .launchIn(scope)
    }

    /**
     * Returns true if the chapter is downloaded.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     * @param mangaTitle the title of the manga to query.
     * @param sourceId the id of the source of the chapter.
     * @param skipCache whether to skip the directory cache and check in the filesystem.
     */
    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        sourceId: Long,
        skipCache: Boolean,
    ): Boolean {
        if (skipCache) {
            val source = sourceManager.getOrStub(sourceId)
            return provider.findChapterDir(chapterName, chapterScanlator, mangaTitle, source) != null
        }

        val rootDirectory = rootDownloadsDir ?: return false

        val sourceDir = rootDirectory.subDirs[sourceId]
        if (sourceDir != null) {
            val mangaDir = sourceDir.subDirs[provider.getMangaDirName(mangaTitle)]
            if (mangaDir != null) {
                return provider.getValidChapterDirNames(
                    chapterName,
                    chapterScanlator,
                ).any { it in mangaDir.subDirs }
            }
        }
        return false
    }

    /**
     * Returns the amount of downloaded chapters.
     */
    fun getTotalDownloadCount(): Int {
        val rootDirectory = rootDownloadsDir ?: return 0

        return rootDirectory.subDirs.values.sumOf { sourceDir ->
            sourceDir.subDirs.values.sumOf { mangaDir ->
                mangaDir.subDirs.size
            }
        }
    }

    /**
     * Returns the amount of downloaded chapters for a manga.
     *
     * @param manga the manga to check.
     */
    fun getDownloadCount(manga: Manga): Int {
        return rootDownloadsDir
            ?.subDirs
            ?.get(manga.source)
            ?.subDirs
            ?.get(provider.getMangaDirName(manga.title))
            ?.subDirs
            ?.size
            ?: 0
    }

    /**
     * Adds a chapter that has just been download to this cache.
     *
     * @param chapterDirName the downloaded chapter's directory name.
     * @param mangaUniFile the directory of the manga.
     * @param manga the manga of the chapter.
     */
    suspend fun addChapter(chapterDirName: String, mangaUniFile: UniFile, manga: Manga) {
        rootDownloadsDirMutex.withLock {
            val rootDirectory = rootDownloadsDir ?: return

            // Retrieve the cached source directory or cache a new one
            var sourceDir = rootDirectory.subDirs[manga.source]
            if (sourceDir == null) {
                val source = sourceManager.get(manga.source) ?: return
                val sourceUniFile = provider.findSourceDir(source) ?: return
                sourceDir = SourceDirectory(sourceUniFile)
                rootDirectory.subDirs += manga.source to sourceDir
            }

            // Retrieve the cached manga directory or cache a new one
            val mangaDirName = provider.getMangaDirName(manga.title)
            var mangaDir = sourceDir.subDirs[mangaDirName]
            if (mangaDir == null) {
                mangaDir = MangaDirectory(mangaUniFile)
                sourceDir.subDirs += mangaDirName to mangaDir
            }

            // Save the chapter directory
            val chapterDirectory = mangaDir.dir.findFile(chapterDirName)
                ?.toChapterDirectory()
                ?: return@withLock
            mangaDir.subDirs += chapterDirectory
        }

        notifyChanges()
    }

    /**
     * Removes a chapter that has been deleted from this cache.
     *
     * @param chapter the chapter to remove.
     * @param manga the manga of the chapter.
     */
    suspend fun removeChapter(chapter: Chapter, manga: Manga) {
        rootDownloadsDirMutex.withLock {
            val rootDirectory = rootDownloadsDir ?: return

            val sourceDir = rootDirectory.subDirs[manga.source] ?: return
            val mangaDir = sourceDir.subDirs[provider.getMangaDirName(manga.title)] ?: return
            provider.getValidChapterDirNames(chapter.name, chapter.scanlator).forEach {
                if (it in mangaDir.subDirs) {
                    mangaDir.subDirs -= it
                }
            }
        }

        notifyChanges()
    }

    /**
     * Removes a list of chapters that have been deleted from this cache.
     *
     * @param chapters the list of chapter to remove.
     * @param manga the manga of the chapter.
     */
    suspend fun removeChapters(chapters: List<Chapter>, manga: Manga) {
        rootDownloadsDirMutex.withLock {
            val rootDirectory = rootDownloadsDir ?: return

            val sourceDir = rootDirectory.subDirs[manga.source] ?: return
            val mangaDir = sourceDir.subDirs[provider.getMangaDirName(manga.title)] ?: return
            chapters.forEach { chapter ->
                provider.getValidChapterDirNames(chapter.name, chapter.scanlator).forEach {
                    if (it in mangaDir.subDirs) {
                        mangaDir.subDirs -= it
                    }
                }
            }
        }

        notifyChanges()
    }

    /**
     * Removes a manga that has been deleted from this cache.
     *
     * @param manga the manga to remove.
     */
    suspend fun removeManga(manga: Manga) {
        rootDownloadsDirMutex.withLock {
            val rootDirectory = rootDownloadsDir ?: return

            val sourceDir = rootDirectory.subDirs[manga.source] ?: return
            val mangaDirName = provider.getMangaDirName(manga.title)
            if (sourceDir.subDirs.containsKey(mangaDirName)) {
                sourceDir.subDirs -= mangaDirName
            }
        }

        notifyChanges()
    }

    suspend fun removeSource(source: Source) {
        rootDownloadsDirMutex.withLock {
            val rootDirectory = rootDownloadsDir ?: return

            rootDirectory.subDirs -= source.id
        }

        notifyChanges()
    }

    fun renewCache() {
        renewCache(force = false)
    }

    fun invalidateCache() {
        renewalJob?.cancel()
        diskCacheFile.delete()
        renewCache(force = true)
    }

    /**
     * Renews the downloads cache.
     */
    private fun renewCache(force: Boolean) {
        if (renewalJob?.isActive == true) return

        renewalJob = scope.launchIO {
            if (force) _isIndexing.emit(true)

            val sources = withTimeoutOrNull(30.seconds) {
                initialized.first { it }
                extensionManager.isInitialized.first { it }
                sourceManager.isInitialized.first { it }

                getSources()
            }
                ?: emptyList()

            val sourceMap = sources.associate { provider.getSourceDirName(it).lowercase() to it.id }

            rootDownloadsDirMutex.withLock {
                val newRootDir = storageManager.getDownloadsDirectory()?.let(::RootDirectory)
                if (newRootDir == null) {
                    rootDownloadsDir = null
                    return@withLock
                }

                newRootDir.subDirs = aggregateSourceDirs(newRootDir, rootDownloadsDir, sourceMap, force)
                rootDownloadsDir = newRootDir
            }

            _isIndexing.emit(false)
        }.also {
            it.invokeOnCompletion(onCancelling = true) { exception ->
                if (exception != null && exception !is CancellationException) {
                    logcat(LogPriority.ERROR, exception) { "failed to create cache" }
                }
                notifyChanges()
            }
        }
    }

    private fun aggregateSourceDirs(
        diskRootDir: RootDirectory,
        cacheRootDir: RootDirectory?,
        sourceMap: Map<String, Long>,
        force: Boolean,
    ): Map<Long, SourceDirectory> {
        val folderChanged = force || diskRootDir.lastModified != cacheRootDir?.lastModified
        val subDirs = if (folderChanged) {
            diskRootDir.dir.listFiles().orEmpty().filter {
                it.isDirectory && !it.name.isNullOrBlank()
            }
        } else {
            cacheRootDir?.subDirs?.values?.map { it.dir }.orEmpty()
        }
        return subDirs.mapNotNull { sourceDir ->
            val sourceId = sourceMap[sourceDir.name!!.lowercase()] ?: return@mapNotNull null
            val cachedSourceDir = cacheRootDir?.subDirs?.get(sourceId)
            val diskSourceDir = SourceDirectory(sourceDir)
            diskSourceDir.subDirs = aggregateMangaDirs(diskSourceDir, cachedSourceDir, force)
            sourceId to diskSourceDir
        }
            .toMap()
    }

    private fun aggregateMangaDirs(
        diskSourceDir: SourceDirectory,
        cacheSourceDir: SourceDirectory?,
        force: Boolean,
    ): Map<String, MangaDirectory> {
        val folderChanged = force || diskSourceDir.lastModified != cacheSourceDir?.lastModified
        val subDirs = if (folderChanged) {
            diskSourceDir.dir.listFiles().orEmpty().filter {
                it.isDirectory && !it.name.isNullOrBlank()
            }
        } else {
            cacheSourceDir?.subDirs?.values?.map { it.dir }.orEmpty()
        }
        return subDirs.mapNotNull { mangaDir ->
            val name = mangaDir.name ?: return@mapNotNull null
            val cachedMangaDir = cacheSourceDir?.subDirs?.get(name)
            val diskMangaDir = MangaDirectory(mangaDir)
            if (!force && cachedMangaDir != null && cachedMangaDir.lastModified == diskMangaDir.lastModified) {
                name to cachedMangaDir
            } else {
                diskMangaDir.subDirs = mangaDir.listFiles().orEmpty()
                    .mapNotNull { it.toChapterDirectory() }
                    .toMap()
                name to diskMangaDir
            }
        }
            .toMap()
    }

    private fun UniFile.toChapterDirectory(): Pair<String, ChapterDirectory>? {
        return when {
            isFile && extension == "cbz" -> {
                nameWithoutExtension!! to ChapterDirectory(
                    size = length(),
                )
            }

            isDirectory && !name!!.endsWith(Downloader.TMP_DIR_SUFFIX) -> {
                name!! to ChapterDirectory(
                    size = listFiles()?.sumOf { if (it.isFile) it.length() else 0 } ?: 0,
                )
            }

            else -> null
        }
    }

    private fun getSources(): List<Source> {
        return sourceManager.getOnlineSources() + sourceManager.getStubSources()
    }

    private fun notifyChanges() {
        scope.launchNonCancellable {
            _changes.send(Unit)
        }
        updateDiskCache()
    }

    private var updateDiskCacheJob: Job? = null
    private fun updateDiskCache() {
        updateDiskCacheJob?.cancel()
        updateDiskCacheJob = scope.launchIO {
            delay(1000)
            ensureActive()
            val rootDownloadsDir = rootDownloadsDir ?: return@launchIO
            val bytes = ProtoBuf.encodeToByteArray(rootDownloadsDir)
            ensureActive()
            try {
                diskCacheFile.writeBytes(bytes)
            } catch (e: Throwable) {
                logcat(
                    priority = LogPriority.ERROR,
                    throwable = e,
                    message = { "Failed to write disk cache file" },
                )
            }
        }
    }
}

/**
 * Class to store the files under the root downloads directory.
 */
@Serializable
private abstract class NonChapterDirectories<K, T> {
    abstract val dir: SerializableUniFile

    private var _lastModified: Long? = null
        private set

    var lastModified: Long
        get() = _lastModified ?: dir.lastModified().also { _lastModified = it }
        private set(value) {
            _lastModified = value
        }

    var subDirs: Map<K, T> = mapOf()
        set(value) {
            lastModified = dir.lastModified()
            field = value
        }

    override fun toString(): String {
        return "RootDirectory(" +
            "dir=${dir.displayablePath}," +
            "lastModified=$lastModified," +
            "subDirs=$subDirs)"
    }
}

@Serializable
private class RootDirectory(
    override val dir: SerializableUniFile,
) : NonChapterDirectories<Long, SourceDirectory>()

/**
 * Class to store the files under a source directory.
 */
@Serializable
private class SourceDirectory(
    override val dir: SerializableUniFile,
) : NonChapterDirectories<String, MangaDirectory>()

/**
 * Class to store the files under a manga directory.
 */
@Serializable
private class MangaDirectory(
    override val dir: SerializableUniFile,
) : NonChapterDirectories<String, ChapterDirectory>()

@Serializable
private data class ChapterDirectory(
    @Suppress("UNUSED")
    val size: Long,
)

private typealias SerializableUniFile =
    @Serializable(with = UniFileAsStringSerializer::class)
    UniFile

private object UniFileAsStringSerializer : KSerializer<UniFile> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UniFile", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UniFile) {
        return encoder.encodeString(value.uri.toString())
    }

    override fun deserialize(decoder: Decoder): UniFile {
        return UniFile.fromUri(Injekt.get<Application>(), decoder.decodeString().toUri())!!
    }
}
