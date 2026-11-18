package eu.kanade.tachiyomi.ui.profile

import android.content.Context
import android.net.Uri

/** Stores the small amount of user-authored profile presentation data locally. */
class ProfilePreferences(context: Context) {

    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    val avatarUri: String?
        get() = preferences.getString(KEY_AVATAR_URI, null)

    val bannerUri: String?
        get() = preferences.getString(KEY_BANNER_URI, null)

    val username: String
        get() = preferences.getString(KEY_USERNAME, DEFAULT_USERNAME) ?: DEFAULT_USERNAME

    val bio: String
        get() = preferences.getString(KEY_BIO, DEFAULT_BIO) ?: DEFAULT_BIO

    fun setAvatarUri(uri: Uri?) {
        preferences.edit().putString(KEY_AVATAR_URI, uri?.toString()).apply()
    }

    fun setBannerUri(uri: Uri?) {
        preferences.edit().putString(KEY_BANNER_URI, uri?.toString()).apply()
    }

    fun setIdentity(username: String, bio: String) {
        preferences.edit()
            .putString(KEY_USERNAME, username.trim().ifBlank { DEFAULT_USERNAME })
            .putString(KEY_BIO, bio.trim())
            .apply()
    }

    private companion object {
        const val FILE_NAME = "afraga_profile"
        const val KEY_AVATAR_URI = "avatar_uri"
        const val KEY_BANNER_URI = "banner_uri"
        const val KEY_USERNAME = "username"
        const val KEY_BIO = "bio"
        const val DEFAULT_USERNAME = "Manga Reader"
        const val DEFAULT_BIO = "Every chapter opens a new world."
    }
}
