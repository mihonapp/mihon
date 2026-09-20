package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.translation.engine.ImageRedrawProcessor
import eu.kanade.tachiyomi.data.translation.engine.TranslationEngineManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.translation.service.TranslationPreferences
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

@Inject
@SingleIn(AppScope::class)
class TranslationManager(
    private val context: Context,
    private val storageManager: StorageManager,
    private val downloadProvider: DownloadProvider,
    private val preferences: TranslationPreferences,
    private val networkHelper: NetworkHelper,
    private val json: Json,
) {

    private val engineManager = TranslationEngineManager(preferences, networkHelper, json)
    private val redrawProcessor = ImageRedrawProcessor(engineManager, preferences)

    private val _runningJobs = MutableStateFlow<Set<Long>>(emptySet())
    val runningJobs = _runningJobs.asStateFlow()

    fun getTranslationDir(): UniFile? {
        val baseDir = storageManager.getDownloadsDirectory() ?: return null
        return baseDir.parent?.createDirectory("translations") ?: baseDir.createDirectory("translations")
    }

    fun getTranslationChapterDir(source: Source, manga: Manga, chapter: Chapter): UniFile? {
        val translationsDir = getTranslationDir() ?: return null
        val sourceDirName = downloadProvider.getSourceDirName(source)
        val sourceDir = translationsDir.createDirectory(sourceDirName) ?: return null
        val mangaDirName = downloadProvider.getMangaDirName(manga.title)
        val mangaDir = sourceDir.createDirectory(mangaDirName) ?: return null
        val chapterDirName = downloadProvider.getChapterDirName(chapter.name, chapter.scanlator, chapter.url)
        return mangaDir.createDirectory(chapterDirName)
    }

    fun findTranslationChapterDir(source: Source, manga: Manga, chapter: Chapter): UniFile? {
        val translationsDir = getTranslationDir() ?: return null
        val sourceDir = translationsDir.findFile(downloadProvider.getSourceDirName(source)) ?: return null
        val mangaDir = sourceDir.findFile(downloadProvider.getMangaDirName(manga.title)) ?: return null
        return downloadProvider.getValidChapterDirNames(chapter.name, chapter.scanlator, chapter.url).asSequence()
            .mapNotNull { mangaDir.findFile(it) }
            .firstOrNull()
    }

    fun isChapterTranslated(source: Source, manga: Manga, chapter: Chapter): Boolean {
        val dir = findTranslationChapterDir(source, manga, chapter) ?: return false
        val files = dir.listFiles() ?: return false
        return files.isNotEmpty()
    }

    suspend fun translateChapter(source: Source, manga: Manga, chapter: Chapter): Boolean = withContext(Dispatchers.IO) {
        val chapterId = chapter.id
        _runningJobs.value = _runningJobs.value + chapterId
        try {
            val downloadDir = downloadProvider.findChapterDir(chapter.name, chapter.scanlator, chapter.url, manga.title, source)
                ?: return@withContext false

            val imageFiles = downloadDir.listFiles()?.filter { file ->
                file.isFile && (file.name?.endsWith(".jpg", true) == true || file.name?.endsWith(".png", true) == true || file.name?.endsWith(".webp", true) == true)
            }?.sortedBy { it.name } ?: return@withContext false

            if (imageFiles.isEmpty()) return@withContext false

            val targetChapterDir = getTranslationChapterDir(source, manga, chapter) ?: return@withContext false

            for (imgFile in imageFiles) {
                val inputStream = imgFile.openInputStream()
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (originalBitmap != null) {
                    var redrawnBitmap: Bitmap? = null
                    try {
                        redrawnBitmap = redrawProcessor.processAndRedraw(originalBitmap)
                        val targetFile = targetChapterDir.createFile(imgFile.name ?: "page.jpg") ?: continue
                        val outputStream = targetFile.openOutputStream()
                        redrawnBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                        outputStream.flush()
                        outputStream.close()
                    } finally {
                        if (redrawnBitmap != null && !redrawnBitmap.isRecycled) {
                            redrawnBitmap.recycle()
                        }
                        if (!originalBitmap.isRecycled) {
                            originalBitmap.recycle()
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            _runningJobs.value = _runningJobs.value - chapterId
        }
    }
}
