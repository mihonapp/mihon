package mihon.desktop.track

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class DesktopTrackerManager(
    trackersList: List<DesktopTracker> = defaultTrackers(),
) {
    private val trackerMap: Map<Long, DesktopTracker> = trackersList.associateBy { it.id }

    val trackers: List<DesktopTracker> get() = trackerMap.values.toList()

    fun get(id: Long): DesktopTracker? = trackerMap[id]

    fun loggedInTrackers(): List<DesktopTracker> = trackers.filter { it.isLoggedIn }

    fun loggedInTrackersFlow(): Flow<List<DesktopTracker>> = combine(trackers.map { it.isLoggedInFlow }) {
        trackers.filter { it.isLoggedIn }
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
