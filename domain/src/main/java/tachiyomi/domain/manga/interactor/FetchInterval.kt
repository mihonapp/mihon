package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import kotlin.math.absoluteValue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@Inject
class FetchInterval(
    private val getChaptersByMangaId: GetChaptersByMangaId,
) {

    suspend fun withFetchInterval(
        manga: Manga,
        dateTime: LocalDateTime,
        timeZone: TimeZone,
        window: ClosedRange<Instant>?,
    ): MangaUpdate {
        val currentWindow = window ?: getWindow(Clock.System.now().toLocalDateTime(timeZone).date, timeZone)
        val nextUpdate: Instant
        val interval: Int
        if (manga.fetchInterval < 0) {
            interval = manga.fetchInterval
            nextUpdate = calculateCustomNextUpdate(manga, interval.absoluteValue, dateTime, timeZone, currentWindow)
        } else {
            val chapters = getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true)
            val schedule = estimateSchedule(chapters, timeZone, dateTime.date)
            interval = schedule.interval
            nextUpdate = calculateNextUpdate(manga, schedule, dateTime.date, timeZone, currentWindow)
        }

        return MangaUpdate(manga.id) {
            this.nextUpdate = nextUpdate
            fetchInterval = interval
        }
    }

    fun getWindow(localDateTime: LocalDate, timeZone: TimeZone): ClosedRange<Instant> {
        val today = localDateTime.atStartOfDayIn(timeZone)
        return (today - GRACE_PERIOD.days)..(today + GRACE_PERIOD.days)
    }

    internal fun calculateInterval(chapters: List<Chapter>, zone: TimeZone): Int {
        return estimateSchedule(chapters, zone, Clock.System.now().toLocalDateTime(zone).date).interval
    }

    internal fun estimateSchedule(chapters: List<Chapter>, zone: TimeZone, today: LocalDate): ReleaseSchedule {
        val chapterWindow = if (chapters.size <= 8) 3 else 10

        // Sources that date chapters ahead of their release would otherwise pull the schedule into the future
        val latestPlausibleDay = today.plus(DatePeriod(days = 1))
        fun releaseDays(dates: Sequence<Instant>): List<LocalDate> {
            return dates
                .map { it.toLocalDateTime(zone).date }
                .filter { it <= latestPlausibleDay }
                .distinct()
                .sortedDescending()
                .take(chapterWindow)
                .toList()
        }

        val uploadDays = releaseDays(chapters.asSequence().mapNotNull { it.dateUpload })
        val days = uploadDays.takeIf { it.size >= 3 }
            ?: releaseDays(chapters.asSequence().map { it.dateFetch }).takeIf { it.size >= 3 }
            ?: return ReleaseSchedule(
                interval = DEFAULT_INTERVAL,
                lastRelease = uploadDays.firstOrNull(),
                pattern = ReleasePattern.EveryDays(DEFAULT_INTERVAL),
            )

        val gaps = days.zipWithNext { newer, older -> older.daysUntil(newer) }.sorted()
        val medianGap = gaps[(gaps.size - 1) / 2]
        val interval = medianGap.coerceIn(1, MAX_INTERVAL)

        return ReleaseSchedule(
            interval = interval,
            lastRelease = days.first(),
            pattern = detectPattern(days, medianGap) ?: ReleasePattern.EveryDays(interval),
        )
    }

    /**
     * Finds a calendar pattern a plain day interval can't follow: a few fixed weekdays, or a day of the
     * month. Both need a few releases to be told apart from chance.
     */
    private fun detectPattern(days: List<LocalDate>, medianGap: Int): ReleasePattern? {
        if (days.size < MIN_RELEASES_FOR_PATTERN) return null

        if (medianGap in MONTHLY_GAPS) {
            val daysOfMonth = days.map { it.day }
            return when {
                daysOfMonth.max() - daysOfMonth.min() <= DAY_OF_MONTH_TOLERANCE -> {
                    ReleasePattern.DayOfMonth(daysOfMonth.sorted()[(daysOfMonth.size - 1) / 2])
                }
                // The last day of the month moves between the 28th and the 31st
                daysOfMonth.all { it >= 28 } -> ReleasePattern.DayOfMonth(31)
                else -> null
            }
        }

        val byFrequency = days.groupingBy { it.dayOfWeek }.eachCount().entries.sortedByDescending { it.value }
        val weekdays = mutableSetOf<DayOfWeek>()
        var covered = 0
        for ((dayOfWeek, count) in byFrequency) {
            if (covered >= days.size * WEEKDAY_COVERAGE) break
            weekdays += dayOfWeek
            covered += count
        }
        // One weekday is a weekly interval, and an irregular schedule spreads over most of the week
        if (weekdays.size !in 2..MAX_WEEKDAYS || covered < days.size * WEEKDAY_COVERAGE) return null
        // Releasing on several weekdays means more than one release a week
        if (medianGap > 7 / weekdays.size + 1) return null
        return ReleasePattern.Weekdays(weekdays)
    }

    private fun calculateNextUpdate(
        manga: Manga,
        schedule: ReleaseSchedule,
        today: LocalDate,
        timeZone: TimeZone,
        window: ClosedRange<Instant>,
    ): Instant {
        val lastRelease = listOfNotNull(schedule.lastRelease, manga.lastUpdate?.toLocalDateTime(timeZone)?.date)
            .maxOrNull()

        // A release on or after the expected day means the next one should be counted from it instead
        val nextUpdate = manga.nextUpdate
        if (
            nextUpdate != null &&
            nextUpdate in window.start..window.endInclusive + 1.milliseconds &&
            (lastRelease == null || lastRelease < nextUpdate.toLocalDateTime(timeZone).date)
        ) {
            return nextUpdate
        }

        val anchor = schedule.lastRelease ?: lastRelease ?: today
        val daysSinceRelease = anchor.daysUntil(today)
        val expected = if (daysSinceRelease > schedule.interval * HIATUS_CYCLES) {
            // Past a few missed releases the schedule no longer holds, so checks spread out until it resumes
            val interval = backOff(schedule.interval, daysSinceRelease)
            anchor.plus(DatePeriod(days = ceilDiv(daysSinceRelease, interval) * interval))
        } else {
            // Today counts, so a release expected today keeps being checked for until the window passes it
            generateSequence(schedule.pattern.nextAfter(anchor)) { schedule.pattern.nextAfter(it) }
                .first { it >= today }
        }
        return expected.atStartOfDayIn(timeZone)
    }

    private fun calculateCustomNextUpdate(
        manga: Manga,
        interval: Int,
        dateTime: LocalDateTime,
        timeZone: TimeZone,
        window: ClosedRange<Instant>,
    ): Instant {
        if (manga.nextUpdate != null && manga.nextUpdate in window.start..window.endInclusive + 1.milliseconds) {
            return manga.nextUpdate
        }

        val latestDate = (manga.lastUpdate ?: Clock.System.now()).toLocalDateTime(timeZone).date
        val cycle = latestDate.daysUntil(dateTime.date).floorDiv(interval)
        return latestDate.plus(DatePeriod(days = (cycle + 1) * interval)).atStartOfDayIn(timeZone)
    }

    private fun backOff(interval: Int, daysSinceRelease: Int): Int {
        var backedOff = interval
        while (backedOff < MAX_INTERVAL && daysSinceRelease > backedOff * HIATUS_CYCLES) {
            backedOff *= 2
        }
        return backedOff.coerceAtMost(MAX_INTERVAL)
    }

    private fun ceilDiv(a: Int, b: Int) = -Math.floorDiv(-a, b)

    companion object {
        const val MAX_INTERVAL = 28

        private const val DEFAULT_INTERVAL = 7
        private const val GRACE_PERIOD = 1L
        private const val HIATUS_CYCLES = 4
        private const val MIN_RELEASES_FOR_PATTERN = 4
        private const val MAX_WEEKDAYS = 3
        private const val WEEKDAY_COVERAGE = 0.8
        private const val DAY_OF_MONTH_TOLERANCE = 3
        private val MONTHLY_GAPS = 26..35
    }
}

