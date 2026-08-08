package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupTracking

/**
 * Combines the local snapshot with the one stored on the remote so no device loses data.
 *
 * Conflicts are settled per record with the `lastModifiedAt` timestamps the app already maintains,
 * so un-marking a chapter as read on one device is not undone by the other device's stale state.
 * Falling back to a union (rather than to whole-snapshot last-write-wins) is what keeps a device
 * that has been offline for a while from wiping entries added elsewhere.
 */
object SyncMerger {

    fun merge(local: Backup, remote: Backup): Backup {
        val categories = mergeCategories(local.backupCategories, remote.backupCategories)
        val remoteCategoryOrders = remoteCategoryOrderMapping(remote.backupCategories, categories)

        return Backup(
            backupManga = mergeManga(local.backupManga, remote.backupManga, remoteCategoryOrders),
            backupCategories = categories,
            // Sources are just a id/name lookup table for error messages, a union is enough
            backupSources = (local.backupSources + remote.backupSources).distinctBy { it.sourceId },
            // App and source settings stay device-local on purpose: syncing them would carry over
            // things like the storage location or reader layout picked for another screen size
            backupPreferences = local.backupPreferences,
            backupSourcePreferences = local.backupSourcePreferences,
            backupExtensionStores = (local.backupExtensionStores + remote.backupExtensionStores)
                .distinctBy { it.indexUrl },
        )
    }

    private fun mergeCategories(local: List<BackupCategory>, remote: List<BackupCategory>): List<BackupCategory> {
        val merged = local.toMutableList()
        var nextOrder = (local.maxOfOrNull { it.order } ?: -1L) + 1

        val localNames = local.mapTo(mutableSetOf()) { it.name }
        remote.sortedBy { it.order }
            .filter { it.name !in localNames }
            .forEach { merged += BackupCategory(name = it.name, order = nextOrder++, flags = it.flags) }

        return merged
    }

    /**
     * Manga reference categories by order, and the same order can mean a different category on
     * each device, so remote orders have to be translated into the merged numbering.
     */
    private fun remoteCategoryOrderMapping(
        remote: List<BackupCategory>,
        merged: List<BackupCategory>,
    ): Map<Long, Long> {
        val mergedOrderByName = merged.associate { it.name to it.order }
        return remote.mapNotNull { category ->
            mergedOrderByName[category.name]?.let { category.order to it }
        }.toMap()
    }

    private fun mergeManga(
        local: List<BackupManga>,
        remote: List<BackupManga>,
        remoteCategoryOrders: Map<Long, Long>,
    ): List<BackupManga> {
        val remoteByKey = remote.associateBy { it.source to it.url }
        val merged = mutableListOf<BackupManga>()

        local.forEach { localManga ->
            val remoteManga = remoteByKey[localManga.source to localManga.url]
            merged += if (remoteManga == null) {
                localManga
            } else {
                mergeManga(localManga, remoteManga, remoteCategoryOrders)
            }
        }

        val localKeys = local.mapTo(mutableSetOf()) { it.source to it.url }
        remote.filter { (it.source to it.url) !in localKeys }
            .forEach { merged += it.remapCategories(remoteCategoryOrders) }

        return merged
    }

    private fun mergeManga(
        local: BackupManga,
        remote: BackupManga,
        remoteCategoryOrders: Map<Long, Long>,
    ): BackupManga {
        val newest = if (local.lastModifiedAt >= remote.lastModifiedAt) local else remote
        val oldest = if (newest === local) remote else local

        // Favorite state carries its own timestamp, so it is resolved independently of the metadata
        val favoriteWinner = when {
            local.favoriteModifiedAt == null -> remote
            remote.favoriteModifiedAt == null -> local
            local.favoriteModifiedAt!! >= remote.favoriteModifiedAt!! -> local
            else -> remote
        }

        val remoteCategories = remote.categories.mapNotNull { remoteCategoryOrders[it] }

        return BackupManga(
            source = newest.source,
            url = newest.url,
            title = newest.title,
            artist = newest.artist,
            author = newest.author,
            description = newest.description,
            genre = newest.genre,
            status = newest.status,
            thumbnailUrl = newest.thumbnailUrl ?: oldest.thumbnailUrl,
            dateAdded = minOfNonZero(local.dateAdded, remote.dateAdded),
            viewer = newest.viewer,
            chapters = mergeChapters(local.chapters, remote.chapters),
            categories = (local.categories + remoteCategories).distinct(),
            tracking = mergeTracking(local.tracking, remote.tracking),
            favorite = favoriteWinner.favorite,
            chapterFlags = newest.chapterFlags,
            viewer_flags = newest.viewer_flags ?: oldest.viewer_flags,
            history = mergeHistory(local.history, remote.history),
            updateStrategy = newest.updateStrategy,
            lastModifiedAt = maxOf(local.lastModifiedAt, remote.lastModifiedAt),
            favoriteModifiedAt = favoriteWinner.favoriteModifiedAt,
            excludedScanlators = (local.excludedScanlators + remote.excludedScanlators).distinct(),
            version = maxOf(local.version, remote.version),
            notes = newest.notes.ifBlank { oldest.notes },
            initialized = local.initialized || remote.initialized,
            memo = newest.memo,
        )
    }

