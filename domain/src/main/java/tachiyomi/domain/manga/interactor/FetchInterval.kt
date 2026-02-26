package tachiyomi.domain.manga.interactor

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import kotlin.math.absoluteValue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class FetchInterval(
    private val getChaptersByMangaId: GetChaptersByMangaId,
) {

    suspend fun toMangaUpdate(
        manga: Manga,
        dateTime: LocalDateTime,
        timeZone: TimeZone,
        window: Pair<Long, Long>,
    ): MangaUpdate {
        val interval = manga.fetchInterval.takeIf { it < 0 } ?: calculateInterval(
            chapters = getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true),
            zone = timeZone,
        )
        val currentWindow = if (window.first == 0L && window.second == 0L) {
            getWindow(Clock.System.now().toLocalDateTime(timeZone).date, timeZone)
        } else {
            window
        }
        val nextUpdate = calculateNextUpdate(manga, interval, dateTime, timeZone, currentWindow)

        return MangaUpdate(id = manga.id, nextUpdate = nextUpdate, fetchInterval = interval)
    }

    fun getWindow(localDateTime: LocalDate, timeZone: TimeZone): Pair<Long, Long> {
        val today = localDateTime.atStartOfDayIn(timeZone)
        val lowerBound = today - GRACE_PERIOD.days
        val upperBound = today + GRACE_PERIOD.days
        return Pair(lowerBound.toEpochMilliseconds(), upperBound.toEpochMilliseconds())
    }

    internal fun calculateInterval(chapters: List<Chapter>, zone: TimeZone): Int {
        val chapterWindow = if (chapters.size <= 8) 3 else 10

        val uploadDates = chapters.asSequence()
            .filter { it.dateUpload > 0L }
            .sortedByDescending { it.dateUpload }
            .map {
                Instant.fromEpochMilliseconds(it.dateUpload)
                    .toLocalDateTime(zone)
                    .date
                    .atStartOfDayIn(zone)
            }
            .distinct()
            .take(chapterWindow)
            .toList()

        val fetchDates = chapters.asSequence()
            .sortedByDescending { it.dateFetch }
            .map {
                Instant.fromEpochMilliseconds(it.dateFetch)
                    .toLocalDateTime(zone)
                    .date
                    .atStartOfDayIn(zone)
            }
            .distinct()
            .take(chapterWindow)
            .toList()

        val interval = when {
            // Enough upload date from source
            uploadDates.size >= 3 -> {
                val ranges = uploadDates.windowed(2).map { x -> x[1].daysUntil(x[0], zone) }.sorted()
                ranges[(ranges.size - 1) / 2]
            }
            // Enough fetch date from client
            fetchDates.size >= 3 -> {
                val ranges = fetchDates.windowed(2).map { x -> x[1].daysUntil(x[0], zone) }.sorted()
                ranges[(ranges.size - 1) / 2]
            }
            // Default to 7 days
            else -> 7
        }

        return interval.coerceIn(1, MAX_INTERVAL)
    }

    private fun calculateNextUpdate(
        manga: Manga,
        interval: Int,
        dateTime: LocalDateTime,
        timeZone: TimeZone,
        window: Pair<Long, Long>,
    ): Long {
        if (manga.nextUpdate in window.first.rangeTo(window.second + 1)) {
            return manga.nextUpdate
        }

        val instant = if (manga.lastUpdate > 0) Instant.fromEpochMilliseconds(manga.lastUpdate) else Clock.System.now()
        val latestDate = instant.toLocalDateTime(timeZone).date.atStartOfDayIn(timeZone)

        val daysSinceLatest = (dateTime.toInstant(timeZone) - latestDate).inWholeDays
        val cycle = daysSinceLatest.floorDiv(
            interval.absoluteValue.takeIf { interval < 0 }
                ?: increaseInterval(interval, daysSinceLatest, increaseWhenOver = 10),
        )

        val offsetDays = ((cycle + 1) * interval.absoluteValue.toLong()).days
        return latestDate.plus(offsetDays).toEpochMilliseconds()
    }

    private fun increaseInterval(delta: Int, daysSinceLatest: Long, increaseWhenOver: Int): Int {
        if (delta >= MAX_INTERVAL) return MAX_INTERVAL

        // double delta again if missed more than 9 check in new delta
        val cycle = daysSinceLatest.floorDiv(delta) + 1
        return if (cycle > increaseWhenOver) {
            increaseInterval(delta * 2, daysSinceLatest, increaseWhenOver)
        } else {
            delta
        }
    }

    companion object {
        const val MAX_INTERVAL = 28

        private const val GRACE_PERIOD = 1L
    }
}
