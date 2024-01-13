package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSerializer
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveSyncService
import eu.kanade.tachiyomi.data.sync.service.SyncData
import eu.kanade.tachiyomi.data.sync.service.SyncYomiSyncService
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import logcat.logcat
import tachiyomi.data.Chapters
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.manga.MangaMapper.mapManga
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.sync.SyncPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Date
import kotlin.system.measureTimeMillis

/**
 * A manager to handle synchronization tasks in the app, such as updating
 * sync preferences and performing synchronization with a remote server.
 *
 * @property context The application context.
 */
class SyncManager(
    private val context: Context,
    private val handler: DatabaseHandler = Injekt.get(),
    private val syncPreferences: SyncPreferences = Injekt.get(),
    private var json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
    private val getFavorites: GetFavorites = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
) {
    private val backupCreator: BackupCreator = BackupCreator(context, false)
    private val notifier: SyncNotifier = SyncNotifier(context)
    private val mangaRestorer: MangaRestorer = MangaRestorer()

    enum class SyncService(val value: Int) {
        NONE(0),
        SYNCYOMI(1),
        GOOGLE_DRIVE(2),
        ;

        companion object {
            fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: NONE
        }
    }

    /**
     * Syncs data with a sync service.
     *
     * This function retrieves local data (favorites, manga, extensions, and categories)
     * from the database using the BackupManager, then synchronizes the data with a sync service.
     */
    suspend fun syncData() {
        val syncOptions = syncPreferences.getSyncOptions()
        val databaseManga = getAllMangaFromDB()
        val backupOptions = BackupOptions(
            libraryEntries = syncOptions.libraryEntries,
            categories = syncOptions.categories,
            chapters = syncOptions.chapters,
            tracking = syncOptions.tracking,
            history = syncOptions.history,
            appSettings = syncOptions.appSettings,
            sourceSettings = syncOptions.sourceSettings,
            privateSettings = syncOptions.privateSettings,
        )
        val backup = Backup(
            backupManga = backupCreator.backupMangas(databaseManga, backupOptions),
            backupCategories = backupCreator.backupCategories(backupOptions),
            backupSources = backupCreator.backupSources(databaseManga),
            backupPreferences = backupCreator.backupAppPreferences(backupOptions),
            backupSourcePreferences = backupCreator.backupSourcePreferences(backupOptions),
        )

        // Create the SyncData object
        val syncData = SyncData(
            backup = backup,
        )

        // Handle sync based on the selected service
        val syncService = when (val syncService = SyncService.fromInt(syncPreferences.syncService().get())) {
            SyncService.SYNCYOMI -> {
                SyncYomiSyncService(
                    context,
                    json,
                    syncPreferences,
                    notifier,
                )
            }

            SyncService.GOOGLE_DRIVE -> {
                GoogleDriveSyncService(context, json, syncPreferences)
            }

            else -> {
                logcat(LogPriority.ERROR) { "Invalid sync service type: $syncService" }
                null
            }
        }

        val remoteBackup = syncService?.doSync(syncData)

        // Stop the sync early if the remote backup is null or empty
        if (remoteBackup?.backupManga?.size == 0) {
            notifier.showSyncError("No data found on remote server.")
            return
        }

        // Check if it's first sync based on lastSyncTimestamp
        if (syncPreferences.lastSyncTimestamp().get() == 0L && databaseManga.isNotEmpty()) {
            // It's first sync no need to restore data. (just update remote data)
            syncPreferences.lastSyncTimestamp().set(Date().time)
            notifier.showSyncSuccess("Updated remote data successfully")
            return
        }

        if (remoteBackup != null) {
            val (filteredFavorites, nonFavorites) = filterFavoritesAndNonFavorites(remoteBackup)
            updateNonFavorites(nonFavorites)

            val newSyncData = backup.copy(
                backupManga = filteredFavorites,
                backupCategories = remoteBackup.backupCategories,
                backupSources = remoteBackup.backupSources,
                backupPreferences = remoteBackup.backupPreferences,
                backupSourcePreferences = remoteBackup.backupSourcePreferences,
            )

            // It's local sync no need to restore data. (just update remote data)
            if (filteredFavorites.isEmpty()) {
                // update the sync timestamp
                syncPreferences.lastSyncTimestamp().set(Date().time)
                notifier.showSyncSuccess("Sync completed successfully")
                return
            }

            val backupUri = writeSyncDataToCache(context, newSyncData)
            logcat(LogPriority.DEBUG) { "Got Backup Uri: $backupUri" }
            if (backupUri != null) {
                BackupRestoreJob.start(
                    context,
                    backupUri,
                    sync = true,
                    options = RestoreOptions(
                        appSettings = true,
                        sourceSettings = true,
                        library = true,
                    ),
                )

                // update the sync timestamp
                syncPreferences.lastSyncTimestamp().set(Date().time)
            } else {
                logcat(LogPriority.ERROR) { "Failed to write sync data to file" }
            }
        }
    }

    private fun writeSyncDataToCache(context: Context, backup: Backup): Uri? {
        val cacheFile = File(context.cacheDir, "tachiyomi_sync_data.proto.gz")
        return try {
            FileOutputStream(cacheFile).use { output ->
                output.write(ProtoBuf.encodeToByteArray(BackupSerializer, backup))
                Uri.fromFile(cacheFile)
            }
        } catch (e: IOException) {
            logcat(LogPriority.ERROR) { "Failed to write sync data to cache" }
            null
        }
    }

    /**
     * Retrieves all manga from the local database.
     *
     * @return a list of all manga stored in the database
     */
    private suspend fun getAllMangaFromDB(): List<Manga> {
        return handler.awaitList { mangasQueries.getAllManga(::mapManga) }
    }

    private suspend fun isMangaDifferent(localManga: Manga, remoteManga: BackupManga): Boolean {
        val localChapters = handler.await { chaptersQueries.getChaptersByMangaId(localManga.id, 0).executeAsList() }
        val localCategories = getCategories.await(localManga.id).map { it.order }
        val logTag = "isMangaDifferent"

        // Logging the start of comparison
        logcat(LogPriority.DEBUG, logTag) {
            "Comparing local manga (Title: ${localManga.title}) with remote manga (Title: ${remoteManga.title})"
        }

        var isDifferent = false

        // Compare each field and log if they are different
        if (localManga.source != remoteManga.source) {
            logDifference(logTag, "Source", localManga.source.toString(), remoteManga.source.toString())
            isDifferent = true
        }
        if (localManga.url != remoteManga.url) {
            logDifference(logTag, "URL", localManga.url, remoteManga.url)
            isDifferent = true
        }
        if (localManga.favorite != remoteManga.favorite) {
            logDifference(logTag, "Favorite", localManga.favorite.toString(), remoteManga.favorite.toString())
            isDifferent = true
        }
        if (areChaptersDifferent(localChapters, remoteManga.chapters)) {
            logcat(LogPriority.DEBUG, logTag) {
                "Chapters are different for manga: ${localManga.title}"
            }
            isDifferent = true
        }

        if (localCategories.toSet() != remoteManga.categories.toSet()) {
            logcat(LogPriority.DEBUG, logTag) {
                "Categories differ for manga: ${localManga.title}. " +
                    "Local categories: ${localCategories.joinToString()}, " +
                    "Remote categories: ${remoteManga.categories.joinToString()}"
            }
            isDifferent = true
        }

        // Log final result
        logcat(LogPriority.DEBUG, logTag) {
            "Manga difference check result for local manga (Title: ${localManga.title}): $isDifferent"
        }

        return isDifferent
    }

    private fun logDifference(tag: String, field: String, localValue: String, remoteValue: String) {
        logcat(LogPriority.DEBUG, tag) {
            "Difference found in $field. Local: $localValue, Remote: $remoteValue"
        }
    }

    private fun areChaptersDifferent(localChapters: List<Chapters>, remoteChapters: List<BackupChapter>): Boolean {
        val logTag = "areChaptersDifferent"

        // Early return if the sizes are different
        if (localChapters.size != remoteChapters.size) {
            logcat(LogPriority.DEBUG, logTag) {
                "Chapter lists differ in size. Local: ${localChapters.size}, Remote: ${remoteChapters.size}"
            }
            return true
        }

        // Convert all remote chapters to Chapter instances
        val convertedRemoteChapters = remoteChapters.map { it.toChapterImpl() }

        // Create a map for the local chapters for efficient comparison
        val localChapterMap = localChapters.associateBy { it.url }

        var isDifferent = false

        // Check for any differences
        for (remoteChapter in convertedRemoteChapters) {
            val localChapter = localChapterMap[remoteChapter.url]

            if (localChapter == null) {
                logcat(LogPriority.DEBUG, logTag) {
                    "Local chapter not found for URL: ${remoteChapter.url}"
                }
                isDifferent = true
                continue
            }

            if (localChapter.url != remoteChapter.url) {
                logDifference(logTag, "URL", localChapter.url, remoteChapter.url)
                isDifferent = true
            }
            if (localChapter.read != remoteChapter.read) {
                logDifference(logTag, "Read Status", localChapter.read.toString(), remoteChapter.read.toString())
                isDifferent = true
            }

            if (localChapter.bookmark != remoteChapter.bookmark) {
                logDifference(
                    logTag,
                    "Bookmark Status",
                    localChapter.bookmark.toString(),
                    remoteChapter.bookmark.toString(),
                )
                isDifferent = true
            }

            if (localChapter.last_page_read != remoteChapter.lastPageRead) {
                logDifference(
                    logTag,
                    "Last Page Read",
                    localChapter.last_page_read.toString(),
                    remoteChapter.lastPageRead.toString(),
                )
                isDifferent = true
            }

            // Break the loop if a difference is found
            if (isDifferent) break
        }

        if (!isDifferent) {
            logcat(LogPriority.DEBUG, logTag) {
                "No differences found in chapters."
            }
        }

        return isDifferent
    }

    /**
     * Filters the favorite and non-favorite manga from the backup and checks if the favorite manga is different from the local database.
     * @param backup the Backup object containing the backup data.
     * @return a Pair of lists, where the first list contains different favorite manga and the second list contains non-favorite manga.
     */
    private suspend fun filterFavoritesAndNonFavorites(backup: Backup): Pair<List<BackupManga>, List<BackupManga>> {
        val favorites = mutableListOf<BackupManga>()
        val nonFavorites = mutableListOf<BackupManga>()
        val logTag = "filterFavoritesAndNonFavorites"

        val elapsedTimeMillis = measureTimeMillis {
            val databaseMangaFavorites = getFavorites.await()
            val localMangaMap = databaseMangaFavorites.associateBy {
                Triple(it.source, it.url, it.title)
            }

            logcat(LogPriority.DEBUG, logTag) { "Starting to filter favorites and non-favorites from backup data." }

            backup.backupManga.forEach { remoteManga ->
                val compositeKey = Triple(remoteManga.source, remoteManga.url, remoteManga.title)
                val localManga = localMangaMap[compositeKey]
                when {
                    // Checks if the manga is in favorites and needs updating or adding
                    remoteManga.favorite -> {
                        if (localManga == null || isMangaDifferent(localManga, remoteManga)) {
                            logcat(LogPriority.DEBUG, logTag) { "Adding to favorites: ${remoteManga.title}" }
                            favorites.add(remoteManga)
                        } else {
                            logcat(LogPriority.DEBUG, logTag) { "Already up-to-date favorite: ${remoteManga.title}" }
                        }
                    }
                    // Handle non-favorites
                    !remoteManga.favorite -> {
                        logcat(LogPriority.DEBUG, logTag) { "Adding to non-favorites: ${remoteManga.title}" }
                        nonFavorites.add(remoteManga)
                    }
                }
            }
        }

        val minutes = elapsedTimeMillis / 60000
        val seconds = (elapsedTimeMillis % 60000) / 1000
        logcat(LogPriority.DEBUG, logTag) {
            "Filtering completed in ${minutes}m ${seconds}s. Favorites found: ${favorites.size}, " +
                "Non-favorites found: ${nonFavorites.size}"
        }

        return Pair(favorites, nonFavorites)
    }

    /**
     * Updates the non-favorite manga in the local database with their favorite status from the backup.
     * @param nonFavorites the list of non-favorite BackupManga objects from the backup.
     */
    private suspend fun updateNonFavorites(nonFavorites: List<BackupManga>) {
        val localMangaList = getAllMangaFromDB()
        val localMangaMap = localMangaList.associateBy { it.url }

        nonFavorites.forEach { nonFavorite ->
            localMangaMap[nonFavorite.url]?.let { localManga ->
                if (localManga.favorite != nonFavorite.favorite) {
                    val updatedManga = localManga.copy(favorite = nonFavorite.favorite)
                    mangaRestorer.updateManga(updatedManga)
                }
            }
        }
    }
}
