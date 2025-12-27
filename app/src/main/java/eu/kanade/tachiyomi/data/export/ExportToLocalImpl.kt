package eu.kanade.tachiyomi.data.export

import android.content.Context
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.util.system.logcat
import tachiyomi.core.metadata.comicinfo.COMIC_INFO_FILE
import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.core.metadata.comicinfo.getComicInfo
import tachiyomi.domain.export.service.ExportService
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class ExportToLocalImpl(
    private val context: Context,
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadProvider: DownloadProvider = Injekt.get(),
    private val storageManager: StorageManager = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
) : ExportService {

    private val xml: XML by injectLazy()

    override suspend fun exportChapter(file: UniFile, destinationSubfolder: UniFile): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val destinationFile = destinationSubfolder.createFile(file.name)
                    ?: return@withContext Result.failure(Exception("Failed to create destination file"))

                context.contentResolver.openInputStream(file.uri)?.use { input ->
                    context.contentResolver.openOutputStream(destinationFile.uri)?.use { output ->
                        input.copyTo(output, bufferSize = DEFAULT_BUFFER_SIZE)
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to export ${file.name} to $destinationSubfolder" }
                Result.failure(e)
            }
        }
    }

    override suspend fun exportCover(manga: Manga, destinationSubfolder: UniFile): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val coverFile = coverCache.getCoverFile(manga.thumbnailUrl)
                if (coverFile != null) {
                    val destinationCover = destinationSubfolder.createFile(DEFAULT_COVER_NAME)

                    context.contentResolver.openInputStream(coverFile.toUri())?.use { input ->
                        context.contentResolver.openOutputStream(destinationCover!!.uri)?.use { output ->
                            input.copyTo(output, bufferSize = DEFAULT_BUFFER_SIZE)
                        }
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to export cover for manga ${manga.title}" }
                Result.success(Unit)
            }
        }
    }

    override suspend fun exportComicInfo(manga: Manga, destinationSubfolder: UniFile): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val comicInfo = manga.toSManga().getComicInfo()
                destinationSubfolder.createFile(COMIC_INFO_FILE)?.openOutputStream()?.use {
                    val comicInfoString = xml.encodeToString(ComicInfo.serializer(), comicInfo)
                    it.write(comicInfoString.toByteArray())
                }
                Result.success(Unit)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to export ComicInfo for manga ${manga.title}" }
                Result.success(Unit)
            }
        }
    }

    override suspend fun getItemsToExport(manga: Manga): Result<Array<UniFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val mangaSource = sourceManager.get(manga.source)
                    ?: return@withContext Result.failure(Exception("Source not found"))

                val folder = downloadProvider.findMangaDir(manga.title, mangaSource)
                    ?: return@withContext Result.failure(Exception("Manga directory not found"))

                val files = folder.listFiles()
                    ?: return@withContext Result.failure(Exception("Failed to list manga files"))

                Result.success(files)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to get export items for manga ${manga.title}" }
                Result.failure(e)
            }
        }
    }

    override suspend fun getDestinationSubfolder(manga: Manga): Result<UniFile> {
        return withContext(Dispatchers.IO) {
            try {
                val mangaSource = sourceManager.get(manga.source)
                    ?: return@withContext Result.failure(Exception("Source not found"))

                val folder = downloadProvider.findMangaDir(manga.title, mangaSource)
                    ?: return@withContext Result.failure(Exception("Manga directory not found"))

                val destinationFolder = storageManager.getLocalSourceDirectory()
                    ?: return@withContext Result.failure(Exception("Local source directory not available"))

                val destination = destinationFolder.createDirectory(folder.name)
                    ?: return@withContext Result.failure(Exception("Failed to create destination directory"))

                Result.success(destination)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to get destination subfolder for manga ${manga.title}" }
                Result.failure(e)
            }
        }
    }

    override suspend fun destinationSubfolderExists(mangaTitle: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val localSourceDir = storageManager.getLocalSourceDirectory()
                val exists = localSourceDir?.findFile(mangaTitle) != null
                Result.success(exists)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to check if destination subfolder exists for $mangaTitle" }
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteAllItemsInSubfolder(subfolder: UniFile): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                subfolder.delete()
                Result.success(Unit)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to delete subfolder $subfolder" }
                Result.failure(e)
            }
        }
    }

    companion object {
        private const val DEFAULT_COVER_NAME = "cover.jpg"
    }
}
