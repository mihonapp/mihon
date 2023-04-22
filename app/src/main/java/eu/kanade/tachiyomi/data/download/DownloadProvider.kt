package eu.kanade.tachiyomi.data.download

import android.content.Context
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * This class is used to provide the directories where the downloads should be saved.
 * It uses the following path scheme: /<root downloads dir>/<source name>/<manga>/<chapter>
 *
 * @param context the application context.
 */
class DownloadProvider(
    private val context: Context,
    downloadPreferences: DownloadPreferences = Injekt.get(),
) {

    private val scope = MainScope()

    /**
     * The root directory for downloads.
     */
    private var downloadsDir = downloadPreferences.downloadsDirectory().get().let {
        val dir = UniFile.fromUri(context, it.toUri())
        DiskUtil.createNoMediaFile(dir, context)
        dir
    }

    init {
        downloadPreferences.downloadsDirectory().changes()
            .onEach { downloadsDir = UniFile.fromUri(context, it.toUri()) }
            .launchIn(scope)
    }

    /**
     * Returns the download directory for a manga. For internal use only.
     *
     * @param mangaTitle the title of the manga to query.
     * @param source the source of the manga.
     */
    internal fun getMangaDir(mangaTitle: String, source: Source): UniFile {
        try {
            return downloadsDir
                .createDirectory(getSourceDirName(source))
                .createDirectory(getMangaDirName(mangaTitle))
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Invalid download directory" }
            throw Exception(context.getString(R.string.invalid_location, downloadsDir))
        }
    }

    /**
     * Returns the download directory for a source if it exists.
     *
     * @param source the source to query.
     */
    fun findSourceDir(source: Source): UniFile? {
        return downloadsDir.findFile(getSourceDirName(source), true)
    }

    /**
     * Returns the download directory for a manga if it exists.
     *
     * @param mangaTitle the title of the manga to query.
     * @param source the source of the manga.
     */
    fun findMangaDir(mangaTitle: String, source: Source): UniFile? {
        val sourceDir = findSourceDir(source)
        return sourceDir?.findFile(getMangaDirName(mangaTitle), true)
    }

    /**
     * Returns the download directory for a chapter if it exists.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     * @param mangaTitle the title of the manga to query.
     * @param source the source of the chapter.
     */
    fun findChapterDir(chapterName: String, chapterScanlator: String?, mangaTitle: String, source: Source): UniFile? {
        val mangaDir = findMangaDir(mangaTitle, source)
        return getValidChapterDirNames(chapterName, chapterScanlator).asSequence()
            .mapNotNull { mangaDir?.findFile(it, true) }
            .firstOrNull()
    }

    /**
     * Returns a list of downloaded directories for the chapters that exist.
     *
     * @param chapters the chapters to query.
     * @param manga the manga of the chapter.
     * @param source the source of the chapter.
     */
    fun findChapterDirs(chapters: List<Chapter>, manga: Manga, source: Source): Pair<UniFile?, List<UniFile>> {
        val mangaDir = findMangaDir(manga.title, source) ?: return null to emptyList()
        return mangaDir to chapters.mapNotNull { chapter ->
            getValidChapterDirNames(chapter.name, chapter.scanlator).asSequence()
                .mapNotNull { mangaDir.findFile(it) }
                .firstOrNull()
        }
    }

    /**
     * Returns the download directory name for a source.
     *
     * @param source the source to query.
     */
    fun getSourceDirName(source: Source): String {
        return DiskUtil.buildValidFilename(source.toString())
    }

    /**
     * Returns the download directory name for a manga.
     *
     * @param mangaTitle the title of the manga to query.
     */
    fun getMangaDirName(mangaTitle: String): String {
        return DiskUtil.buildValidFilename(mangaTitle)
    }

    /**
     * Returns the chapter directory name for a chapter.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     */
    fun getChapterDirName(chapterName: String, chapterScanlator: String?): String {
        val newChapterName = sanitizeChapterName(chapterName)
        return DiskUtil.buildValidFilename(
            when {
                chapterScanlator.isNullOrBlank().not() -> "${chapterScanlator}_$newChapterName"
                else -> newChapterName
            },
        )
    }

    /**
     * Return the new name for the chapter (in case it's empty or blank)
     *
     * @param chapterName the name of the chapter
     */
    private fun sanitizeChapterName(chapterName: String): String {
        return chapterName.ifBlank {
            "Chapter"
        }
    }

    fun isChapterDirNameChanged(oldChapter: Chapter, newChapter: Chapter): Boolean {
        return oldChapter.name != newChapter.name ||
            oldChapter.scanlator?.takeIf { it.isNotBlank() } != newChapter.scanlator?.takeIf { it.isNotBlank() }
    }

    /**
     * Returns valid downloaded chapter directory names.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     */
    fun getValidChapterDirNames(chapterName: String, chapterScanlator: String?): List<String> {
        val chapterDirName = getChapterDirName(chapterName, chapterScanlator)
        return buildList(4) {
            // Folder of images
            add(chapterDirName)

            // Archived chapters
            add("$chapterDirName.cbz")

            if (chapterScanlator.isNullOrBlank()) {
                // Previously null scanlator fields were converted to "" due to a bug
                add("_$chapterDirName")
                add("_$chapterDirName.cbz")
            } else {
                // Legacy chapter directory name used in v0.9.2 and before
                add(DiskUtil.buildValidFilename(chapterName))
            }
        }
    }
}
