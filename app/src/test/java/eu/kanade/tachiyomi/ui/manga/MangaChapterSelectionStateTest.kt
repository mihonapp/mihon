package eu.kanade.tachiyomi.ui.manga

import eu.kanade.tachiyomi.data.download.model.Download
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class MangaChapterSelectionStateTest {
    private val visible = listOf(item(1), item(3), item(7))
    private val hidden = listOf(item(2, true), item(4, true), item(6, true))

    @Test
    fun `selection all inverse and ranges are limited to the displayed surface`() {
        val normal = MangaChapterSelection()
        normal.selectAll(visible, true)
        assertEquals(setOf(1L, 3L, 7L), normal.ids.value)
        normal.invert(visible)
        assertTrue(normal.ids.value.isEmpty())
        normal.toggle(visible, hidden.first(), true, true)
        assertTrue(normal.ids.value.isEmpty())
        normal.toggle(visible, visible.first(), true, true)
        normal.toggle(visible, visible.last(), true, true)
        assertEquals(setOf(1L, 3L, 7L), normal.ids.value)
    }

    @Test
    fun `hide then reveal drops invisible selection and recalculates range anchors`() {
        val selection = MangaChapterSelection()
        val all = (visible + hidden).sortedBy { it.id }
        selection.selectAll(all, true)
        selection.retain(visible)
        assertEquals(setOf(1L, 3L, 7L), selection.ids.value)
        selection.retain(all)
        assertEquals(setOf(1L, 3L, 7L), selection.ids.value)
        selection.selectAll(visible, false)
        selection.toggle(visible, visible.last(), true, true)
        selection.toggle(visible, visible.first(), true, true)
        assertEquals(setOf(1L, 3L, 7L), selection.ids.value)
    }

    @Test
    fun `recovery selection unhide and reopen are independent of normal selection`() {
        val normal = MangaChapterSelection()
        val recovery = MangaChapterSelection()
        normal.selectAll(visible, true)
        recovery.selectAll(hidden, true)
        assertEquals(setOf(2L, 4L, 6L), recovery.ids.value)
        recovery.retain(hidden.drop(1))
        assertEquals(setOf(4L, 6L), recovery.ids.value)
        recovery.invert(hidden.drop(1))
        assertTrue(recovery.ids.value.isEmpty())
        recovery.toggle(hidden, hidden.first(), true, true)
        recovery.toggle(hidden, hidden.last(), true, true)
        assertEquals(setOf(2L, 4L, 6L), recovery.ids.value)
        recovery.retain(emptyList())
        assertTrue(recovery.ids.value.isEmpty())
        assertTrue(MangaChapterSelection().ids.value.isEmpty())
        assertEquals(setOf(1L, 3L, 7L), normal.ids.value)
    }

    private fun item(id: Long, hidden: Boolean = false) = ChapterList.Item(
        Chapter.create().copy(id = id, hidden = hidden),
        Download.State.NOT_DOWNLOADED,
    )
}
