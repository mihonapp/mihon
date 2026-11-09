package mihon.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.backupExtensionStoreMapper
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionStoreRestorer
import eu.kanade.tachiyomi.extension.ExtensionManager
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.domain.extension.interactor.GetExtensionStores
import mihon.sync.drive.DriveFile
import mihon.sync.drive.GoogleDriveApi
import mihon.sync.model.SyncExtensionInfo
import mihon.sync.model.SyncExtensionRegistry
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.source.repository.StubSourceRepository
import tachiyomi.domain.source.service.SourceManager

/**
 * Keeps the *catalogue* side of the library in step: which extension repositories the account uses,
 * what its sources are called, and which extensions its devices have installed.
 *
 * Without it a device that has just signed in receives a library it cannot make sense of — entries
 * with no cover, no source name, and an error instead of a reader. None of this installs anything:
 * it only gives the new device enough to *offer* the missing extensions.
 *
 * Repositories and extensions are only ever added to the shared lists, never removed from them.
 * There is no timestamp to arbitrate on as there is for favourites, and the two mistakes are not
 * symmetric: an entry that lingers costs a line in a file, while a wrongly dropped repository costs
 * the user every source behind it. Removing one therefore stays a per-device act.
 *
 * Both lists change rarely, so a round only reads them when Drive holds a version this device has
 * not settled yet, or when its own side changed since.
 */
