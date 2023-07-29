package tachiyomi.domain.manga.interactor

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

const val MAX_GRACE_PERIOD = 28

class SetFetchInterval(
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) {

    fun update(
        manga: Manga,
        chapters: List<Chapter>,
        zonedDateTime: ZonedDateTime,
        fetchRange: Pair<Long, Long>,
    ): MangaUpdate? {
        val currentInterval = if (fetchRange.first == 0L && fetchRange.second == 0L) {
            getCurrent(ZonedDateTime.now())
        } else {
            fetchRange
        }
        val interval = manga.fetchInterval.takeIf { it < 0 } ?: calculateInterval(chapters, zonedDateTime)
        val nextUpdate = calculateNextUpdate(manga, interval, zonedDateTime, currentInterval)

        return if (manga.nextUpdate == nextUpdate && manga.fetchInterval == interval) {
            null
        } else {
            MangaUpdate(id = manga.id, nextUpdate = nextUpdate, fetchInterval = interval)
        }
    }

    fun getCurrent(timeToCal: ZonedDateTime): Pair<Long, Long> {
        // lead range and the following range depend on if updateOnlyExpectedPeriod set.
        var followRange = 0
        var leadRange = 0
        if (LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in libraryPreferences.libraryUpdateMangaRestriction().get()) {
            followRange = libraryPreferences.followingExpectedDays().get()
            leadRange = libraryPreferences.leadingExpectedDays().get()
        }
        val startToday = timeToCal.toLocalDate().atStartOfDay(timeToCal.zone)
        // revert math of (next_update + follow < now) become (next_update < now - follow)
        // so (now - follow) become lower limit
        val lowerRange = startToday.minusDays(followRange.toLong())
        val higherRange = startToday.plusDays(leadRange.toLong())
        return Pair(lowerRange.toEpochSecond() * 1000, higherRange.toEpochSecond() * 1000 - 1)
    }

    internal fun calculateInterval(chapters: List<Chapter>, zonedDateTime: ZonedDateTime): Int {
        val sortedChapters = chapters
            .sortedWith(compareByDescending<Chapter> { it.dateUpload }.thenByDescending { it.dateFetch })
            .take(50)

        val uploadDates = sortedChapters
            .filter { it.dateUpload > 0L }
            .map {
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.dateUpload), zonedDateTime.zone)
                    .toLocalDate()
                    .atStartOfDay()
            }
            .distinct()
        val fetchDates = sortedChapters
            .map {
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.dateFetch), zonedDateTime.zone)
                    .toLocalDate()
                    .atStartOfDay()
            }
            .distinct()

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
        // Min 1, max 28 days
        return interval.coerceIn(1, MAX_GRACE_PERIOD)
    }

    private fun calculateNextUpdate(
        manga: Manga,
        interval: Int,
        zonedDateTime: ZonedDateTime,
        fetchRange: Pair<Long, Long>,
    ): Long {
        return if (
            manga.nextUpdate !in fetchRange.first.rangeTo(fetchRange.second + 1) ||
            manga.fetchInterval == 0
        ) {
            val latestDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(manga.lastUpdate), zonedDateTime.zone).toLocalDate().atStartOfDay()
            val timeSinceLatest = ChronoUnit.DAYS.between(latestDate, zonedDateTime).toInt()
            val cycle = timeSinceLatest.floorDiv(interval.absoluteValue.takeIf { interval < 0 } ?: doubleInterval(interval, timeSinceLatest, doubleWhenOver = 10, maxValue = 28))
            latestDate.plusDays((cycle + 1) * interval.toLong()).toEpochSecond(zonedDateTime.offset) * 1000
        } else {
            manga.nextUpdate
        }
    }

    private fun doubleInterval(delta: Int, timeSinceLatest: Int, doubleWhenOver: Int, maxValue: Int): Int {
        if (delta >= maxValue) return maxValue
        val cycle = timeSinceLatest.floorDiv(delta) + 1
        // double delta again if missed more than 9 check in new delta
        return if (cycle > doubleWhenOver) {
            doubleInterval(delta * 2, timeSinceLatest, doubleWhenOver, maxValue)
        } else {
            delta
        }
    }
}
