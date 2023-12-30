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
import tachiyomi.core.util.system.logcat
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
        val databaseManga = getAllMangaFromDB()
        val backupOptions = BackupOptions(
            libraryEntries = true,
            categories = true,
            chapters = true,
            tracking = true,
            history = true,
            appSettings = true,
            sourceSettings = true,
            privateSettings = true,
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

        return localManga.source != remoteManga.source ||
            localManga.url != remoteManga.url ||
            localManga.title != remoteManga.title ||
            localManga.artist != remoteManga.artist ||
            localManga.author != remoteManga.author ||
            localManga.description != remoteManga.description ||
            localManga.genre != remoteManga.genre ||
            localManga.status.toInt() != remoteManga.status ||
            localManga.thumbnailUrl != remoteManga.thumbnailUrl ||
            localManga.dateAdded != remoteManga.dateAdded ||
            localManga.chapterFlags.toInt() != remoteManga.chapterFlags ||
            localManga.favorite != remoteManga.favorite ||
            localManga.viewerFlags.toInt() != remoteManga.viewer_flags ||
            localManga.updateStrategy != remoteManga.updateStrategy ||
            areChaptersDifferent(localChapters, remoteManga.chapters) ||
            localCategories != remoteManga.categories
    }

    private fun areChaptersDifferent(localChapters: List<Chapters>, remoteChapters: List<BackupChapter>): Boolean {
        // Early return if the sizes are different
        if (localChapters.size != remoteChapters.size) {
            return true
        }

        // Convert all remote chapters to Chapter instances
        val convertedRemoteChapters = remoteChapters.map { it.toChapterImpl() }

        // Create a map for the local chapters for efficient comparison
        val localChapterMap = localChapters.associateBy { it.url }

        // Check for any differences
        return convertedRemoteChapters.any { remoteChapter ->
            val localChapter = localChapterMap[remoteChapter.url]
            localChapter == null || // No corresponding local chapter
                localChapter.url != remoteChapter.url ||
                localChapter.name != remoteChapter.name ||
                localChapter.scanlator != remoteChapter.scanlator ||
                localChapter.read != remoteChapter.read ||
                localChapter.bookmark != remoteChapter.bookmark ||
                localChapter.last_page_read != remoteChapter.lastPageRead ||
                localChapter.chapter_number != remoteChapter.chapterNumber ||
                localChapter.source_order != remoteChapter.sourceOrder ||
                localChapter.date_fetch != remoteChapter.dateFetch ||
                localChapter.date_upload != remoteChapter.dateUpload
        }
    }

    /**
     * Filters the favorite and non-favorite manga from the backup and checks if the favorite manga is different from the local database.
     * @param backup the Backup object containing the backup data.
     * @return a Pair of lists, where the first list contains different favorite manga and the second list contains non-favorite manga.
     */
    private suspend fun filterFavoritesAndNonFavorites(backup: Backup): Pair<List<BackupManga>, List<BackupManga>> {
        val databaseMangaFavorites = getFavorites.await()
        val localMangaMap = databaseMangaFavorites.associateBy { it.url }
        val favorites = mutableListOf<BackupManga>()
        val nonFavorites = mutableListOf<BackupManga>()

        backup.backupManga.forEach { remoteManga ->
            if (remoteManga.favorite) {
                localMangaMap[remoteManga.url]?.let { localManga ->
                    if (isMangaDifferent(localManga, remoteManga)) {
                        favorites.add(remoteManga)
                    }
                } ?: favorites.add(remoteManga)
            } else {
                nonFavorites.add(remoteManga)
            }
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
