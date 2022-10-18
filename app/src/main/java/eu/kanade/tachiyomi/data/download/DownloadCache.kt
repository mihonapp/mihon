package eu.kanade.tachiyomi.data.download

import android.content.Context
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.domain.download.service.DownloadPreferences
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.source.SourceManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

/**
 * Cache where we dump the downloads directory from the filesystem. This class is needed because
 * directory checking is expensive and it slows down the app. The cache is invalidated by the time
 * defined in [renewInterval] as we don't have any control over the filesystem and the user can
 * delete the folders at any time without the app noticing.
 *
 * @param context the application context.
 * @param provider the downloads directories provider.
 * @param sourceManager the source manager.
 * @param downloadPreferences the preferences of the app.
 */
class DownloadCache(
    private val context: Context,
    private val provider: DownloadProvider,
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
) {

    private val scope = MainScope()

    /**
     * The interval after which this cache should be invalidated. 1 hour shouldn't cause major
     * issues, as the cache is only used for UI feedback.
     */
    private val renewInterval = TimeUnit.HOURS.toMillis(1)

    /**
     * The last time the cache was refreshed.
     */
    private var lastRenew = 0L

    private var rootDownloadsDir = RootDirectory(getDirectoryFromPreference())

    init {
        downloadPreferences.downloadsDirectory().changes()
            .onEach {
                lastRenew = 0L // invalidate cache
                rootDownloadsDir = RootDirectory(getDirectoryFromPreference())
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

        val sourceDir = rootDownloadsDir.files[sourceId]
        if (sourceDir != null) {
            val mangaDir = sourceDir.files[provider.getMangaDirName(mangaTitle)]
            if (mangaDir != null) {
                return provider.getValidChapterDirNames(chapterName, chapterScanlator).any { it in mangaDir.files }
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

        val sourceDir = rootDownloadsDir.files[manga.source]
        if (sourceDir != null) {
            val mangaDir = sourceDir.files[provider.getMangaDirName(manga.title)]
            if (mangaDir != null) {
                return mangaDir.files.size
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
        var sourceDir = rootDownloadsDir.files[manga.source]
        if (sourceDir == null) {
            val source = sourceManager.get(manga.source) ?: return
            val sourceUniFile = provider.findSourceDir(source) ?: return
            sourceDir = SourceDirectory(sourceUniFile)
            rootDownloadsDir.files += manga.source to sourceDir
        }

        // Retrieve the cached manga directory or cache a new one
        val mangaDirName = provider.getMangaDirName(manga.title)
        var mangaDir = sourceDir.files[mangaDirName]
        if (mangaDir == null) {
            mangaDir = MangaDirectory(mangaUniFile)
            sourceDir.files += mangaDirName to mangaDir
        }

        // Save the chapter directory
        mangaDir.files += chapterDirName
    }

    /**
     * Removes a chapter that has been deleted from this cache.
     *
     * @param chapter the chapter to remove.
     * @param manga the manga of the chapter.
     */
    @Synchronized
    fun removeChapter(chapter: Chapter, manga: Manga) {
        val sourceDir = rootDownloadsDir.files[manga.source] ?: return
        val mangaDir = sourceDir.files[provider.getMangaDirName(manga.title)] ?: return
        provider.getValidChapterDirNames(chapter.name, chapter.scanlator).forEach {
            if (it in mangaDir.files) {
                mangaDir.files -= it
            }
        }
    }

    /**
     * Removes a list of chapters that have been deleted from this cache.
     *
     * @param chapters the list of chapter to remove.
     * @param manga the manga of the chapter.
     */
    @Synchronized
    fun removeChapters(chapters: List<Chapter>, manga: Manga) {
        val sourceDir = rootDownloadsDir.files[manga.source] ?: return
        val mangaDir = sourceDir.files[provider.getMangaDirName(manga.title)] ?: return
        chapters.forEach { chapter ->
            provider.getValidChapterDirNames(chapter.name, chapter.scanlator).forEach {
                if (it in mangaDir.files) {
                    mangaDir.files -= it
                }
            }
        }
    }

    /**
     * Removes a manga that has been deleted from this cache.
     *
     * @param manga the manga to remove.
     */
    @Synchronized
    fun removeManga(manga: Manga) {
        val sourceDir = rootDownloadsDir.files[manga.source] ?: return
        val mangaDirName = provider.getMangaDirName(manga.title)
        if (mangaDirName in sourceDir.files) {
            sourceDir.files -= mangaDirName
        }
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
    @Synchronized
    private fun renewCache() {
        if (lastRenew + renewInterval >= System.currentTimeMillis()) {
            return
        }

        val sources = sourceManager.getOnlineSources() + sourceManager.getStubSources()

        // Ensure we try again later if no sources have been loaded
        if (sources.isEmpty()) {
            return
        }

        val sourceDirs = rootDownloadsDir.dir.listFiles()
            .orEmpty()
            .associate { it.name to SourceDirectory(it) }
            .mapNotNullKeys { entry ->
                sources.find {
                    provider.getSourceDirName(it).equals(entry.key, ignoreCase = true)
                }?.id
            }

        rootDownloadsDir.files = sourceDirs

        sourceDirs.values.forEach { sourceDir ->
            val mangaDirs = sourceDir.dir.listFiles()
                .orEmpty()
                .associateNotNullKeys { it.name to MangaDirectory(it) }

            sourceDir.files = mangaDirs

            mangaDirs.values.forEach { mangaDir ->
                val chapterDirs = mangaDir.dir.listFiles()
                    .orEmpty()
                    .mapNotNull { chapterDir ->
                        chapterDir.name
                            ?.replace(".cbz", "")
                            ?.takeUnless { it.endsWith(Downloader.TMP_DIR_SUFFIX) }
                    }
                    .toHashSet()

                mangaDir.files = chapterDirs
            }
        }

        lastRenew = System.currentTimeMillis()
    }

    /**
     * Returns a new map containing only the key entries of [transform] that are not null.
     */
    private inline fun <K, V, R> Map<out K, V>.mapNotNullKeys(transform: (Map.Entry<K?, V>) -> R?): Map<R, V> {
        val destination = LinkedHashMap<R, V>()
        forEach { element -> transform(element)?.let { destination[it] = element.value } }
        return destination
    }

    /**
     * Returns a map from a list containing only the key entries of [transform] that are not null.
     */
    private inline fun <T, K, V> Array<T>.associateNotNullKeys(transform: (T) -> Pair<K?, V>): Map<K, V> {
        val destination = LinkedHashMap<K, V>()
        for (element in this) {
            val (key, value) = transform(element)
            if (key != null) {
                destination[key] = value
            }
        }
        return destination
    }
}

/**
 * Class to store the files under the root downloads directory.
 */
private class RootDirectory(
    val dir: UniFile,
    var files: Map<Long, SourceDirectory> = hashMapOf(),
)

/**
 * Class to store the files under a source directory.
 */
private class SourceDirectory(
    val dir: UniFile,
    var files: Map<String, MangaDirectory> = hashMapOf(),
)

/**
 * Class to store the files under a manga directory.
 */
private class MangaDirectory(
    val dir: UniFile,
    var files: Set<String> = hashSetOf(),
)
