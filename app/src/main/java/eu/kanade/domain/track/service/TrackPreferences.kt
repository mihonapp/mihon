package eu.kanade.domain.track.service

import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import tachiyomi.core.preference.Preference
import tachiyomi.core.preference.PreferenceStore

class TrackPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun trackUsername(sync: Tracker) = preferenceStore.getString(trackUsername(sync.id), "")

    fun trackPassword(sync: Tracker) = preferenceStore.getString(trackPassword(sync.id), "")

    fun setCredentials(sync: Tracker, username: String, password: String) {
        trackUsername(sync).set(username)
        trackPassword(sync).set(password)
    }

    fun trackToken(sync: Tracker) = preferenceStore.getString(trackToken(sync.id), "")

    fun anilistScoreType() = preferenceStore.getString("anilist_score_type", Anilist.POINT_10)

    fun autoUpdateTrack() = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)

    companion object {
        fun trackUsername(syncId: Long) = Preference.privateKey("pref_mangasync_username_$syncId")

        private fun trackPassword(syncId: Long) = Preference.privateKey("pref_mangasync_password_$syncId")

        private fun trackToken(syncId: Long) = Preference.privateKey("track_token_$syncId")
    }
}
