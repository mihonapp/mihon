package eu.kanade.tachiyomi.data.track.suwayomi

import android.graphics.Color
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.source.Source
import tachiyomi.domain.manga.model.Manga as DomainManga
import tachiyomi.domain.track.model.Track as DomainTrack

class Suwayomi(id: Long) : TrackService(id, "Suwayomi"), EnhancedTrackService {

    val api by lazy { TachideskApi() }

    override fun getLogo() = R.drawable.ic_tracker_suwayomi

    override fun getLogoColor() = Color.rgb(255, 35, 35) // TODO

    companion object {
        const val UNREAD = 1
        const val READING = 2
        const val COMPLETED = 3
    }

    override fun getStatusList() = listOf(UNREAD, READING, COMPLETED)

    @StringRes
    override fun getStatus(status: Int): Int? = when (status) {
        UNREAD -> R.string.unread
        READING -> R.string.reading
        COMPLETED -> R.string.completed
        else -> null
    }

    override fun getReadingStatus(): Int = READING

    override fun getRereadingStatus(): Int = -1

    override fun getCompletionStatus(): Int = COMPLETED

    override fun getScoreList(): List<String> = emptyList()

    override fun displayScore(track: Track): String = ""

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toInt() == track.total_chapters && track.total_chapters > 0) {
                    track.status = COMPLETED
                } else {
                    track.status = READING
                }
            }
        }

        return api.updateProgress(track)
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        return track
    }

    override suspend fun search(query: String): List<TrackSearch> {
        TODO("Not yet implemented")
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getTrackSearch(track.tracking_url)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(username: String, password: String) {
        saveCredentials("user", "pass")
    }

    override fun loginNoop() {
        saveCredentials("user", "pass")
    }

    override fun getAcceptedSources(): List<String> = listOf("eu.kanade.tachiyomi.extension.all.tachidesk.Tachidesk")

    override suspend fun match(manga: DomainManga): TrackSearch? =
        try {
            api.getTrackSearch(manga.url)
        } catch (e: Exception) {
            null
        }

    override fun isTrackFrom(track: DomainTrack, manga: DomainManga, source: Source?): Boolean = source?.let { accept(it) } == true

    override fun migrateTrack(track: DomainTrack, manga: DomainManga, newSource: Source): DomainTrack? =
        if (accept(newSource)) {
            track.copy(remoteUrl = manga.url)
        } else {
            null
        }
}
