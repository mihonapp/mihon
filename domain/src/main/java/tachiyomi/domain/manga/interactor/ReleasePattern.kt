package tachiyomi.domain.manga.interactor

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.abs

/** The days an entry is expected to release on. */
internal sealed interface ReleasePattern {

    /** The first expected release day after [date]. */
    fun nextAfter(date: LocalDate): LocalDate

    /** The last expected release day before [date]. */
    fun previousBefore(date: LocalDate): LocalDate

    /** The expected release day closest to [date], which a release on [date] stands in for. */
    fun nearest(date: LocalDate): LocalDate {
        val before = previousBefore(date.plus(DatePeriod(days = 1)))
        val after = nextAfter(date.minus(DatePeriod(days = 1)))
        // A release halfway between is taken as late, so the next check comes sooner
        return if (before.daysUntil(date) <= date.daysUntil(after)) before else after
    }

    /** Releases every [days] days, in step with [onSchedule]. */
    data class EveryDays(val days: Int, val onSchedule: LocalDate) : ReleasePattern {
        override fun nextAfter(date: LocalDate): LocalDate {
            return date.plus(DatePeriod(days = days - onSchedule.daysUntil(date).mod(days)))
        }

        override fun previousBefore(date: LocalDate): LocalDate {
            return date.minus(DatePeriod(days = (onSchedule.daysUntil(date) - 1).mod(days) + 1))
        }
    }

    data class Weekdays(val days: Set<DayOfWeek>) : ReleasePattern {
        override fun nextAfter(date: LocalDate): LocalDate {
            return generateSequence(date.plus(DatePeriod(days = 1))) { it.plus(DatePeriod(days = 1)) }
                .first { it.dayOfWeek in days }
        }

        override fun previousBefore(date: LocalDate): LocalDate {
            return generateSequence(date.minus(DatePeriod(days = 1))) { it.minus(DatePeriod(days = 1)) }
                .first { it.dayOfWeek in days }
        }

        // Release days are a day or two apart, so a release between them is more likely an extra than an early one
        override fun nearest(date: LocalDate) = date
    }

    /** Releases for [length] days in a row, every [every] days in step with [onSchedule]. */
    data class Batches(val every: Int, val onSchedule: LocalDate, val length: Int) : ReleasePattern {
        override fun nextAfter(date: LocalDate): LocalDate {
            val offset = onSchedule.daysUntil(date).mod(every)
            return date.plus(DatePeriod(days = if (offset + 1 < length) 1 else every - offset))
        }

        override fun previousBefore(date: LocalDate): LocalDate {
            val dayBefore = date.minus(DatePeriod(days = 1))
            val offset = onSchedule.daysUntil(dayBefore).mod(every)
            return dayBefore.minus(DatePeriod(days = (offset - length + 1).coerceAtLeast(0)))
        }
    }

    /** A pattern that repeats every month. */
    sealed interface Monthly : ReleasePattern {
        /** The release days in the month starting on [firstOfMonth]. */
        fun daysIn(firstOfMonth: LocalDate): List<LocalDate>

        override fun nextAfter(date: LocalDate): LocalDate {
            return generateSequence(date.firstOfMonth()) { it.plus(DatePeriod(months = 1)) }
                .flatMap { daysIn(it).sorted() }
                .first { it > date }
        }

        override fun previousBefore(date: LocalDate): LocalDate {
            return generateSequence(date.firstOfMonth()) { it.minus(DatePeriod(months = 1)) }
                .flatMap { daysIn(it).sortedDescending() }
                .first { it < date }
        }
    }

    /** Releases on [days] of every month, or a month's last day for those past it. */
    data class DaysOfMonth(val days: Set<Int>) : Monthly {
        override fun daysIn(firstOfMonth: LocalDate): List<LocalDate> {
            val lastDay = firstOfMonth.lastOfMonth().day
            return days.map { firstOfMonth.plus(DatePeriod(days = it.coerceAtMost(lastDay) - 1)) }.distinct()
        }
    }

    /** Releases on [dayOfWeek] of the [week]th week of every month, or its last week for [LAST_WEEK]. */
    data class WeekdayOfMonth(val week: Int, val dayOfWeek: DayOfWeek) : Monthly {
        override fun daysIn(firstOfMonth: LocalDate): List<LocalDate> {
            if (week == LAST_WEEK) {
                val lastOfMonth = firstOfMonth.lastOfMonth()
                val daysBack = (lastOfMonth.dayOfWeek.isoDayNumber - dayOfWeek.isoDayNumber).mod(7)
                return listOf(lastOfMonth.minus(DatePeriod(days = daysBack)))
            }
            val daysAhead = (dayOfWeek.isoDayNumber - firstOfMonth.dayOfWeek.isoDayNumber).mod(7)
            return listOf(firstOfMonth.plus(DatePeriod(days = daysAhead + (week - 1) * 7)))
        }
    }

