package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class MissingChaptersTest {

    @Test
    fun `skipping a hidden chapter does not force a missing chapter transition`() {
        calculateChapterGap(chapter(3, setOf(2)), chapter(1, setOf(2))) shouldBe 0
    }

    @Test
    fun `genuine gaps remain visible while known hidden chapters are excluded`() {
        calculateChapterGap(chapter(5, setOf(2)), chapter(1, setOf(2))) shouldBe 2
    }

    @Test
    fun `duplicate hidden numbers and outliers do not erase genuine gaps`() {
        calculateChapterGap(chapter(5, setOf(2, 99990000)), chapter(1, setOf(2, 99990000))) shouldBe 2
    }

    @Test
    fun `visible chapters keep existing gap and boundary behavior`() {
        calculateChapterGap(chapter(3), chapter(1)) shouldBe 1
        calculateChapterGap(chapter(2), chapter(1)) shouldBe 0
        calculateChapterGap(null, chapter(1)) shouldBe 0
        calculateChapterGap(chapter(1), null) shouldBe 0
    }

    private fun chapter(number: Long, hiddenNumbers: Set<Int> = emptySet()) = ReaderChapter(
        Chapter.create().copy(id = number, chapterNumber = number.toDouble()),
        hiddenChapterNumbers = hiddenNumbers,
    )
}