    private fun mergeChapters(local: List<BackupChapter>, remote: List<BackupChapter>): List<BackupChapter> {
        val remoteByUrl = remote.associateBy { it.url }
        val merged = local.map { localChapter ->
            val remoteChapter = remoteByUrl[localChapter.url] ?: return@map localChapter
            mergeChapter(localChapter, remoteChapter)
        }

        val localUrls = local.mapTo(mutableSetOf()) { it.url }
        return merged + remote.filter { it.url !in localUrls }
    }

    private fun mergeChapter(local: BackupChapter, remote: BackupChapter): BackupChapter {
        // Read state is only meaningful as a whole: taking `read` from one side and `lastPageRead`
        // from the other would resurrect progress the user deliberately cleared
        val newest = if (local.lastModifiedAt >= remote.lastModifiedAt) local else remote
        val oldest = if (newest === local) remote else local

        return BackupChapter(
            url = newest.url,
            name = newest.name,
            scanlator = newest.scanlator ?: oldest.scanlator,
            read = newest.read,
            bookmark = newest.bookmark,
            lastPageRead = newest.lastPageRead,
            dateFetch = minOfNonZero(local.dateFetch, remote.dateFetch),
            dateUpload = maxOf(local.dateUpload, remote.dateUpload),
            chapterNumber = newest.chapterNumber,
            sourceOrder = newest.sourceOrder,
            lastModifiedAt = maxOf(local.lastModifiedAt, remote.lastModifiedAt),
            version = maxOf(local.version, remote.version),
            memo = newest.memo,
        )
    }

    private fun mergeHistory(local: List<BackupHistory>, remote: List<BackupHistory>): List<BackupHistory> {
        val remoteByUrl = remote.associateBy { it.url }
        val merged = local.map { localHistory ->
            val remoteHistory = remoteByUrl[localHistory.url] ?: return@map localHistory
            BackupHistory(
                url = localHistory.url,
                lastRead = maxOf(localHistory.lastRead, remoteHistory.lastRead),
                // Reading time is cumulative per device, so the larger total is the closest to truth
                readDuration = maxOf(localHistory.readDuration, remoteHistory.readDuration),
            )
        }

        val localUrls = local.mapTo(mutableSetOf()) { it.url }
        return merged + remote.filter { it.url !in localUrls }
    }

    @Suppress("DEPRECATION")
    private fun mergeTracking(local: List<BackupTracking>, remote: List<BackupTracking>): List<BackupTracking> {
        val key = { track: BackupTracking ->
            track.syncId to (track.mediaId.takeIf { it != 0L } ?: track.mediaIdInt.toLong())
        }
        val remoteByKey = remote.associateBy(key)

        val merged = local.map { localTrack ->
            val remoteTrack = remoteByKey[key(localTrack)] ?: return@map localTrack
            if (localTrack.lastChapterRead >= remoteTrack.lastChapterRead) localTrack else remoteTrack
        }

        val localKeys = local.mapTo(mutableSetOf(), key)
        return merged + remote.filter { key(it) !in localKeys }
    }

    private fun BackupManga.remapCategories(remoteCategoryOrders: Map<Long, Long>) = apply {
        categories = categories.mapNotNull { remoteCategoryOrders[it] }
    }

    /** Creation timestamps should keep the earliest known value, ignoring unset ones. */
    private fun minOfNonZero(first: Long, second: Long): Long = when {
        first == 0L -> second
        second == 0L -> first
        else -> minOf(first, second)
    }
}
