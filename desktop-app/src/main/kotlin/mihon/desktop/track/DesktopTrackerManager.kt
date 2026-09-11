package mihon.desktop.track

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class DesktopTrackerManager(
    trackersList: List<DesktopTracker> = defaultTrackers(),
    val store: DesktopTrackerStore? = null,
) {
    private val trackerMap: Map<Long, DesktopTracker> = trackersList.associateBy { it.id }

    @Volatile
    private var pendingSyncHandler: (suspend () -> Int)? = null

    init {
        store?.let { s ->
            trackersList.forEach { tracker ->
                val info = s.getLoginInfo(tracker.id)
                if (info != null) {
                    tracker.restoreLogin(info)
                    // Drop persisted credentials for trackers that explicitly refuse to restore
                    // (for example the not-yet-supported trackers) so the UI doesn't show a fake
                    // logged-in state after an upgrade.
                    if (!tracker.isLoggedIn) {
                        s.clearLogin(tracker.id)
                    }
                }
            }
        }
    }

    val trackers: List<DesktopTracker> get() = trackerMap.values.toList()

    fun get(id: Long): DesktopTracker? = trackerMap[id]

    fun loggedInTrackers(): List<DesktopTracker> = trackers.filter { it.isLoggedIn }

    fun loggedInTrackersFlow(): Flow<List<DesktopTracker>> = combine(trackers.map { it.isLoggedInFlow }) {
        trackers.filter { it.isLoggedIn }
    }

    /**
     * Registers the component that can drain [OfflineTrackingQueue] entries. TrackOnReadSyncService
     * registers itself here so a successful login can flush pending offline updates.
     */
    fun setPendingSyncHandler(handler: (suspend () -> Int)?) {
        pendingSyncHandler = handler
    }

    /** Drains pending offline tracking updates using the registered handler, if any. */
    suspend fun syncPending(): Int = pendingSyncHandler?.invoke() ?: 0

    /** Alias for [syncPending]. */
    suspend fun flushPending(): Int = syncPending()

    suspend fun login(trackerId: Long, credentials: Map<String, String>): Boolean {
        val tracker = get(trackerId) ?: return false
        val success = tracker.login(credentials)
        if (success) {
            if (store != null) {
                val token = tracker.persistenceToken
                    ?: credentials["token"]
                    ?: credentials["access_token"]
                    ?: credentials["password"]
                    ?: ""
                val serverUrl = credentials["server_url"] ?: credentials["url"] ?: ""
                store.saveLogin(
                    trackerId = tracker.id,
                    username = tracker.username.orEmpty(),
                    token = token,
                    serverUrl = serverUrl,
                )
            }
            drainPendingSafely()
        }
        return success
    }

    private suspend fun drainPendingSafely() {
        val handler = pendingSyncHandler ?: return
        try {
            handler()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Login must still succeed if the optional offline drain fails; individual queue
            // entries keep their retry state and are attempted again on the next login/flush.
        }
    }

    fun logout(trackerId: Long) {
        val tracker = get(trackerId) ?: return
        tracker.logout()
        store?.clearLogin(trackerId)
    }

    companion object {
        fun defaultTrackers(): List<DesktopTracker> = listOf(
            MyAnimeListTracker(),
            AniListTracker(),
            KitsuTracker(),
            ShikimoriTracker(),
            BangumiTracker(),
            KomgaTracker(),
            MangaUpdatesTracker(),
            KavitaTracker(),
            SuwayomiTracker(),
        )
    }
}
