package mihon.desktop.track

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class DesktopTrackerManager(
    trackersList: List<DesktopTracker> = defaultTrackers(),
    val store: DesktopTrackerStore? = null,
) {
    private val trackerMap: Map<Long, DesktopTracker> = trackersList.associateBy { it.id }

    init {
        store?.let { s ->
            trackersList.forEach { tracker ->
                val info = s.getLoginInfo(tracker.id)
                if (info != null) {
                    tracker.restoreLogin(info)
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

    suspend fun login(trackerId: Long, credentials: Map<String, String>): Boolean {
        val tracker = get(trackerId) ?: return false
        val success = tracker.login(credentials)
        if (success && store != null) {
            val token = credentials["token"] ?: credentials["password"] ?: ""
            val serverUrl = credentials["server_url"] ?: credentials["url"] ?: ""
            store.saveLogin(
                trackerId = tracker.id,
                username = tracker.username.orEmpty(),
                token = token,
                serverUrl = serverUrl,
            )
        }
        return success
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
