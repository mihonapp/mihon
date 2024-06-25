package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat

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
    abstract suspend fun doSync(syncData: SyncData): Backup?;

    /**
     * Merges the local and remote sync data into a single JSON string.
     *
     * @param localSyncData The SData containing the local sync data.
     * @param remoteSyncData The SData containing the remote sync data.
     * @return The JSON string containing the merged sync data.
     */
    protected fun mergeSyncData(localSyncData: SyncData, remoteSyncData: SyncData): SyncData {
        val mergedCategoriesList =
            mergeCategoriesLists(localSyncData.backup?.backupCategories, remoteSyncData.backup?.backupCategories)

        val mergedMangaList = mergeMangaLists(
            localSyncData.backup?.backupManga,
            remoteSyncData.backup?.backupManga,
            localSyncData.backup?.backupCategories ?: emptyList(),
            remoteSyncData.backup?.backupCategories ?: emptyList(),
            mergedCategoriesList)

        val mergedSourcesList =
            mergeSourcesLists(localSyncData.backup?.backupSources, remoteSyncData.backup?.backupSources)
        val mergedPreferencesList =
            mergePreferencesLists(localSyncData.backup?.backupPreferences, remoteSyncData.backup?.backupPreferences)
        val mergedSourcePreferencesList = mergeSourcePreferencesLists(
            localSyncData.backup?.backupSourcePreferences,
            remoteSyncData.backup?.backupSourcePreferences,
        )


        // Create the merged Backup object
        val mergedBackup = Backup(
            backupManga = mergedMangaList,
            backupCategories = mergedCategoriesList,
            backupSources = mergedSourcesList,
            backupPreferences = mergedPreferencesList,
            backupSourcePreferences = mergedSourcePreferencesList,

        )

        // Create the merged SData object
        return SyncData(
            deviceId = syncPreferences.uniqueDeviceID(),
            backup = mergedBackup,
        )
    }

    /**
     * Merges two lists of BackupManga objects, selecting the most recent manga based on the lastModifiedAt value.
     * If lastModifiedAt is null for a manga, it treats that manga as the oldest possible for comparison purposes.
     * This function is designed to reconcile local and remote manga lists, ensuring the most up-to-date manga is retained.
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
            return "${manga.source}|${manga.url}|${manga.title.lowercase().trim()}|${manga.author?.lowercase()?.trim()}"
        }

        // Create maps using composite keys
        val localMangaMap = localMangaListSafe.associateBy { mangaCompositeKey(it) }
        val remoteMangaMap = remoteMangaListSafe.associateBy { mangaCompositeKey(it) }

        val localCategoriesMapByOrder = localCategories.associateBy { it.order }
        val remoteCategoriesMapByOrder = remoteCategories.associateBy { it.order }
        val mergedCategoriesMapByName = mergedCategories.associateBy { it.name }

        fun updateCategories(theManga: BackupManga, theMap: Map<Long, BackupCategory>): BackupManga {
            return theManga.copy(categories = theManga.categories.mapNotNull {
                theMap[it]?.let { category ->
                    mergedCategoriesMapByName[category.name]?.order
                }
            })
        }

        logcat(LogPriority.DEBUG, logTag) {
            "Starting merge. Local list size: ${localMangaListSafe.size}, Remote list size: ${remoteMangaListSafe.size}"
        }

        val mergedList = (localMangaMap.keys + remoteMangaMap.keys).distinct().mapNotNull { compositeKey ->
            val local = localMangaMap[compositeKey]
            val remote = remoteMangaMap[compositeKey]

            // New version comparison logic
            when {
                local != null && remote == null -> updateCategories(local, localCategoriesMapByOrder)
                local == null && remote != null -> updateCategories(remote, remoteCategoriesMapByOrder)
                local != null && remote != null -> {
                    // Compare versions to decide which manga to keep
                    if (local.version >= remote.version) {
                        logcat(LogPriority.DEBUG, logTag) {
                            "Keeping local version of ${local.title} with merged chapters."
                        }
                        updateCategories(
                            local.copy(chapters = mergeChapters(local.chapters, remote.chapters)),
                            localCategoriesMapByOrder
                        )
                    } else {
                        logcat(LogPriority.DEBUG, logTag) {
                            "Keeping remote version of ${remote.title} with merged chapters."
                        }
                        updateCategories(
                            remote.copy(chapters = mergeChapters(local.chapters, remote.chapters)),
                            remoteCategoriesMapByOrder
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
     * Merges two lists of BackupChapter objects, selecting the most recent chapter based on the lastModifiedAt value.
     * If lastModifiedAt is null for a chapter, it treats that chapter as the oldest possible for comparison purposes.
     * This function is designed to reconcile local and remote chapter lists, ensuring the most up-to-date chapter is retained.
     *
     * @param localChapters The list of local BackupChapter objects.
     * @param remoteChapters The list of remote BackupChapter objects.
     * @return A list of BackupChapter objects, each representing the most recent version of the chapter from either local or remote sources.
     *
     * - This function is used in scenarios where local and remote chapter lists need to be synchronized.
     * - It iterates over the union of the URLs from both local and remote chapters.
     * - For each URL, it compares the corresponding local and remote chapters based on the lastModifiedAt value.
     * - If only one source (local or remote) has the chapter for a URL, that chapter is used.
     * - If both sources have the chapter, the one with the more recent lastModifiedAt value is chosen.
     * - If lastModifiedAt is null or missing, the chapter is considered the oldest for safety, ensuring that any chapter with a valid timestamp is preferred.
     * - The resulting list contains the most recent chapters from the combined set of local and remote chapters.
     */
    private fun mergeChapters(
        localChapters: List<BackupChapter>,
        remoteChapters: List<BackupChapter>,
    ): List<BackupChapter> {
        val logTag = "MergeChapters"

        fun chapterCompositeKey(chapter: BackupChapter): String {
            return "${chapter.url}|${chapter.name}|${chapter.chapterNumber}"
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

            logcat(LogPriority.DEBUG, logTag) {
                "Processing chapter key: $compositeKey. Local chapter: ${localChapter != null}, " +
                    "Remote chapter: ${remoteChapter != null}"
            }

            when {
                localChapter != null && remoteChapter == null -> {
                    logcat(LogPriority.DEBUG, logTag) { "Keeping local chapter: ${localChapter.name}." }
                    localChapter
                }
                localChapter == null && remoteChapter != null -> {
                    logcat(LogPriority.DEBUG, logTag) { "Taking remote chapter: ${remoteChapter.name}." }
                    remoteChapter
                }
                localChapter != null && remoteChapter != null -> {
                    // Use version number to decide which chapter to keep
                    val chosenChapter = if (localChapter.version >= remoteChapter.version) {
                        localChapter
                    } else {
                        remoteChapter
                    }
                    logcat(LogPriority.DEBUG, logTag) {
                        "Merging chapter: ${chosenChapter.name}. Chosen version from: ${
                            if (localChapter.version >= remoteChapter.version) "Local" else "Remote"
                        }, Local version: ${localChapter.version}, Remote version: ${remoteChapter.version}."
                    }
                    chosenChapter
                }
                else -> {
                    logcat(LogPriority.DEBUG, logTag) {
                        "No chapter found for composite key: $compositeKey. Skipping."
                    }
                    null
                }
            }
        }

        logcat(LogPriority.DEBUG, logTag) { "Chapter merge completed. Total merged chapters: ${mergedChapters.size}" }

        return mergedChapters
    }

    /**
     * Merges two lists of SyncCategory objects, prioritizing the category with the most recent order value.
     *
     * @param localCategoriesList The list of local SyncCategory objects.
     * @param remoteCategoriesList The list of remote SyncCategory objects.
     * @return The merged list of SyncCategory objects.
     */
    private fun mergeCategoriesLists(
        localCategoriesList: List<BackupCategory>?,
        remoteCategoriesList: List<BackupCategory>?,
    ): List<BackupCategory> {
        if (localCategoriesList == null) return remoteCategoriesList ?: emptyList()
        if (remoteCategoriesList == null) return localCategoriesList
        val localCategoriesMap = localCategoriesList.associateBy { it.name }
        val remoteCategoriesMap = remoteCategoriesList.associateBy { it.name }

        val mergedCategoriesMap = mutableMapOf<String, BackupCategory>()

        localCategoriesMap.forEach { (name, localCategory) ->
            val remoteCategory = remoteCategoriesMap[name]
            if (remoteCategory != null) {
                // Compare and merge local and remote categories
                val mergedCategory = if (localCategory.order > remoteCategory.order) {
                    localCategory
                } else {
                    remoteCategory
                }
                mergedCategoriesMap[name] = mergedCategory
            } else {
                // If the category is only in the local list, add it to the merged list
                mergedCategoriesMap[name] = localCategory
            }
        }

        // Add any categories from the remote list that are not in the local list
        remoteCategoriesMap.forEach { (name, remoteCategory) ->
            if (!mergedCategoriesMap.containsKey(name)) {
                mergedCategoriesMap[name] = remoteCategory
            }
        }

        return mergedCategoriesMap.values.toList()
    }

    private fun mergeSourcesLists(
        localSources: List<BackupSource>?,
        remoteSources: List<BackupSource>?
    ): List<BackupSource> {
        val logTag = "MergeSources"

        // Create maps using sourceId as key
        val localSourceMap = localSources?.associateBy { it.sourceId } ?: emptyMap()
        val remoteSourceMap = remoteSources?.associateBy { it.sourceId } ?: emptyMap()

        logcat(LogPriority.DEBUG, logTag) {
            "Starting source merge. Local sources: ${localSources?.size}, Remote sources: ${remoteSources?.size}"
        }

        // Merge both source maps
        val mergedSources = (localSourceMap.keys + remoteSourceMap.keys).distinct().mapNotNull { sourceId ->
            val localSource = localSourceMap[sourceId]
            val remoteSource = remoteSourceMap[sourceId]

            logcat(LogPriority.DEBUG, logTag) {
                "Processing source ID: $sourceId. Local source: ${localSource != null}, " +
                    "Remote source: ${remoteSource != null}"
            }

            when {
                localSource != null && remoteSource == null -> {
                    logcat(LogPriority.DEBUG, logTag) { "Using local source: ${localSource.name}." }
                    localSource
                }
                remoteSource != null && localSource == null -> {
                    logcat(LogPriority.DEBUG, logTag) { "Using remote source: ${remoteSource.name}." }
                    remoteSource
                }
                else -> {
                    logcat(LogPriority.DEBUG, logTag) { "Remote and local is not empty: $sourceId. Skipping." }
                    null
                }
            }
        }

        logcat(LogPriority.DEBUG, logTag) { "Source merge completed. Total merged sources: ${mergedSources.size}" }

        return mergedSources
    }

    private fun mergePreferencesLists(
        localPreferences: List<BackupPreference>?,
        remotePreferences: List<BackupPreference>?
    ): List<BackupPreference> {
        val logTag = "MergePreferences"

        // Create maps using key as the unique identifier
        val localPreferencesMap = localPreferences?.associateBy { it.key } ?: emptyMap()
        val remotePreferencesMap = remotePreferences?.associateBy { it.key } ?: emptyMap()

        logcat(LogPriority.DEBUG, logTag) {
            "Starting preferences merge. Local preferences: ${localPreferences?.size}, " +
                "Remote preferences: ${remotePreferences?.size}"
        }

        // Merge both preferences maps
        val mergedPreferences = (localPreferencesMap.keys + remotePreferencesMap.keys).distinct().mapNotNull { key ->
            val localPreference = localPreferencesMap[key]
            val remotePreference = remotePreferencesMap[key]

            logcat(LogPriority.DEBUG, logTag) {
                "Processing preference key: $key. Local preference: ${localPreference != null}, " +
                    "Remote preference: ${remotePreference != null}"
            }

            when {
                localPreference != null && remotePreference == null -> {
                    logcat(LogPriority.DEBUG, logTag) { "Using local preference: ${localPreference.key}." }
                    localPreference
                }
                remotePreference != null && localPreference == null -> {
                    logcat(LogPriority.DEBUG, logTag) { "Using remote preference: ${remotePreference.key}." }
                    remotePreference
                }
                else -> {
                    logcat(LogPriority.DEBUG, logTag) { "Both remote and local have keys. Skipping: $key" }
                    null
                }
            }
        }

        logcat(LogPriority.DEBUG, logTag) {
            "Preferences merge completed. Total merged preferences: ${mergedPreferences.size}"
        }

        return mergedPreferences
    }

    private fun mergeSourcePreferencesLists(
        localPreferences: List<BackupSourcePreferences>?,
        remotePreferences: List<BackupSourcePreferences>?
    ): List<BackupSourcePreferences> {
        val logTag = "MergeSourcePreferences"

        // Create maps using sourceKey as the unique identifier
        val localPreferencesMap = localPreferences?.associateBy { it.sourceKey } ?: emptyMap()
        val remotePreferencesMap = remotePreferences?.associateBy { it.sourceKey } ?: emptyMap()

        logcat(LogPriority.DEBUG, logTag) {
            "Starting source preferences merge. Local source preferences: ${localPreferences?.size}, " +
                "Remote source preferences: ${remotePreferences?.size}"
        }

        // Merge both source preferences maps
        val mergedSourcePreferences = (localPreferencesMap.keys + remotePreferencesMap.keys).distinct().mapNotNull { sourceKey ->
            val localSourcePreference = localPreferencesMap[sourceKey]
            val remoteSourcePreference = remotePreferencesMap[sourceKey]

            logcat(LogPriority.DEBUG, logTag) {
                "Processing source preference key: $sourceKey. " +
                    "Local source preference: ${localSourcePreference != null}, " +
                    "Remote source preference: ${remoteSourcePreference != null}"
            }

            when {
                localSourcePreference != null && remoteSourcePreference == null -> {
                    logcat(LogPriority.DEBUG, logTag) {
                        "Using local source preference: ${localSourcePreference.sourceKey}."
                    }
                    localSourcePreference
                }
                remoteSourcePreference != null && localSourcePreference == null -> {
                    logcat(LogPriority.DEBUG, logTag) {
                        "Using remote source preference: ${remoteSourcePreference.sourceKey}."
                    }
                    remoteSourcePreference
                }
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
        remotePrefs: List<BackupPreference>
    ): List<BackupPreference> {
        val mergedPrefsMap = (localPrefs + remotePrefs).associateBy { it.key }
        return mergedPrefsMap.values.toList()
    }
}
