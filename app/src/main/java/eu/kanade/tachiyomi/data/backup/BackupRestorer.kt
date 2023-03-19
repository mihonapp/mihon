package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.util.BackupUtil
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.model.Track
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRestorer(
    private val context: Context,
    private val notifier: BackupNotifier,
) {

    private var backupManager = BackupManager(context)

    private var restoreAmount = 0
    private var restoreProgress = 0

    /**
     * Mapping of source ID to source name from backup data
     */
    private var sourceMapping: Map<Long, String> = emptyMap()

    private val errors = mutableListOf<Pair<Date, String>>()

    suspend fun restoreBackup(uri: Uri): Boolean {
        val startTime = System.currentTimeMillis()
        restoreProgress = 0
        errors.clear()

        if (!performRestore(uri)) {
            return false
        }

        val endTime = System.currentTimeMillis()
        val time = endTime - startTime

        val logFile = writeErrorLog()

        notifier.showRestoreComplete(time, errors.size, logFile.parent, logFile.name)
        return true
    }

    private fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("tachiyomi_restore.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
                    }
                }
                return file
            }
        } catch (e: Exception) {
            // Empty
        }
        return File("")
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun performRestore(uri: Uri): Boolean {
        val backup = BackupUtil.decodeBackup(context, uri)

        restoreAmount = backup.backupManga.size + 1 // +1 for categories

        // Restore categories
        if (backup.backupCategories.isNotEmpty()) {
            restoreCategories(backup.backupCategories)
        }

        // Store source mapping for error messages
        val backupMaps = backup.backupBrokenSources.map { BackupSource(it.name, it.sourceId) } + backup.backupSources
        sourceMapping = backupMaps.associate { it.sourceId to it.name }

        return coroutineScope {
            // Restore individual manga
            backup.backupManga.forEach {
                if (!isActive) {
                    return@coroutineScope false
                }

                restoreManga(it, backup.backupCategories)
            }
            // TODO: optionally trigger online library + tracker update
            true
        }
    }

    private suspend fun restoreCategories(backupCategories: List<BackupCategory>) {
        backupManager.restoreCategories(backupCategories)

        restoreProgress += 1
        showRestoreProgress(restoreProgress, restoreAmount, context.getString(R.string.categories))
    }

    private suspend fun restoreManga(backupManga: BackupManga, backupCategories: List<BackupCategory>) {
        val manga = backupManga.getMangaImpl()
        val chapters = backupManga.getChaptersImpl()
        val categories = backupManga.categories.map { it.toInt() }
        val history =
            backupManga.brokenHistory.map { BackupHistory(it.url, it.lastRead, it.readDuration) } + backupManga.history
        val tracks = backupManga.getTrackingImpl()

        try {
            val dbManga = backupManager.getMangaFromDatabase(manga.url, manga.source)
            if (dbManga == null) {
                // Manga not in database
                restoreExistingManga(manga, chapters, categories, history, tracks, backupCategories)
            } else {
                // Manga in database
                // Copy information from manga already in database
                val manga = backupManager.restoreExistingManga(manga, dbManga)
                // Fetch rest of manga information
                restoreNewManga(manga, chapters, categories, history, tracks, backupCategories)
            }
        } catch (e: Exception) {
            val sourceName = sourceMapping[manga.source] ?: manga.source.toString()
            errors.add(Date() to "${manga.title} [$sourceName]: ${e.message}")
        }

        restoreProgress += 1
        showRestoreProgress(restoreProgress, restoreAmount, manga.title)
    }

    /**
     * Fetches manga information
     *
     * @param manga manga that needs updating
     * @param chapters chapters of manga that needs updating
     * @param categories categories that need updating
     */
    private suspend fun restoreExistingManga(
        manga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
    ) {
        val fetchedManga = backupManager.restoreNewManga(manga)
        backupManager.restoreChapters(fetchedManga, chapters)
        restoreExtras(fetchedManga, categories, history, tracks, backupCategories)
    }

    private suspend fun restoreNewManga(
        backupManga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
    ) {
        backupManager.restoreChapters(backupManga, chapters)
        restoreExtras(backupManga, categories, history, tracks, backupCategories)
    }

    private suspend fun restoreExtras(manga: Manga, categories: List<Int>, history: List<BackupHistory>, tracks: List<Track>, backupCategories: List<BackupCategory>) {
        backupManager.restoreCategories(manga, categories, backupCategories)
        backupManager.restoreHistory(history)
        backupManager.restoreTracking(manga, tracks)
    }

    /**
     * Called to update dialog in [BackupConst]
     *
     * @param progress restore progress
     * @param amount total restoreAmount of manga
     * @param title title of restored manga
     */
    private fun showRestoreProgress(progress: Int, amount: Int, title: String) {
        notifier.showRestoreProgress(title, progress, amount)
    }
}
