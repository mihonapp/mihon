package eu.kanade.tachiyomi.ui.reader.cast

import android.graphics.Color
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

/**
 * How the content is rotated on the cast target. The target (a TV) is usually physically
 * landscape; [PORTRAIT] and [REVERSE_PORTRAIT] are for screens mounted vertically.
 */
enum class CastOrientation(val degrees: Int, val titleRes: StringResource) {
    LANDSCAPE(0, MR.strings.cast_orientation_landscape),
    PORTRAIT(90, MR.strings.cast_orientation_portrait),
    REVERSE_LANDSCAPE(180, MR.strings.cast_orientation_reverse_landscape),
    REVERSE_PORTRAIT(270, MR.strings.cast_orientation_reverse_portrait),
    ;

    val swapsAxes: Boolean
        get() = degrees == 90 || degrees == 270
}

/**
 * Base size of a page on the cast target (paged layouts). [CastState.zoomPercent] is applied on top.
 */
enum class CastScaleMode(val key: String, val titleRes: StringResource) {
    FIT_SCREEN("fit_screen", MR.strings.scale_type_fit_screen),
    FIT_WIDTH("fit_width", MR.strings.scale_type_fit_width),
    FIT_HEIGHT("fit_height", MR.strings.scale_type_fit_height),
    ORIGINAL("original", MR.strings.scale_type_original_size),
}

enum class CastBackground(val color: Int, val titleRes: StringResource) {
    BLACK(Color.BLACK, MR.strings.black_background),
    GRAY(0xFF202125.toInt(), MR.strings.gray_background),
    WHITE(Color.WHITE, MR.strings.white_background),
    ;

    val cssColor: String
        get() = String.format("#%06X", 0xFFFFFF and color)
}

enum class CastImageQuality(val titleRes: StringResource) {
    OPTIMIZED(MR.strings.cast_quality_optimized),
    ORIGINAL(MR.strings.cast_quality_original),
}

enum class CastLayoutMode(val key: String) {
    PAGED("paged"),
    CONTINUOUS("continuous"),
}

enum class CastTargetType {
    DISPLAY,
    WEB,
}

/**
 * A page of the chapter currently being cast. [width]/[height] are the image dimensions in pixels
 * once known (0 until the image has been probed) so receivers can lay out strips before decoding.
 */
data class CastPageInfo(
    val chapterId: Long,
    val index: Int,
    val width: Int = 0,
    val height: Int = 0,
) {
    val key: String
        get() = "$chapterId/$index"

    val hasDimensions: Boolean
        get() = width > 0 && height > 0
}

sealed interface CastPosition {
    /**
     * [index] is the page index inside [CastState.chapterId]. [offset] is the fraction (0..1) of the
     * page height that has scrolled past the top of the viewport; only meaningful for
     * [CastLayoutMode.CONTINUOUS].
     */
    data class Page(val index: Int, val offset: Float = 0f) : CastPosition

    /** A chapter transition card (between chapters). [forward] is true when moving to the next chapter. */
    data class Transition(val title: String, val subtitle: String? = null, val forward: Boolean = true) : CastPosition
}

/**
 * Immutable snapshot of everything a cast target needs to render. Every change bumps [version]
 * so receivers can long-poll / diff cheaply.
 */
data class CastState(
    val version: Long = 0,
    val active: Boolean = false,
    val targetType: CastTargetType? = null,
    val targetName: String? = null,
    val mangaId: Long = -1L,
    val mangaTitle: String = "",
    val chapterName: String = "",
    val chapterId: Long = -1L,
    val layoutMode: CastLayoutMode = CastLayoutMode.PAGED,
    val rtl: Boolean = false,
    /** Paged layouts only: pages are turned by vertical swipes (vertical pager). */
    val verticalPaging: Boolean = false,
    val pages: List<CastPageInfo> = emptyList(),
    val position: CastPosition = CastPosition.Page(0),
    val orientation: CastOrientation = CastOrientation.LANDSCAPE,
    val scaleMode: CastScaleMode = CastScaleMode.FIT_SCREEN,
    val zoomPercent: Int = 100,
    val stripWidthPercent: Int = 50,
    val background: CastBackground = CastBackground.BLACK,
    /** Pan of an over-sized page as a fraction of the overflow, -1..1 (paged layouts). */
    val panX: Float = 0f,
    val panY: Float = 0f,
    val autoScrollRunning: Boolean = false,
) {
    val currentPageIndex: Int
        get() = (position as? CastPosition.Page)?.index ?: -1

    fun pageInfo(index: Int): CastPageInfo? = pages.getOrNull(index)
}

/** Commands from a cast target / remote / notification that the reader must execute. */
sealed interface CastEvent {
    /** A target was just connected; the reader should re-report its exact position. */
    data object Started : CastEvent
    data object NextPage : CastEvent
    data object PreviousPage : CastEvent

    /** Scroll continuous viewers by [deltaPx] device pixels (positive = down). */
    data class ScrollBy(val deltaPx: Float) : CastEvent
    data object NextChapter : CastEvent
    data object PreviousChapter : CastEvent
    data object ToggleAutoScroll : CastEvent

    /** Casting ended (target lost, user stopped it, ...). */
    data class Stopped(val messageRes: StringResource?) : CastEvent
}

/** An external display that can be used as a cast target. */
data class CastDisplayInfo(
    val displayId: Int,
    val name: String,
    val width: Int,
    val height: Int,
)

/** Runtime information about the embedded web receiver. */
data class CastWebInfo(
    val url: String,
    val port: Int,
    val clientCount: Int,
)
