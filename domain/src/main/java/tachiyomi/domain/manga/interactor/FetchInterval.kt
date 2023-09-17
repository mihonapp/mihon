package tachiyomi.domain.manga.interactor

import tachiyomi.domain.chapter.interactor.GetChapterByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

class FetchInterval(
    private val getChapterByMangaId: GetChapterByMangaId,
) {

    suspend fun toMangaUpdateOrNull(
        manga: Manga,
        dateTime: ZonedDateTime,
        window: Pair<Long, Long>,
    ): MangaUpdate? {
        val currentWindow = if (window.first == 0L && window.second == 0L) {
            getWindow(ZonedDateTime.now())
        } else {
            window
        }
        val chapters = getChapterByMangaId.await(manga.id)
        val interval = manga.fetchInterval.takeIf { it < 0 } ?: calculateInterval(
            chapters,
            dateTime.zone,
        )
        val nextUpdate = calculateNextUpdate(manga, interval, dateTime, currentWindow)

        return if (manga.nextUpdate == nextUpdate && manga.fetchInterval == interval) {
            null
        } else {
            MangaUpdate(id = manga.id, nextUpdate = nextUpdate, fetchInterval = interval)
        }
    }

    fun getWindow(dateTime: ZonedDateTime): Pair<Long, Long> {
        val today = dateTime.toLocalDate().atStartOfDay(dateTime.zone)
        val lowerBound = today.minusDays(GRACE_PERIOD)
        val upperBound = today.plusDays(GRACE_PERIOD)
        return Pair(lowerBound.toEpochSecond() * 1000, upperBound.toEpochSecond() * 1000 - 1)
    }

    internal fun calculateInterval(chapters: List<Chapter>, zone: ZoneId): Int {
        val uploadDates = chapters.asSequence()
            .filter { it.dateUpload > 0L }
            .sortedByDescending { it.dateUpload }
            .map {
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.dateUpload), zone)
                    .toLocalDate()
                    .atStartOfDay()
            }
            .distinct()
            .take(10)
            .toList()

        val fetchDates = chapters.asSequence()
            .sortedByDescending { it.dateFetch }
            .map {
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.dateFetch), zone)
                    .toLocalDate()
                    .atStartOfDay()
            }
            .distinct()
            .take(10)
            .toList()

        val interval = when {
            // Enough upload date from source
            uploadDates.size >= 3 -> {
                val uploadDelta = uploadDates.last().until(uploadDates.first(), ChronoUnit.DAYS)
                val uploadPeriod = uploadDates.indexOf(uploadDates.last())
                uploadDelta.floorDiv(uploadPeriod).toInt()
            }
            // Enough fetch date from client
            fetchDates.size >= 3 -> {
                val fetchDelta = fetchDates.last().until(fetchDates.first(), ChronoUnit.DAYS)
                val uploadPeriod = fetchDates.indexOf(fetchDates.last())
                fetchDelta.floorDiv(uploadPeriod).toInt()
            }
            // Default to 7 days
            else -> 7
        }

        return interval.coerceIn(1, MAX_INTERVAL)
    }

    private fun calculateNextUpdate(
        manga: Manga,
        interval: Int,
        dateTime: ZonedDateTime,
        window: Pair<Long, Long>,
    ): Long {
        return if (
            manga.nextUpdate !in window.first.rangeTo(window.second + 1) ||
            manga.fetchInterval == 0
        ) {
            val latestDate = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(manga.lastUpdate),
                dateTime.zone,
            )
                .toLocalDate()
                .atStartOfDay()
            val timeSinceLatest = ChronoUnit.DAYS.between(latestDate, dateTime).toInt()
            val cycle = timeSinceLatest.floorDiv(
                interval.absoluteValue.takeIf { interval < 0 }
                    ?: doubleInterval(interval, timeSinceLatest, doubleWhenOver = 10),
            )
            latestDate.plusDays((cycle + 1) * interval.toLong()).toEpochSecond(dateTime.offset) * 1000
        } else {
            manga.nextUpdate
        }
    }

    private fun doubleInterval(delta: Int, timeSinceLatest: Int, doubleWhenOver: Int): Int {
        if (delta >= MAX_INTERVAL) return MAX_INTERVAL

        // double delta again if missed more than 9 check in new delta
        val cycle = timeSinceLatest.floorDiv(delta) + 1
        return if (cycle > doubleWhenOver) {
            doubleInterval(delta * 2, timeSinceLatest, doubleWhenOver)
        } else {
            delta
        }
    }

    companion object {
        const val MAX_INTERVAL = 28

        private const val GRACE_PERIOD = 1L
    }
}
