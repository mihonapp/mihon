package eu.kanade.tachiyomi.ui.reader.viewer.book

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlin.math.min

/**
 * What the book shows at once: one item across the whole width, or two pages side by side.
 */
internal sealed interface Spread {

    /** The items shown, in reading order. */
    val items: List<Any>

    /**
     * An item across the whole width: a chapter transition, a page printed as a spread, or any page when the book
     * shows one at a time.
     */
    data class Full(val item: Any) : Spread {
        override val items: List<Any> = listOf(item)
    }

    /**
     * Two facing pages, [first] read before [second]. Either may be missing, like the first page of a chapter shown
     * alone, which then sits where a book's first page does.
     */
    data class Facing(val first: ReaderPage?, val second: ReaderPage?) : Spread {
        override val items: List<Any> = listOfNotNull(first, second)

        fun left(isRightToLeft: Boolean): ReaderPage? = if (isRightToLeft) second else first

        fun right(isRightToLeft: Boolean): ReaderPage? = if (isRightToLeft) first else second
    }
}

/**
 * Groups [items], in reading order, into spreads. With [double] off every item is [Spread.Full]. Otherwise pages of
 * a chapter pair up, except [isWide] ones, which are printed spreads already, and the first page of a chapter when
 * [firstPageAlone], so that pairs fall as they do in print.
 */
internal fun buildSpreads(
    items: List<Any>,
    double: Boolean,
    firstPageAlone: Boolean,
    isWide: (ReaderPage) -> Boolean,
): List<Spread> {
    if (!double) return items.map { Spread.Full(it) }

    val spreads = mutableListOf<Spread>()
    var pending: ReaderPage? = null
    fun flush() {
        pending?.let { spreads += Spread.Facing(it, null) }
        pending = null
    }

    for (item in items) {
        if (item !is ReaderPage || isWide(item)) {
            flush()
            spreads += Spread.Full(item)
            continue
        }
        val startsChapter = item.index == 0
        if (startsChapter) flush()
        if (startsChapter && firstPageAlone) {
            spreads += Spread.Facing(null, item)
            continue
        }
        val waiting = pending
        if (waiting != null && waiting.chapter === item.chapter) {
            spreads += Spread.Facing(waiting, item)
            pending = null
        } else {
            flush()
            pending = item
        }
    }
    flush()
    return spreads
}

/**
 * Where the pages of a spread go in a viewer of [area], bound at [spine], the gap between the facing pages.
 */
internal class SpreadPlacer(
    private val area: Size,
    private val spine: ClosedFloatingPointRange<Float>,
    private val isRightToLeft: Boolean,
    private val sizeOf: (ReaderPage) -> IntSize?,
) {

    /** Width over height of a page whose size isn't known yet. */
    private val defaultAspect = 0.7f

    /**
     * The rect of each item of [spread], in the viewer.
     */
    fun place(spread: Spread): Map<Any, Rect> = when (spread) {
        is Spread.Full -> mapOf(spread.item to placeFull(spread.item))
        is Spread.Facing -> buildMap {
            spread.left(isRightToLeft)?.let {
                put(it, fit(it, Rect(0f, 0f, spine.start, area.height), alignEnd = true))
            }
            spread.right(isRightToLeft)?.let {
                put(it, fit(it, Rect(spine.endInclusive, 0f, area.width, area.height), alignEnd = false))
            }
        }
    }

    private fun placeFull(item: Any): Rect {
        val whole = Rect(Offset.Zero, area)
        if (item !is ReaderPage) return whole
        val aspect = aspectOf(item)
        val width = min(area.width, area.height * aspect)
        val height = width / aspect
        val left = (area.width - width) / 2f
        val top = (area.height - height) / 2f
        return Rect(left, top, left + width, top + height)
    }

    /**
     * [page] as large as fits in [half], against the spine: at its end when [alignEnd].
     */
    private fun fit(page: ReaderPage, half: Rect, alignEnd: Boolean): Rect {
        val aspect = aspectOf(page)
        val width = min(half.width, half.height * aspect)
        val height = width / aspect
        val top = (half.height - height) / 2f
        val left = if (alignEnd) half.right - width else half.left
        return Rect(left, top, left + width, top + height)
    }

    private fun aspectOf(page: ReaderPage): Float {
        val size = sizeOf(page) ?: return defaultAspect
        if (size.width <= 0 || size.height <= 0) return defaultAspect
        return size.width.toFloat() / size.height
    }
}
