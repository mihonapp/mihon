package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tachiyomi.domain.sync.SyncPreferences
import java.time.Instant

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
    fun mergeSyncData(localSyncData: SyncData, remoteSyncData: SyncData): SyncData {
        val mergedMangaList = mergeMangaLists(localSyncData.backup?.backupManga, remoteSyncData.backup?.backupManga)
        val mergedCategoriesList =
            mergeCategoriesLists(localSyncData.backup?.backupCategories, remoteSyncData.backup?.backupCategories)

        // Create the merged Backup object
        val mergedBackup = Backup(
            backupManga = mergedMangaList,
            backupCategories = mergedCategoriesList,
            backupBrokenSources = localSyncData.backup?.backupBrokenSources ?: emptyList(),
            backupSources = localSyncData.backup?.backupSources ?: emptyList(),
        )

        // Create the merged SData object
        return SyncData(
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
    ): List<BackupManga> {
        // Convert null lists to empty to simplify logic
        val localMangaListSafe = localMangaList.orEmpty()
        val remoteMangaListSafe = remoteMangaList.orEmpty()

        // Associate both local and remote manga by their unique keys (source and url)
        val localMangaMap = localMangaListSafe.associateBy { Pair(it.source, it.url) }
        val remoteMangaMap = remoteMangaListSafe.associateBy { Pair(it.source, it.url) }

        // Prepare to merge both sets of manga
        return (localMangaMap.keys + remoteMangaMap.keys).mapNotNull { key ->
            val local = localMangaMap[key]
            val remote = remoteMangaMap[key]

            when {
                local != null && remote == null -> local
                local == null && remote != null -> remote
                local != null && remote != null -> {
                    // Compare last modified times and merge chapters
                    val localTime = Instant.ofEpochMilli(local.lastModifiedAt)
                    val remoteTime = Instant.ofEpochMilli(remote.lastModifiedAt)
                    val mergedChapters = mergeChapters(local.chapters, remote.chapters)

                    if (localTime >= remoteTime) {
                        local.copy(chapters = mergedChapters)
                    } else {
                        remote.copy(chapters = mergedChapters)
                    }
                }
                else -> null
            }
        }
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
        // Associate chapters by URL for both local and remote
        val localChapterMap = localChapters.associateBy { it.url }
        val remoteChapterMap = remoteChapters.associateBy { it.url }

        // Merge both chapter maps
        return (localChapterMap.keys + remoteChapterMap.keys).mapNotNull { url ->
            // Determine the most recent chapter by comparing lastModifiedAt, considering null as Instant.MIN
            val localChapter = localChapterMap[url]
            val remoteChapter = remoteChapterMap[url]

            when {
                localChapter != null && remoteChapter == null -> localChapter
                localChapter == null && remoteChapter != null -> remoteChapter
                localChapter != null && remoteChapter != null -> {
                    val localInstant = localChapter.lastModifiedAt.let { Instant.ofEpochMilli(it) } ?: Instant.MIN
                    val remoteInstant = remoteChapter.lastModifiedAt.let { Instant.ofEpochMilli(it) } ?: Instant.MIN
                    if (localInstant >= remoteInstant) localChapter else remoteChapter
                }
                else -> null
            }
        }
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
}
