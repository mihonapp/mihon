package eu.kanade.tachiyomi.data.download

import android.content.Context
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.domain.download.service.DownloadPreferences
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchNonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeout
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Cache where we dump the downloads directory from the filesystem. This class is needed because
 * directory checking is expensive and it slows down the app. The cache is invalidated by the time
 * defined in [renewInterval] as we don't have any control over the filesystem and the user can
 * delete the folders at any time without the app noticing.
 */
class DownloadCache(
    private val context: Context,
    private val provider: DownloadProvider = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
) {

    private val _changes: Channel<Unit> = Channel(Channel.UNLIMITED)
    val changes = _changes.receiveAsFlow().onStart { emit(Unit) }

    private val scope = CoroutineScope(Dispatchers.IO)

    private val notifier by lazy { DownloadNotifier(context) }

    /**
     * The interval after which this cache should be invalidated. 1 hour shouldn't cause major
     * issues, as the cache is only used for UI feedback.
     */
    private val renewInterval = TimeUnit.HOURS.toMillis(1)

    /**
     * The last time the cache was refreshed.
     */
    private var lastRenew = 0L
    private var renewalJob: Job? = null

    private var rootDownloadsDir = RootDirectory(getDirectoryFromPreference())

    init {
        downloadPreferences.downloadsDirectory().changes()
            .onEach {
                rootDownloadsDir = RootDirectory(getDirectoryFromPreference())

                // Invalidate cache
                lastRenew = 0L
            }
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

        renewCache()

        val sourceDir = rootDownloadsDir.sourceDirs[sourceId]
        if (sourceDir != null) {
            val mangaDir = sourceDir.mangaDirs[provider.getMangaDirName(mangaTitle)]
            if (mangaDir != null) {
                return provider.getValidChapterDirNames(chapterName, chapterScanlator).any { it in mangaDir.chapterDirs }
            }
        }
        return false
    }

    /**
     * Returns the amount of downloaded chapters for a manga.
     *
     * @param manga the manga to check.
     */
    fun getDownloadCount(manga: Manga): Int {
        renewCache()

        val sourceDir = rootDownloadsDir.sourceDirs[manga.source]
        if (sourceDir != null) {
            val mangaDir = sourceDir.mangaDirs[provider.getMangaDirName(manga.title)]
            if (mangaDir != null) {
                return mangaDir.chapterDirs.size
            }
        }
        return 0
    }

    /**
     * Adds a chapter that has just been download to this cache.
     *
     * @param chapterDirName the downloaded chapter's directory name.
     * @param mangaUniFile the directory of the manga.
     * @param manga the manga of the chapter.
     */
    @Synchronized
    fun addChapter(chapterDirName: String, mangaUniFile: UniFile, manga: Manga) {
        // Retrieve the cached source directory or cache a new one
        var sourceDir = rootDownloadsDir.sourceDirs[manga.source]
        if (sourceDir == null) {
            val source = sourceManager.get(manga.source) ?: return
            val sourceUniFile = provider.findSourceDir(source) ?: return
            sourceDir = SourceDirectory(sourceUniFile)
            rootDownloadsDir.sourceDirs += manga.source to sourceDir
        }

        // Retrieve the cached manga directory or cache a new one
        val mangaDirName = provider.getMangaDirName(manga.title)
        var mangaDir = sourceDir.mangaDirs[mangaDirName]
        if (mangaDir == null) {
            mangaDir = MangaDirectory(mangaUniFile)
            sourceDir.mangaDirs += mangaDirName to mangaDir
        }

        // Save the chapter directory
        mangaDir.chapterDirs += chapterDirName

        notifyChanges()
    }

    /**
     * Removes a chapter that has been deleted from this cache.
     *
     * @param chapter the chapter to remove.
     * @param manga the manga of the chapter.
     */
    @Synchronized
    fun removeChapter(chapter: Chapter, manga: Manga) {
        val sourceDir = rootDownloadsDir.sourceDirs[manga.source] ?: return
        val mangaDir = sourceDir.mangaDirs[provider.getMangaDirName(manga.title)] ?: return
        provider.getValidChapterDirNames(chapter.name, chapter.scanlator).forEach {
            if (it in mangaDir.chapterDirs) {
                mangaDir.chapterDirs -= it
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
    @Synchronized
    fun removeChapters(chapters: List<Chapter>, manga: Manga) {
        val sourceDir = rootDownloadsDir.sourceDirs[manga.source] ?: return
        val mangaDir = sourceDir.mangaDirs[provider.getMangaDirName(manga.title)] ?: return
        chapters.forEach { chapter ->
            provider.getValidChapterDirNames(chapter.name, chapter.scanlator).forEach {
                if (it in mangaDir.chapterDirs) {
                    mangaDir.chapterDirs -= it
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
    @Synchronized
    fun removeManga(manga: Manga) {
        val sourceDir = rootDownloadsDir.sourceDirs[manga.source] ?: return
        val mangaDirName = provider.getMangaDirName(manga.title)
        if (sourceDir.mangaDirs.containsKey(mangaDirName)) {
            sourceDir.mangaDirs -= mangaDirName
        }

        notifyChanges()
    }

    @Synchronized
    fun removeSourceIfEmpty(source: Source) {
        val sourceDir = provider.findSourceDir(source)
        if (sourceDir?.listFiles()?.isEmpty() == true) {
            sourceDir.delete()
            rootDownloadsDir.sourceDirs -= source.id
        }

        notifyChanges()
    }

    /**
     * Returns the downloads directory from the user's preferences.
     */
    private fun getDirectoryFromPreference(): UniFile {
        val dir = downloadPreferences.downloadsDirectory().get()
        return UniFile.fromUri(context, dir.toUri())
    }

    /**
     * Renews the downloads cache.
     */
    private fun renewCache() {
        // Avoid renewing cache if in the process nor too often
        if (lastRenew + renewInterval >= System.currentTimeMillis() || renewalJob?.isActive == true) {
            return
        }

        renewalJob = scope.launchIO {
            try {
                notifier.onCacheProgress()

                var sources = getSources()

                // Try to wait until extensions and sources have loaded
                withTimeout(30.seconds) {
                    while (!extensionManager.isInitialized) {
                        delay(2.seconds)
                    }

                    while (sources.isEmpty()) {
                        delay(2.seconds)
                        sources = getSources()
                    }
                }

                val sourceDirs = rootDownloadsDir.dir.listFiles().orEmpty()
                    .associate { it.name to SourceDirectory(it) }
                    .mapNotNullKeys { entry ->
                        sources.find {
                            provider.getSourceDirName(it).equals(entry.key, ignoreCase = true)
                        }?.id
                    }

                rootDownloadsDir.sourceDirs = sourceDirs

                sourceDirs.values
                    .map { sourceDir ->
                        async {
                            val mangaDirs = sourceDir.dir.listFiles().orEmpty()
                                .filterNot { it.name.isNullOrBlank() }
                                .associate { it.name!! to MangaDirectory(it) }

                            sourceDir.mangaDirs = ConcurrentHashMap(mangaDirs)

                            mangaDirs.values.forEach { mangaDir ->
                                val chapterDirs = mangaDir.dir.listFiles().orEmpty()
                                    .mapNotNull { chapterDir ->
                                        chapterDir.name
                                            ?.replace(".cbz", "")
                                            ?.takeUnless { it.endsWith(Downloader.TMP_DIR_SUFFIX) }
                                    }
                                    .toMutableSet()

                                mangaDir.chapterDirs = chapterDirs
                            }
                        }
                    }
                    .awaitAll()

                lastRenew = System.currentTimeMillis()
                notifyChanges()
            } finally {
                notifier.dismissCacheProgress()
            }
        }
    }

    private fun getSources(): List<Source> {
        return sourceManager.getOnlineSources() + sourceManager.getStubSources()
    }

    private fun notifyChanges() {
        scope.launchNonCancellable {
            _changes.send(Unit)
        }
    }

    /**
     * Returns a new map containing only the key entries of [transform] that are not null.
     */
    private inline fun <K, V, R> Map<out K, V>.mapNotNullKeys(transform: (Map.Entry<K?, V>) -> R?): ConcurrentHashMap<R, V> {
        val mutableMap = ConcurrentHashMap<R, V>()
        forEach { element -> transform(element)?.let { mutableMap[it] = element.value } }
        return mutableMap
    }
}

/**
 * Class to store the files under the root downloads directory.
 */
private class RootDirectory(
    val dir: UniFile,
    var sourceDirs: ConcurrentHashMap<Long, SourceDirectory> = ConcurrentHashMap(),
)

/**
 * Class to store the files under a source directory.
 */
private class SourceDirectory(
    val dir: UniFile,
    var mangaDirs: ConcurrentHashMap<String, MangaDirectory> = ConcurrentHashMap(),
)

/**
 * Class to store the files under a manga directory.
 */
private class MangaDirectory(
    val dir: UniFile,
    var chapterDirs: MutableSet<String> = mutableSetOf(),
)
