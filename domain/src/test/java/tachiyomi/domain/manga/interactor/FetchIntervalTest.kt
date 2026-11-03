package tachiyomi.domain.manga.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@Execution(ExecutionMode.CONCURRENT)
class FetchIntervalTest {

    private val testTime = LocalDateTime.parse("2020-01-01T00:00:00")
    private val testTimeZone = TimeZone.UTC
    private var chapter = Chapter.create().copy(
        dateFetch = testTime.toInstant(testTimeZone),
        dateUpload = testTime.toInstant(testTimeZone),
    )

    @Test
    fun `returns default interval of 7 days when not enough distinct days`() {
        val chaptersWithUploadDate = (1..50).map {
            chapterWithTime(chapter, 1.days)
        }
        interval(chaptersWithUploadDate) shouldBe 7

        val chaptersWithoutUploadDate = chaptersWithUploadDate.map {
            it.copy(dateUpload = null)
        }
        interval(chaptersWithoutUploadDate) shouldBe 7
    }

    @Test
    fun `returns interval based on more recent chapters`() {
        val oldChapters = (1..5).map {
            chapterWithTime(chapter, (it * 7).days) // Would have interval of 7 days
        }
        val newChapters = (1..10).map {
            chapterWithTime(chapter, oldChapters.lastUploadDate() + it.days)
        }

        val chapters = oldChapters + newChapters

        interval(chapters) shouldBe 1
    }

    @Test
    fun `returns interval based on smaller subset of recent chapters if very few chapters`() {
        val oldChapters = (1..3).map {
            chapterWithTime(chapter, (it * 7).days)
        }
        // Significant gap between chapters
        val newChapters = (1..3).map {
            chapterWithTime(chapter, oldChapters.lastUploadDate() + 365.days + (it * 7).days)
        }

        val chapters = oldChapters + newChapters

        interval(chapters) shouldBe 7
    }

    @Test
    fun `returns interval of 7 days when multiple chapters in 1 day`() {
        val chapters = (1..10).map {
            chapterWithTime(chapter, 10.hours)
        }
        interval(chapters) shouldBe 7
    }

    @Test
    fun `returns interval of 7 days when multiple chapters in 2 days`() {
        val chapters = (1..2).map {
            chapterWithTime(chapter, 1.days)
        } + (1..5).map {
            chapterWithTime(chapter, 2.days)
        }
        interval(chapters) shouldBe 7
    }

    @Test
    fun `returns interval of 1 day when chapters are released every 1 day`() {
        val chapters = (1..20).map {
            chapterWithTime(chapter, it.days)
        }
        interval(chapters) shouldBe 1
    }

    @Test
    fun `returns interval of 1 day when delta is less than 1 day`() {
        val chapters = (1..20).map {
            chapterWithTime(chapter, (15 * it).hours)
        }
        interval(chapters) shouldBe 1
    }

    @Test
    fun `returns interval of 2 days when chapters are released every 2 days`() {
        val chapters = (1..20).map {
            chapterWithTime(chapter, (2 * it).days)
        }
        interval(chapters) shouldBe 2
    }

    @Test
    fun `returns interval with floored value when interval is decimal`() {
        val chaptersWithUploadDate = (1..5).map {
            chapterWithTime(chapter, (25 * it).hours)
        }
        interval(chaptersWithUploadDate) shouldBe 1

        val chaptersWithoutUploadDate = chaptersWithUploadDate.map {
            it.copy(dateUpload = null)
        }
        interval(chaptersWithoutUploadDate) shouldBe 1
    }

    @Test
    fun `returns interval of 2 days when chapters are released just below every 2 days`() {
        val chapters = (1..20).map {
            chapterWithTime(chapter, (43 * it).hours)
        }
        interval(chapters) shouldBe 2
    }

