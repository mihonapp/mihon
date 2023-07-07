package eu.kanade.tachiyomi.data.sync

import android.content.Context
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.backup.BackupConst.BACKUP_ALL
import eu.kanade.tachiyomi.data.backup.BackupManager
import eu.kanade.tachiyomi.data.backup.BackupRestoreJob
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.sync.models.SyncData
import eu.kanade.tachiyomi.data.sync.models.SyncDevice
import eu.kanade.tachiyomi.data.sync.models.SyncStatus
import eu.kanade.tachiyomi.data.sync.service.SyncYomiSyncService
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.data.Chapters
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.manga.mangaMapper
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.sync.SyncPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

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
    private val backupManager: BackupManager = BackupManager(context)
    private val notifier: SyncNotifier = SyncNotifier(context)

    enum class SyncService(val value: Int) {
        NONE(0),
        SYNCYOMI(1),
        ;

        companion object {
            fun fromInt(value: Int) = values().firstOrNull { it.value == value } ?: NONE
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
        val backup = Backup(
            backupManager.backupMangas(databaseManga, BACKUP_ALL),
            backupManager.backupCategories(BACKUP_ALL),
            emptyList(),
            backupManager.backupExtensionInfo(databaseManga),
        )

        // Create the SyncStatus object
        val syncStatus = SyncStatus(
            lastSynced = Instant.now().toString(),
            status = "completed",
        )

        // Create the Device object
        val device = SyncDevice(
            id = syncPreferences.deviceID().get(),
            name = syncPreferences.deviceName().get(),
        )

        // Create the SyncData object
        val syncData = SyncData(
            sync = syncStatus,
            backup = backup,
            device = device,
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

            else -> {
                logcat(LogPriority.ERROR) { "Invalid sync service type: $syncService" }
                null
            }
        }

        val remoteBackup = syncService?.doSync(syncData)

        if (remoteBackup != null) {
            val (filteredFavorites, nonFavorites) = filterFavoritesAndNonFavorites(remoteBackup)
            updateNonFavorites(nonFavorites)
            SyncHolder.backup = backup.copy(
                backupManga = filteredFavorites,
                backupCategories = remoteBackup.backupCategories,
                backupSources = remoteBackup.backupSources,
                backupBrokenSources = remoteBackup.backupBrokenSources,
            )
            BackupRestoreJob.start(context, "".toUri(), true)
            syncPreferences.syncLastSync().set(Instant.now())
        }
    }

    /**
     * Retrieves all manga from the local database.
     *
     * @return a list of all manga stored in the database
     */
    private suspend fun getAllMangaFromDB(): List<Manga> {
        return handler.awaitList { mangasQueries.getAllManga(mangaMapper) }
    }

    /**
     * Compares two Manga objects (one from the local database and one from the backup) to check if they are different.
     * @param localManga the Manga object from the local database.
     * @param remoteManga the BackupManga object from the backup.
     * @return true if the Manga objects are different, otherwise false.
     */
    private suspend fun isMangaDifferent(localManga: Manga, remoteManga: BackupManga): Boolean {
        val localChapters = handler.await { chaptersQueries.getChaptersByMangaId(localManga.id).executeAsList() }
        val localCategories = getCategories.await(localManga.id).map { it.order }

        return localManga.source != remoteManga.source || localManga.url != remoteManga.url || localManga.title != remoteManga.title || localManga.artist != remoteManga.artist || localManga.author != remoteManga.author || localManga.description != remoteManga.description || localManga.genre != remoteManga.genre || localManga.status.toInt() != remoteManga.status || localManga.thumbnailUrl != remoteManga.thumbnailUrl || localManga.dateAdded != remoteManga.dateAdded || localManga.chapterFlags.toInt() != remoteManga.chapterFlags || localManga.favorite != remoteManga.favorite || localManga.viewerFlags.toInt() != remoteManga.viewer_flags || localManga.updateStrategy != remoteManga.updateStrategy || areChaptersDifferent(localChapters, remoteManga.chapters) || localCategories != remoteManga.categories
    }

    /**
     * Compares two lists of chapters (one from the local database and one from the backup) to check if they are different.
     * @param localChapters the list of chapters from the local database.
     * @param remoteChapters the list of BackupChapter objects from the backup.
     * @return true if the lists of chapters are different, otherwise false.
     */
    private fun areChaptersDifferent(localChapters: List<Chapters>, remoteChapters: List<BackupChapter>): Boolean {
        if (localChapters.size != remoteChapters.size) {
            return true
        }

        val localChapterMap = localChapters.associateBy { it.url }

        return remoteChapters.any { remoteChapter ->
            localChapterMap[remoteChapter.url]?.let { localChapter ->
                localChapter.name != remoteChapter.name || localChapter.scanlator != remoteChapter.scanlator || localChapter.read != remoteChapter.read || localChapter.bookmark != remoteChapter.bookmark || localChapter.last_page_read != remoteChapter.lastPageRead || localChapter.date_fetch != remoteChapter.dateFetch || localChapter.date_upload != remoteChapter.dateUpload || localChapter.chapter_number != remoteChapter.chapterNumber || localChapter.source_order != remoteChapter.sourceOrder
            } ?: true
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
                    backupManager.updateManga(updatedManga)
                }
            }
        }
    }
}
