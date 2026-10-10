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

    private val fetchInterval = FetchInterval(mockk())

    @Test
    fun `returns default interval of 7 days when not enough distinct days`() {
        val chaptersWithUploadDate = (1..50).map {
            chapterWithTime(chapter, 1.days)
        }
        fetchInterval.calculateInterval(chaptersWithUploadDate, testTimeZone) shouldBe 7

        val chaptersWithoutUploadDate = chaptersWithUploadDate.map {
            it.copy(dateUpload = null)
        }
        fetchInterval.calculateInterval(chaptersWithoutUploadDate, testTimeZone) shouldBe 7
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

        fetchInterval.calculateInterval(chapters, testTimeZone) shouldBe 1
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

        fetchInterval.calculateInterval(chapters, testTimeZone) shouldBe 7
    }

    @Test
    fun `returns interval of 7 days when multiple chapters in 1 day`() {
        val chapters = (1..10).map {
            chapterWithTime(chapter, 10.hours)
        }
        fetchInterval.calculateInterval(chapters, testTimeZone) shouldBe 7
    }

    @Test
    fun `returns interval of 7 days when multiple chapters in 2 days`() {
        val chapters = (1..2).map {
            chapterWithTime(chapter, 1.days)
        } + (1..5).map {
            chapterWithTime(chapter, 2.days)
        }
        fetchInterval.calculateInterval(chapters, testTimeZone) shouldBe 7
    }

    @Test
    fun `returns interval of 1 day when chapters are released every 1 day`() {
        val chapters = (1..20).map {
            chapterWithTime(chapter, it.days)
        }
        fetchInterval.calculateInterval(chapters, testTimeZone) shouldBe 1
    }

    @Test
    fun `returns interval of 1 day when delta is less than 1 day`() {
        val chapters = (1..20).map {
            chapterWithTime(chapter, (15 * it).hours)
        }
        fetchInterval.calculateInterval(chapters, testTimeZone) shouldBe 1
    }

    @Test
    fun `returns interval of 2 days when chapters are released every 2 days`() {
        val chapters = (1..20).map {
            chapterWithTime(chapter, (2 * it).days)
        }
        fetchInterval.calculateInterval(chapters, testTimeZone) shouldBe 2
    }

    @Test
    fun `returns interval with floored value when interval is decimal`() {
        val chaptersWithUploadDate = (1..5).map {
            chapterWithTime(chapter, (25 * it).hours)
        }
        fetchInterval.calculateInterval(chaptersWithUploadDate, testTimeZone) shouldBe 1

        val chaptersWithoutUploadDate = chaptersWithUploadDate.map {
            it.copy(dateUpload = null)
        }
        fetchInterval.calculateInterval(chaptersWithoutUploadDate, testTimeZone) shouldBe 1
    }

    @Test
    fun `returns interval of 2 days when chapters are released just below every 2 days`() {
        val chapters = (1..20).map {
            chapterWithTime(chapter, (43 * it).hours)
        }
        fetchInterval.calculateInterval(chapters, testTimeZone) shouldBe 2
    }

    @Test
    fun `follows releases on a few fixed weekdays`() = runTest {
        val mondaysAndThursdays = generateSequence(LocalDate.parse("2019-12-02")) { it.plus(DatePeriod(days = 1)) }
            .takeWhile { it <= LocalDate.parse("2020-01-20") }
            .filter { it.dayOfWeek == DayOfWeek.MONDAY || it.dayOfWeek == DayOfWeek.THURSDAY }
            .toList()
        val chapters = chaptersOn(mondaysAndThursdays)

        fetchInterval.estimateSchedule(chapters, testTimeZone, LocalDate.parse("2020-01-21")).pattern shouldBe
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
        )
        return update.nextUpdate!!.toLocalDateTime(testTimeZone).date
    }

    private fun weekly(from: String, to: String): List<LocalDate> {
        return generateSequence(LocalDate.parse(from)) { it.plus(DatePeriod(days = 7)) }
            .takeWhile { it <= LocalDate.parse(to) }
            .toList()
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
