package eu.kanade.domain.track.service

import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import tachiyomi.core.preference.PreferenceStore

class TrackPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun trackUsername(sync: TrackService) = preferenceStore.getString(trackUsername(sync.id), "")

    fun trackPassword(sync: TrackService) = preferenceStore.getString(trackPassword(sync.id), "")

    fun setTrackCredentials(sync: TrackService, username: String, password: String) {
        trackUsername(sync).set(username)
        trackPassword(sync).set(password)
    }

    fun trackToken(sync: TrackService) = preferenceStore.getString(trackToken(sync.id), "")

    fun anilistScoreType() = preferenceStore.getString("anilist_score_type", Anilist.POINT_10)

    fun autoUpdateTrack() = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)

    companion object {
        fun trackUsername(syncId: Long) = "pref_mangasync_username_$syncId"

        private fun trackPassword(syncId: Long) = "pref_mangasync_password_$syncId"

        private fun trackToken(syncId: Long) = "track_token_$syncId"
    }
}
