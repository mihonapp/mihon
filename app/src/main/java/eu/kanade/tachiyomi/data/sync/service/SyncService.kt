package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Serializable
data class SyncData(
    val deviceId: String = "",
    val backup: Backup? = null,
)

abstract class SyncService(
    val context: Context,
    val json: Json,
    val syncPreferences: SyncPreferences,
) {
    abstract suspend fun doSync(syncData: SyncData): Backup?

    /**
     * Merges the local and remote sync data into a single sync data object.
     *
     * @param localSyncData The [SyncData] containing the local sync data.
     * @param remoteSyncData The [SyncData] containing the remote sync data.
     * @return The [SyncData] containing the merged sync data.
     */
    protected fun mergeSyncData(localSyncData: SyncData, remoteSyncData: SyncData): SyncData {
        val mergedCategoriesList =
            mergeCategoriesLists(localSyncData.backup?.backupCategories, remoteSyncData.backup?.backupCategories)

        val mergedMangaList = mergeMangaLists(
            localSyncData.backup?.backupManga,
            remoteSyncData.backup?.backupManga,
            localSyncData.backup?.backupCategories ?: emptyList(),
            remoteSyncData.backup?.backupCategories ?: emptyList(),
            mergedCategoriesList,
        )

        val mergedSourcesList =
            mergeSourcesLists(localSyncData.backup?.backupSources, remoteSyncData.backup?.backupSources)
        val mergedPreferencesList =
            mergePreferencesLists(localSyncData.backup?.backupPreferences, remoteSyncData.backup?.backupPreferences)
        val mergedSourcePreferencesList = mergeSourcePreferencesLists(
            localSyncData.backup?.backupSourcePreferences,
            remoteSyncData.backup?.backupSourcePreferences,
        )
        val mergedExtensionStoresList = mergeExtensionStoresLists(
            localSyncData.backup?.backupExtensionStores,
            remoteSyncData.backup?.backupExtensionStores,
        )

        // Create the merged Backup object
        val mergedBackup = Backup(
            backupManga = mergedMangaList,
            backupCategories = mergedCategoriesList,
            backupSources = mergedSourcesList,
            backupPreferences = mergedPreferencesList,
            backupSourcePreferences = mergedSourcePreferencesList,
            backupExtensionStores = mergedExtensionStoresList,
        )

        // Create the merged SyncData object
        return SyncData(
            deviceId = syncPreferences.uniqueDeviceID(),
            backup = mergedBackup,
        )
    }

    /**
     * Merges two lists of BackupManga objects, selecting the most recent manga based on the version value.
     *
     * @param localMangaList The list of local BackupManga objects or null.
     * @param remoteMangaList The list of remote BackupManga objects or null.
     * @return A list of BackupManga objects, each representing the most recent version of the manga from either local or remote sources.
     */
    private fun mergeMangaLists(
        localMangaList: List<BackupManga>?,
        remoteMangaList: List<BackupManga>?,
        localCategories: List<BackupCategory>,
        remoteCategories: List<BackupCategory>,
        mergedCategories: List<BackupCategory>,
    ): List<BackupManga> {
        val logTag = "MergeMangaLists"

        val localMangaListSafe = localMangaList.orEmpty()
        val remoteMangaListSafe = remoteMangaList.orEmpty()

        logcat(LogPriority.DEBUG, logTag) {
            "Starting merge. Local list size: ${localMangaListSafe.size}, Remote list size: ${remoteMangaListSafe.size}"
        }

        fun mangaCompositeKey(manga: BackupManga): String {
            return "${manga.source}|${manga.url}"
        }

        // Create maps using composite keys
        val localMangaMap = localMangaListSafe.associateBy { mangaCompositeKey(it) }
        val remoteMangaMap = remoteMangaListSafe.associateBy { mangaCompositeKey(it) }

        val localCategoriesMapByOrder = localCategories.associateBy { it.order }
        val remoteCategoriesMapByOrder = remoteCategories.associateBy { it.order }
        val mergedCategoriesMapByName = mergedCategories.associateBy { it.name }

        fun updateCategories(theManga: BackupManga, theMap: Map<Long, BackupCategory>): BackupManga {
            return theManga.copy(
                categories = theManga.categories.mapNotNull {
                    theMap[it]?.let { category ->
                        mergedCategoriesMapByName[category.name]?.order
                    }
                },
            )
        }

        val lastSyncTime = syncPreferences.lastSyncTimestamp.get().milliseconds.inWholeSeconds
        val syncOptions = syncPreferences.getSyncSettings()

        val mergedList = (localMangaMap.keys + remoteMangaMap.keys).distinct().mapNotNull { compositeKey ->
            val local = localMangaMap[compositeKey]
            val remote = remoteMangaMap[compositeKey]

            // New version comparison logic
            when {
                local != null && remote == null -> {
                    if (lastSyncTime == 0L || local.lastModifiedAt > lastSyncTime) {
                        updateCategories(local, localCategoriesMapByOrder)
                    } else {
                        logcat(LogPriority.DEBUG, logTag) { "Dropping local manga deleted on remote: ${local.title}." }
                        null
                    }
                }
                local == null && remote != null -> {
                    if (lastSyncTime == 0L || remote.lastModifiedAt > lastSyncTime) {
                        updateCategories(remote, remoteCategoriesMapByOrder)
                    } else {
                        logcat(LogPriority.DEBUG, logTag) { "Dropping deleted remote manga: ${remote.title}." }
                        null
                    }
                }
                local != null && remote != null -> {
                    // Compare versions to decide which manga to keep
                    if (local.version >= remote.version) {
                        logcat(LogPriority.DEBUG, logTag) {
                            "Keeping local version of ${local.title} with merged chapters."
                        }
                        updateCategories(
                            local.copy(
                                chapters = mergeChapters(
                                    local.chapters,
                                    remote.chapters,
                                    lastSyncTime,
                                    syncOptions.chapters,
                                ),
                            ),
                            localCategoriesMapByOrder,
                        )
                    } else {
                        logcat(LogPriority.DEBUG, logTag) {
                            "Keeping remote version of ${remote.title} with merged chapters."
                        }
                        updateCategories(
                            remote.copy(
                                chapters = mergeChapters(
                                    local.chapters,
                                    remote.chapters,
                                    lastSyncTime,
                                    syncOptions.chapters,
                                ),
                            ),
                            remoteCategoriesMapByOrder,
                        )
                    }
                }
                else -> null // No manga found for key
            }
        }

        // Counting favorites and non-favorites
        val (favorites, nonFavorites) = mergedList.partition { it.favorite }

        logcat(LogPriority.DEBUG, logTag) {
            "Merge completed. Total merged manga: ${mergedList.size}, Favorites: ${favorites.size}, " +
                "Non-Favorites: ${nonFavorites.size}"
        }

        return mergedList
    }

    /**
     * Merges two lists of BackupChapter objects, selecting the most recent chapter based on the version value.
     *
     * @param localChapters The list of local BackupChapter objects.
     * @param remoteChapters The list of remote BackupChapter objects.
     * @return A list of BackupChapter objects, each representing the most recent version of the chapter from either local or remote sources.
     */
    private fun mergeChapters(
        localChapters: List<BackupChapter>,
        remoteChapters: List<BackupChapter>,
        lastSyncTime: Long,
        syncingChapters: Boolean,
    ): List<BackupChapter> {
        val logTag = "MergeChapters"

        if (!syncingChapters) {
            return remoteChapters // If not syncing chapters, keep remote untouched
        }

        fun chapterCompositeKey(chapter: BackupChapter): String {
            return chapter.url
        }

        val localChapterMap = localChapters.associateBy { chapterCompositeKey(it) }
        val remoteChapterMap = remoteChapters.associateBy { chapterCompositeKey(it) }

        logcat(LogPriority.DEBUG, logTag) {
            "Starting chapter merge. Local chapters: ${localChapters.size}, Remote chapters: ${remoteChapters.size}"
        }

        // Merge both chapter maps based on version numbers
        val mergedChapters = (localChapterMap.keys + remoteChapterMap.keys).distinct().mapNotNull { compositeKey ->
            val localChapter = localChapterMap[compositeKey]
            val remoteChapter = remoteChapterMap[compositeKey]

            when {
                localChapter != null && remoteChapter == null -> {
                    if (lastSyncTime == 0L || localChapter.lastModifiedAt > lastSyncTime) {
                        logcat(LogPriority.DEBUG, logTag) { "Keeping local chapter: ${localChapter.name}." }
                        localChapter
                    } else {
                        logcat(LogPriority.DEBUG, logTag) {
                            "Dropping local chapter deleted on remote: ${localChapter.name}."
                        }
                        null
                    }
                }
                localChapter == null && remoteChapter != null -> {
                    if (lastSyncTime == 0L || remoteChapter.lastModifiedAt > lastSyncTime) {
                        logcat(LogPriority.DEBUG, logTag) { "Taking remote chapter: ${remoteChapter.name}." }
                        remoteChapter
                    } else {
                        logcat(LogPriority.DEBUG, logTag) {
                            "Dropping deleted remote chapter: ${remoteChapter.name}."
                        }
                        null
                    }
                }
                localChapter != null && remoteChapter != null -> {
                    // Use version number to decide which chapter to keep
                    val chosenChapter = if (localChapter.version >= remoteChapter.version) {
                        // If there are more chapters on remote, local sourceOrder will need to be updated to
                        // maintain correct source order.
                        if (localChapters.size < remoteChapters.size) {
                            localChapter.copy(sourceOrder = remoteChapter.sourceOrder)
                        } else {
                            localChapter
                        }
                    } else {
                        remoteChapter
                    }
                    chosenChapter
                }
                else -> null
            }
        }

        logcat(LogPriority.DEBUG, logTag) { "Chapter merge completed. Total merged chapters: ${mergedChapters.size}" }

        return mergedChapters
    }

    /**
     * Merges two lists of BackupCategory objects, using UID and name matching with version-based conflict resolution.
     *
     * @param localCategoriesList The list of local BackupCategory objects.
     * @param remoteCategoriesList The list of remote BackupCategory objects.
     * @return The merged list of BackupCategory objects.
     */
    private fun mergeCategoriesLists(
        localCategoriesList: List<BackupCategory>?,
        remoteCategoriesList: List<BackupCategory>?,
    ): List<BackupCategory> {
        val logTag = "MergeCategories"
        if (localCategoriesList == null) return remoteCategoriesList ?: emptyList()
        if (remoteCategoriesList == null) return localCategoriesList

        val result = mutableListOf<BackupCategory>()
        val processedLocals = mutableSetOf<BackupCategory>()

        val localMapByUid = localCategoriesList.filter { it.uid != 0L }.associateBy { it.uid }
        val localMapByName = localCategoriesList.associateBy { it.name }

        val lastSyncTime = syncPreferences.lastSyncTimestamp.get()

        remoteCategoriesList.forEach { remote ->
            var localMatch: BackupCategory? = null

            // 1. Try match by UID
            if (remote.uid != 0L) {
                localMatch = localMapByUid[remote.uid]
            }

            // 2. Try match by Name (fallback)
            if (localMatch == null) {
                localMatch = localMapByName[remote.name]
            }

            if (localMatch != null) {
                processedLocals.add(localMatch)
                // Conflict resolution
                if (localMatch.version >= remote.version) {
                    logcat(LogPriority.DEBUG, logTag) {
                        "Keeping local category: ${localMatch.name} (UID: ${localMatch.uid})"
                    }
                    result.add(localMatch)
                } else {
                    logcat(LogPriority.DEBUG, logTag) { "Keeping remote category: ${remote.name} (UID: ${remote.uid})" }
                    // Preserve Local UID if Remote was 0
                    if (remote.uid == 0L) {
                        remote.uid = localMatch.uid
                    }
                    result.add(remote)
                }
            } else {
                val remoteModifiedTimeMillis = remote.lastModifiedAt.seconds.inWholeMilliseconds
                if (lastSyncTime == 0L || remoteModifiedTimeMillis > lastSyncTime) {
                    logcat(LogPriority.DEBUG, logTag) {
                        "Adding new remote category: ${remote.name} (UID: ${remote.uid})"
                    }
                    result.add(remote)
                } else {
                    logcat(LogPriority.DEBUG, logTag) {
                        "Dropping deleted remote category: ${remote.name} (UID: ${remote.uid})"
                    }
                }
            }
        }

        // Add remaining Local Categories
        localCategoriesList.forEach { local ->
            if (local !in processedLocals) {
                val localModifiedTimeMillis = local.lastModifiedAt.seconds.inWholeMilliseconds
                if (lastSyncTime == 0L || localModifiedTimeMillis > lastSyncTime) {
                    logcat(LogPriority.DEBUG, logTag) {
                        "Keeping local only category: ${local.name} (UID: ${local.uid})"
                    }
                    result.add(local)
                } else {
                    logcat(LogPriority.DEBUG, logTag) {
                        "Dropping local category deleted on remote: ${local.name} (UID: ${local.uid})"
                    }
                }
            }
        }

        return result.sortedBy { it.order }
    }

    private fun mergeSourcesLists(
        localSources: List<BackupSource>?,
        remoteSources: List<BackupSource>?,
    ): List<BackupSource> {
        val logTag = "MergeSources"

        // Create maps using sourceId as key
        val localSourceMap = localSources?.associateBy { it.sourceId } ?: emptyMap()
        val remoteSourceMap = remoteSources?.associateBy { it.sourceId } ?: emptyMap()

        // Merge both source maps
        val mergedSources = (localSourceMap.keys + remoteSourceMap.keys).distinct().mapNotNull { sourceId ->
            val localSource = localSourceMap[sourceId]
            val remoteSource = remoteSourceMap[sourceId]

            when {
                localSource != null && remoteSource == null -> localSource
                remoteSource != null && localSource == null -> remoteSource
                else -> localSource
            }
        }

        logcat(LogPriority.DEBUG, logTag) { "Source merge completed. Total merged sources: ${mergedSources.size}" }

        return mergedSources
    }

    private fun mergePreferencesLists(
        localPreferences: List<BackupPreference>?,
        remotePreferences: List<BackupPreference>?,
    ): List<BackupPreference> {
        val logTag = "MergePreferences"

        // Create maps using key as the unique identifier
        val localPreferencesMap = localPreferences?.associateBy { it.key } ?: emptyMap()
        val remotePreferencesMap = remotePreferences?.associateBy { it.key } ?: emptyMap()

        // Merge both preferences maps
        val mergedPreferences = (localPreferencesMap.keys + remotePreferencesMap.keys).distinct().mapNotNull { key ->
            val localPreference = localPreferencesMap[key]
            val remotePreference = remotePreferencesMap[key]

            when {
                localPreference != null && remotePreference == null -> localPreference
                remotePreference != null && localPreference == null -> remotePreference
                else -> localPreference
            }
        }

        logcat(LogPriority.DEBUG, logTag) {
            "Preferences merge completed. Total merged preferences: ${mergedPreferences.size}"
        }

        return mergedPreferences
    }

    private fun mergeSourcePreferencesLists(
        localPreferences: List<BackupSourcePreferences>?,
        remotePreferences: List<BackupSourcePreferences>?,
    ): List<BackupSourcePreferences> {
        val logTag = "MergeSourcePreferences"

        // Create maps using sourceKey as the unique identifier
        val localPreferencesMap = localPreferences?.associateBy { it.sourceKey } ?: emptyMap()
        val remotePreferencesMap = remotePreferences?.associateBy { it.sourceKey } ?: emptyMap()

        // Merge both source preferences maps
        val mergedSourcePreferences = (localPreferencesMap.keys + remotePreferencesMap.keys).distinct()
            .mapNotNull { sourceKey ->
                val localSourcePreference = localPreferencesMap[sourceKey]
                val remoteSourcePreference = remotePreferencesMap[sourceKey]

                when {
                    localSourcePreference != null && remoteSourcePreference == null -> localSourcePreference
                    remoteSourcePreference != null && localSourcePreference == null -> remoteSourcePreference
                    localSourcePreference != null && remoteSourcePreference != null -> {
                        // Merge the individual preferences within the source preferences
                        val mergedPrefs =
                            mergeIndividualPreferences(localSourcePreference.prefs, remoteSourcePreference.prefs)
                        BackupSourcePreferences(sourceKey, mergedPrefs)
                    }
                    else -> null
                }
            }

        logcat(LogPriority.DEBUG, logTag) {
            "Source preferences merge completed. Total merged source preferences: ${mergedSourcePreferences.size}"
        }

        return mergedSourcePreferences
    }

    private fun mergeIndividualPreferences(
        localPrefs: List<BackupPreference>,
        remotePrefs: List<BackupPreference>,
    ): List<BackupPreference> {
        val mergedPrefsMap = (localPrefs + remotePrefs).associateBy { it.key }
        return mergedPrefsMap.values.toList()
    }

    private fun mergeExtensionStoresLists(
        localStores: List<BackupExtensionStore>?,
        remoteStores: List<BackupExtensionStore>?,
    ): List<BackupExtensionStore> {
        val logTag = "MergeExtensionStores"

        // Create maps using indexUrl as the unique identifier
        val localStoreMap = localStores?.associateBy { it.indexUrl } ?: emptyMap()
        val remoteStoreMap = remoteStores?.associateBy { it.indexUrl } ?: emptyMap()

        val mergedStores = (localStoreMap.keys + remoteStoreMap.keys).distinct().mapNotNull { indexUrl ->
            val localStore = localStoreMap[indexUrl]
            val remoteStore = remoteStoreMap[indexUrl]

            when {
                localStore != null && remoteStore == null -> localStore
                remoteStore != null && localStore == null -> remoteStore
                else -> localStore
            }
        }

        logcat(LogPriority.DEBUG, logTag) {
            "Extension store merge completed. Total merged extension stores: ${mergedStores.size}"
        }

        return mergedStores
    }
}
