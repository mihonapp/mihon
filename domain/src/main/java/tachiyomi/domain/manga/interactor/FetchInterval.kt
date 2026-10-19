package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@Inject
class FetchInterval(
    private val getChaptersByMangaId: GetChaptersByMangaId,
) {

    /**
     * Works out the entry's fetch interval and the next day to check it for new chapters.
     *
     * @param foundRelease whether the check that led here found new chapters.
     */
    suspend fun withFetchInterval(
        manga: Manga,
        dateTime: LocalDateTime,
        timeZone: TimeZone,
        window: ClosedRange<Instant>?,
        foundRelease: Boolean = false,
    ): MangaUpdate {
        val today = dateTime.date
        val currentWindow = window ?: getWindow(Clock.System.now().toLocalDateTime(timeZone).date, timeZone)
        val schedule = if (manga.fetchInterval < 0) {
            // The check that found a release is the entry's last update
            val lastUpdate = if (foundRelease) today else manga.lastUpdate?.toLocalDateTime(timeZone)?.date
            ReleaseSchedule.fixed(-manga.fetchInterval, lastUpdate ?: today)
        } else {
            val chapters = getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true)
            ReleaseSchedule.estimate(chapters, timeZone, today)
        }

        val expected = manga.nextUpdate
        val expectedDay = expected?.toLocalDateTime(timeZone)?.date
        // A release on or after the expected day means the next one is counted from it instead
        val isAwaitingRelease = expected != null &&
            expectedDay != null &&
            expected in currentWindow.start..currentWindow.endInclusive + 1.milliseconds &&
            (schedule.lastRelease == null || schedule.lastRelease < expectedDay)
        val nextUpdate = when {
            foundRelease -> schedule.nextCheckAfterRelease(today).atStartOfDayIn(timeZone)
            isAwaitingRelease -> expected
            else -> schedule.nextCheck(today).atStartOfDayIn(timeZone)
        }

        return MangaUpdate(manga.id) {
            this.nextUpdate = nextUpdate
            fetchInterval = if (manga.fetchInterval < 0) manga.fetchInterval else schedule.interval
        }
    }

    fun getWindow(localDateTime: LocalDate, timeZone: TimeZone): ClosedRange<Instant> {
        val today = localDateTime.atStartOfDayIn(timeZone)
        return (today - GRACE_PERIOD.days)..(today + GRACE_PERIOD.days)
    }

    companion object {
        const val MAX_INTERVAL = 28

        private const val GRACE_PERIOD = 1L
    }
}
