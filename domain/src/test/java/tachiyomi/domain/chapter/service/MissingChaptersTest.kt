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
        emptyList<Float>().missingChaptersCount() shouldBe 0
    }

    @Test
    fun `missingChaptersCount returns 0 when all unknown chapter numbers`() {
        listOf(-1f, -1f, -1f).missingChaptersCount() shouldBe 0
    }

    @Test
    fun `missingChaptersCount handles repeated base chapter numbers`() {
        listOf(1f, 1.0f, 1.1f, 1.5f, 1.6f, 1.99f).missingChaptersCount() shouldBe 0
    }

    @Test
    fun `missingChaptersCount returns number of missing chapters`() {
        listOf(-1f, 1f, 2f, 2.2f, 4f, 6f, 10f, 11f).missingChaptersCount() shouldBe 5
    }

    @Test
    fun `calculateChapterGap returns difference`() {
        calculateChapterGap(chapter(10f), chapter(9f)) shouldBe 0f
        calculateChapterGap(chapter(10f), chapter(8f)) shouldBe 1f
        calculateChapterGap(chapter(10f), chapter(8.5f)) shouldBe 1f
        calculateChapterGap(chapter(10f), chapter(1.1f)) shouldBe 8f

        calculateChapterGap(10f, 9f) shouldBe 0f
        calculateChapterGap(10f, 8f) shouldBe 1f
        calculateChapterGap(10f, 8.5f) shouldBe 1f
        calculateChapterGap(10f, 1.1f) shouldBe 8f
    }

    @Test
    fun `calculateChapterGap returns 0 if either are not valid chapter numbers`() {
        calculateChapterGap(chapter(-1f), chapter(10f)) shouldBe 0
        calculateChapterGap(chapter(99f), chapter(-1f)) shouldBe 0

        calculateChapterGap(-1f, 10f) shouldBe 0
        calculateChapterGap(99f, -1f) shouldBe 0
    }

    private fun chapter(number: Float) = Chapter.create().copy(
        chapterNumber = number,
    )
}