    companion object {
        const val LAST_WEEK = -1

        private const val MIN_RELEASES = 4
        private const val MAX_WEEKDAYS = 3
        private const val COVERAGE = 0.8
        private const val DAY_OF_MONTH_TOLERANCE = 3
        private val MONTHLY_GAPS = 26..35
        private val TWICE_MONTHLY_GAPS = 12..18

        private const val BATCH_HISTORY = 40
        private const val BATCH_GAP = 2
        private const val BATCHES = 6
        private const val MIN_BATCHES = 3

        /**
         * Finds a calendar pattern a plain day interval can't follow in [days], newest first: a few fixed weekdays,
         * days or a weekday of the month, or releases in batches, which can take longer than [days] to show up in
         * [history]. They all need a few releases to be told apart from chance.
         */
        fun detect(days: List<LocalDate>, history: List<LocalDate>, medianGap: Int): ReleasePattern? {
            if (days.size < MIN_RELEASES) return null
            // Batches come first, since their days can look like a few weekdays or a monthly schedule
            return batches(history) ?: when (medianGap) {
                in MONTHLY_GAPS -> monthly(days)
                in TWICE_MONTHLY_GAPS -> twiceMonthly(days)
                else -> weekdays(days, medianGap)
            }
        }

        private fun monthly(days: List<LocalDate>): ReleasePattern? {
            val daysOfMonth = days.map { it.day }
            return when {
                daysOfMonth.max() - daysOfMonth.min() <= DAY_OF_MONTH_TOLERANCE -> DaysOfMonth(
                    setOf(daysOfMonth.median()),
                )
                // The last day of the month moves between the 28th and the 31st
                daysOfMonth.all { it >= 28 } -> DaysOfMonth(setOf(31))
                else -> weekdayOfMonth(days)
            }
        }

        private fun weekdayOfMonth(days: List<LocalDate>): ReleasePattern? {
            val dayOfWeek = days.mostCommon { it.dayOfWeek } ?: return null
            // A fifth week isn't in every month, so those days only count as the last week
            val week = days.mostCommon { (it.day - 1) / 7 + 1 }?.takeIf { it < 5 }
            if (week != null) return WeekdayOfMonth(week, dayOfWeek)
            val isLastWeek = days.count { it.day + 7 > it.lastOfMonth().day } >= days.size * COVERAGE
            return if (isLastWeek) WeekdayOfMonth(LAST_WEEK, dayOfWeek) else null
        }

        private fun twiceMonthly(days: List<LocalDate>): ReleasePattern? {
            val daysOfMonth = days.map { it.day }.sorted()
            val split = (1..<daysOfMonth.size).maxBy { daysOfMonth[it] - daysOfMonth[it - 1] }
            val halves = listOf(daysOfMonth.subList(0, split), daysOfMonth.subList(split, daysOfMonth.size))
            if (halves.any { it.size < 2 || it.last() - it.first() > DAY_OF_MONTH_TOLERANCE }) return null
            return DaysOfMonth(halves.map { it.median() }.toSet())
        }

        private fun weekdays(days: List<LocalDate>, medianGap: Int): ReleasePattern? {
            val byFrequency = days.groupingBy { it.dayOfWeek }.eachCount().entries.sortedByDescending { it.value }
            val weekdays = mutableSetOf<DayOfWeek>()
            var covered = 0
            for ((dayOfWeek, count) in byFrequency) {
                if (covered >= days.size * COVERAGE) break
                weekdays += dayOfWeek
                covered += count
            }
            // One weekday is a weekly interval, and an irregular schedule spreads over most of the week
            if (weekdays.size !in 2..MAX_WEEKDAYS || covered < days.size * COVERAGE) return null
            // Releasing on several weekdays means more than one release a week
            if (medianGap > 7 / weekdays.size + 1) return null
            return Weekdays(weekdays)
        }

        /** Runs of releases a day or two apart, starting at a steady interval and mostly longer than a day. */
        private fun batches(history: List<LocalDate>): ReleasePattern? {
            val runs = mutableListOf<MutableList<LocalDate>>()
            for (day in history.take(BATCH_HISTORY).asReversed()) {
                val run = runs.lastOrNull()
                if (run != null && run.last().daysUntil(day) <= BATCH_GAP) run += day else runs += mutableListOf(day)
            }
            val recent = runs.takeLast(BATCHES)
            if (recent.size < MIN_BATCHES || recent.count { it.size > 1 } * 2 < recent.size) return null

            val gaps = recent.zipWithNext { older, newer -> older.first().daysUntil(newer.first()) }
            val every = gaps.median()
            val length = recent.map { it.first().daysUntil(it.last()) + 1 }.median()
            if (every < length * 2 || gaps.any { abs(it - every) > every / 4 }) return null
            return Batches(every, recent.last().first(), length)
        }

        private fun List<Int>.median() = sorted()[(size - 1) / 2]

        /** The most common key of [selector], if at least [COVERAGE] of the days share it. */
        private fun <T> List<LocalDate>.mostCommon(selector: (LocalDate) -> T): T? {
            val (key, count) = groupingBy(selector).eachCount().maxBy { it.value }
            return key.takeIf { count >= size * COVERAGE }
        }
    }
}

private fun LocalDate.firstOfMonth() = LocalDate(year, month, 1)

private fun LocalDate.lastOfMonth() = firstOfMonth().plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
