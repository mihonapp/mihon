package tachiyomi.domain.manga.interactor

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.chapter.model.Chapter
import java.time.Duration
import java.time.ZonedDateTime

@Execution(ExecutionMode.CONCURRENT)
class SetFetchIntervalTest {

    private val testTime = ZonedDateTime.parse("2020-01-01T00:00:00Z")
    private var chapter = Chapter.create().copy(
        dateFetch = testTime.toEpochSecond() * 1000,
        dateUpload = testTime.toEpochSecond() * 1000,
    )

    private val setFetchInterval = SetFetchInterval(mockk())

    @Test
    fun `calculateInterval returns default of 7 days when less than 3 distinct days`() {
        val chapters = mutableListOf<Chapter>()
        (1..1).forEach {
            val duration = Duration.ofHours(10)
            val newChapter = chapterAddTime(chapter, duration)
            chapters.add(newChapter)
        }
        setFetchInterval.calculateInterval(chapters, testTime) shouldBe 7
    }

    @Test
    fun `calculateInterval returns 7 when 5 chapters in 1 day`() {
        val chapters = mutableListOf<Chapter>()
        (1..5).forEach {
            val duration = Duration.ofHours(10)
            val newChapter = chapterAddTime(chapter, duration)
            chapters.add(newChapter)
        }
        setFetchInterval.calculateInterval(chapters, testTime) shouldBe 7
    }

    @Test
    fun `calculateInterval returns 7 when 7 chapters in 48 hours, 2 day`() {
        val chapters = mutableListOf<Chapter>()
        (1..2).forEach {
            val duration = Duration.ofHours(24L)
            val newChapter = chapterAddTime(chapter, duration)
            chapters.add(newChapter)
        }
        (1..5).forEach {
            val duration = Duration.ofHours(48L)
            val newChapter = chapterAddTime(chapter, duration)
            chapters.add(newChapter)
        }
        setFetchInterval.calculateInterval(chapters, testTime) shouldBe 7
    }

    @Test
    fun `calculateInterval returns default of 1 day when interval less than 1`() {
        val chapters = mutableListOf<Chapter>()
        (1..5).forEach {
            val duration = Duration.ofHours(15L * it)
            val newChapter = chapterAddTime(chapter, duration)
            chapters.add(newChapter)
        }
        setFetchInterval.calculateInterval(chapters, testTime) shouldBe 1
    }

    // Normal interval calculation
    @Test
    fun `calculateInterval returns 1 when 5 chapters in 120 hours, 5 days`() {
        val chapters = mutableListOf<Chapter>()
        (1..5).forEach {
            val duration = Duration.ofHours(24L * it)
            val newChapter = chapterAddTime(chapter, duration)
            chapters.add(newChapter)
        }
        setFetchInterval.calculateInterval(chapters, testTime) shouldBe 1
    }

    @Test
    fun `calculateInterval returns 2 when 5 chapters in 240 hours, 10 days`() {
        val chapters = mutableListOf<Chapter>()
        (1..5).forEach {
            val duration = Duration.ofHours(48L * it)
            val newChapter = chapterAddTime(chapter, duration)
            chapters.add(newChapter)
        }
        setFetchInterval.calculateInterval(chapters, testTime) shouldBe 2
    }

    @Test
    fun `calculateInterval returns floored value when interval is decimal`() {
        val chapters = mutableListOf<Chapter>()
        (1..5).forEach {
            val duration = Duration.ofHours(25L * it)
            val newChapter = chapterAddTime(chapter, duration)
            chapters.add(newChapter)
        }
        setFetchInterval.calculateInterval(chapters, testTime) shouldBe 1
    }

    @Test
    fun `calculateInterval returns 1 when 5 chapters in 215 hours, 5 days`() {
        val chapters = mutableListOf<Chapter>()
        (1..5).forEach {
            val duration = Duration.ofHours(43L * it)
            val newChapter = chapterAddTime(chapter, duration)
            chapters.add(newChapter)
        }
        setFetchInterval.calculateInterval(chapters, testTime) shouldBe 1
    }

    @Test
    fun `calculateInterval returns interval based on fetch time if upload time not available`() {
        val chapters = mutableListOf<Chapter>()
        (1..5).forEach {
            val duration = Duration.ofHours(25L * it)
            val newChapter = chapterAddTime(chapter, duration).copy(dateUpload = 0L)
            chapters.add(newChapter)
        }
        setFetchInterval.calculateInterval(chapters, testTime) shouldBe 1
    }

    private fun chapterAddTime(chapter: Chapter, duration: Duration): Chapter {
        val newTime = testTime.plus(duration).toEpochSecond() * 1000
        return chapter.copy(dateFetch = newTime, dateUpload = newTime)
    }
}
