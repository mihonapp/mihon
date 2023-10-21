package eu.kanade.tachiyomi.data.track

import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import okhttp3.OkHttpClient

interface Tracker {

    val id: Long

    val name: String

    val client: OkHttpClient

    // Application and remote support for reading dates
    val supportsReadingDates: Boolean

    @ColorInt
    fun getLogoColor(): Int

    @DrawableRes
    fun getLogo(): Int

    fun getStatusList(): List<Int>

    @StringRes
    fun getStatus(status: Int): Int?

    fun getReadingStatus(): Int

    fun getRereadingStatus(): Int

    fun getCompletionStatus(): Int

    fun getScoreList(): List<String>

    // TODO: Store all scores as 10 point in the future maybe?
    fun get10PointScore(track: tachiyomi.domain.track.model.Track): Double

    fun indexToScore(index: Int): Float

    fun displayScore(track: Track): String

    suspend fun update(track: Track, didReadChapter: Boolean = false): Track

    suspend fun bind(track: Track, hasReadChapters: Boolean = false): Track

    suspend fun search(query: String): List<TrackSearch>

    suspend fun refresh(track: Track): Track

    suspend fun login(username: String, password: String)

    @CallSuper
    fun logout()

    val isLoggedIn: Boolean

    fun getUsername(): String

    fun getPassword(): String

    fun saveCredentials(username: String, password: String)

    // TODO: move this to an interactor, and update all trackers based on common data
    suspend fun register(item: Track, mangaId: Long)

    suspend fun setRemoteStatus(track: Track, status: Int)

    suspend fun setRemoteLastChapterRead(track: Track, chapterNumber: Int)

    suspend fun setRemoteScore(track: Track, scoreString: String)

    suspend fun setRemoteStartDate(track: Track, epochMillis: Long)

    suspend fun setRemoteFinishDate(track: Track, epochMillis: Long)
}