@Inject
@SingleIn(AppScope::class)
class SyncCatalog(
    private val driveApi: GoogleDriveApi,
    private val syncPreferences: SyncPreferences,
    private val documents: SyncDocuments,
    private val codec: SyncCodec,
    private val json: Json,
    private val getExtensionStores: GetExtensionStores,
    private val extensionStoreRestorer: ExtensionStoreRestorer,
    private val extensionManager: ExtensionManager,
    private val sourcePreferences: SourcePreferences,
    private val sourceManager: SourceManager,
    private val stubSourceRepository: StubSourceRepository,
    private val getFavorites: GetFavorites,
) {

    suspend fun reconcile(rootId: String, listing: SyncRootListing) {
        if (!syncPreferences.syncExtensionStores().get()) return

        // Diagnostic, never fatal: a library that syncs without its catalogue is still a library,
        // whereas failing the whole round would strand the progress the user actually asked for.
        try {
            val adoptedStore = reconcileStores(rootId, listing[SyncLayout.SOURCES_FILE])
            reconcileCatalogue(rootId, listing[SyncLayout.EXTENSIONS_FILE])

            // A repository's catalogue is what turns "source not installed" into an offer to
            // install it, so fetch it now rather than when the user hits the wall.
            if (adoptedStore) extensionManager.findAvailableExtensions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not reconcile the sync catalogue" }
        }
    }

    /**
     * Returns whether a repository this device did not have was adopted from another one.
     */
    private suspend fun reconcileStores(rootId: String, remoteFile: DriveFile?): Boolean {
        val local = localStoreDocument()
        if (documents.isSettled(SyncLayout.SOURCES_FILE, remoteFile, codec.contentDigest(local))) return false

        val remote = remoteFile?.let { driveApi.download(it.id) }?.let { payload ->
            runCatching { codec.decode(payload) }.getOrElse {
                logcat(LogPriority.WARN, it) { "Skipping an unreadable ${SyncLayout.SOURCES_FILE}" }
                null
            }
        }
        val remoteStores = remote?.backupExtensionStores.orEmpty()

        // Only a repository never seen here is adopted. One this device removed stays removed,
        // instead of coming straight back from the shared list on the next round.
        val seen = seenStores()
        val localUrls = local.backupExtensionStores.mapTo(HashSet()) { it.indexUrl }
        val newStores = remoteStores.filter { it.indexUrl !in localUrls && it.indexUrl !in seen }
        newStores.forEach { extensionStoreRestorer(it) }

        adoptSourceNames(remote?.backupSources.orEmpty())

        // Local first, so a repository whose metadata this device refreshed wins over a stale copy.
        val merged = Backup(
            backupManga = emptyList(),
            backupSources = (local.backupSources + remote?.backupSources.orEmpty())
                .distinctBy { it.sourceId }
                .sortedBy { it.sourceId },
            backupExtensionStores = (local.backupExtensionStores + remoteStores)
                .distinctBy { it.indexUrl }
                .sortedBy { it.indexUrl },
        )

        val remoteDigest = remote?.let(codec::contentDigest)
        val written = if (remoteFile != null && remoteDigest == codec.contentDigest(merged)) {
            remoteFile to null
        } else {
            val content = codec.encode(merged)
            driveApi.upsert(
                name = SyncLayout.SOURCES_FILE,
                parentId = rootId,
                content = content,
                knownId = remoteFile?.id,
            ) to content
        }

        saveSeenStores(seen + merged.backupExtensionStores.map { it.indexUrl })
        // This device's side as it stands now, adopted repositories and names included, so that an
        // untouched next round is recognised as one.
        documents.record(
            SyncLayout.SOURCES_FILE,
            written.first,
            written.second,
            codec.contentDigest(localStoreDocument()),
        )

        if (newStores.isNotEmpty()) {
            logcat(LogPriority.INFO) { "Adopted ${newStores.size} extension repositories from the sync" }
        }
        return newStores.isNotEmpty()
    }

    /**
     * This device's side of the shared repository list: its repositories, and the names of the
     * sources its library uses.
     */
    private suspend fun localStoreDocument() = Backup(
        backupManga = emptyList(),
        backupSources = localSources().sortedBy { it.sourceId },
        backupExtensionStores = getExtensionStores.get().map(backupExtensionStoreMapper).sortedBy { it.indexUrl },
    )

    /**
     * Names every source the library refers to, so another device can label an entry whose
     * extension it does not have instead of showing a bare number.
     */
    private suspend fun localSources(): List<BackupSource> {
        return getFavorites.await()
            .map { it.source }
            .distinct()
            .map { sourceManager.getOrStub(it) }
            .filter { it.name.isNotBlank() }
            .map { BackupSource(name = it.name, sourceId = it.id) }
    }

    /**
     * Records the names other devices know, for sources no extension here provides.
     *
     * Only fills gaps: a name this device learned from an installed extension is authoritative and
     * is never overwritten by one that travelled.
     */
    private suspend fun adoptSourceNames(sources: List<BackupSource>) {
        for (source in sources) {
            if (source.name.isBlank()) continue
            val existing = stubSourceRepository.getStubSource(source.sourceId)
            if (existing != null && existing.name.isNotBlank()) continue
            stubSourceRepository.upsertStubSource(
                id = source.sourceId,
                lang = existing?.lang.orEmpty(),
                name = source.name,
            )
        }
    }

    private fun seenStores(): Set<String> =
        syncPreferences.seenExtensionStores().get().takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<Set<String>>(it) }.getOrNull() }
            .orEmpty()

    private fun saveSeenStores(urls: Set<String>) {
        syncPreferences.seenExtensionStores().set(json.encodeToString(urls.sorted()))
    }

    /**
     * The side of the catalogue that is pure user choice: which extensions the account's devices
     * have, and which sources are pinned.
     */
    private suspend fun reconcileCatalogue(rootId: String, remoteFile: DriveFile?) {
        val syncExtensions = syncPreferences.syncInstalledExtensions().get()
        val syncPinned = syncPreferences.syncPinnedSources().get()
        if (!syncExtensions && !syncPinned) return

        val localDigest = localRegistryDigest(syncExtensions, syncPinned)
        if (documents.isSettled(SyncLayout.EXTENSIONS_FILE, remoteFile, localDigest)) return

        val remoteText = remoteFile?.let { driveApi.downloadText(it.id) }
        val remote = remoteText
            ?.let { text -> runCatching { json.decodeFromString<SyncExtensionRegistry>(text) }.getOrNull() }
            ?: SyncExtensionRegistry()

        val mergedExtensions = if (syncExtensions) mergeExtensions(remote.extensions) else remote.extensions
        val mergedPinned = if (syncPinned) mergePinnedSources(remote.pinnedSources) else remote.pinnedSources

        val payload = json.encodeToString(
            SyncExtensionRegistry(extensions = mergedExtensions, pinnedSources = mergedPinned),
        )
        val written = if (remoteFile != null && payload == remoteText) {
            remoteFile to null
        } else {
            val content = payload.toByteArray()
            driveApi.upsert(
                name = SyncLayout.EXTENSIONS_FILE,
                parentId = rootId,
                content = content,
                knownId = remoteFile?.id,
                mimeType = GoogleDriveApi.JSON_MIME,
            ) to content
        }

        // Pins adopted from the other side are part of this device's side from now on.
        documents.record(
            SyncLayout.EXTENSIONS_FILE,
            written.first,
            written.second,
            localRegistryDigest(syncExtensions, syncPinned),
        )
    }

    /**
     * This device's side of the shared extension list, including which halves of it take part, so
     * that turning one on counts as a change worth a full reconciliation.
     */
    private suspend fun localRegistryDigest(syncExtensions: Boolean, syncPinned: Boolean): String {
        val local = SyncExtensionRegistry(
            extensions = if (syncExtensions) installedExtensions() else emptyList(),
            pinnedSources = if (syncPinned) sourcePreferences.pinnedSources.get().sorted() else emptyList(),
        )
        return codec.textDigest("$syncExtensions/$syncPinned/${json.encodeToString(local)}")
    }

    private suspend fun installedExtensions(): List<SyncExtensionInfo> =
        extensionManager.getLoadedExtensions()
            .map { extension ->
                SyncExtensionInfo(
                    pkgName = extension.pkgName,
                    name = extension.name,
                    lang = extension.lang,
                    sourceIds = extension.sources.map { it.id }.sorted(),
                )
            }
            .sortedBy { it.pkgName }

    private suspend fun mergeExtensions(remote: List<SyncExtensionInfo>): List<SyncExtensionInfo> {
        // Local first: this device can read the real package, the remote entry is hearsay.
        val merged = (installedExtensions() + remote)
            .distinctBy { it.pkgName }
            .sortedBy { it.pkgName }

        syncPreferences.knownExtensions().set(json.encodeToString(SyncExtensionRegistry(extensions = merged)))
        return merged
    }

    /**
     * Pinned sources are one small set the account holds, not a pile of independent facts, so they
     * are replaced wholesale rather than merged — otherwise unpinning could never travel.
     *
     * Which side wins is decided by what changed here since the last round: a device still holding
     * exactly what it was given has nothing to say and takes the other's word. The first round on a
     * device adopts whatever Drive already holds, so signing in on a fresh phone cannot wipe the
     * pins of the device that set them.
     */
    private fun mergePinnedSources(remote: List<String>): List<String> {
        val local = sourcePreferences.pinnedSources.get()
        val lastReconciled = syncPreferences.lastPinnedSources().get()
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<Set<String>>(it) }.getOrNull() }

        val merged = when {
            lastReconciled == null -> remote.takeIf { it.isNotEmpty() }?.toSet() ?: local
            local != lastReconciled -> local
            else -> remote.toSet()
        }

        if (merged != local) sourcePreferences.pinnedSources.set(merged)
        syncPreferences.lastPinnedSources().set(json.encodeToString(merged))
        return merged.sorted()
    }
}