    @Test
    fun `follows releases on a few fixed weekdays`() = runTest {
        val mondaysAndThursdays = generateSequence(LocalDate.parse("2019-12-02")) { it.plus(DatePeriod(days = 1)) }
            .takeWhile { it <= LocalDate.parse("2020-01-20") }
            .filter { it.dayOfWeek == DayOfWeek.MONDAY || it.dayOfWeek == DayOfWeek.THURSDAY }
            .toList()
        val chapters = chaptersOn(mondaysAndThursdays)

        ReleaseSchedule.estimate(chapters, testTimeZone, LocalDate.parse("2020-01-21")).pattern shouldBe
            ReleasePattern.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        nextUpdate(today = "2020-01-21", chapters) shouldBe LocalDate.parse("2020-01-23")
    }

    @Test
    fun `follows releases on a day of the month`() = runTest {
        val chapters = chaptersOn(
            listOf("2019-09-15", "2019-10-15", "2019-11-14", "2019-12-15", "2020-01-16").map(LocalDate::parse),
            perDay = 2,
        )

        nextUpdate(today = "2020-01-20", chapters) shouldBe LocalDate.parse("2020-02-15")
    }

    @Test
    fun `follows releases on the last day of the month`() = runTest {
        val chapters = chaptersOn(
            listOf("2019-09-30", "2019-10-31", "2019-11-30", "2019-12-31", "2020-01-31").map(LocalDate::parse),
            perDay = 2,
        )

        nextUpdate(today = "2020-02-05", chapters) shouldBe LocalDate.parse("2020-02-29")
    }

    @Test
    fun `follows releases on two days of the month`() = runTest {
        val days =
            listOf("2019-10-01", "2019-10-15", "2019-11-01", "2019-11-16", "2019-12-01", "2019-12-15", "2020-01-01")
        val chapters = chaptersOn((days + "2020-01-15").map(LocalDate::parse), perDay = 2)

        ReleaseSchedule.estimate(chapters, testTimeZone, LocalDate.parse("2020-01-20")).pattern shouldBe
            ReleasePattern.DaysOfMonth(setOf(1, 15))
        nextUpdate(today = "2020-01-20", chapters) shouldBe LocalDate.parse("2020-02-01")
    }

    @Test
    fun `follows releases on a weekday of the month`() = runTest {
        val firstSaturdays = listOf("2019-09-07", "2019-10-05", "2019-11-02", "2019-12-07", "2020-01-04")
        val chapters = chaptersOn(firstSaturdays.map(LocalDate::parse), perDay = 2)

        nextUpdate(today = "2020-01-10", chapters) shouldBe LocalDate.parse("2020-02-01")
    }

    @Test
    fun `follows releases on a weekday of the last week of the month`() = runTest {
        val lastFridays = listOf("2019-09-27", "2019-10-25", "2019-11-29", "2019-12-27", "2020-01-31")
        val chapters = chaptersOn(lastFridays.map(LocalDate::parse), perDay = 2)

        nextUpdate(today = "2020-02-03", chapters) shouldBe LocalDate.parse("2020-02-28")
    }

    @Test
    fun `follows releases in batches`() = runTest {
        // Three days of releases every three weeks
        val batches = every(days = 21, from = "2019-09-02", to = "2019-12-16")
            .flatMap { start -> (0..2).map { start.plus(DatePeriod(days = it)) } }
        val chapters = chaptersOn(batches.filter { it <= LocalDate.parse("2019-12-17") })

        nextUpdate(today = "2019-12-17", chapters, foundRelease = true) shouldBe LocalDate.parse("2019-12-18")
        nextUpdate(today = "2019-12-18", chaptersOn(batches), foundRelease = true) shouldBe
            LocalDate.parse("2020-01-06")
    }

    @Test
    fun `checks again around when an earlier break ended`() = runTest {
        // A season of weekly releases, a 98 day break, and another season
        val chapters = chaptersOn(weekly(from = "2019-01-02", to = "2019-03-27") + weekly("2019-07-03", "2019-09-25"))

        nextUpdate(today = "2019-12-20", chapters) shouldBe LocalDate.parse("2019-12-25")
        nextUpdate(today = "2019-12-26", chapters) shouldBe LocalDate.parse("2020-01-01")
    }