internal data class ReleaseSchedule(
    /** Days between releases, which is what the fetch interval shows. */
    val interval: Int,
    /** The most recent release day the next ones are counted from, if any chapter has a date. */
    val lastRelease: LocalDate?,
    val pattern: ReleasePattern,
)

internal sealed interface ReleasePattern {

    /** The first expected release day after [date]. */
    fun nextAfter(date: LocalDate): LocalDate

    data class EveryDays(val days: Int) : ReleasePattern {
        override fun nextAfter(date: LocalDate) = date.plus(DatePeriod(days = days))
    }

    data class Weekdays(val days: Set<DayOfWeek>) : ReleasePattern {
        override fun nextAfter(date: LocalDate): LocalDate {
            return generateSequence(date.plus(DatePeriod(days = 1))) { it.plus(DatePeriod(days = 1)) }
                .first { it.dayOfWeek in days }
        }
    }

    /** Releases on [day] of every month, or its last day in months too short for it. */
    data class DayOfMonth(val day: Int) : ReleasePattern {
        override fun nextAfter(date: LocalDate): LocalDate {
            val sameMonth = date.inMonthClamped(day)
            if (sameMonth > date) return sameMonth
            return date.plus(DatePeriod(months = 1)).inMonthClamped(day)
        }

        private fun LocalDate.inMonthClamped(day: Int): LocalDate {
            val firstOfMonth = LocalDate(year, month, 1)
            val lastDay = firstOfMonth.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).day
            return LocalDate(year, month, day.coerceAtMost(lastDay))
        }
    }
}
