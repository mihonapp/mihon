package mihon.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.sync.drive.DriveFile
import mihon.sync.drive.GoogleDriveApi
import mihon.sync.merge.SyncCategoryMerge
import mihon.sync.merge.SyncCategoryMerge.Catalogue
import mihon.sync.merge.SyncCategoryMerge.Snapshot
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.DeleteCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.NewCategory
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.service.LibraryPreferences
import kotlin.time.Clock

/**
 * Keeps the account's categories — which exist, what they are called, their order and display
 * settings — the same on every device, through one shared list on Drive.
 *
 * See [SyncCategoryMerge] for the rules. This class only moves the list between Drive, the database
 * and the snapshot this device keeps of what it last agreed with.
 */
@Inject
@SingleIn(AppScope::class)
class SyncCategories(
    private val driveApi: GoogleDriveApi,
    private val syncPreferences: SyncPreferences,
    private val documents: SyncDocuments,
    private val codec: SyncCodec,
    private val json: Json,
    private val categoryRepository: CategoryRepository,
    private val deleteCategory: DeleteCategory,
    private val libraryPreferences: LibraryPreferences,
) {

    /**
     * Brings the shared list and this device's categories into agreement, and returns what the
     * library shards need to refer to categories by id.
     */
    suspend fun reconcile(rootId: String, listing: SyncRootListing): SyncCategoryIndex {
        val snapshot = readSnapshot()
        val remote = readRemote(listing, snapshot)
        val now = Clock.System.now().toEpochMilliseconds()

        val view = SyncCategoryMerge.localView(categoryRepository.getAll(), snapshot, remote.catalogue, now)
        val merged = SyncCategoryMerge.merge(remote.catalogue, view.catalogue)

        val written = if (remote.file != null && merged == remote.catalogue) {
            remote.file to null
        } else {
            val content = json.encodeToString(merged).toByteArray()
            driveApi.upsert(
                name = SyncLayout.CATEGORY_LIST_FILE,
                parentId = rootId,
                content = content,
                knownId = remote.file?.id,
                mimeType = GoogleDriveApi.JSON_MIME,
            ) to content
        }

        val localIds = apply(merged, view.localIds)
        syncPreferences.categorySnapshot().set(json.encodeToString(Snapshot(merged, localIds)))
        documents.record(SyncLayout.CATEGORY_LIST_FILE, written.first, written.second)

        return SyncCategoryIndex(localIds)
    }

    private class Remote(val file: DriveFile?, val catalogue: Catalogue)

    private suspend fun readRemote(listing: SyncRootListing, snapshot: Snapshot?): Remote {
        val file = listing[SyncLayout.CATEGORY_LIST_FILE] ?: return Remote(null, readLegacy(listing))

        // Drive still holds the version this device settled last time, and that list is exactly what
        // the snapshot kept: nothing to download.
        if (snapshot != null && documents.isUnchanged(SyncLayout.CATEGORY_LIST_FILE, file)) {
            return Remote(file, snapshot.catalogue)
        }

        // Deleted since the listing: write it again from what this device knows.
        val text = driveApi.downloadText(file.id) ?: return Remote(null, Catalogue())

        // An unreadable list is replaced rather than left to fail every round. Nothing is lost for
        // good: every device keeps its own dated copy and merges it back in.
        val catalogue = runCatching { json.decodeFromString<Catalogue>(text) }.getOrElse {
            logcat(LogPriority.WARN, it) { "Replacing an unreadable ${SyncLayout.CATEGORY_LIST_FILE}" }
            Catalogue()
        }
        return Remote(file, catalogue)
    }

    /**
     * The categories as the first version of the sync shared them, read once to seed the new list.
     */
    private suspend fun readLegacy(listing: SyncRootListing): Catalogue {
        val legacy = listing[SyncLayout.LEGACY_CATEGORIES_FILE] ?: return Catalogue()
        val payload = driveApi.download(legacy.id) ?: return Catalogue()

        val categories = runCatching { codec.decode(payload).backupCategories }.getOrElse {
            logcat(LogPriority.WARN, it) { "Ignoring an unreadable ${SyncLayout.LEGACY_CATEGORIES_FILE}" }
            emptyList()
        }
        return SyncCategoryMerge.fromLegacy(
            categories.map { Category(id = -1, name = it.name, order = it.order, flags = it.flags) },
        )
    }

    private fun readSnapshot(): Snapshot? {
        val stored = syncPreferences.categorySnapshot().get().takeIf { it.isNotBlank() } ?: return null
        return runCatching { json.decodeFromString<Snapshot>(stored) }.getOrNull()
    }

    /**
     * Makes this device's categories match [merged], and returns the local category behind each id.
     *
     * Deletions go through [DeleteCategory] so the library settings that point at a category are
     * cleaned up exactly as when the user deletes one by hand.
     */
    private suspend fun apply(merged: Catalogue, mapped: Map<Long, Long>): Map<Long, Long> {
        val localIds = mapped.toMutableMap()
        val current = categoryRepository.getAll().filterNot(Category::isSystemCategory).associateBy { it.id }

        for (entry in merged.entries.filter { it.deleted }) {
            val localId = localIds.remove(entry.id) ?: continue
            if (localId in current) deleteCategory.await(localId)
        }

        val live = merged.live
        for (entry in live) {
            val existing = localIds[entry.id]?.let(current::get)
            if (existing == null) {
                categoryRepository.insert(NewCategory(name = entry.name, flags = entry.flags))
                val created = categoryRepository.getAll()
                    .filter { it.name == entry.name && it.id !in localIds.values }
                    .maxByOrNull { it.id }
                    ?: continue
                localIds[entry.id] = created.id
                continue
            }
            if (existing.name != entry.name) categoryRepository.updateName(existing.id, entry.name)
            if (existing.flags != entry.flags) categoryRepository.updateFlags(existing.id, entry.flags)
        }

        val ordered = live.mapNotNull { localIds[it.id] }
        val currentOrder = categoryRepository.getAll()
            .filterNot(Category::isSystemCategory)
            .sortedBy(Category::order)
            .map(Category::id)
        if (currentOrder != ordered) categoryRepository.updateAllOrders(ordered)

        // Settings that differ between categories only show when the library is told to honour them,
        // which is what restoring a backup does too.
        if (live.map(SyncCategoryMerge.Entry::flags).distinct().size > 1) {
            libraryPreferences.categorizedDisplaySettings.set(true)
        }

        val liveIds = live.mapTo(HashSet()) { it.id }
        return localIds.filterKeys { it in liveIds }
    }
}

/**
 * How the library shards of one round refer to categories: by the stable id of the shared list,
 * never by a position, which differs from one device to the next.
 */
class SyncCategoryIndex(private val localIdBySyncId: Map<Long, Long>) {

    private val syncIdByLocalId = localIdBySyncId.entries.associate { (syncId, localId) -> localId to syncId }

    fun syncIdOf(localCategoryId: Long): Long? = syncIdByLocalId[localCategoryId]

    /** The category here that a shard means by [syncId], if it still exists. */
    fun localIdOf(syncId: Long): Long? = localIdBySyncId[syncId]
}