    @Test
    fun `never waits more than 28 days between checks`() = runTest {
        val lastDays = listOf("2019-09-30", "2019-10-31", "2019-11-30", "2019-12-31", "2020-01-31")
        val chapters = chaptersOn(lastDays.map(LocalDate::parse), perDay = 2)

        nextUpdate(today = "2020-01-31", chapters, foundRelease = true) shouldBe LocalDate.parse("2020-02-28")
    }

    @Test
    fun `keeps checking on the expected day`() = runTest {
        val chapters = chaptersOn(weekly(from = "2019-12-04", to = "2019-12-25"))

        nextUpdate(today = "2020-01-01", chapters) shouldBe LocalDate.parse("2020-01-01")
    }

    @Test
    fun `keeps a next update in the window while nothing new came out`() = runTest {
        val chapters = chaptersOn(weekly(from = "2019-12-04", to = "2019-12-25"))
        val manga = Manga.create().copy(nextUpdate = LocalDate.parse("2020-01-01").atStartOfDayIn(testTimeZone))

        nextUpdate(today = "2020-01-02", chapters, manga) shouldBe LocalDate.parse("2020-01-01")
    }

    @Test
    fun `counts from a release on the expected day`() = runTest {
        val chapters = chaptersOn(weekly(from = "2019-12-04", to = "2020-01-01"))
        val manga = Manga.create().copy(nextUpdate = LocalDate.parse("2020-01-01").atStartOfDayIn(testTimeZone))

        nextUpdate(today = "2020-01-01", chapters, manga) shouldBe LocalDate.parse("2020-01-08")
    }

    @Test
    fun `keeps the schedule for a release uploaded the day before it was found`() = runTest {
        val chapters = chaptersOn(weekly(from = "2019-10-02", to = "2019-12-25") + LocalDate.parse("2019-12-31"))

        nextUpdate(today = "2020-01-01", chapters, expecting("2020-01-01"), foundRelease = true) shouldBe
            LocalDate.parse("2020-01-08")
    }

    @Test
    fun `keeps the schedule after a release shortly before the expected day`() = runTest {
        val chapters = chaptersOn(weekly(from = "2019-10-02", to = "2019-12-25") + LocalDate.parse("2019-12-30"))

        nextUpdate(today = "2019-12-30", chapters, expecting("2020-01-01"), foundRelease = true) shouldBe
            LocalDate.parse("2020-01-08")
    }

    @Test
    fun `keeps the schedule after a late release`() = runTest {
        val chapters = chaptersOn(weekly(from = "2019-12-04", to = "2019-12-25") + LocalDate.parse("2020-01-03"))

        nextUpdate(today = "2020-01-03", chapters, expecting("2020-01-01"), foundRelease = true) shouldBe
            LocalDate.parse("2020-01-08")
    }

    @Test
    fun `keeps expecting the release after an extra one well before it`() = runTest {
        val chapters = chaptersOn(weekly(from = "2019-10-02", to = "2020-01-01") + LocalDate.parse("2020-01-02"))

        nextUpdate(today = "2020-01-02", chapters, expecting("2020-01-08"), foundRelease = true) shouldBe
            LocalDate.parse("2020-01-08")
    }

    @Test
    fun `follows the schedule again when releases resume after a hiatus`() = runTest {
        val chapters = chaptersOn(weekly(from = "2019-09-11", to = "2019-10-02") + LocalDate.parse("2020-01-01"))

        nextUpdate(today = "2020-01-01", chapters, expecting("2020-01-22"), foundRelease = true) shouldBe
            LocalDate.parse("2020-01-08")
    }

    @Test
    fun `follows the schedule once releases keep coming early`() = runTest {
        val history = every(days = 5, from = "2019-10-01", to = "2019-12-15")
        val chapters = chaptersOn(history + every(days = 5, from = "2019-12-19", to = "2019-12-29"))

        nextUpdate(today = "2019-12-29", chapters, expecting("2019-12-30"), foundRelease = true) shouldBe
            LocalDate.parse("2020-01-03")
    }

