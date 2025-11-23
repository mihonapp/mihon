package eu.kanade.domain.track.service

import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import kotlinx.serialization.json.Json
import mihonx.auth.models.AuthSession
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class TrackPreferences(
    private val preferenceStore: PreferenceStore,
    private val json: Json,
) {

    fun authSession(tracker: Tracker) = preferenceStore.getObjectFromString<AuthSession?>(
        key = Preference.privateKey("pref_tracker_auth_session_${tracker.id}"),
        defaultValue = null,
        deserializer = { json.decodeFromString(it) },
        serializer = { json.encodeToString(it) },
    )

    fun trackUsername(tracker: Tracker) = preferenceStore.getString(
        Preference.privateKey("pref_mangasync_username_${tracker.id}"),
        "",
    )

    fun trackPassword(tracker: Tracker) = preferenceStore.getString(
        Preference.privateKey("pref_mangasync_password_${tracker.id}"),
        "",
    )

    fun trackAuthExpired(tracker: Tracker) = preferenceStore.getBoolean(
        Preference.privateKey("pref_tracker_auth_expired_${tracker.id}"),
        false,
    )

    fun setCredentials(tracker: Tracker, username: String, password: String) {
        trackUsername(tracker).set(username)
        trackPassword(tracker).set(password)
        trackAuthExpired(tracker).set(false)
    }

    fun trackToken(tracker: Tracker) = preferenceStore.getString(Preference.privateKey("track_token_${tracker.id}"), "")

    fun anilistScoreType() = preferenceStore.getString("anilist_score_type", Anilist.POINT_10)

    fun autoUpdateTrack() = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)

    fun autoUpdateTrackOnMarkRead() = preferenceStore.getEnum(
        "pref_auto_update_manga_on_mark_read",
        AutoTrackState.ALWAYS,
    )
}
