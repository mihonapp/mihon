package eu.kanade.tachiyomi.data.track.suwayomi

import android.app.Application
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.nio.charset.Charset
import java.security.MessageDigest

class SuwayomiApi(private val trackId: Long) {

    private val sourceManager: SourceManager by injectLazy()
    private val source: HttpSource by lazy { (sourceManager.get(sourceId) as HttpSource) }
    private val suwayomiExt: SuwayomiExtensionInterface by lazy {
        (sourceManager.get(sourceId) as? SuwayomiExtensionInterface)?.let { return@lazy it }
        val context = Injekt.get<Application>()
        throw NullPointerException(MR.strings.error_extension_mismatch.getString(context))
    }
    private val client: OkHttpClient by lazy { source.client }
    private val baseUrl: String by lazy { source.baseUrl.trimEnd('/') }

    suspend fun getTrackSearch(trackUrl: String): TrackSearch = withIOContext {
        val mangaId = trackUrl.getMangaId()
        val manga = suwayomiExt.getTrackSearch(mangaId)
        TrackSearch.create(trackId).apply {
            title = manga.title
            cover_url = "$baseUrl/${manga.thumbnailUrl}"
            summary = manga.description.orEmpty()
            tracking_url = mangaId.toString()
            total_chapters = manga.chapters.toLong()
            publishing_status = manga.status
            last_chapter_read = manga.latestReadChapter ?: 0.0
            status = when (manga.unreadCount) {
                manga.chapters -> Suwayomi.UNREAD
                0 -> Suwayomi.COMPLETED
                else -> Suwayomi.READING
            }
        }
    }

    suspend fun updateProgress(track: Track): Track {
        val mangaId = track.tracking_url.getMangaId()
        suwayomiExt.updateProgress(mangaId, track.last_chapter_read)
        return getTrackSearch(track.tracking_url)
    }

    private val sourceId by lazy {
        val key = "tachidesk/en/1"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    private fun String.getMangaId(): Int =
        // NOTE: Originally tracks were stored as API-v1 URLs of the form http://<base>/api/v1/manga/<mangaId>
        //       Now, we store the ID directly, but for backwards compatibility support parsing the old format
        this.substringAfterLast('/').toInt()
}
