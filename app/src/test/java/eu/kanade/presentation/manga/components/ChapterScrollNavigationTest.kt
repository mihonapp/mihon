package eu.kanade.presentation.manga.components

import eu.kanade.presentation.manga.MangaScreenItem
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ChapterScrollNavigationTest {

    @Test
    fun `long descriptions use overlapping seventy percent jumps in both directions`() {
        val rows = listOf(ChapterNavigationRow(-300, 1600, MangaScreenItem.DESCRIPTION_WITH_TAG))
        chapterPageDistance(rows, 0, 1000, 1) shouldBe 700f
        chapterPageDistance(rows, 0, 1000, -1) shouldBe -700f
    }

    @Test
    fun `visible chapter header is brought to the top once the description has been shown`() {
        val rows = listOf(
            ChapterNavigationRow(-200, 350, MangaScreenItem.DESCRIPTION_WITH_TAG),
            ChapterNavigationRow(350, 410, MangaScreenItem.CHAPTER_HEADER),
            ChapterNavigationRow(410, 510, MangaScreenItem.CHAPTER),
        )
        chapterPageDistance(rows, 0, 1000, 1) shouldBe 350f
        chapterPageDistance(rows, 0, 1000, -1) shouldBe -700f
    }

    @Test
    fun `chapter pages count complete rows and spacing without including partial rows`() {
        val rows = listOf(
            ChapterNavigationRow(-30, 70, MangaScreenItem.CHAPTER),
            ChapterNavigationRow(74, 174, MangaScreenItem.CHAPTER),
            ChapterNavigationRow(178, 278, MangaScreenItem.CHAPTER),
            ChapterNavigationRow(282, 382, MangaScreenItem.CHAPTER),
        )
        chapterPageDistance(rows, 0, 350, 1, 4) shouldBe 208f
        chapterPageDistance(rows, 0, 350, -1, 4) shouldBe -208f
    }

    @Test
    fun `header and rows hidden behind the toolbar do not increase chapter page size`() {
        val rows = listOf(
            ChapterNavigationRow(0, 60, MangaScreenItem.CHAPTER_HEADER),
            ChapterNavigationRow(60, 160, MangaScreenItem.CHAPTER),
            ChapterNavigationRow(160, 260, MangaScreenItem.CHAPTER),
            ChapterNavigationRow(260, 360, MangaScreenItem.CHAPTER),
        )
        chapterPageDistance(rows, 60, 350, 1) shouldBe 200f
    }

    @Test
    fun `empty viewports do not move and oversized rows remain navigable`() {
        chapterPageDistance(emptyList(), 0, 1000, 1) shouldBe 0f
        val rows = listOf(ChapterNavigationRow(0, 1600, MangaScreenItem.CHAPTER))
        chapterPageDistance(rows, 0, 1000, 1) shouldBe 700f
        chapterPageDistance(rows, 1000, 1000, 1) shouldBe 0f
    }
}
