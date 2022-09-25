package eu.kanade.tachiyomi.data.track

import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.OkHttpClient
import uy.kohesive.injekt.injectLazy

abstract class TrackService(val id: Long) {

    val preferences: BasePreferences by injectLazy()
    val trackPreferences: TrackPreferences by injectLazy()
    val networkService: NetworkHelper by injectLazy()

    open val client: OkHttpClient
        get() = networkService.client

    // Name of the manga sync service to display
    @StringRes
    abstract fun nameRes(): Int

    // Application and remote support for reading dates
    open val supportsReadingDates: Boolean = false

    @DrawableRes
    abstract fun getLogo(): Int

    @ColorInt
    abstract fun getLogoColor(): Int

    abstract fun getStatusList(): List<Int>

    abstract fun getStatus(status: Int): String

    abstract fun getReadingStatus(): Int

    abstract fun getRereadingStatus(): Int

    abstract fun getCompletionStatus(): Int

    abstract fun getScoreList(): List<String>

    open fun indexToScore(index: Int): Float {
        return index.toFloat()
    }

    abstract fun displayScore(track: Track): String

    abstract suspend fun update(track: Track, didReadChapter: Boolean = false): Track

    abstract suspend fun bind(track: Track, hasReadChapters: Boolean = false): Track

    abstract suspend fun search(query: String): List<TrackSearch>

    abstract suspend fun refresh(track: Track): Track

    abstract suspend fun login(username: String, password: String)

    @CallSuper
    open fun logout() {
        trackPreferences.setTrackCredentials(this, "", "")
    }

    open val isLogged: Boolean
        get() = getUsername().isNotEmpty() &&
            getPassword().isNotEmpty()

    fun getUsername() = trackPreferences.trackUsername(this).get()

    fun getPassword() = trackPreferences.trackPassword(this).get()

    fun saveCredentials(username: String, password: String) {
        trackPreferences.setTrackCredentials(this, username, password)
    }
}
