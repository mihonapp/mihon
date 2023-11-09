package eu.kanade.tachiyomi.data.track

import android.app.Application
import androidx.annotation.CallSuper
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import okhttp3.OkHttpClient
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.track.interactor.InsertTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

abstract class BaseTracker(
    override val id: Long,
    override val name: String,
) : Tracker {

    val trackPreferences: TrackPreferences by injectLazy()
    val networkService: NetworkHelper by injectLazy()
    private val addTracks: AddTracks by injectLazy()
    private val insertTrack: InsertTrack by injectLazy()

    override val client: OkHttpClient
        get() = networkService.client

    // Application and remote support for reading dates
    override val supportsReadingDates: Boolean = false

    // TODO: Store all scores as 10 point in the future maybe?
    override fun get10PointScore(track: DomainTrack): Double {
        return track.score
    }

    override fun indexToScore(index: Int): Float {
        return index.toFloat()
    }

    @CallSuper
    override fun logout() {
        trackPreferences.setCredentials(this, "", "")
    }

    override val isLoggedIn: Boolean
        get() = getUsername().isNotEmpty() &&
            getPassword().isNotEmpty()

    override fun getUsername() = trackPreferences.trackUsername(this).get()

    override fun getPassword() = trackPreferences.trackPassword(this).get()

    override fun saveCredentials(username: String, password: String) {
        trackPreferences.setCredentials(this, username, password)
    }

    override suspend fun register(item: Track, mangaId: Long) {
        item.manga_id = mangaId
        try {
            addTracks.bind(this, item, mangaId)
        } catch (e: Throwable) {
            withUIContext { Injekt.get<Application>().toast(e.message) }
        }
    }

    override suspend fun setRemoteStatus(track: Track, status: Int) {
        track.status = status
        if (track.status == getCompletionStatus() && track.total_chapters != 0) {
            track.last_chapter_read = track.total_chapters.toFloat()
        }
        updateRemote(track)
    }

    override suspend fun setRemoteLastChapterRead(track: Track, chapterNumber: Int) {
        if (
            track.last_chapter_read == 0f &&
            track.last_chapter_read < chapterNumber &&
            track.status != getRereadingStatus()
        ) {
            track.status = getReadingStatus()
        }
        track.last_chapter_read = chapterNumber.toFloat()
        if (track.total_chapters != 0 && track.last_chapter_read.toInt() == track.total_chapters) {
            track.status = getCompletionStatus()
            track.finished_reading_date = System.currentTimeMillis()
        }
        updateRemote(track)
    }

    override suspend fun setRemoteScore(track: Track, scoreString: String) {
        track.score = indexToScore(getScoreList().indexOf(scoreString))
        updateRemote(track)
    }

    override suspend fun setRemoteStartDate(track: Track, epochMillis: Long) {
        track.started_reading_date = epochMillis
        updateRemote(track)
    }

    override suspend fun setRemoteFinishDate(track: Track, epochMillis: Long) {
        track.finished_reading_date = epochMillis
        updateRemote(track)
    }

    private suspend fun updateRemote(track: Track): Unit = withIOContext {
        try {
            update(track)
            track.toDomainTrack(idRequired = false)?.let {
                insertTrack.await(it)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to update remote track data id=$id" }
            withUIContext { Injekt.get<Application>().toast(e.message) }
        }
    }
}
