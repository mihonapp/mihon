package eu.kanade.tachiyomi.ui.manga

import eu.kanade.domain.manga.model.downloadedFilter
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.Source
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

class MangaHiddenChaptersTest {

    @BeforeEach
    fun setUp() {
        // The Android extension also reads the app-wide downloaded-only preference.
        mockkStatic("eu.kanade.domain.manga.model.MangaKt")
        every { any<Manga>().downloadedFilter } returns TriState.DISABLED
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("eu.kanade.domain.manga.model.MangaKt")
    }

    @Test
    fun `hidden outlier does not inflate header or gaps in either sort direction`() {
        val chapters = (1..203).map { item(it) } + item(99990000, hidden = true, selected = true)
        for (descending in listOf(false, true)) {
            val state = state(chapters, descending)

            assertEquals(0, state.missingChapterCount)
            assertTrue(state.chapterListItems.none { it is ChapterList.MissingCount })
            assertEquals(204, state.chapterListItems.filterIsInstance<ChapterList.Item>().size)
            assertEquals(listOf(99990000L), state.selectedChapters.map { it.id })
        }
    }

    @Test
    fun `gap calculation bridges hidden rows in either sort direction`() {
        val chapters = listOf(item(1), item(2, hidden = true), item(4))
        for (descending in listOf(false, true)) {
            val state = state(chapters, descending)

            assertEquals(1, state.missingChapterCount)
            assertEquals(
                listOf(1),
                state.chapterListItems.filterIsInstance<ChapterList.MissingCount>().map { it.count },
            )
            assertEquals(state.processedChapters, state.chapterListItems.filterIsInstance<ChapterList.Item>())
        }
    }

    @Test
    fun `hidden lowest chapter does not determine initial gap`() {
        for (descending in listOf(false, true)) {
            val state = state(listOf(item(1, hidden = true), item(3)), descending)

            assertEquals(1, state.missingChapterCount)
            assertEquals(
                listOf(1),
                state.chapterListItems.filterIsInstance<ChapterList.MissingCount>().map { it.count },
            )
        }
    }

    @Test
    fun `all hidden chapters remain selectable with no reading action or missing count`() {
        val chapters = listOf(item(1, hidden = true, selected = true), item(99990000, hidden = true))
        val state = state(chapters)

        assertEquals(2, state.processedChapters.size)
        assertEquals(1, state.selectedChapters.size)
        assertFalse(state.hasUnreadChapters)
        assertFalse(state.hasReadChapters)
        assertEquals(0, state.missingChapterCount)
        assertEquals(state.processedChapters, state.chapterListItems)
    }

    @Test
    fun `hidden read chapters do not change start label to resume`() {
        val hiddenRead = item(1, hidden = true).let { it.copy(chapter = it.chapter.copy(read = true)) }
        val state = state(listOf(hiddenRead, item(2)))

        assertTrue(state.hasUnreadChapters)
        assertFalse(state.hasReadChapters)
    }

    @Test
    fun `hiding missing chapter indicators still retains hidden rows`() {
        val state = state(listOf(item(1), item(99990000, hidden = true))).copy(hideMissingChapters = true)

        assertEquals(0, state.missingChapterCount)
        assertEquals(state.processedChapters, state.chapterListItems)
    }

    private fun item(number: Int, hidden: Boolean = false, selected: Boolean = false) = ChapterList.Item(
        chapter = Chapter.create().copy(id = number.toLong(), chapterNumber = number.toDouble(), hidden = hidden),
        downloadState = Download.State.NOT_DOWNLOADED,
        selected = selected,
    )

    private fun state(chapters: List<ChapterList.Item>, descending: Boolean = false) = MangaViewModel.State.Success(
        manga = Manga.create().copy(
            source = 1,
            chapterFlags = Manga.CHAPTER_SORTING_NUMBER or if (descending) Manga.CHAPTER_SORT_DESC else 0L,
        ),
        source = mockk<Source>(),
        isFromSource = false,
        chapters = chapters,
        availableScanlators = emptySet(),
        excludedScanlators = emptySet(),
    )
}
