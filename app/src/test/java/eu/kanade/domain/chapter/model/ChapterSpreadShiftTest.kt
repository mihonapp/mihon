package eu.kanade.domain.chapter.model

import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.ui.reader.setting.SpreadShift
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.toChapterUpdate

/**
 * The remembered spread pairing shift is threaded by hand through the chapter model layers (domain
 * <-> the legacy DB model the reader consumes, and the update projection the reader persists with).
 * The compiler pins the mapper arities; these pin that each of the three tri-state values survives every
 * hop unchanged, so a reader's manual shift is restored on the next visit.
 */
class ChapterSpreadShiftTest {

    private fun chapter(spreadShift: Long) =
        Chapter.create().copy(id = 1L, mangaId = 1L, spreadShift = spreadShift)

    // DEFAULT (0, no override), SHIFTED, UNSHIFTED: the persisted values the column can hold.
    private val allStates = listOf(
        SpreadShift.DEFAULT.flagValue.toLong(),
        SpreadShift.SHIFTED.flagValue.toLong(),
        SpreadShift.UNSHIFTED.flagValue.toLong(),
    )

    @Test
    fun `spreadShift survives the domain to legacy-DB round trip`() {
        // The reader reads the shift back off the legacy model (ReaderChapter.chapter), so the value
        // must ride domain -> toDbChapter -> toDomainChapter unchanged for all three states.
        for (shift in allStates) {
            val roundTripped = chapter(shift).toDbChapter().toDomainChapter()
            assertEquals(shift, roundTripped?.spreadShift)
        }
    }

    @Test
    fun `toChapterUpdate carries spreadShift so a full-row update never drops it`() {
        // Sync rebuilds an update from the DB chapter; the value must pass through the projection
        // verbatim, including DEFAULT (0), which the coalescing update writes rather than skips.
        for (shift in allStates) {
            assertEquals(shift, chapter(shift).toChapterUpdate().spreadShift)
        }
    }
}
