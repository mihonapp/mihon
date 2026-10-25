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

    @Test
    fun `visibility preserves genuine gaps and initial gaps in both directions`() {
        for (descending in listOf(false, true)) {
            for (includeInitial in listOf(false, true)) {
                val chapters = (if (includeInitial) (1..3).map { item(it) } else emptyList()) +
                    listOf(item(4), item(5, hidden = true), item(7))
                val shown = state(chapters, descending)
                val hidden = shown.copy(hideHiddenChapters = true)
                val expectedGaps = if (includeInitial) listOf(1) else listOf(1, 3)

                assertEquals(if (includeInitial) 1 else 4, hidden.missingChapterCount)
                assertEquals(shown.missingChapterCount, hidden.missingChapterCount)
                assertEquals(
                    expectedGaps,
                    hidden.chapterListItems.filterIsInstance<ChapterList.MissingCount>()
                        .map { it.count }.sorted(),
                )
                assertEquals(shown.processedChapters.filterNot { it.chapter.hidden }, hidden.processedChapters)
                assertTrue(hidden.hasHiddenChapters)
                assertEquals(descending, hidden.manga.sortDescending())
            }
        }
    }

    @Test
    fun `hidden outliers and all hidden rows do not create gaps when omitted`() {
        for (descending in listOf(false, true)) {
            val outlier = state((1..7).map { item(it) } + item(99990000, hidden = true), descending)
                .copy(hideHiddenChapters = true)
            assertEquals(0, outlier.missingChapterCount)
            assertTrue(outlier.chapterListItems.none { it is ChapterList.MissingCount })
            assertEquals(7, outlier.processedChapters.size)

            val allHidden = state(listOf(item(5, hidden = true, selected = true)), descending)
                .copy(hideHiddenChapters = true)
            assertTrue(allHidden.processedChapters.isEmpty())
            assertTrue(allHidden.chapterListItems.isEmpty())
            assertTrue(allHidden.selectedChapters.isEmpty())
            assertFalse(allHidden.isAnySelected)
            assertFalse(allHidden.hasUnreadChapters)
            assertEquals(0, allHidden.missingChapterCount)
            assertTrue(allHidden.hasHiddenChapters)
        }
    }

    @Test
    fun `raw hidden numbers remain known even when ordinary filters exclude their rows`() {
        val base = state((1..4).map { item(it) } + item(7)).let {
            it.copy(hiddenChapters = listOf(item(5, hidden = true)))
        }
        for (hide in listOf(false, true)) {
            val state = base.copy(hideHiddenChapters = hide)
            assertEquals(1, state.missingChapterCount)
            assertEquals(
                listOf(1),
                state.chapterListItems.filterIsInstance<ChapterList.MissingCount>().map { it.count },
            )
        }
    }

    @Test
    fun `hidden recovery list ignores ordinary filters and remains available with no normal rows`() {
        val read = item(5, hidden = true).let { it.copy(chapter = it.chapter.copy(read = true)) }
        val bookmarked = item(6, hidden = true).let { it.copy(chapter = it.chapter.copy(bookmark = true)) }
        val downloaded = item(7, hidden = true).copy(downloadState = Download.State.DOWNLOADED)
        val rawHidden = listOf(read, bookmarked, downloaded)
        val base = state(emptyList()).copy(hiddenChapters = rawHidden, hideHiddenChapters = true)
        val filters = listOf(Manga.CHAPTER_SHOW_UNREAD, Manga.CHAPTER_SHOW_NOT_BOOKMARKED)
        for (filter in filters) {
            val state = base.copy(manga = base.manga.copy(chapterFlags = base.manga.chapterFlags or filter))
            assertTrue(state.processedChapters.isEmpty())
            assertEquals(rawHidden, state.hiddenChapters)
            assertTrue(state.hasHiddenChapters)
        }
        every { any<Manga>().downloadedFilter } returns TriState.ENABLED_NOT
        assertEquals(rawHidden, base.hiddenChapters)
        assertTrue(base.processedChapters.isEmpty())
    }

    @Test
    fun `normal read bookmark and download filters still compose with visibility`() {
        val visible = item(1).let { it.copy(chapter = it.chapter.copy(bookmark = true)) }
        val hidden = item(2, hidden = true).let { it.copy(chapter = it.chapter.copy(bookmark = true)) }
        val read = item(3).let { it.copy(chapter = it.chapter.copy(read = true, bookmark = true)) }
        val plain = item(4)
        val downloaded = item(5).copy(downloadState = Download.State.DOWNLOADED)
        val base = state(listOf(visible, hidden, read, plain, downloaded))
        for ((flag, expected) in listOf(
            Manga.CHAPTER_SHOW_UNREAD to setOf(1L, 2L, 4L, 5L),
            Manga.CHAPTER_SHOW_BOOKMARKED to setOf(1L, 2L, 3L),
        )) {
            val filtered = base.copy(manga = base.manga.copy(chapterFlags = base.manga.chapterFlags or flag))
            assertEquals(expected, filtered.processedChapters.map { it.id }.toSet())
            assertEquals(
                expected - 2L,
                filtered.copy(hideHiddenChapters = true).processedChapters.map { it.id }.toSet(),
            )
        }
        every { any<Manga>().downloadedFilter } returns TriState.ENABLED_IS
        for (hide in listOf(false, true)) {
            assertEquals(listOf(5L), base.copy(hideHiddenChapters = hide).processedChapters.map { it.id })
        }
    }

    private fun item(number: Int, hidden: Boolean = false, selected: Boolean = false) = ChapterList.Item(
        chapter = Chapter.create().copy(id = number.toLong(), chapterNumber = number.toDouble(), hidden = hidden),
        downloadState = Download.State.NOT_DOWNLOADED,
        selected = selected,
    )

    private fun state(chapters: List<ChapterList.Item>, descending: Boolean = false) = MangaViewModel.State.Success(
        manga = Manga.create().copy(
            source = 1,
            chapterFlags = Manga.CHAPTER_SORTING_NUMBER or
                if (descending) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC,
        ),
        source = mockk<Source>(),
        isFromSource = false,
        chapters = chapters,
        availableScanlators = emptySet(),
        excludedScanlators = emptySet(),
    )
}
