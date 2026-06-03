package eu.kanade.tachiyomi.data.export

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import logcat.LogPriority
import mihon.core.archive.ZipWriter
import mihon.core.archive.archiveReader
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class EReaderExporter(
    private val context: Context,
    private val downloadProvider: DownloadProvider = Injekt.get(),
    private val storageManager: StorageManager = Injekt.get(),
) {

    sealed interface Result {
        data class Success(val file: UniFile) : Result
        data class Error(val cause: Throwable) : Result
    }

    suspend fun export(
        manga: Manga,
        source: Source,
        chapters: List<Chapter>,
    ): Result {
        return try {
            val destDir = storageManager.getExportsDirectory()
                ?: return Result.Error(IllegalStateException("Exports directory unavailable"))
            val outputFile = resolveOutputFile(manga.title, destDir)
            ZipWriter(context, outputFile).use { writer ->
                val chapterOrdinalWidth = paddingWidth(chapters.size)

                chapters.forEachIndexed { chapterIndex, chapter ->
                    val chapterDir = downloadProvider.findChapterDir(
                        chapter.name,
                        chapter.scanlator,
                        chapter.url,
                        manga.title,
                        source,
                    ) ?: run {
                        logcat(LogPriority.WARN) { "Chapter dir not found for: ${chapter.name}" }
                        return@forEachIndexed
                    }

                    val chapterOrdinal = paddedIndex(chapterIndex, chapterOrdinalWidth)

                    if (chapterDir.isDirectory) {
                        writeChapter(writer, chapterDir, chapterOrdinal)
                    } else {
                        extractArchiveAndWriteChapter(writer, chapterDir, chapterOrdinal)
                    }
                }
            }
            Result.Success(outputFile)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to export manga: ${manga.title}" }
            Result.Error(e)
        }
    }

    private fun writeChapter(writer: ZipWriter, dir: UniFile, chapterOrdinal: String) {
        val pages = dir.listFiles()
            ?.filter { !it.isDirectory && ImageUtil.isImage(it.name) { it.openInputStream() } }
            ?.sortedWith { f1, f2 -> f1.name.orEmpty().compareToCaseInsensitiveNaturalOrder(f2.name.orEmpty()) }
            ?: return

        val pageOrdinalWidth = paddingWidth(pages.size)
        pages.forEachIndexed { pageIndex, file ->
            val ext = file.name?.substringAfterLast('.', "") ?: ""
            val pageOrdinal = paddedIndex(pageIndex, pageOrdinalWidth)
            val entryName = buildEntryName(chapterOrdinal, pageOrdinal, ext)
            writer.write(file, entryName)
        }
    }

    private fun extractArchiveAndWriteChapter(writer: ZipWriter, archiveFile: UniFile, chapterOrdinal: String) {
        val tempDir = File(context.cacheDir, "export_${System.currentTimeMillis()}").also { it.mkdirs() }
        try {
            archiveFile.archiveReader(context).use { reader ->
                reader.useEntries { entries ->
                    entries
                        .filter { it.isFile && ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! } }
                        .forEach { entry ->
                            val outFile = File(tempDir, entry.name)
                            reader.getInputStream(entry.name)?.use { input ->
                                outFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                }
            }

            val tempUniFile = UniFile.fromFile(tempDir)!!
            writeChapter(writer, tempUniFile, chapterOrdinal)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun buildEntryName(chapterOrdinal: String, pageOrdinal: String, ext: String): String {
        return "${chapterOrdinal}_${pageOrdinal}${".$ext".takeIf { ext.isNotEmpty() } ?: ""}"
    }

    private fun paddingWidth(count: Int) = count.toString().length.coerceAtLeast(4)

    private fun paddedIndex(index: Int, width: Int) = (index + 1).toString().padStart(width, '0')

    private fun resolveOutputFile(mangaTitle: String, destDir: UniFile): UniFile {
        val safeName = mangaTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(200)

        if (destDir.findFile("$safeName.cbz") == null) {
            return destDir.createFile("$safeName.cbz")!!
        }

        var index = 1
        while (true) {
            val name = "$safeName ($index).cbz"
            if (destDir.findFile(name) == null) {
                return destDir.createFile(name)!!
            }
            index++
        }
    }
}
