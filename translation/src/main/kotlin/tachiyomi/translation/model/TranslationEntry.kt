package tachiyomi.translation.model

import kotlinx.serialization.Serializable

/**
 * Entry lifecycle. `DRAFT` (finger still down) never reaches storage; it only exists in the overlay.
 *
 * OCR -> MT -> READY <=> EXPANDED
 *                    -> EDITING -> MT (re-translate)
 *           MT -> FAILED -> MT (retry)
 */
@Serializable
enum class EntryState {
    OCR,
    MT,
    READY,
    EXPANDED,
    EDITING,
    FAILED,
}

@Serializable
data class TranslationEntry(
    val id: Long,
    val seq: Int,
    val chapterId: Long,
    val pageIndex: Int,
    val side: PageSide = PageSide.NONE,
    val rect: NormalizedRect,
    val state: EntryState = EntryState.OCR,
    val sourceText: String? = null,
    val translation: String? = null,
    val error: String? = null,
    /**
     * Set when a box crossed the spread gutter and was split into two halves that must be
     * translated as one sentence. Both halves share this id.
     */
    val groupId: Long? = null,
    /**
     * The box's continuation on the neighbouring strip page: a bubble straddling a webtoon page
     * boundary keeps one entry (one text), but its outline must be drawn on BOTH pages.
     */
    val extraPageIndex: Int? = null,
    val extraRect: NormalizedRect? = null,
) {
    val pageKey: PageKey get() = PageKey(chapterId, pageIndex, side)

    val isBusy: Boolean get() = state == EntryState.OCR || state == EntryState.MT

    val isTranslated: Boolean get() = !translation.isNullOrBlank()
}
