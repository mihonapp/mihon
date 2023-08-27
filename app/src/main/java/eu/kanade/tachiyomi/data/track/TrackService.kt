package eu.kanade.tachiyomi.data.track

import android.app.Application
import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.kanade.domain.chapter.interactor.SyncChapterProgressWithTrack
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import okhttp3.OkHttpClient
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChapterByMangaId
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.track.interactor.InsertTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.time.ZoneOffset
import tachiyomi.domain.track.model.Track as DomainTrack

abstract class TrackService(val id: Long, val name: String) {

    val trackPreferences: TrackPreferences by injectLazy()
    val networkService: NetworkHelper by injectLazy()
    private val insertTrack: InsertTrack by injectLazy()
    private val syncChapterProgressWithTrack: SyncChapterProgressWithTrack by injectLazy()

    open val client: OkHttpClient
        get() = networkService.client

    // Application and remote support for reading dates
    open val supportsReadingDates: Boolean = false

    @DrawableRes
    abstract fun getLogo(): Int

    @ColorInt
    abstract fun getLogoColor(): Int

    abstract fun getStatusList(): List<Int>

    @StringRes
    abstract fun getStatus(status: Int): Int?

    abstract fun getReadingStatus(): Int

    abstract fun getRereadingStatus(): Int

    abstract fun getCompletionStatus(): Int

    abstract fun getScoreList(): List<String>

    // TODO: Store all scores as 10 point in the future maybe?
    open fun get10PointScore(track: DomainTrack): Double {
        return track.score
    }

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

    open val isLoggedIn: Boolean
        get() = getUsername().isNotEmpty() &&
            getPassword().isNotEmpty()

    fun getUsername() = trackPreferences.trackUsername(this).get()

    fun getPassword() = trackPreferences.trackPassword(this).get()

    fun saveCredentials(username: String, password: String) {
        trackPreferences.setTrackCredentials(this, username, password)
    }

    // TODO: move this to an interactor, and update all trackers based on common data
    suspend fun register(item: Track, mangaId: Long) {
        item.manga_id = mangaId
        try {
            withIOContext {
                val allChapters = Injekt.get<GetChapterByMangaId>().await(mangaId)
                val hasReadChapters = allChapters.any { it.read }
                bind(item, hasReadChapters)

                var track = item.toDomainTrack(idRequired = false) ?: return@withIOContext

                insertTrack.await(track)

                // TODO: merge into SyncChaptersWithTrackServiceTwoWay?
                // Update chapter progress if newer chapters marked read locally
                if (hasReadChapters) {
                    val latestLocalReadChapterNumber = allChapters
                        .sortedBy { it.chapterNumber }
                        .takeWhile { it.read }
                        .lastOrNull()
                        ?.chapterNumber ?: -1.0

                    if (latestLocalReadChapterNumber > track.lastChapterRead) {
                        track = track.copy(
                            lastChapterRead = latestLocalReadChapterNumber,
                        )
                        setRemoteLastChapterRead(track.toDbTrack(), latestLocalReadChapterNumber.toInt())
                    }

                    if (track.startDate <= 0) {
                        val firstReadChapterDate = Injekt.get<GetHistory>().await(mangaId)
                            .sortedBy { it.readAt }
                            .firstOrNull()
                            ?.readAt

                        firstReadChapterDate?.let {
                            val startDate = firstReadChapterDate.time.convertEpochMillisZone(ZoneOffset.systemDefault(), ZoneOffset.UTC)
                            track = track.copy(
                                startDate = startDate,
                            )
                            setRemoteStartDate(track.toDbTrack(), startDate)
                        }
                    }
                }

                syncChapterProgressWithTrack.await(mangaId, track, this@TrackService)
            }
        } catch (e: Throwable) {
            withUIContext { Injekt.get<Application>().toast(e.message) }
        }
    }

    suspend fun setRemoteStatus(track: Track, status: Int) {
        track.status = status
        if (track.status == getCompletionStatus() && track.total_chapters != 0) {
            track.last_chapter_read = track.total_chapters.toFloat()
        }
        withIOContext { updateRemote(track) }
    }

    suspend fun setRemoteLastChapterRead(track: Track, chapterNumber: Int) {
        if (track.last_chapter_read == 0f && track.last_chapter_read < chapterNumber && track.status != getRereadingStatus()) {
            track.status = getReadingStatus()
        }
        track.last_chapter_read = chapterNumber.toFloat()
        if (track.total_chapters != 0 && track.last_chapter_read.toInt() == track.total_chapters) {
            track.status = getCompletionStatus()
            track.finished_reading_date = System.currentTimeMillis()
        }
        withIOContext { updateRemote(track) }
    }

    suspend fun setRemoteScore(track: Track, scoreString: String) {
        track.score = indexToScore(getScoreList().indexOf(scoreString))
        withIOContext { updateRemote(track) }
    }

    suspend fun setRemoteStartDate(track: Track, epochMillis: Long) {
        track.started_reading_date = epochMillis
        withIOContext { updateRemote(track) }
    }

    suspend fun setRemoteFinishDate(track: Track, epochMillis: Long) {
        track.finished_reading_date = epochMillis
        withIOContext { updateRemote(track) }
    }

    private suspend fun updateRemote(track: Track) {
        withIOContext {
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
}
