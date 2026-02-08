package eu.kanade.tachiyomi.data.epub

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import eu.kanade.domain.manga.model.toSManga
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import mihon.core.archive.EpubWriter
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.repository.TranslatedChapterRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import mihon.core.archive.ArchiveReader
import eu.kanade.tachiyomi.source.model.SManga

class EpubExportJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val sourceManager: SourceManager = Injekt.get()
    private val mangaRepository: MangaRepository = Injekt.get()
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get()
    private val downloadManager: DownloadManager = Injekt.get()
    private val downloadProvider: DownloadProvider = Injekt.get()
    private val networkHelper: NetworkHelper = Injekt.get()
    private val translatedChapterRepository: TranslatedChapterRepository = Injekt.get()

    private val notificationBuilder = context.notificationBuilder(Notifications.CHANNEL_EPUB_EXPORT) {
        setSmallIcon(android.R.drawable.ic_menu_save)
        setContentTitle("EPUB Export")
        setContentText("Starting...")
        setOngoing(true)
        setOnlyAlertOnce(true)
    }

    override suspend fun doWork(): Result {
        val mangaIds = inputData.getLongArray(KEY_MANGA_IDS)?.toList() ?: return Result.failure()
        val uriString = inputData.getString(KEY_OUTPUT_URI) ?: return Result.failure()
        val downloadedOnly = inputData.getBoolean(KEY_DOWNLOADED_ONLY, false)
        val preferTranslated = inputData.getBoolean(KEY_PREFER_TRANSLATED, false)
        val includeChapterCount = inputData.getBoolean(KEY_INCLUDE_CHAPTER_COUNT, false)
        val includeChapterRange = inputData.getBoolean(KEY_INCLUDE_CHAPTER_RANGE, false)
        val includeStatus = inputData.getBoolean(KEY_INCLUDE_STATUS, false)

        logcat(LogPriority.INFO) { "EPUB Export starting: ${mangaIds.size} novels, downloadedOnly=$downloadedOnly" }

        try {
            setForegroundSafely()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to set foreground service" }
        }

        return withIOContext {
            try {
                performExport(
                    mangaIds = mangaIds,
                    outputUri = Uri.parse(uriString),
                    downloadedOnly = downloadedOnly,
                    preferTranslated = preferTranslated,
                    includeChapterCount = includeChapterCount,
                    includeChapterRange = includeChapterRange,
                    includeStatus = includeStatus,
                )
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e) { "EPUB export failed" }
                    showErrorNotification(e.message ?: "Unknown error")
                    Result.failure()
                }
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_EPUB_EXPORT_PROGRESS,
            notificationBuilder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private suspend fun performExport(
        mangaIds: List<Long>,
        outputUri: Uri,
        downloadedOnly: Boolean,
        preferTranslated: Boolean,
        includeChapterCount: Boolean,
        includeChapterRange: Boolean,
        includeStatus: Boolean,
    ) {
        logcat(LogPriority.INFO) { "performExport called with ${mangaIds.size} manga IDs, outputUri=$outputUri" }
        
        val mangaList = mangaIds.mapNotNull { mangaRepository.getMangaById(it) }
        if (mangaList.isEmpty()) {
            logcat(LogPriority.ERROR) { "No manga found for IDs: $mangaIds" }
            showErrorNotification("No novels found to export")
            return
        }
        
        logcat(LogPriority.INFO) { "Found ${mangaList.size} manga to export" }

        val tempDir = File(context.cacheDir, "epub_export_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        var successCount = 0
        var skippedCount = 0
        val totalCount = mangaList.size

        try {
            for ((index, manga) in mangaList.withIndex()) {
                updateProgress(index + 1, totalCount, manga.title)

                try {
                    val source = sourceManager.get(manga.source)
                    if (source == null || !source.isNovelSource()) {
                        logcat(LogPriority.WARN) { "${manga.title}: Not a novel source" }
                        skippedCount++
                        continue
                    }

                    val chapters = getChaptersByMangaId.await(manga.id)
                        .sortedBy { it.chapterNumber }

                    if (chapters.isEmpty()) {
                        logcat(LogPriority.WARN) { "${manga.title}: No chapters found" }
                        skippedCount++
                        continue
                    }

                    val epubChapters = mutableListOf<EpubWriter.Chapter>()
                    var firstChapterNum = Double.MAX_VALUE
                    var lastChapterNum = Double.MIN_VALUE

                    // Get translated chapter IDs if preferTranslated is enabled
                    val translatedChapterIds = if (preferTranslated) {
                        translatedChapterRepository.getTranslatedChapterIds(manga.id)
                    } else {
                        emptySet()
                    }

                    for ((chapterIndex, chapter) in chapters.withIndex()) {
                        val isDownloaded = downloadManager.isChapterDownloaded(
                            chapter.name,
                            chapter.scanlator,
                            chapter.url,
                            manga.title,
                            manga.source,
                        )

                        val hasTranslation = chapter.id in translatedChapterIds
                        
                        if (chapterIndex == 0) {
                            logcat(LogPriority.DEBUG) { "${manga.title} ch ${chapter.name}: isDownloaded=$isDownloaded, hasTranslation=$hasTranslation" }
                        }

                        // Skip undownloaded chapters if downloadedOnly
                        if (downloadedOnly && !isDownloaded && !hasTranslation) {
                            if (chapterIndex < 3) {
                                logcat(LogPriority.DEBUG) { "${manga.title} ch ${chapter.name}: skipping - not downloaded and downloadedOnly=true" }
                            }
                            continue
                        }

                        // If not downloadedOnly, also try to include undownloaded chapters
                        // but only if we have some content for them

                        // Try to get content
                        var content: String? = null

                        // Try translated content first
                        if (preferTranslated && hasTranslation) {
                            try {
                                val translations = translatedChapterRepository.getAllTranslationsForChapter(chapter.id)
                                content = translations.firstOrNull()?.translatedContent
                            } catch (e: Exception) {
                                logcat(LogPriority.WARN, e) { "Failed to get translation for chapter: ${chapter.name}" }
                            }
                        }

                        // Fall back to downloaded content
                        if (content == null && isDownloaded) {
                            try {
                                // First try to find the chapter directory (uncompressed or cbz)
                                val chapterDirOrCbz = downloadProvider.findChapterDir(
                                    chapter.name,
                                    chapter.scanlator,
                                    chapter.url,
                                    manga.title,
                                    source,
                                )
                                
                                logcat(LogPriority.DEBUG) { "${manga.title} ch ${chapter.name}: chapterDir found=${chapterDirOrCbz != null}, exists=${chapterDirOrCbz?.exists()}, name=${chapterDirOrCbz?.name}" }

                                if (chapterDirOrCbz != null) {
                                    // Check if it's a CBZ file
                                    val isCbz = chapterDirOrCbz.name?.endsWith(".cbz") == true
                                    
                                    if (isCbz) {
                                        // Read content from CBZ archive
                                        logcat(LogPriority.DEBUG) { "${manga.title} ch ${chapter.name}: reading from CBZ archive" }
                                        content = readContentFromCbz(chapterDirOrCbz.uri)
                                    } else {
                                        // It's a directory, list files
                                        val allFiles = chapterDirOrCbz.listFiles() ?: emptyArray()
                                        logcat(LogPriority.DEBUG) { "${manga.title} ch ${chapter.name}: found ${allFiles.size} files in dir" }
                                    
                                        val htmlFiles = allFiles.filter {
                                            it.isFile && it.name?.endsWith(".html") == true
                                        }.sortedBy { it.name }
                                    
                                        // Also check for .txt files as fallback
                                        val txtFiles = allFiles.filter {
                                            it.isFile && it.name?.endsWith(".txt") == true
                                        }.sortedBy { it.name }
                                    
                                        logcat(LogPriority.DEBUG) { "${manga.title} ch ${chapter.name}: htmlFiles=${htmlFiles.size}, txtFiles=${txtFiles.size}" }
                                        allFiles.take(5).forEach { file ->
                                            logcat(LogPriority.DEBUG) { "  - ${file.name}" }
                                        }

                                        val filesToRead = htmlFiles.ifEmpty { txtFiles }
                                    
                                        if (filesToRead.isNotEmpty()) {
                                            val sb = StringBuilder()
                                            filesToRead.forEachIndexed { i, file ->
                                                val fileContent = context.contentResolver.openInputStream(file.uri)?.use {
                                                    it.bufferedReader().readText()
                                                } ?: ""
                                                sb.append(fileContent)
                                                if (i < filesToRead.size - 1) {
                                                    sb.append("\n\n")
                                                }
                                            }
                                            content = sb.toString()
                                            logcat(LogPriority.DEBUG) { "${manga.title} ch ${chapter.name}: read content length=${content?.length ?: 0}" }
                                        }
                                    }
                                }
                                
                                // If no content found, try CBZ archive in manga directory
                                if (content == null) {
                                    val mangaDir = downloadProvider.findMangaDir(manga.title, source)
                                    if (mangaDir != null) {
                                        val cbzFiles = mangaDir.listFiles()?.filter { 
                                            it.isFile && it.name?.endsWith(".cbz") == true 
                                        } ?: emptyList()
                                        
                                        // Find CBZ file matching this chapter
                                        val chapterDirNames = downloadProvider.getValidChapterDirNames(
                                            chapter.name,
                                            chapter.scanlator,
                                            chapter.url,
                                        )
                                        
                                        val matchingCbz = cbzFiles.find { cbzFile ->
                                            val cbzBaseName = cbzFile.name?.removeSuffix(".cbz") ?: ""
                                            chapterDirNames.any { it == cbzBaseName }
                                        }
                                        
                                        if (matchingCbz != null) {
                                            logcat(LogPriority.DEBUG) { "${manga.title} ch ${chapter.name}: found CBZ archive ${matchingCbz.name}" }
                                            content = readContentFromCbz(matchingCbz.uri)
                                            if (content == null) {
                                                logcat(LogPriority.WARN) { "${manga.title} ch ${chapter.name}: CBZ found but no readable content" }
                                            }
                                        } else {
                                            logcat(LogPriority.DEBUG) { "${manga.title} ch ${chapter.name}: no matching CBZ found. Available: ${cbzFiles.map { it.name }}" }
                                            logcat(LogPriority.DEBUG) { "${manga.title} ch ${chapter.name}: looking for: $chapterDirNames" }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                logcat(LogPriority.ERROR, e) { "Failed to read downloaded chapter: ${chapter.name}" }
                            }
                        }

                        if (content == null && isDownloaded) {
                            logcat(LogPriority.WARN) { "${manga.title} ch ${chapter.name}: marked as downloaded but no content could be read" }
                        }

                        if (content != null && content.isNotBlank()) {
                            val chNum = chapter.chapterNumber
                            if (chNum < firstChapterNum) firstChapterNum = chNum
                            if (chNum > lastChapterNum) lastChapterNum = chNum

                            epubChapters.add(
                                EpubWriter.Chapter(
                                    title = chapter.name,
                                    content = content,
                                    order = chapterIndex,
                                ),
                            )
                        }
                    }

                    // Skip novels without any exported chapters
                    if (epubChapters.isEmpty()) {
                        logcat(LogPriority.WARN) { "${manga.title}: No chapters could be exported (chapters=${chapters.size}, downloadedOnly=$downloadedOnly)" }
                        skippedCount++
                        continue
                    }

                    logcat(LogPriority.INFO) { "${manga.title}: Exporting ${epubChapters.size} chapters" }

                    // Get cover image
                    val coverImage = try {
                        manga.thumbnailUrl?.let { url ->
                            val request = okhttp3.Request.Builder().url(url).build()
                            networkHelper.client.newCall(request).execute().body.bytes()
                        }
                    } catch (e: Exception) {
                        null
                    }

                    // Create EPUB metadata
                    val metadata = EpubWriter.Metadata(
                        title = manga.title,
                        author = manga.author,
                        description = manga.description,
                        language = "en",
                        genres = manga.genre ?: emptyList(),
                        publisher = source.name,
                    )

                    // Build filename
                    val filenameBuilder = StringBuilder(sanitizeFilename(manga.title))
                    if (includeChapterCount) {
                        filenameBuilder.append(" [${epubChapters.size}ch]")
                    }
                    if (includeChapterRange && firstChapterNum != Double.MAX_VALUE) {
                        val firstCh = if (firstChapterNum == firstChapterNum.toLong().toDouble()) {
                            firstChapterNum.toLong().toString()
                        } else {
                            firstChapterNum.toString()
                        }
                        val lastCh = if (lastChapterNum == lastChapterNum.toLong().toDouble()) {
                            lastChapterNum.toLong().toString()
                        } else {
                            lastChapterNum.toString()
                        }
                        if (firstCh != lastCh) {
                            filenameBuilder.append(" [ch$firstCh-$lastCh]")
                        } else {
                            filenameBuilder.append(" [ch$firstCh]")
                        }
                    }
                    if (includeStatus) {
                        val statusStr = when (manga.status) {
                            SManga.ONGOING.toLong() -> "Ongoing"
                            SManga.COMPLETED.toLong() -> "Completed"
                            SManga.LICENSED.toLong() -> "Licensed"
                            SManga.PUBLISHING_FINISHED.toLong() -> "Finished"
                            SManga.CANCELLED.toLong() -> "Cancelled"
                            SManga.ON_HIATUS.toLong() -> "Hiatus"
                            else -> null
                        }
                        statusStr?.let { filenameBuilder.append(" [$it]") }
                    }
                    filenameBuilder.append(".epub")

                    // Write EPUB to temp file
                    val filename = filenameBuilder.toString()
                    val tempFile = File(tempDir, filename)
                    tempFile.outputStream().use { outputStream ->
                        EpubWriter().write(
                            outputStream = outputStream,
                            metadata = metadata,
                            chapters = epubChapters,
                            coverImage = coverImage,
                        )
                    }

                    logcat(LogPriority.INFO) { "Exported ${manga.title}: ${epubChapters.size} chapters" }
                    successCount++

                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to export ${manga.title}" }
                    skippedCount++
                }
            }

            // Write to output
            val tempFiles = tempDir.listFiles()?.filter { it.name.endsWith(".epub") } ?: emptyList()
            
            logcat(LogPriority.INFO) { "Export complete: ${tempFiles.size} EPUB files in temp dir, successCount=$successCount, skippedCount=$skippedCount" }
            
            if (tempFiles.isEmpty()) {
                logcat(LogPriority.ERROR) { "No EPUB files were created in temp dir" }
                showErrorNotification("No novels could be exported. Check that chapters are downloaded.")
                return
            }

            if (totalCount > 1) {
                // Create ZIP for multiple novels
                logcat(LogPriority.INFO) { "Writing ${tempFiles.size} EPUBs to ZIP at $outputUri" }
                context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    ZipOutputStream(outputStream).use { zipOut ->
                        tempFiles.forEach { file ->
                            val entry = ZipEntry(file.name)
                            zipOut.putNextEntry(entry)
                            file.inputStream().use { input ->
                                input.copyTo(zipOut)
                            }
                            zipOut.closeEntry()
                        }
                    }
                } ?: run {
                    logcat(LogPriority.ERROR) { "Failed to open output stream for URI: $outputUri" }
                    showErrorNotification("Failed to write to output file")
                    return
                }
            } else if (tempFiles.isNotEmpty()) {
                // Single file, copy directly
                logcat(LogPriority.INFO) { "Writing single EPUB to $outputUri (size=${tempFiles.first().length()} bytes)" }
                context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    tempFiles.first().inputStream().use { input ->
                        input.copyTo(outputStream)
                    }
                } ?: run {
                    logcat(LogPriority.ERROR) { "Failed to open output stream for URI: $outputUri" }
                    showErrorNotification("Failed to write to output file")
                    return
                }
            }

            showCompleteNotification(successCount, skippedCount)

        } finally {
            // Cleanup
            tempDir.deleteRecursively()
        }
    }

    private fun updateProgress(current: Int, total: Int, title: String) {
        context.notify(
            Notifications.ID_EPUB_EXPORT_PROGRESS,
            notificationBuilder
                .setContentTitle("EPUB Export")
                .setContentText("Exporting $current/$total: $title")
                .setProgress(total, current, false)
                .build(),
        )
    }

    private fun showCompleteNotification(success: Int, skipped: Int) {
        context.cancelNotification(Notifications.ID_EPUB_EXPORT_PROGRESS)
        val message = buildString {
            append("Exported $success novels")
            if (skipped > 0) append(", $skipped skipped")
        }
        context.notify(
            Notifications.ID_EPUB_EXPORT_COMPLETE,
            context.notificationBuilder(Notifications.CHANNEL_EPUB_EXPORT) {
                setSmallIcon(android.R.drawable.ic_menu_save)
                setContentTitle("EPUB Export Complete")
                setContentText(message)
                setAutoCancel(true)
            }.build(),
        )
    }

    private fun showErrorNotification(error: String) {
        context.cancelNotification(Notifications.ID_EPUB_EXPORT_PROGRESS)
        context.notify(
            Notifications.ID_EPUB_EXPORT_COMPLETE,
            context.notificationBuilder(Notifications.CHANNEL_EPUB_EXPORT) {
                setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
                setContentTitle("EPUB Export Failed")
                setContentText(error)
                setAutoCancel(true)
            }.build(),
        )
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(200)
    }
    
    /**
     * Read HTML/TXT content from a CBZ archive.
     */
    private fun readContentFromCbz(cbzUri: Uri): String? {
        logcat(LogPriority.DEBUG) { "CBZ: attempting to read from $cbzUri" }
        return try {
            // Use ParcelFileDescriptor and ArchiveReader (libarchive) for compatibility
            // with CBZ files created by ZipWriter which also uses libarchive
            val pfd = context.contentResolver.openFileDescriptor(cbzUri, "r")
            if (pfd == null) {
                logcat(LogPriority.WARN) { "CBZ: failed to open file descriptor for $cbzUri" }
                return null
            }
            
            pfd.use { descriptor ->
                val archiveReader = ArchiveReader(descriptor)
                archiveReader.use { reader ->
                    // First pass: collect names of content files
                    val contentFileNames = mutableListOf<String>()
                    var entryCount = 0
                    
                    reader.useEntries { sequence ->
                        sequence.forEach { entry ->
                            entryCount++
                            val name = entry.name.lowercase()
                            logcat(LogPriority.DEBUG) { "CBZ: entry #$entryCount: ${entry.name}, isFile=${entry.isFile}" }
                            
                            if (entry.isFile && (name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".xhtml") || name.endsWith(".txt"))) {
                                contentFileNames.add(entry.name)
                            }
                        }
                    }
                    
                    logcat(LogPriority.DEBUG) { "CBZ: found ${contentFileNames.size} content files out of $entryCount entries" }
                    
                    // Second pass: read content from each file using getInputStream
                    val entries = mutableListOf<Pair<String, String>>()
                    contentFileNames.forEach { fileName ->
                        try {
                            val inputStream = reader.getInputStream(fileName)
                            if (inputStream != null) {
                                inputStream.use { stream ->
                                    val entryContent = stream.bufferedReader().readText()
                                    entries.add(fileName to entryContent)
                                    logcat(LogPriority.DEBUG) { "CBZ: read content file $fileName, length=${entryContent.length}" }
                                }
                            } else {
                                logcat(LogPriority.WARN) { "CBZ: could not get input stream for $fileName" }
                            }
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR, e) { "CBZ: failed to read entry $fileName" }
                        }
                    }
                    
                    logcat(LogPriority.DEBUG) { "CBZ: successfully read ${entries.size} content files" }
                    
                    // Sort entries by name and combine
                    val content = StringBuilder()
                    entries.sortedBy { it.first }.forEachIndexed { i, (_, text) ->
                        content.append(text)
                        if (i < entries.size - 1) {
                            content.append("\n\n")
                        }
                    }
                    
                    val result = content.toString().ifEmpty { null }
                    logcat(LogPriority.DEBUG) { "CBZ: final content length=${result?.length ?: 0}" }
                    result
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "CBZ: failed to read archive $cbzUri" }
            null
        }
    }

    companion object {
        private const val TAG = "EpubExportJob"
        private const val KEY_MANGA_IDS = "manga_ids"
        private const val KEY_OUTPUT_URI = "output_uri"
        private const val KEY_DOWNLOADED_ONLY = "downloaded_only"
        private const val KEY_PREFER_TRANSLATED = "prefer_translated"
        private const val KEY_INCLUDE_CHAPTER_COUNT = "include_chapter_count"
        private const val KEY_INCLUDE_CHAPTER_RANGE = "include_chapter_range"
        private const val KEY_INCLUDE_STATUS = "include_status"

        fun start(
            context: Context,
            mangaIds: List<Long>,
            outputUri: Uri,
            downloadedOnly: Boolean = false,
            preferTranslated: Boolean = false,
            includeChapterCount: Boolean = false,
            includeChapterRange: Boolean = false,
            includeStatus: Boolean = false,
        ) {
            val data = workDataOf(
                KEY_MANGA_IDS to mangaIds.toLongArray(),
                KEY_OUTPUT_URI to outputUri.toString(),
                KEY_DOWNLOADED_ONLY to downloadedOnly,
                KEY_PREFER_TRANSLATED to preferTranslated,
                KEY_INCLUDE_CHAPTER_COUNT to includeChapterCount,
                KEY_INCLUDE_CHAPTER_RANGE to includeChapterRange,
                KEY_INCLUDE_STATUS to includeStatus,
            )

            val request = OneTimeWorkRequestBuilder<EpubExportJob>()
                .addTag(TAG)
                .setInputData(data)
                .build()

            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
