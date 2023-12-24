package eu.kanade.test

import android.graphics.Color
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import okhttp3.OkHttpClient
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR

data class DummyTracker(
    override val id: Long,
    override val name: String,
    override val supportsReadingDates: Boolean = false,
    override val isLoggedIn: Boolean = false,
    val valLogoColor: Int = Color.rgb(18, 25, 35),
    val valLogo: Int = R.drawable.ic_tracker_anilist,
    val valStatuses: List<Int> = (1..6).toList(),
    val valReadingStatus: Int = 1,
    val valRereadingStatus: Int = 1,
    val valCompletionStatus: Int = 2,
    val valScoreList: ImmutableList<String> = (0..10).map(Int::toString).toImmutableList(),
    val val10PointScore: Double = 5.4,
    val valSearchResults: List<TrackSearch> = listOf(),
) : Tracker {

    override val client: OkHttpClient
        get() = TODO("Not yet implemented")

    override fun getLogoColor(): Int = valLogoColor

    override fun getLogo(): Int = valLogo

    override fun getStatusList(): List<Int> = valStatuses

    override fun getStatus(status: Int): StringResource? = when (status) {
        1 -> MR.strings.reading
        2 -> MR.strings.plan_to_read
        3 -> MR.strings.completed
        4 -> MR.strings.on_hold
        5 -> MR.strings.dropped
        6 -> MR.strings.repeating
        else -> null
    }

    override fun getReadingStatus(): Int = valReadingStatus

    override fun getRereadingStatus(): Int = valRereadingStatus

    override fun getCompletionStatus(): Int = valCompletionStatus

    override fun getScoreList(): ImmutableList<String> = valScoreList

    override fun get10PointScore(track: Track): Double = val10PointScore

    override fun indexToScore(index: Int): Float = getScoreList()[index].toFloat()

    override fun displayScore(track: Track): String =
        track.score.toString()

    override suspend fun update(
        track: eu.kanade.tachiyomi.data.database.models.Track,
        didReadChapter: Boolean,
    ): eu.kanade.tachiyomi.data.database.models.Track = track

    override suspend fun bind(
        track: eu.kanade.tachiyomi.data.database.models.Track,
        hasReadChapters: Boolean,
    ): eu.kanade.tachiyomi.data.database.models.Track = track

    override suspend fun search(query: String): List<TrackSearch> = valSearchResults

    override suspend fun refresh(
        track: eu.kanade.tachiyomi.data.database.models.Track,
    ): eu.kanade.tachiyomi.data.database.models.Track = track

    override suspend fun login(username: String, password: String) = Unit

    override fun logout() = Unit

    override fun getUsername(): String = "username"

    override fun getPassword(): String = "passw0rd"

    override fun saveCredentials(username: String, password: String) = Unit

    override suspend fun register(
        item: eu.kanade.tachiyomi.data.database.models.Track,
        mangaId: Long,
    ) = Unit

    override suspend fun setRemoteStatus(
        track: eu.kanade.tachiyomi.data.database.models.Track,
        status: Int,
    ) = Unit

    override suspend fun setRemoteLastChapterRead(
        track: eu.kanade.tachiyomi.data.database.models.Track,
        chapterNumber: Int,
    ) = Unit

    override suspend fun setRemoteScore(
        track: eu.kanade.tachiyomi.data.database.models.Track,
        scoreString: String,
    ) = Unit

    override suspend fun setRemoteStartDate(
        track: eu.kanade.tachiyomi.data.database.models.Track,
        epochMillis: Long,
    ) = Unit

    override suspend fun setRemoteFinishDate(
        track: eu.kanade.tachiyomi.data.database.models.Track,
        epochMillis: Long,
    ) = Unit
}
