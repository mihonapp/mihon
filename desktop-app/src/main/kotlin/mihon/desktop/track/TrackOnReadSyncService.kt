package mihon.desktop.track

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository

class TrackOnReadSyncService(
    private val repository: LibraryRepository,
    private val mutationPort: LibraryMutationPort,
    private val trackerManager: DesktopTrackerManager,
    private val trackingQueue: OfflineTrackingQueue? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val pollMillis: Long = 5_000,
) : AutoCloseable {
    private val drainMutex = Mutex()
    private val readMutex = Mutex()
    private val wakeup = Channel<Unit>(Channel.CONFLATED)
    private var worker: Job? = null

    @Volatile private var online = true
    private val authentication = MutableStateFlow<Set<Long>>(emptySet())
    val authenticationRequired = authentication.asStateFlow()
    private val conflictState = MutableStateFlow<List<TrackConflict>>(emptyList())
    val conflicts = conflictState.asStateFlow()
    private val remoteConflicts = mutableMapOf<Pair<Long, Long>, DesktopTrackRecord>()
    private val localOverrides = mutableSetOf<Pair<Long, Long>>()

    suspend fun resolveConflict(
        mangaId: Long,
        trackerId: Long,
        policy: ConflictResolutionPolicy,
    ) = drainMutex.withLock {
        if (policy == ConflictResolutionPolicy.PROMPT) return@withLock
        val key = mangaId to trackerId
        val remote = remoteConflicts.remove(key) ?: return@withLock
        if (policy == ConflictResolutionPolicy.REMOTE_WINS) {
            val local = mutationPort.findTracking(mangaId, trackerId)
            if (local !=
                null
            ) {
                mutationPort.updateTracking(remote.copy(id = local.id, mangaId = mangaId).toTrackingRecord())
            }
            trackingQueue?.peekAll()?.filter {
                it.mangaId == mangaId && it.trackerId == trackerId
            }?.forEach { trackingQueue.remove(it) }
        } else {
            localOverrides.add(key)
        }
        conflictState.value = conflictState.value.filterNot { it.mangaId == mangaId && it.trackerId == trackerId }
        trigger()
    }

    init {
        if (trackingQueue != null) {
            trackerManager.setPendingSyncHandler { flushPending() }
            trackerManager.setLoginRecoveryHandler { trackerId ->
                trackingQueue.resumeAuthentication(trackerId)
                authentication.value = authentication.value - trackerId
                trigger()
            }
        }
    }

    /** Call once with the application's scope after database and trackers have been restored. */
    @Synchronized fun start(scope: CoroutineScope) {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (isActive) {
                try {
                    flushPending()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) { }
                withTimeoutOrNull(pollMillis.coerceAtLeast(1)) { wakeup.receive() }
            }
        }
    }

    fun trigger() {
        wakeup.trySend(Unit)
    }
    fun onConnectivityChanged(isOnline: Boolean) {
        online = isOnline
        if (isOnline) trigger()
    }
    override fun close() {
        worker?.cancel()
        worker = null
    }

    suspend fun onChapterRead(mangaId: Long, chapterNumber: Double): Int = readMutex.withLock {
        val tracks = repository.trackingSnapshot(mangaId)
        var synced = 0
        for (track in tracks) {
            if (chapterNumber <= track.lastChapterRead) continue
            val updated = track.copy(
                lastChapterRead = chapterNumber,
                status = if (track.totalChapters > 0 &&
                    chapterNumber >= track.totalChapters
                ) {
                    TrackStatus.COMPLETED.value
                } else {
                    track.status
                },
            )
            mutationPort.updateTracking(updated)
            if (trackingQueue != null) {
                trackingQueue.enqueue(updated.toDesktopTrackRecord())
            } else {
                val tracker = trackerManager.get(track.trackerId)
                if (tracker?.isLoggedIn == true) {
                    try {
                        tracker.updateRemote(updated.toDesktopTrackRecord())
                        synced++
                    } catch (
                        error: CancellationException,
                    ) {
                        throw error
                    } catch (_: Exception) { }
                }
            }
        }
        if (trackingQueue != null) {
            if (worker?.isActive == true) trigger() else synced += flushPending()
        }
        synced
    }

    suspend fun flushPending(): Int = drainMutex.withLock {
        val queue = trackingQueue ?: return@withLock 0
        if (!online) return@withLock 0
        val pending = queue.peekAll()
        authentication.value = pending.filter { it.authenticationRequired }.map { it.trackerId }.toSet()
        var success = 0
        for (item in pending) {
            if (item.authenticationRequired || item.trackerId in authentication.value ||
                item.nextAttemptAt > now()
            ) {
                continue
            }
            val tracker = trackerManager.get(item.trackerId)
            if (tracker?.isLoggedIn != true) continue
            val record = mutationPort.findTracking(item.mangaId, item.trackerId)
            if (record == null) {
                queue.remove(item)
                continue
            }
            try {
                val key = item.mangaId to item.trackerId
                if (key in remoteConflicts) continue
                val local = record.toDesktopTrackRecord()
                if (key !in localOverrides) {
                    val remote = tracker.findRemote(local)
                    // A remote progress ahead of this durable local progress is an actual
                    // overwrite conflict. Remote lag is the normal offline catch-up case.
                    if (remote != null && remote.lastChapterRead > local.lastChapterRead) {
                        remoteConflicts[key] = remote
                        val conflict = TrackingConflictResolver().detectConflict(
                            local,
                            remote,
                            local.title,
                            tracker.name,
                        )!!
                        conflictState.value = conflictState.value + conflict
                        continue
                    }
                }
                tracker.updateRemote(local)
                localOverrides.remove(key)
                queue.remove(item)
                success++
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val unauthorized = (error as? TrackerHttpException)?.code == 401
                queue.updateRetry(item, now(), unauthorized)
                if (unauthorized) authentication.value = authentication.value + item.trackerId
            }
        }
        success
    }

    suspend fun syncPending(): Int = flushPending()

    @Deprecated("Use flushPending() instead", ReplaceWith("flushPending()"))
    suspend fun flushQueue(): Int = flushPending()
}
