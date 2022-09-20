package eu.kanade.tachiyomi.data.preference

/**
 * This class stores the keys for the preferences in the application.
 */
object PreferenceKeys {

    const val dateFormat = "app_date_format"

    fun trackUsername(syncId: Long) = "pref_mangasync_username_$syncId"

    fun trackPassword(syncId: Long) = "pref_mangasync_password_$syncId"

    fun trackToken(syncId: Long) = "track_token_$syncId"
}
