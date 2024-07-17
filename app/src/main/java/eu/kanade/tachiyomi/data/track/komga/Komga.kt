package eu.kanade.tachiyomi.data.track.komga

import android.graphics.Color
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.PageTracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.source.Source
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import okhttp3.Dns
import okhttp3.OkHttpClient
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.domain.track.model.Track as DomainTrack

class Komga(id: Long) : BaseTracker(id, "Komga"), EnhancedTracker, PageTracker {

    companion object {
        const val UNREAD = 1L
        const val READING = 2L
        const val COMPLETED = 3L
    }

    override val client: OkHttpClient =
        networkService.client.newBuilder()
            .dns(Dns.SYSTEM) // don't use DNS over HTTPS as it breaks IP addressing
            .build()

    val api by lazy { KomgaApi(id, client) }

    override fun getLogo() = R.drawable.ic_tracker_komga

    override fun getLogoColor() = Color.rgb(51, 37, 50)

    override fun getStatusList(): List<Long> = listOf(UNREAD, READING, COMPLETED)

    override fun getStatus(status: Long): StringResource? = when (status) {
        UNREAD -> MR.strings.unread
        READING -> MR.strings.reading
        COMPLETED -> MR.strings.completed
        else -> null
    }

    override fun getReadingStatus(): Long = READING

    override fun getRereadingStatus(): Long = -1

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): ImmutableList<String> = persistentListOf()

    override fun displayScore(track: DomainTrack): String = ""

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toLong() == track.total_chapters && track.total_chapters > 0) {
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
        TODO("Not yet implemented: search")
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

    // [Tracker].isLogged works by checking that credentials are saved.
    // By saving dummy, unused credentials, we can activate the tracker simply by login/logout
    override fun loginNoop() {
        saveCredentials("user", "pass")
    }

    override fun getAcceptedSources() = listOf("eu.kanade.tachiyomi.extension.all.komga.Komga")

    override suspend fun match(manga: Manga): TrackSearch? =
        try {
            api.getTrackSearch(manga.url)
        } catch (e: Exception) {
            null
        }

    override fun isTrackFrom(track: DomainTrack, manga: Manga, source: Source?): Boolean =
        track.remoteUrl == manga.url && source?.let { accept(it) } == true

    override fun migrateTrack(track: DomainTrack, manga: Manga, newSource: Source): DomainTrack? =
        if (accept(newSource)) {
            track.copy(remoteUrl = manga.url)
        } else {
            null
        }

    override suspend fun updateWithUrl(chapterUrl: String, page: Int) {
        api.updateBookProgress(chapterUrl, page)
    }

    override suspend fun getChapterProgress(chapter: Chapter): Int {
        val book = api.getBookInfo(chapter)
        return if (book.readProgress?.completed == false) book.readProgress.page - 1 else -1
    }

    override suspend fun batchGetChapterProgress(chapters: List<Chapter>): Map<Chapter, Int> {
        if (chapters.isEmpty()) return mapOf()
        val seriesId = api.getBookInfo(chapters[0]).seriesId
        val urlBase = chapters[0].url.split("/books")[0]
        val books = api.getAllBooksOfSeries(urlBase, seriesId)
        return chapters.associateWith { chapter ->
            val book = books.find { chapter.url.toBookId() == it.id }
            return@associateWith if (book?.readProgress?.completed == false) book.readProgress.page - 1 else -1
        }
    }

    private fun String.toBookId():String? {
        return Regex("/api/v1/books/(\\S+)").find(this)?.destructured?.let { (id) -> id }
    }
}
