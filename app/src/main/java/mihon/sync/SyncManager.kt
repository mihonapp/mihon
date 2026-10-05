package mihon.sync

import android.content.Context
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.domain.sync.model.SyncMangaState
import mihon.domain.sync.model.SyncedChapter
import mihon.domain.sync.model.SyncedManga
import mihon.domain.sync.repository.SyncRepository
import mihon.sync.auth.GoogleDriveAuth
import mihon.sync.auth.SyncAuthRequiredException
import mihon.sync.drive.DriveFile
import mihon.sync.drive.GoogleDriveApi
import mihon.sync.merge.SyncCategoryMerge
import mihon.sync.model.SyncHistoryEntry
import mihon.sync.model.SyncTally
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.model.RestoredHistory
import tachiyomi.domain.backup.repository.RestoreRepository
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.storage.service.StorageManager
import kotlin.time.Clock

/**
 * Runs one sync round against the user's Drive.
 *
 * The library is stored one file per entry rather than as a single archive: a chapter's progress
 * costs kilobytes to publish instead of the whole library, and two devices reading different series
 * never write to the same object. Each shard is an ordinary Mihon backup holding exactly one entry;
 * only the merge differs from a restore, since it lets the latest decision win ([SyncRepository]).
 */
@Inject
@SingleIn(AppScope::class)
class SyncManager(
    private val context: Context,
    private val syncPreferences: SyncPreferences,
    private val device: SyncDevice,
    private val driveApi: GoogleDriveApi,
    private val auth: GoogleDriveAuth,
    private val history: SyncHistory,
    private val json: Json,
    private val codec: SyncCodec,
    private val catalog: SyncCatalog,
    private val categories: SyncCategories,
    private val getCategories: GetCategories,
    private val getFavorites: GetFavorites,
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId,
    private val syncRepository: SyncRepository,
    private val restoreRepository: RestoreRepository,
    private val mangaBackupCreator: MangaBackupCreator,
    private val fetchInterval: FetchInterval,
    private val backupCreatorFactory: BackupCreator.Factory,
    private val storageManager: StorageManager,
) {

    /**
     * Guards against two triggers (a favourite toggle and the periodic job, say) overlapping.
     */
    private val mutex = Mutex()

    /**
     * @param onSettled called once the library is in step with Drive, before the history is written.
     */
    suspend fun sync(onSettled: () -> Unit = {}): SyncHistoryEntry = mutex.withLock {
        if (!auth.isConfigured) throw SyncAuthRequiredException("This build has no Google Drive client ID")
        if (!auth.isLoggedIn) throw SyncAuthRequiredException("No Google account is linked")

        val startedAt = Clock.System.now().toEpochMilliseconds()
        val tally = SyncTally()

        try {
            writeSafetyBackupOnce()

            val folders = resolveFolders()
            val shards = loadShardState().toMutableMap()

            // The two listings a round needs, made together. The library one serves both halves of
            // the round: the pull decides what to merge from it, and the push reads it to tell a new
            // shard from one Drive already holds. The root one says which shared documents moved, so
            // the ones that did not are never downloaded.
            //
            // Both come before the categories on purpose. A device publishes its categories before the
            // entries that use them, so every shard listed here was written after the category list
            // it refers to — reading the list first could miss a category the shard already uses.
            val (listing, remoteShards) = coroutineScope {
                val root = async { SyncRootListing(driveApi.listFolder(folders.rootId)) }
                val library = async { driveApi.listFolder(folders.libraryId) }
                root.await() to library.await()
            }
            val afterListing = Clock.System.now().toEpochMilliseconds()

            catalog.reconcile(folders.rootId, listing)
            val categoryIndex = categories.reconcile(folders.rootId, listing)
            val afterDocuments = Clock.System.now().toEpochMilliseconds()

            val keptShards: List<DriveFile>
            val afterPull: Long
            val afterPush: Long
            try {
                keptShards = pull(remoteShards, shards, categoryIndex, tally)
                afterPull = Clock.System.now().toEpochMilliseconds()
                push(folders.libraryId, keptShards, shards, categoryIndex, tally)
                afterPush = Clock.System.now().toEpochMilliseconds()
            } finally {
                // What was merged or published so far is recorded even when the round fails part way,
                // so the next one does not download and merge it all over again.
                saveShardState(shards)
            }

            syncPreferences.lastSyncAt().set(Clock.System.now().toEpochMilliseconds())
            onSettled()

            // A round that exchanged nothing leaves no line in the shared history: it would cost a
            // download and an upload of the whole file, every time the app opens, to record nothing.
            val entry = tally.toEntry(device.id, device.name, startedAt)
            if (entry.hasChanges) {
                history.record(folders.rootId, entry)
            } else {
                history.touchDevice(folders.rootId, entry.at)
            }
            val finishedAt = Clock.System.now().toEpochMilliseconds()

            logcat(LogPriority.INFO) {
                "Sync round: ${tally.pulled} merged in, ${tally.pushed} published, " +
                    "${tally.added.size} added, ${tally.removed.size} removed, ${tally.failed} not merged " +
                    "in ${finishedAt - startedAt}ms " +
                    "(listing ${afterListing - startedAt}ms, " +
                    "documents ${afterDocuments - afterListing}ms, " +
                    "pull ${afterPull - afterDocuments}ms, " +
                    "push ${afterPush - afterPull}ms, " +
                    "history ${finishedAt - afterPush}ms, ${keptShards.size} shards)"
            }

            entry
        } catch (e: SyncAuthRequiredException) {
            throw e
        } catch (e: CancellationException) {
            // Stopped on purpose, not failed: nothing worth recording.
            throw e
        } catch (e: Exception) {
            // Still record the failure: a history that only shows successes hides the problem.
            val entry = tally.toEntry(device.id, device.name, startedAt, error = e.message ?: e::class.simpleName)
            runCatching { history.record(syncPreferences.rootFolderId().get(), entry) }
            throw e
        }
    }

    // Remote layout

    private data class Folders(val rootId: String, val libraryId: String)

    private suspend fun resolveFolders(): Folders {
        val cachedRoot = syncPreferences.rootFolderId().get()
        val cachedLibrary = syncPreferences.libraryFolderId().get()
        if (cachedRoot.isNotBlank() && cachedLibrary.isNotBlank()) {
            return Folders(cachedRoot, cachedLibrary)
        }

        val root = driveApi.findOrCreateFolder(SyncLayout.ROOT_FOLDER)
        val library = driveApi.findOrCreateFolder(SyncLayout.LIBRARY_FOLDER, root.id)

        syncPreferences.rootFolderId().set(root.id)
        syncPreferences.libraryFolderId().set(library.id)

        // Explains the folder to whoever finds it in their Drive later. Written once.
        if (driveApi.findFile(SyncLayout.README_FILE, root.id) == null) {
            driveApi.create(
                name = SyncLayout.README_FILE,
                parentId = root.id,
                content = SyncLayout.readmeContent.toByteArray(),
                mimeType = GoogleDriveApi.TEXT_MIME,
            )
        }

        return Folders(root.id, library.id)
    }

    // Pull

    /**
     * Merges what the other devices published, and returns the shards the rest of the round works
     * from: one per entry, with duplicates folded away.
     */
    private suspend fun pull(
        remoteShards: List<DriveFile>,
        shards: MutableMap<String, SyncShardState>,
        categoryIndex: SyncCategoryIndex,
        tally: SyncTally,
    ): List<DriveFile> {
        val favourites = getFavorites.await()
        val states = syncRepository.getStates()
        val favouritesByShard = favourites.associateBy { SyncLayout.mangaFileName(it.source, it.url) }
        val merge = ShardMerge(categoryIndex, favourites.associateBy { it.source to it.url }, tally)

        val kept = foldDuplicates(remoteShards, merge)

        // Same content under a newer version: only the version is taken, nothing is downloaded.
        for (remote in kept) {
            val known = shards[remote.name] ?: continue
            if (known.remoteVersion != remote.version && known.matches(remote)) {
                shards[remote.name] = known.copy(remoteVersion = remote.version)
            }
        }

        val changed = kept.filter { remote ->
            val known = shards[remote.name]
            when {
                known == null || known.remoteVersion != remote.version -> true
                known.format >= SyncShardState.CURRENT_FORMAT -> false
                // Reconciled under older rules: read it again rather than republish it from here, as
                // what this device made of it back then may be incomplete — its categories, for one,
                // could not be resolved. Unless the entry changed here since: then this device's
                // version is the newer one, and the push sends it as usual.
                else -> !hasLocalChanges(known, favouritesByShard[remote.name], states)
            }
        }
        if (changed.isEmpty()) return kept

        // Downloads go out together; the merge stays strictly serial. One round trip at a time made
        // a real library take minutes, while interleaving writes to SQLite and its triggers
        // is not something to risk for the sake of a few seconds more.
        for (batch in changed.chunked(NETWORK_BATCH)) {
            val downloaded = coroutineScope {
                batch.map { remote -> async { remote to driveApi.download(remote.id) } }.awaitAll()
            }

            for ((remote, payload) in downloaded) {
                if (payload == null || !merge(remote, payload)) continue

                // The identity of what Drive holds. The push phase compares against it, so a merge
                // that rejected the incoming value still gets published back.
                shards[remote.name] = SyncShardState(
                    fileId = remote.id,
                    remoteVersion = remote.version,
                    contentHash = codec.identityDigest(payload),
                    format = SyncShardState.CURRENT_FORMAT,
                    remoteMd5 = codec.md5(payload),
                )
            }
        }

        return kept
    }

    /**
     * Folds duplicate shards back into one file per entry.
     *
     * Two devices publishing a new entry at the same moment each create its file. Left alone, each
     * copy keeps looking changed next to the other, and one was downloaded again on every round, for
     * ever. Every copy but one is merged in, then deleted. The copy kept is the one with the lowest
     * id, the same on every device, so two devices doing this at once delete the same copies.
     */
    private suspend fun foldDuplicates(remoteShards: List<DriveFile>, merge: ShardMerge): List<DriveFile> {
        val byName = remoteShards.groupBy { it.name }
        if (byName.values.none { it.size > 1 }) return remoteShards

        return byName.values.map { copies ->
            val kept = copies.minBy { it.id }
            for (extra in copies) {
                if (extra.id == kept.id) continue
                val payload = driveApi.download(extra.id)
                // A copy that cannot be merged is kept on Drive: deleting it would lose what it holds.
                if (payload != null && !merge(extra, payload)) continue
                driveApi.delete(extra.id)
                logcat(LogPriority.INFO) { "Folded a duplicate of sync shard ${extra.name}" }
            }
            kept
        }
    }

    /**
     * Merges downloaded shards into the library, for one round.
     */
    private inner class ShardMerge(
        private val categoryIndex: SyncCategoryIndex,
        private val favouritesBefore: Map<Pair<Long, String>, Manga>,
        private val tally: SyncTally,
    ) {
        private val timeZone = TimeZone.currentSystemDefault()
        private val now = Clock.System.now().toLocalDateTime(timeZone)
        private val fetchWindow = fetchInterval.getWindow(now.date, timeZone)

        /**
         * False when the shard could not be merged. It is then left for the next round rather than
         * failing this one: an entry that cannot be read or merged must not hold every other back.
         */
        suspend operator fun invoke(remote: DriveFile, payload: ByteArray): Boolean {
            val backup = runCatching { codec.decode(payload) }.getOrElse {
                logcat(LogPriority.WARN, it) { "Skipping unreadable sync shard ${remote.name}" }
                tally.failed++
                return false
            }

            for (backupManga in backup.backupManga) {
                val before = favouritesBefore[backupManga.source to backupManga.url]
                try {
                    syncRepository.merge(listOf(backupManga.toSyncedManga(categoryIndex))) {
                        fetchInterval.withFetchInterval(it, now, timeZone, fetchWindow)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Could not merge sync shard ${remote.name}; retrying next round" }
                    tally.failed++
                    return false
                }
                recordPullChange(before, backupManga, tally)
            }

            tally.pulled += backup.backupManga.size
            return true
        }
    }

    /**
     * A shard's entry in the shape the merge takes. A shard written before categories had ids refers to
     * them by position on the device that wrote it, which nothing here can resolve: its memberships are
     * left as they are until that device publishes the entry again.
     */
    private fun BackupManga.toSyncedManga(categoryIndex: SyncCategoryIndex) = SyncedManga(
        manga = getMangaImpl(),
        chapters = chapters.map { SyncedChapter(it.toChapterImpl(), it.readModifiedAt) },
        categoryIds = categories
            .takeIf { it.all(SyncCategoryMerge::isSyncId) }
            ?.mapNotNull(categoryIndex::localIdOf),
        history = history.map {
            val history = it.getHistoryImpl()
            RestoredHistory(it.url, history.readAt, history.readDuration)
        },
        tracks = tracking.map { it.getTrackImpl() },
        excludedScanlators = excludedScanlators,
        favoriteChangedAt = favoriteModifiedAt ?: 0L,
        chapterListAt = chapterListAt,
    )

    private fun recordPullChange(before: Manga?, incoming: BackupManga, tally: SyncTally) {
        when {
            incoming.favorite && before == null -> tally.added += incoming.title
            !incoming.favorite && before?.favorite == true -> tally.removed += incoming.title
            else -> tally.progressed += incoming.title
        }
    }

    // Push

    /**
     * One entry whose shard needs writing, with everything the write needs.
     *
     * Deciding what to publish is all local work, so it happens for the whole library first and only
     * then does anything go over the network. That split is what lets the uploads run together while
     * the bookkeeping that follows them stays single-threaded.
     */
    private class PendingShard(
        val name: String,
        val manga: Manga,
        val payload: ByteArray,
        val hash: String,
        val known: SyncShardState?,
        val remoteId: String?,
        val changeCount: Long,
        val isRemoval: Boolean,
    )

    private suspend fun push(
        libraryId: String,
        remoteShards: List<DriveFile>,
        shards: MutableMap<String, SyncShardState>,
        categoryIndex: SyncCategoryIndex,
        tally: SyncTally,
    ) {
        val round = PushRound(
            shards = shards,
            remoteByName = remoteShards.associateBy { it.name },
            states = syncRepository.getStates(),
            categoryIdByOrder = getCategories.await()
                .filterNot(Category::isSystemCategory)
                .mapNotNull { category -> categoryIndex.syncIdOf(category.id)?.let { category.order to it } }
                .toMap(),
        )
        val favouriteNames = mutableSetOf<String>()
        val pending = mutableListOf<PendingShard>()

        for (manga in getFavorites.await()) {
            val name = SyncLayout.mangaFileName(manga.source, manga.url)
            favouriteNames += name
            prepareShard(name, manga, round, isRemoval = false)?.let { pending += it }
        }

        pending += prepareRemovals(round, favouriteNames)

        upload(libraryId, pending, shards, tally)
    }

    /**
     * What every entry of one push is weighed against.
     *
     * [categoryIdByOrder] turns the positions a backup records an entry's categories by into the ids
     * of the shared category list, which are the same on every device.
     */
    private class PushRound(
        val shards: MutableMap<String, SyncShardState>,
        val remoteByName: Map<String, DriveFile>,
        val states: Map<Long, SyncMangaState>,
        val categoryIdByOrder: Map<Long, Long>,
    )

    /**
     * Whether [manga] moved since [known] was recorded, which its change counter tells without building
     * anything.
     */
    private fun hasLocalChanges(known: SyncShardState, manga: Manga?, states: Map<Long, SyncMangaState>): Boolean {
        if (manga == null) return false
        return known.changeCount != (states[manga.id]?.changeCount ?: 0L)
    }

    /**
     * Entries this device has taken out of its library.
     *
     * The favourites loop cannot see them: once `favorite` is false the entry is gone from
     * [GetFavorites], so without this pass a removal would stay local forever while every other
     * device kept the entry. Migrating a series hits this too, since it removes the old entry and
     * adds the new one, so only half the change would ever travel.
     *
     * Only entries already published from here are considered, which keeps the pass to the handful
     * of shards that actually went stale rather than the whole database.
     */
    private suspend fun prepareRemovals(round: PushRound, favouriteNames: Set<String>): List<PendingShard> {
        val orphaned = restoreRepository.getMangaUrlsBySourceId()
            .flatMap { (source, urls) ->
                urls.map { url -> Triple(SyncLayout.mangaFileName(source, url), source, url) }
            }
            .filter { (name) -> name in round.shards && name !in favouriteNames }

        return orphaned.mapNotNull { (name, source, url) ->
            val manga = getMangaByUrlAndSourceId.await(url, source) ?: return@mapNotNull null
            prepareShard(name, manga, round, isRemoval = true)
        }
    }

    /**
     * Works out whether one entry needs publishing. Touches no network at all, so an entry that
     * turns out to be unchanged costs nothing but local reads.
     */
    private suspend fun prepareShard(
        name: String,
        manga: Manga,
        round: PushRound,
        isRemoval: Boolean,
    ): PendingShard? {
        val shards = round.shards
        val known = shards[name]
        val state = round.states[manga.id]
        val changeCount = state?.changeCount ?: 0L

        // Fast path: nothing local moved since the last reconciliation, so the payload cannot have
        // changed and there is no need to build it.
        val untouched = known != null &&
            known.contentHash.isNotEmpty() &&
            known.format >= SyncShardState.CURRENT_FORMAT &&
            !hasLocalChanges(known, manga, round.states)
        if (untouched) return null

        val backupManga = mangaBackupCreator(listOf(manga), backupOptions()).firstOrNull() ?: return null
        withSyncState(backupManga, manga, state)
        backupManga.categories = backupManga.categories.map { order ->
            // A category created since this round reconciled the list has no id yet. The entry goes
            // out next round, once the category itself has been published.
            round.categoryIdByOrder[order] ?: return null
        }
        val payload = codec.encode(Backup(backupManga = listOf(backupManga)))
        val hash = codec.identityDigest(payload)

        // The merge accepted what Drive already held, so publishing would only echo it back and
        // start a ping-pong with the other device. Recording the counters here is also what absorbs
        // a library refresh, where every modification time moves without the payload changing.
        if (known != null && known.contentHash == hash) {
            shards[name] = known.copy(changeCount = changeCount, format = SyncShardState.CURRENT_FORMAT)
            return null
        }

        return PendingShard(
            name = name,
            manga = manga,
            payload = payload,
            hash = hash,
            known = known,
            remoteId = round.remoteByName[name]?.id,
            changeCount = changeCount,
            isRemoval = isRemoval,
        )
    }

    /**
     * Adds what only the sync carries to an entry's backup: when it joined or left the library, when its
     * chapter list last changed from the source, and when each chapter's reading state was decided.
     */
    private suspend fun withSyncState(backupManga: BackupManga, manga: Manga, state: SyncMangaState?) {
        backupManga.favoriteModifiedAt = state?.favoriteChangedAt?.takeIf { it > 0 }
        backupManga.chapterListAt = manga.lastUpdate
        if (backupManga.chapters.isEmpty()) return
        val decidedAt = syncRepository.getReadChangedAt(manga.id)
        backupManga.chapters.forEach { it.readModifiedAt = decidedAt[it.url] ?: 0L }
    }

    private suspend fun upload(
        libraryId: String,
        pending: List<PendingShard>,
        shards: MutableMap<String, SyncShardState>,
        tally: SyncTally,
    ) {
        if (pending.isEmpty()) return

        for (batch in pending.chunked(NETWORK_BATCH)) {
            val written = coroutineScope {
                batch.map { entry -> async { entry to writeShard(libraryId, entry) } }.awaitAll()
            }

            // Bookkeeping back on one thread, so the shard map and the tally never see two writers.
            for ((entry, uploaded) in written) {
                if (uploaded == null) continue

                shards[entry.name] = SyncShardState(
                    fileId = uploaded.id,
                    remoteVersion = uploaded.version,
                    changeCount = entry.changeCount,
                    contentHash = entry.hash,
                    format = SyncShardState.CURRENT_FORMAT,
                    remoteMd5 = codec.md5(entry.payload),
                )
                tally.pushed++
                if (entry.isRemoval) tally.removed += entry.manga.title
            }
        }
    }

    private suspend fun writeShard(libraryId: String, entry: PendingShard): DriveFile? {
        val known = entry.known

        // Another device may have published this entry between our listing and now. Overwriting it
        // would drop their change silently, so leave it: the next round pulls it, merges, and
        // publishes the result. Asked here rather than before the payload is built, so the check
        // costs a round trip only for entries that really did change.
        if (known != null) {
            val currentRemote = driveApi.getMetadata(known.fileId)
            if (currentRemote != null && !known.matches(currentRemote)) {
                logcat(LogPriority.INFO) { "Shard ${entry.name} moved remotely mid-sync; merging it next round" }
                return null
            }
        }

        // The folder listing already said whether the name is there, so a brand new shard goes
        // straight to create rather than asking Drive about a file we know does not exist.
        val fileId = known?.fileId ?: entry.remoteId
        return if (fileId == null) {
            driveApi.create(name = entry.name, parentId = libraryId, content = entry.payload)
        } else {
            driveApi.upsert(
                name = entry.name,
                parentId = libraryId,
                content = entry.payload,
                knownId = fileId,
            )
        }
    }

    // Shard bookkeeping

    private fun loadShardState(): Map<String, SyncShardState> =
        runCatching { json.decodeFromString<Map<String, SyncShardState>>(syncPreferences.shardState().get()) }
            .getOrElse { emptyMap() }

    private fun saveShardState(shards: Map<String, SyncShardState>) {
        syncPreferences.shardState().set(json.encodeToString(shards))
    }

    /**
     * Writes one local backup before the first sync that is able to remove entries.
     *
     * Sync deliberately propagates removals, so a bug in that arbitration could empty a library on
     * every device at once. A failure to write this is worth a loud log but not a refusal to sync:
     * the user asked for a sync, not for a backup.
     */
    private suspend fun writeSafetyBackupOnce() {
        if (syncPreferences.safetyBackupDone().get()) return

        try {
            val directory = storageManager.getAutomaticBackupsDirectory() ?: UniFile.fromFile(context.filesDir)
            val file = directory?.createFile(SAFETY_FILE_NAME)
                ?: error("Could not create a file for the pre-sync safety backup")

            backupCreatorFactory.create(isAutoBackup = false).backup(file.uri, BackupOptions())
            syncPreferences.safetyBackupDone().set(true)
            logcat(LogPriority.INFO) { "Wrote a pre-sync safety backup to ${file.uri}" }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Could not write the pre-sync safety backup; syncing anyway" }
        }
    }

    private fun backupOptions() = BackupOptions(
        libraryEntries = true,
        categories = true,
        chapters = true,
        tracking = true,
        history = true,
        readEntries = false,
        // A shard holds one entry and nothing else. Repositories travel in their own file, and
        // settings do not travel at all: they are mostly choices made per device.
        appSettings = false,
        extensionStores = false,
        sourceSettings = false,
        // Never: the payload lands in the user's Drive, so credentials stay out of it.
        privateSettings = false,
    )

    private companion object {
        const val SAFETY_FILE_NAME = "mihon_pre-sync-safety.tachibk"

        /**
         * How many shards travel at once. OkHttp allows five requests per host by default, so this
         * is really an upper bound it paces for us; the point is not to sit idle between round
         * trips, which is what made the first sync of a real library take minutes.
         */
        const val NETWORK_BATCH = 8
    }
}