    @Test
    fun `keeps checking for an overdue release while recent ones came that late`() = runTest {
        val chapters = chaptersOn(afterGaps("2019-10-18", 6, 5, 8, 6, 5, 6, 8, 6, 5))

        // Due on the 18th from a median gap of 6, and gaps have run to 8
        nextUpdate(today = "2019-12-20", chapters, expecting("2019-12-18")) shouldBe LocalDate.parse("2019-12-20")
    }

    @Test
    fun `counts from an early release with a custom interval`() = runTest {
        val manga = expecting("2020-01-01").copy(fetchInterval = -7)

        nextUpdate(today = "2019-12-31", emptyList(), manga, foundRelease = true) shouldBe
            LocalDate.parse("2020-01-07")
    }

    @Test
    fun `spreads out checks during a hiatus`() = runTest {
        val chapters = chaptersOn(weekly(from = "2019-09-11", to = "2019-10-02"))

        nextUpdate(today = "2020-01-01", chapters) shouldBe LocalDate.parse("2020-01-22")
    }

    @Test
    fun `ignores chapters dated in the future`() = runTest {
        val chapters = chaptersOn(weekly(from = "2019-12-04", to = "2019-12-25") + LocalDate.parse("2020-06-01"))

        nextUpdate(today = "2020-01-01", chapters) shouldBe LocalDate.parse("2020-01-01")
    }

    private suspend fun nextUpdate(
        today: String,
        chapters: List<Chapter>,
        manga: Manga = Manga.create(),
        foundRelease: Boolean = false,
    ): LocalDate {
        val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
        coEvery { getChaptersByMangaId.await(any(), any()) } returns chapters
        val fetchInterval = FetchInterval(getChaptersByMangaId)
        val date = LocalDate.parse(today)
        val update = fetchInterval.withFetchInterval(
            manga = manga,
            dateTime = date.atTime(10, 0),
            timeZone = testTimeZone,
            window = fetchInterval.getWindow(date, testTimeZone),
            foundRelease = foundRelease,
        )
        return update.nextUpdate!!.toLocalDateTime(testTimeZone).date
    }

    private fun expecting(day: String): Manga {
        return Manga.create().copy(nextUpdate = LocalDate.parse(day).atStartOfDayIn(testTimeZone))
    }

    private fun interval(chapters: List<Chapter>): Int {
        return ReleaseSchedule.estimate(
            chapters,
            testTimeZone,
            Clock.System.now().toLocalDateTime(testTimeZone).date,
        ).interval
    }

    private fun weekly(from: String, to: String) = every(days = 7, from, to)

    private fun every(days: Int, from: String, to: String): List<LocalDate> {
        return generateSequence(LocalDate.parse(from)) { it.plus(DatePeriod(days = days)) }
            .takeWhile { it <= LocalDate.parse(to) }
            .toList()
    }

    private fun afterGaps(from: String, vararg gaps: Int): List<LocalDate> {
        return gaps.runningFold(LocalDate.parse(from)) { day, gap -> day.plus(DatePeriod(days = gap)) }
    }

    private fun chaptersOn(days: List<LocalDate>, perDay: Int = 1): List<Chapter> {
        return days.flatMap { day ->
            val time = day.atStartOfDayIn(testTimeZone) + 12.hours
            List(perDay) { chapter.copy(dateFetch = time, dateUpload = time) }
        }
    }

    private fun chapterWithTime(chapter: Chapter, duration: Duration): Chapter {
        val newTime = testTime.toInstant(testTimeZone) + duration
        return chapter.copy(dateFetch = newTime, dateUpload = newTime)
    }

    private fun List<Chapter>.lastUploadDate() =
        last().dateUpload!! - testTime.toInstant(testTimeZone)
}
