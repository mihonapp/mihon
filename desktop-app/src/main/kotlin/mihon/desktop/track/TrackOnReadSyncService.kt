package mihon.desktop.track

import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository

class TrackOnReadSyncService(
    private val repository: LibraryRepository,
    private val mutationPort: LibraryMutationPort,
    private val trackerManager: DesktopTrackerManager,
    private val trackingQueue: OfflineTrackingQueue? = null,
) {
    init {
        if (trackingQueue != null) {
            trackerManager.setPendingSyncHandler { flushPending() }
        }
    }

    suspend fun onChapterRead(mangaId: Long, chapterNumber: Double): Int {
        val tracks = repository.trackingSnapshot(mangaId)
        if (tracks.isEmpty()) return 0
        var syncedCount = 0

        for (track in tracks) {
            if (chapterNumber > track.lastChapterRead) {
                val newStatus = if (track.totalChapters > 0 && chapterNumber >= track.totalChapters) {
                    TrackStatus.COMPLETED.value
                } else {
                    track.status
                }
                val updatedRecord = track.copy(
                    lastChapterRead = chapterNumber,
                    status = newStatus,
                )
                mutationPort.updateTracking(updatedRecord)

                val desktopTrack = updatedRecord.toDesktopTrackRecord()
                val tracker = trackerManager.get(track.trackerId)
                if (tracker != null && tracker.isLoggedIn) {
                    try {
                        tracker.updateRemote(desktopTrack)
                        syncedCount++
                    } catch (_: Exception) {
                        trackingQueue?.enqueue(desktopTrack)
                    }
                } else {
                    trackingQueue?.enqueue(desktopTrack)
                }
            }
        }
        return syncedCount
    }

    /**
     * Drains pending offline updates, updating each logged-in tracker from the current DB record.
     * Items for trackers that are not logged in are left in the queue for a later attempt.
     */
    suspend fun flushPending(): Int {
        val queue = trackingQueue ?: return 0
        val pending = queue.peekAll()
        var successCount = 0

        for (item in pending) {
            val tracker = trackerManager.get(item.trackerId)
            if (tracker != null && tracker.isLoggedIn) {
                val record = mutationPort.findTracking(item.mangaId, item.trackerId)
                if (record != null) {
                    try {
                        tracker.updateRemote(record.toDesktopTrackRecord())
                        queue.remove(item)
                        successCount++
                    } catch (_: Exception) {
                        queue.updateRetry(item)
                    }
                } else {
                    // Record no longer exists in DB, drop from queue
                    queue.remove(item)
                }
            }
        }
        return successCount
    }

    /** Alias for [flushPending]. */
    suspend fun syncPending(): Int = flushPending()

    @Deprecated("Use flushPending() instead", ReplaceWith("flushPending()"))
    suspend fun flushQueue(): Int = flushPending()
}
