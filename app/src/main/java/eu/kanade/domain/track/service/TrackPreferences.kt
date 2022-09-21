package eu.kanade.domain.track.service

import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.Anilist

class TrackPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun trackUsername(sync: TrackService) = preferenceStore.getString(PreferenceKeys.trackUsername(sync.id), "")

    fun trackPassword(sync: TrackService) = preferenceStore.getString(PreferenceKeys.trackPassword(sync.id), "")

    fun setTrackCredentials(sync: TrackService, username: String, password: String) {
        trackUsername(sync).set(username)
        trackPassword(sync).set(password)
    }

    fun trackToken(sync: TrackService) = preferenceStore.getString(PreferenceKeys.trackToken(sync.id), "")

    fun anilistScoreType() = preferenceStore.getString("anilist_score_type", Anilist.POINT_10)

    fun autoUpdateTrack() = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)
}
