package eu.kanade.tachiyomi.data.track.mangaupdates

import android.content.Context
import android.graphics.Color
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.copyTo
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.toTrackSearch
import eu.kanade.tachiyomi.data.track.model.TrackSearch

class MangaUpdates(private val context: Context, id: Long) : TrackService(id) {

    companion object {
        const val READING_LIST = 0
        const val WISH_LIST = 1
        const val COMPLETE_LIST = 2
        const val UNFINISHED_LIST = 3
        const val ON_HOLD_LIST = 4
    }

    private val interceptor by lazy { MangaUpdatesInterceptor(this) }

    private val api by lazy { MangaUpdatesApi(interceptor, client) }

    @StringRes
    override fun nameRes(): Int = R.string.tracker_manga_updates

    override fun getLogo(): Int = R.drawable.ic_manga_updates

    override fun getLogoColor(): Int = Color.rgb(146, 160, 173)

    override fun getStatusList(): List<Int> {
        return listOf(READING_LIST, COMPLETE_LIST, ON_HOLD_LIST, UNFINISHED_LIST, WISH_LIST)
    }

    override fun getStatus(status: Int): String = with(context) {
        when (status) {
            READING_LIST -> getString(R.string.reading_list)
            WISH_LIST -> getString(R.string.wish_list)
            COMPLETE_LIST -> getString(R.string.complete_list)
            ON_HOLD_LIST -> getString(R.string.on_hold_list)
            UNFINISHED_LIST -> getString(R.string.unfinished_list)
            else -> ""
        }
    }

    override fun getReadingStatus(): Int = READING_LIST

    override fun getRereadingStatus(): Int = -1

    override fun getCompletionStatus(): Int = COMPLETE_LIST

    private val _scoreList = (0..9).flatMap { i -> (0..9).map { j -> "$i.$j" } } + listOf("10.0")

    override fun getScoreList(): List<String> = _scoreList

    override fun indexToScore(index: Int): Float = _scoreList[index].toFloat()

    override fun displayScore(track: Track): String = track.score.toString()

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        if (track.status != COMPLETE_LIST && didReadChapter) {
            track.status = READING_LIST
        }
        api.updateSeriesListItem(track)
        return track
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        return try {
            val (series, rating) = api.getSeriesListItem(track)
            series.copyTo(track)
            rating?.copyTo(track) ?: track
        } catch (e: Exception) {
            api.addSeriesToList(track, hasReadChapters)
            track
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        return api.search(query)
            .map {
                it.toTrackSearch(id)
            }
    }

    override suspend fun refresh(track: Track): Track {
        val (series, rating) = api.getSeriesListItem(track)
        series.copyTo(track)
        return rating?.copyTo(track) ?: track
    }

    override suspend fun login(username: String, password: String) {
        val authenticated = api.authenticate(username, password) ?: throw Throwable("Unable to login")
        saveCredentials(authenticated.uid.toString(), authenticated.sessionToken)
        interceptor.newAuth(authenticated.sessionToken)
    }

    fun restoreSession(): String? {
        return preferences.trackPassword(this)
    }
}
