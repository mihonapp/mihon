package tachiyomi.translation.model

import kotlinx.serialization.Serializable

/**
 * Which half of a split wide page a box belongs to. [NONE] for ordinary pages.
 */
@Serializable
enum class PageSide {
    NONE,
    LEFT,
    RIGHT,
}

/**
 * Identity of a page inside a chapter.
 *
 * [pageIndex] is the loader's page index. [side] only matters when a wide page was split into two
 * halves by `processPagedImage`, in which case both halves share [pageIndex] but differ by side.
 */
data class PageKey(
    val chapterId: Long,
    val pageIndex: Int,
    val side: PageSide = PageSide.NONE,
)

/**
 * Display and eviction unit: one spread, keyed by the anchor page index.
 *
 * Spread composition is derived at render time and can change with orientation or reading mode,
 * so this key is only used for grouping and for the 5-spread cap, never as storage.
 */
data class SpreadKey(
    val chapterId: Long,
    val anchorIndex: Int,
)
