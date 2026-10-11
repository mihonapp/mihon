package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.lang.Hash.md5
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import java.io.IOException

/**
 * This class is used to provide the directories where the downloads should be saved.
 * It uses the following path scheme: /<root downloads dir>/<source name>/<manga>/<chapter>
 *
 * @param context the application context.
 */
@Inject
@SingleIn(AppScope::class)
class DownloadProvider(
    private val context: Context,
    private val storageManager: StorageManager,
    private val libraryPreferences: LibraryPreferences,
) {

    private val downloadsDir: UniFile?
        get() = storageManager.getDownloadsDirectory()

    /**
     * Returns the download directory for a manga. For internal use only.
     *
     * @param mangaTitle the title of the manga to query.
     * @param source the source of the manga.
     */
    internal suspend fun getMangaDir(mangaTitle: String, source: Source): Result<UniFile> =
        withContext(Dispatchers.IO) { createMangaDir(mangaTitle, source) }

    private fun createMangaDir(mangaTitle: String, source: Source): Result<UniFile> {
        val downloadsDir = downloadsDir
        if (downloadsDir == null) {
            logcat(LogPriority.ERROR) { "Failed to create download directory" }
            return Result.failure(
                IOException(context.stringResource(MR.strings.storage_failed_to_create_download_directory)),
            )
        }

        val sourceDirName = getSourceDirName(source)
        val sourceDir = downloadsDir.createDirectory(sourceDirName)
        if (sourceDir == null) {
            val displayablePath = downloadsDir.displayablePath + "/$sourceDirName"
            logcat(LogPriority.ERROR) { "Failed to create source download directory: $displayablePath" }
            return Result.failure(
                IOException(context.stringResource(MR.strings.storage_failed_to_create_directory, displayablePath)),
            )
        }

        val mangaDirName = getMangaDirName(mangaTitle)
        val mangaDir = sourceDir.createDirectory(mangaDirName)
        if (mangaDir == null) {
            val displayablePath = sourceDir.displayablePath + "/$mangaDirName"
            logcat(LogPriority.ERROR) { "Failed to create manga download directory: $displayablePath" }
            return Result.failure(
                IOException(context.stringResource(MR.strings.storage_failed_to_create_directory, displayablePath)),
            )
        }

        return Result.success(mangaDir)
    }

    /**
     * Returns the download directory for a source if it exists.
     *
     * @param source the source to query.
     */
    suspend fun findSourceDir(source: Source): UniFile? = withContext(Dispatchers.IO) {
        downloadsDir?.findFile(getSourceDirName(source))
    }

    /**
     * Returns the download directory for a manga if it exists.
     *
     * @param mangaTitle the title of the manga to query.
     * @param source the source of the manga.
     */
    suspend fun findMangaDir(mangaTitle: String, source: Source): UniFile? = withContext(Dispatchers.IO) {
        findSourceDir(source)?.findFile(getMangaDirName(mangaTitle))
    }

    /**
     * Returns the download directory for a chapter if it exists.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     * @param mangaTitle the title of the manga to query.
     * @param source the source of the chapter.
     */
    suspend fun findChapterDir(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        mangaTitle: String,
        source: Source,
    ): UniFile? = withContext(Dispatchers.IO) {
        val mangaDir = findMangaDir(mangaTitle, source)
        getValidChapterDirNames(chapterName, chapterScanlator, chapterUrl).asSequence()
            .mapNotNull { mangaDir?.findFile(it) }
            .firstOrNull()
    }

    /**
     * Returns a list of downloaded directories for the chapters that exist.
     *
     * @param chapters the chapters to query.
     * @param manga the manga of the chapter.
     * @param source the source of the chapter.
     */
    suspend fun findChapterDirs(
        chapters: List<Chapter>,
        manga: Manga,
        source: Source,
    ): Pair<UniFile?, List<UniFile>> = withContext(Dispatchers.IO) {
        val mangaDir = findMangaDir(manga.title, source) ?: return@withContext null to emptyList()
        mangaDir to chapters.mapNotNull { chapter ->
            getValidChapterDirNames(chapter.name, chapter.scanlator, chapter.url).asSequence()
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
        return DiskUtil.buildValidFilename(
            source.toString(),
            disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames.get(),
        )
    }

    /**
     * Returns the download directory name for a manga.
     *
     * @param mangaTitle the title of the manga to query.
     */
    fun getMangaDirName(mangaTitle: String): String {
        return DiskUtil.buildValidFilename(
            mangaTitle,
            disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames.get(),
        )
    }

    /**
     * Returns the chapter directory name for a chapter.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query.
     * @param chapterUrl url of the chapter to query.
     */
    fun getChapterDirName(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        disallowNonAsciiFilenames: Boolean = libraryPreferences.disallowNonAsciiFilenames.get(),
        enableChapterNameHash: Boolean = libraryPreferences.enableChapterNameHash.get(),
    ): String {
        return ChapterDirNameParts(chapterName, chapterScanlator, chapterUrl)
            .dirName(disallowNonAsciiFilenames, enableChapterNameHash, sanitizeScanlator = true)
    }

    /**
     * Returns list of names that might have been previously used as
     * the directory name for a chapter.
     * Add to this list if naming pattern ever changes.
     */
    private fun getLegacyChapterDirNames(parts: ChapterDirNameParts): Set<String> {
        val chapterNameV1 = DiskUtil.buildValidFilename(
            when {
                !parts.chapterScanlator.isNullOrBlank() -> "${parts.chapterScanlator}_${parts.chapterName}"
                else -> parts.chapterName
            },
        )

        // Generate all possible legacy directory name variations by combining
        // different states of non-ASCII filenames and chapter name hash settings.
        // This ensures that chapters downloaded under any past configuration
        // combination can still be successfully found.
        val booleanPairPermutation = listOf(false to false, false to true, true to false, true to true)
        val othersChapterDirNames = booleanPairPermutation
            .map { (disallowNonAsciiFilenames, enableChapterNameHash) ->
                parts.dirName(disallowNonAsciiFilenames, enableChapterNameHash, sanitizeScanlator = true)
            }

        // Scanlators weren't sanitized for a while. One with a "/" nested its folder, so it was never found anyway.
        val scanlator = parts.chapterScanlator
        val unsanitizedScanlatorChapterDirNames = if (scanlator != null && '/' !in scanlator) {
            booleanPairPermutation.map { (disallowNonAsciiFilenames, enableChapterNameHash) ->
                parts.dirName(disallowNonAsciiFilenames, enableChapterNameHash, sanitizeScanlator = false)
            }
        } else {
            emptyList()
        }

        return buildSet {
            // Chapter name without hash (unable to handle duplicate
            // chapter names)
            add(chapterNameV1)
            addAll(othersChapterDirNames)
            addAll(unsanitizedScanlatorChapterDirNames)
        }
    }

    fun isChapterDirNameChanged(oldChapter: Chapter, newChapter: Chapter): Boolean {
        return getChapterDirName(oldChapter.name, oldChapter.scanlator, oldChapter.url) !=
            getChapterDirName(newChapter.name, newChapter.scanlator, newChapter.url)
    }

    /**
     * Returns valid downloaded chapter directory names.
     *
     * @param chapter the domain chapter object.
     */
    fun getValidChapterDirNames(chapterName: String, chapterScanlator: String?, chapterUrl: String): List<String> {
        val parts = ChapterDirNameParts(chapterName, chapterScanlator, chapterUrl)
        val chapterDirName = parts.dirName(
            disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames.get(),
            enableHash = libraryPreferences.enableChapterNameHash.get(),
            sanitizeScanlator = true,
        )
        val legacyChapterDirNames = getLegacyChapterDirNames(parts)

        return buildList {
            // Folder of images
            add(chapterDirName)
            // Archived chapters
            add("$chapterDirName.cbz")

            // any legacy names
            legacyChapterDirNames.forEach {
                add(it)
                add("$it.cbz")
            }
        }
    }
}

/**
 * The parts of a chapter's directory name, each computed once however many name variations are built from them.
 */
private class ChapterDirNameParts(chapterName: String, val chapterScanlator: String?, private val chapterUrl: String) {

    /** The chapter name, or a placeholder when it's blank. */
    val chapterName = chapterName.ifBlank { "Chapter" }

    private val hashSuffix by lazy(LazyThreadSafetyMode.NONE) { "_${md5(chapterUrl).take(6)}" }

    private val sanitizedScanlator by lazy(LazyThreadSafetyMode.NONE) {
        chapterScanlator?.let { DiskUtil.buildValidFilename(it) }
    }

    private val validChapterNames = arrayOfNulls<String>(2)

    fun dirName(disallowNonAscii: Boolean, enableHash: Boolean, sanitizeScanlator: Boolean): String {
        return buildString {
            if (!chapterScanlator.isNullOrBlank()) {
                // A "/" in the scanlator would otherwise nest the chapter in a folder of its own
                append(if (sanitizeScanlator) sanitizedScanlator else chapterScanlator)
                append("_")
            }
            append(validChapterName(disallowNonAscii))
            if (enableHash) append(hashSuffix)
        }
    }

    private fun validChapterName(disallowNonAscii: Boolean): String {
        val index = if (disallowNonAscii) 1 else 0
        return validChapterNames[index]
            // Subtract 7 bytes for hash and underscore, 4 bytes for .cbz
            ?: DiskUtil.buildValidFilename(chapterName, DiskUtil.MAX_FILE_NAME_BYTES - 11, disallowNonAscii)
                .also { validChapterNames[index] = it }
    }
}
