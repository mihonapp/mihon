package eu.kanade.domain.manga.interactor

import android.content.Context
import dev.zacsweers.metro.Inject
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mihon.sync.job.SyncJob
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import kotlin.time.Clock

@Inject
class UpdateManga(
    private val context: Context,
    private val mangaRepository: MangaRepository,
    private val fetchInterval: FetchInterval,
) {

    suspend fun await(mangaUpdate: MangaUpdate): Boolean {
        return mangaRepository.update(mangaUpdate).also { updated ->
            if (updated) notifySyncIfFavoriteChanged(listOf(mangaUpdate))
        }
    }

    suspend fun awaitAll(mangaUpdates: List<MangaUpdate>): Boolean {
        return mangaRepository.updateAll(mangaUpdates).also { updated ->
            if (updated) notifySyncIfFavoriteChanged(mangaUpdates)
        }
    }

    /**
     * Publishes library membership changes right away.
     *
     * Every path that adds or removes an entry — the entry screen, browsing, bulk removal from the
     * library, migration — funnels through here, so this is the one place that catches them all.
     * Updates that leave `favoriteAt` untouched, such as a metadata refresh during a library update,
     * are ignored; otherwise a library refresh would trigger a sync per entry.
     */
    private fun notifySyncIfFavoriteChanged(mangaUpdates: List<MangaUpdate>) {
        if (mangaUpdates.none { it.isSet(MangaUpdate::favoriteAt) }) return

        SyncJob.onUserAction(context)
    }

    suspend fun awaitUpdateFetchInterval(
        manga: Manga,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        dateTime: LocalDateTime = Clock.System.now().toLocalDateTime(timeZone),
        window: Pair<Long, Long> = fetchInterval.getWindow(dateTime.date, timeZone),
    ): Boolean {
        return mangaRepository.update(
            fetchInterval.withFetchInterval(manga, dateTime, timeZone, window),
        )
    }

    suspend fun awaitUpdateLastUpdate(mangaId: Long): Boolean {
        return mangaRepository.update(MangaUpdate(mangaId) { lastUpdate = Clock.System.now().toEpochMilliseconds() })
    }

    suspend fun awaitUpdateCoverLastModified(mangaId: Long): Boolean {
        return mangaRepository.update(
            MangaUpdate(mangaId) {
                coverLastModified = Clock.System.now().toEpochMilliseconds()
            },
        )
    }

    suspend fun awaitUpdateFavorite(mangaId: Long, favorite: Boolean): Boolean {
        val update = when (favorite) {
            true -> MangaUpdate(mangaId) { favoriteAt = Clock.System.now().toEpochMilliseconds() }
            false -> MangaUpdate(mangaId) { favoriteAt = null }
        }
        return await(update)
    }
}
