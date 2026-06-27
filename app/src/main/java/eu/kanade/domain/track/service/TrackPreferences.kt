package eu.kanade.domain.track.service

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.i18n.MR

class TrackPreferences(
    private val preferenceStore: PreferenceStore,
) {

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

    val anilistScoreType: Preference<String> = preferenceStore.getString("anilist_score_type", Anilist.POINT_10)

    val autoUpdateTrack: Preference<Boolean> = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)

    val autoUpdateTrackOnMarkRead: Preference<AutoTrackState> = preferenceStore.getEnum(
        "pref_auto_update_manga_on_mark_read",
        AutoTrackState.ALWAYS,
    )

    val syncStrategy: Preference<SyncStrategy> = preferenceStore.getEnum(
        "pref_tracking_sync_strategy",
        SyncStrategy.ALLOW_REREAD,
    )

    enum class SyncStrategy(val titleRes: StringResource) {
        DEFAULT(MR.strings.pref_tracking_sync_strategy_default),
        ACCEPT_ALL(MR.strings.pref_tracking_sync_strategy_accept_all),
        ALLOW_REREAD(MR.strings.pref_tracking_sync_strategy_allow_reread),
    }
}
