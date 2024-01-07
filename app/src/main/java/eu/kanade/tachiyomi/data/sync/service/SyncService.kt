package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
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
        val logTag = "MergeMangaLists"

        // Convert null lists to empty to simplify logic
        val localMangaListSafe = localMangaList.orEmpty()
        val remoteMangaListSafe = remoteMangaList.orEmpty()

        logcat(logTag, LogPriority.DEBUG) {
            "Starting merge. Local list size: ${localMangaListSafe.size}, Remote list size: ${remoteMangaListSafe.size}"
        }

        // Define a function to create a composite key from manga
        fun mangaCompositeKey(manga: BackupManga): String {
            return "${manga.source}|${manga.url}|${manga.title.lowercase().trim()}|${manga.author?.lowercase()?.trim()}"
        }

        // Create maps using composite keys
        val localMangaMap = localMangaListSafe.associateBy { mangaCompositeKey(it) }
        val remoteMangaMap = remoteMangaListSafe.associateBy { mangaCompositeKey(it) }

        logcat(LogPriority.DEBUG, logTag) {
            "Starting merge. Local list size: ${localMangaListSafe.size}, Remote list size: ${remoteMangaListSafe.size}"
        }

        // Prepare to merge both sets of manga
        val mergedList = (localMangaMap.keys + remoteMangaMap.keys).distinct().mapNotNull { compositeKey ->
            val local = localMangaMap[compositeKey]
            val remote = remoteMangaMap[compositeKey]

            logcat(LogPriority.DEBUG, logTag) {
                "Processing key: $compositeKey. Local favorite: ${local?.favorite}, " +
                    "Remote favorite: ${remote?.favorite}"
            }

            when {
                local != null && remote == null -> {
                    logcat(LogPriority.DEBUG, logTag) {
                        "Taking local manga: ${local.title} as it is not present remotely. " +
                            "Favorite status: ${local.favorite}"
                    }
                    local
                }
                local == null && remote != null -> {
                    logcat(LogPriority.DEBUG, logTag) {
                        "Taking remote manga: ${remote.title} as it is not present locally. " +
                            "Favorite status: ${remote.favorite}"
                    }
                    remote
                }
                local != null && remote != null -> {
                    logcat(LogPriority.DEBUG, logTag) {
                        "Inspecting timestamps for ${local.title}. Local lastModifiedAt: ${local.lastModifiedAt}, " +
                            "Remote lastModifiedAt: ${remote.lastModifiedAt}"
                    }
                    // Convert seconds to milliseconds for accurate time comparison
                    val localTime = Instant.ofEpochMilli(local.lastModifiedAt * 1000L)
                    val remoteTime = Instant.ofEpochMilli(remote.lastModifiedAt * 1000L)
                    val mergedChapters = mergeChapters(local.chapters, remote.chapters)

                    logcat(LogPriority.DEBUG, logTag) {
                        "Merging manga: ${local.title}. Local time: $localTime, Remote time: $remoteTime, " +
                            "Local favorite: ${local.favorite}, Remote favorite: ${remote.favorite}"
                    }

                    if (localTime >= remoteTime) {
                        logcat(
                            LogPriority.DEBUG,
                            logTag,
                        ) { "Keeping local version of ${local.title} with merged chapters." }
                        local.copy(chapters = mergedChapters)
                    } else {
                        logcat(
                            LogPriority.DEBUG,
                            logTag,
                        ) { "Keeping remote version of ${remote.title} with merged chapters." }
                        remote.copy(chapters = mergedChapters)
                    }
                }
                else -> {
                    logcat(LogPriority.DEBUG, logTag) { "No manga found for key: $compositeKey. Skipping." }
                    null
                }
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

        // Define a function to create a composite key from a chapter
        fun chapterCompositeKey(chapter: BackupChapter): String {
            return "${chapter.url}|${chapter.name}|${chapter.chapterNumber}"
        }

        // Create maps using composite keys
        val localChapterMap = localChapters.associateBy { chapterCompositeKey(it) }
        val remoteChapterMap = remoteChapters.associateBy { chapterCompositeKey(it) }

        logcat(LogPriority.DEBUG, logTag) {
            "Starting chapter merge. Local chapters: ${localChapters.size}, Remote chapters: ${remoteChapters.size}"
        }

        // Merge both chapter maps
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
                    val localInstant = Instant.ofEpochMilli(localChapter.lastModifiedAt * 1000L)
                    val remoteInstant = Instant.ofEpochMilli(remoteChapter.lastModifiedAt * 1000L)

                    val chosenChapter = if (localInstant >= remoteInstant) localChapter else remoteChapter
                    logcat(LogPriority.DEBUG, logTag) {
                        "Merging chapter: ${chosenChapter.name}. Chosen from: ${if (localInstant >= remoteInstant) {
                            "Local"
                        } else {
                            "Remote"
                        }}."
                    }
                    chosenChapter
                }
                else -> {
                    logcat(LogPriority.DEBUG, logTag) { "No chapter found for composite key: $compositeKey. Skipping." }
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
}
