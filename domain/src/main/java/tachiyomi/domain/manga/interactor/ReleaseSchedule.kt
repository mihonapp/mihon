package tachiyomi.domain.manga.interactor

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.chapter.model.Chapter
import kotlin.time.Instant

/** How often an entry releases, and so when to check it for the next release. */
internal data class ReleaseSchedule(
    /** Days between releases, which is what the fetch interval shows. */
    val interval: Int,
    /** The most recent release day, which the next ones are counted from. */
    val lastRelease: LocalDate?,
    val pattern: ReleasePattern,
    /** Days past the expected day a release can still come, from how late recent ones were. */
    val lateness: Int = 0,
    /** Whether checks spread out once releases stop coming, which a schedule set by the user doesn't. */
    val backsOff: Boolean = true,
    /** Lengths in days of earlier breaks in releases, which a break like a season's end is expected to last again. */
    val pastBreaks: List<Int> = emptyList(),
) {

    /** The next day to check for a release, as of [today]. Today counts, so a due release keeps being checked for. */
    fun nextCheck(today: LocalDate): LocalDate {
        val anchor = lastRelease ?: today
        val daysSinceRelease = anchor.daysUntil(today)
        val isOnBreak = backsOff && daysSinceRelease > interval * HIATUS_CYCLES
        // Around when earlier breaks ended, this one likely ends too, so the schedule is followed again
        if (isOnBreak && pastBreaks.none { daysSinceRelease in it - interval..it + interval }) {
            // Past a few missed releases the schedule no longer holds, so checks spread out until it resumes
            val backedOff = backOff(daysSinceRelease)
            val next = anchor.plus(DatePeriod(days = ceilDiv(daysSinceRelease, backedOff) * backedOff))
            val breakEnd = pastBreaks.map {
                anchor.plus(DatePeriod(days = it - interval))
            }.filter { it > today }.minOrNull()
            return minOf(next, breakEnd ?: next)
        }

        val due = pattern.nextAfter(pattern.nearest(anchor))
        if (today in due..due.plus(DatePeriod(days = lateness))) return today
        return generateSequence(due, pattern::nextAfter).first { it >= today }.cappedFrom(today)
    }

    /**
     * The next day to check after a check [today] found a release.
     *
     * The release is dated by its newest chapter, since a check can turn up one that came out on an earlier day. It
     * stands in for the expected day nearest to it, so an early, late or extra release leaves the schedule in place.
     */
    fun nextCheckAfterRelease(today: LocalDate): LocalDate {
        val release = lastRelease?.coerceAtMost(today) ?: today
        // What was found is out already, so checking again today would only find it again
        val next = pattern.nextAfter(pattern.nearest(release))
        return generateSequence(next, pattern::nextAfter).first { it > today }.cappedFrom(today)
    }

    // Months and batches can be further apart than a check should ever wait
    private fun LocalDate.cappedFrom(today: LocalDate): LocalDate {
        return coerceAtMost(today.plus(DatePeriod(days = FetchInterval.MAX_INTERVAL)))
    }

    private fun backOff(daysSinceRelease: Int): Int {
        var backedOff = interval
        while (backedOff < FetchInterval.MAX_INTERVAL && daysSinceRelease > backedOff * HIATUS_CYCLES) {
            backedOff *= 2
        }
        return backedOff.coerceAtMost(FetchInterval.MAX_INTERVAL)
    }

    private fun ceilDiv(a: Int, b: Int) = -Math.floorDiv(-a, b)

    companion object {
        private const val DEFAULT_INTERVAL = 7
        private const val HIATUS_CYCLES = 4
        private const val MIN_RELEASES = 3
        private const val FEW_CHAPTERS = 8
        private const val FEW_CHAPTERS_RELEASES = 3
        private const val RECENT_RELEASES = 10
        private const val PHASE_RELEASES = 5

        /** A schedule the user set to every [interval] days, counted from [lastRelease]. */
        fun fixed(interval: Int, lastRelease: LocalDate): ReleaseSchedule {
            val pattern = ReleasePattern.EveryDays(interval, onSchedule = lastRelease)
            return ReleaseSchedule(interval, lastRelease, pattern, backsOff = false)
        }

        /** Estimates the schedule from the recent release days of [chapters], by upload date or else fetch date. */
        fun estimate(chapters: List<Chapter>, zone: TimeZone, today: LocalDate): ReleaseSchedule {
            val recentReleases = if (chapters.size <= FEW_CHAPTERS) FEW_CHAPTERS_RELEASES else RECENT_RELEASES
            // Sources that date chapters ahead of their release would otherwise pull the schedule into the future
            val latestPlausibleDay = today.plus(DatePeriod(days = 1))
            fun releaseDays(dates: Sequence<Instant>): List<LocalDate> {
                return dates
                    .map { it.toLocalDateTime(zone).date }
                    .filter { it <= latestPlausibleDay }
                    .distinct()
                    .sortedDescending()
                    .toList()
            }

            val uploadDays = releaseDays(chapters.asSequence().mapNotNull { it.dateUpload })
            val fetchDays = releaseDays(chapters.asSequence().map { it.dateFetch })
            val history = uploadDays.takeIf { it.size >= MIN_RELEASES }
                ?: fetchDays.takeIf { it.size >= MIN_RELEASES }
                ?: run {
                    val lastRelease = uploadDays.firstOrNull() ?: fetchDays.firstOrNull()
                    val pattern = ReleasePattern.EveryDays(DEFAULT_INTERVAL, onSchedule = lastRelease ?: today)
                    return ReleaseSchedule(DEFAULT_INTERVAL, lastRelease, pattern)
                }
            val days = history.take(recentReleases)

            val gaps = days.zipWithNext { newer, older -> older.daysUntil(newer) }.sorted()
            val medianGap = gaps[(gaps.size - 1) / 2]
            val detected = ReleasePattern.detect(days, history, medianGap)
            // Within a batch releases are a day apart, but it's the batches that come every so often
            val interval = ((detected as? ReleasePattern.Batches)?.every ?: medianGap)
                .coerceIn(1, FetchInterval.MAX_INTERVAL)
            return ReleaseSchedule(
                interval = interval,
                lastRelease = days.first(),
                pattern = detected
                    ?: ReleasePattern.EveryDays(
                        interval,
                        onSchedule = medianPhase(days.take(PHASE_RELEASES), interval),
                    ),
                lateness = (gaps.last() - interval).coerceIn(0, interval / 2),
                pastBreaks = history
                    .zipWithNext { newer, older -> older.daysUntil(newer) }
                    .filter { it > interval * HIATUS_CYCLES },
            )
        }

        /**
         * The release in [days], newest first, that the others land closest to in step with [interval], so one early
         * or late release doesn't shift the schedule. Ties go to the newest, which follows a schedule that moved.
         */
        private fun medianPhase(days: List<LocalDate>, interval: Int): LocalDate {
            fun offset(
                from: LocalDate,
                to: LocalDate,
            ) = from.daysUntil(to).mod(interval).let { minOf(it, interval - it) }
            val phase = days.minBy { candidate -> days.sumOf { offset(candidate, it) } }
            val onPhase = days.count { offset(phase, it) == 0 }
            return if (onPhase * 5 >= days.size * 3) phase else days.first()
        }
    }
}
