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

fun updateIntervalMeta(
    manga: Manga,
    chapters: List<Chapter>,
    zonedDateTime: ZonedDateTime = ZonedDateTime.now(),
    setCurrentFetchRange: Pair<Long, Long> = getCurrentFetchRange(zonedDateTime),
): MangaUpdate? {
    val currentFetchRange = if (setCurrentFetchRange.first == 0L && setCurrentFetchRange.second == 0L) {
        getCurrentFetchRange(ZonedDateTime.now())
    } else {
        setCurrentFetchRange
    }
    val interval = manga.calculateInterval.takeIf { it < 0 } ?: calculateInterval(chapters, zonedDateTime)
    val nextUpdate = calculateNextUpdate(manga, interval, zonedDateTime, currentFetchRange)

    return if (manga.nextUpdate == nextUpdate && manga.calculateInterval == interval) {
        null
    } else { MangaUpdate(id = manga.id, nextUpdate = nextUpdate, calculateInterval = interval) }
}
fun calculateInterval(chapters: List<Chapter>, zonedDateTime: ZonedDateTime): Int {
    val sortChapters =
        chapters.sortedWith(compareBy<Chapter> { it.dateUpload }.thenBy { it.dateFetch })
            .reversed().take(50)
    val uploadDates = sortChapters.filter { it.dateUpload != 0L }.map {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.dateUpload), zonedDateTime.zone).toLocalDate()
            .atStartOfDay()
    }
    val uploadDateDistinct = uploadDates.distinctBy { it }
    val fetchDates = sortChapters.map {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.dateFetch), zonedDateTime.zone).toLocalDate()
            .atStartOfDay()
    }
    val fetchDatesDistinct = fetchDates.distinctBy { it }
    val newInterval = when {
        // enough upload date from source
        (uploadDateDistinct.size >= 3) -> {
            val uploadDelta = uploadDateDistinct.last().until(uploadDateDistinct.first(), ChronoUnit.DAYS)
            val uploadPeriod = uploadDates.indexOf(uploadDateDistinct.last())
            (uploadDelta).floorDiv(uploadPeriod).toInt()
        }
        // enough fetch date from client
        (fetchDatesDistinct.size >= 3) -> {
            val fetchDelta = fetchDatesDistinct.last().until(fetchDatesDistinct.first(), ChronoUnit.DAYS)
            val uploadPeriod = fetchDates.indexOf(fetchDatesDistinct.last())
            (fetchDelta).floorDiv(uploadPeriod).toInt()
        }
        // default 7 days
        else -> 7
    }
    // min 1, max 28 days
    return newInterval.coerceIn(1, 28)
}

private fun calculateNextUpdate(manga: Manga, interval: Int, zonedDateTime: ZonedDateTime, currentFetchRange: Pair<Long, Long>): Long {
    return if (manga.nextUpdate !in currentFetchRange.first.rangeTo(currentFetchRange.second + 1) ||
        manga.calculateInterval == 0
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

fun getCurrentFetchRange(
    timeToCal: ZonedDateTime,
): Pair<Long, Long> {
    val preferences: LibraryPreferences = Injekt.get()

    // lead range and the following range depend on if updateOnlyExpectedPeriod set.
    var followRange = 0
    var leadRange = 0
    if (LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in preferences.libraryUpdateMangaRestriction().get()) {
        followRange = preferences.followingExpectedDays().get()
        leadRange = preferences.leadingExpectedDays().get()
    }
    val startToday = timeToCal.toLocalDate().atStartOfDay(timeToCal.zone)
    // revert math of (next_update + follow < now) become (next_update < now - follow)
    // so (now - follow) become lower limit
    val lowerRange = startToday.minusDays(followRange.toLong())
    val higherRange = startToday.plusDays(leadRange.toLong())
    return Pair(lowerRange.toEpochSecond() * 1000, higherRange.toEpochSecond() * 1000 - 1)
}
