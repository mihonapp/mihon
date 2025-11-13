package tachiyomi.domain.chapter.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.chapter.model.Chapter

@Execution(ExecutionMode.CONCURRENT)
class MissingChaptersTest {

    @Test
    fun `missingChaptersCount returns 0 when empty list`() {
        emptyList<Double>().missingChaptersCount() shouldBe 0
    }

    @Test
    fun `missingChaptersCount returns 0 when all unknown chapter numbers`() {
        listOf(-1.0, -1.0, -1.0).missingChaptersCount() shouldBe 0
    }

    @Test
    fun `missingChaptersCount handles repeated base chapter numbers`() {
        listOf(1.0, 1.0, 1.1, 1.5, 1.6, 1.99).missingChaptersCount() shouldBe 0
    }

    @Test
    fun `missingChaptersCount returns number of missing chapters`() {
        listOf(-1.0, 1.0, 2.0, 2.2, 4.0, 6.0, 10.0, 11.0).missingChaptersCount() shouldBe 5
    }

    @Test
    fun `calculateChapterGap returns difference`() {
        calculateChapterGap(chapter(10.0), chapter(9.0)) shouldBe 0f
        calculateChapterGap(chapter(10.0), chapter(8.0)) shouldBe 1f
        calculateChapterGap(chapter(10.0), chapter(8.5)) shouldBe 1f
        calculateChapterGap(chapter(10.0), chapter(1.1)) shouldBe 8f

        calculateChapterGap(10.0, 9.0) shouldBe 0f
        calculateChapterGap(10.0, 8.0) shouldBe 1f
        calculateChapterGap(10.0, 8.5) shouldBe 1f
        calculateChapterGap(10.0, 1.1) shouldBe 8f
    }

    @Test
    fun `calculateChapterGap returns 0 if either are not valid chapter numbers`() {
        calculateChapterGap(chapter(-1.0), chapter(10.0)) shouldBe 0
        calculateChapterGap(chapter(99.0), chapter(-1.0)) shouldBe 0

        calculateChapterGap(-1.0, 10.0) shouldBe 0
        calculateChapterGap(99.0, -1.0) shouldBe 0
    }

    private fun chapter(number: Double) = Chapter.create().copy(
        chapterNumber = number,
    )
}
