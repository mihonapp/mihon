package eu.kanade.presentation.manga

import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.manga.ChapterList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class MangaChapterSelectionTest {

    @Test
    fun `hidden row cannot open reader outside selection mode`() {
        var opened = false
        var toggled = false
        onChapterItemClick(
            chapterItem = item(hidden = true),
            isAnyChapterSelected = false,
            onToggleSelection = { toggled = true },
            onChapterClicked = { opened = true },
        )
        assertFalse(opened)
        assertFalse(toggled)
    }

    @Test
    fun `hidden row can be selected and deselected in selection mode`() {
        for (selected in listOf(false, true)) {
            var selection: Boolean? = null
            var opened = false
            onChapterItemClick(
                chapterItem = item(hidden = true, selected = selected),
                isAnyChapterSelected = true,
                onToggleSelection = { selection = it },
                onChapterClicked = { opened = true },
            )
            assertEquals(!selected, selection)
            assertFalse(opened)
        }
    }

    @Test
    fun `visible row opens normally outside selection mode`() {
        val item = item(hidden = false)
        var opened: Chapter? = null
        onChapterItemClick(
            chapterItem = item,
            isAnyChapterSelected = false,
            onToggleSelection = { error("Unexpected selection") },
            onChapterClicked = { opened = it },
        )
        assertEquals(item.chapter, opened)
    }

    private fun item(hidden: Boolean, selected: Boolean = false) = ChapterList.Item(
        chapter = Chapter.create().copy(hidden = hidden),
        downloadState = Download.State.NOT_DOWNLOADED,
        selected = selected,
    )
}
