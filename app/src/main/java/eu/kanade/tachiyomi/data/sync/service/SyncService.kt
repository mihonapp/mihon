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
    val backup: Backup? = null,
)

abstract class SyncService(
    val context: Context,
    val json: Json,
    val syncPreferences: SyncPreferences,
) {
    open suspend fun doSync(syncData: SyncData): Backup? {
        beforeSync()

        val remoteSData = pullSyncData()

        val finalSyncData =
            if (remoteSData == null) {
                pushSyncData(syncData)
                syncData
            } else {
                val mergedSyncData = mergeSyncData(syncData, remoteSData)
                pushSyncData(mergedSyncData)
                mergedSyncData
            }

        return finalSyncData.backup
    }

    /**
     * For refreshing tokens and other possible operations before connecting to the remote storage
     */
    open suspend fun beforeSync() {}

    /**
     * Download sync data from the remote storage
     */
    abstract suspend fun pullSyncData(): SyncData?

    /**
     * Upload sync data to the remote storage
     */
    abstract suspend fun pushSyncData(syncData: SyncData)

    /**
     * Merges the local and remote sync data into a single JSON string.
     *
     * @param localSyncData The SData containing the local sync data.
     * @param remoteSyncData The SData containing the remote sync data.
     * @return The JSON string containing the merged sync data.
     */
    private fun mergeSyncData(localSyncData: SyncData, remoteSyncData: SyncData): SyncData {
        val mergedMangaList = mergeMangaLists(localSyncData.backup?.backupManga, remoteSyncData.backup?.backupManga)
        val mergedCategoriesList =
            mergeCategoriesLists(localSyncData.backup?.backupCategories, remoteSyncData.backup?.backupCategories)

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
            backup = mergedBackup,
        )
    }

    private fun mergeMangaLists(
        localMangaList: List<BackupManga>?,
        remoteMangaList: List<BackupManga>?,
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

        logcat(LogPriority.DEBUG, logTag) {
            "Starting merge. Local list size: ${localMangaListSafe.size}, Remote list size: ${remoteMangaListSafe.size}"
        }

        val mergedList = (localMangaMap.keys + remoteMangaMap.keys).distinct().mapNotNull { compositeKey ->
            val local = localMangaMap[compositeKey]
            val remote = remoteMangaMap[compositeKey]

            // New version comparison logic
            when {
                local != null && remote == null -> local
                local == null && remote != null -> remote
                local != null && remote != null -> {
                    // Compare versions to decide which manga to keep
                    if (local.version >= remote.version) {
                        logcat(LogPriority.DEBUG, logTag) { "Keeping local version of ${local.title} with merged chapters." }
                        local.copy(chapters = mergeChapters(local.chapters, remote.chapters))
                    } else {
                        logcat(LogPriority.DEBUG, logTag) { "Keeping remote version of ${remote.title} with merged chapters." }
                        remote.copy(chapters = mergeChapters(local.chapters, remote.chapters))
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
                    val chosenChapter = if (localChapter.version >= remoteChapter.version) localChapter else remoteChapter
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
        remoteSources: List<BackupSource>?,
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
        remotePreferences: List<BackupPreference>?,
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
        remotePreferences: List<BackupSourcePreferences>?,
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
        val mergedSourcePreferences = (localPreferencesMap.keys + remotePreferencesMap.keys).distinct().mapNotNull {
                sourceKey ->
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
        remotePrefs: List<BackupPreference>,
    ): List<BackupPreference> {
        val mergedPrefsMap = (localPrefs + remotePrefs).associateBy { it.key }
        return mergedPrefsMap.values.toList()
    }
}
