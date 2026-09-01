package mihon.reader.model

enum class ReadingMode(
    val isRightToLeft: Boolean,
    val isDualPage: Boolean,
) {
    SINGLE_LTR(isRightToLeft = false, isDualPage = false),
    SINGLE_RTL(isRightToLeft = true, isDualPage = false),
    DUAL_LTR(isRightToLeft = false, isDualPage = true),
    DUAL_RTL(isRightToLeft = true, isDualPage = true),
    VERTICAL(isRightToLeft = false, isDualPage = false),
    WEBTOON(isRightToLeft = false, isDualPage = false),
}

enum class ScaleMode {
    ORIGINAL,
    FIT_WIDTH,
    FIT_HEIGHT,
}

enum class ReaderErrorCode {
    SOURCE_UNAVAILABLE,
    PAGE_NOT_FOUND,
    PAGE_DECODE_FAILED,
    MEMORY_LIMIT_REACHED,
    INVALID_PROGRESS,
}

data class PageId(
    val chapterId: String,
    val entryName: String,
) {
    init {
        require(chapterId.isNotBlank()) { "chapterId must not be blank" }
        require(entryName.isNotBlank()) { "entryName must not be blank" }
    }
}

data class FrameId(
    val pageId: PageId,
    val frameIndex: Int,
) {
    init {
        require(frameIndex >= 0) { "frameIndex must not be negative" }
    }
}

data class PageDescriptor(
    val id: PageId,
    val width: Int,
    val height: Int,
    val frameCount: Int = 1,
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        require(frameCount > 0) { "frameCount must be positive" }
    }
}

data class ReaderViewport(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
    }
}

data class ReaderPosition(
    val pageIndex: Int,
    val offsetPixels: Int = 0,
) {
    init {
        require(pageIndex >= 0) { "pageIndex must not be negative" }
        require(offsetPixels >= 0) { "offsetPixels must not be negative" }
    }
}

data class ReaderPan(
    val x: Float,
    val y: Float,
) {
    init {
        require(x.isFinite()) { "x must be finite" }
        require(y.isFinite()) { "y must be finite" }
    }
}

data class ReaderLayoutPolicy(
    val isRightToLeft: Boolean,
    val isDualPage: Boolean,
    val isContinuous: Boolean,
    val continuousGapPixels: Int,
    val forcedScaleMode: ScaleMode?,
) {
    init {
        require(continuousGapPixels >= 0) { "continuousGapPixels must not be negative" }
    }
}

object ReaderLayout {
    const val DEFAULT_CONTINUOUS_GAP_PIXELS = 16
    const val MIN_ZOOM = 0.25f
    const val MAX_ZOOM = 8f

    fun policy(mode: ReadingMode): ReaderLayoutPolicy = when (mode) {
        ReadingMode.SINGLE_LTR -> ReaderLayoutPolicy(false, false, false, 0, null)
        ReadingMode.SINGLE_RTL -> ReaderLayoutPolicy(true, false, false, 0, null)
        ReadingMode.DUAL_LTR -> ReaderLayoutPolicy(false, true, false, 0, null)
        ReadingMode.DUAL_RTL -> ReaderLayoutPolicy(true, true, false, 0, null)
        ReadingMode.VERTICAL -> ReaderLayoutPolicy(false, false, true, DEFAULT_CONTINUOUS_GAP_PIXELS, null)
        ReadingMode.WEBTOON -> ReaderLayoutPolicy(false, false, true, 0, ScaleMode.FIT_WIDTH)
    }

    fun clampZoom(zoom: Float): Float {
        require(zoom.isFinite()) { "zoom must be finite" }
        return zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    fun clampPostLayoutPan(
        viewport: ReaderViewport,
        contentWidth: Int,
        contentHeight: Int,
        requested: ReaderPan,
    ): ReaderPan {
        require(contentWidth > 0) { "contentWidth must be positive" }
        require(contentHeight > 0) { "contentHeight must be positive" }
        val maxX = ((contentWidth - viewport.width).coerceAtLeast(0)) / 2f
        val maxY = ((contentHeight - viewport.height).coerceAtLeast(0)) / 2f
        return ReaderPan(
            x = requested.x.coerceIn(-maxX, maxX),
            y = requested.y.coerceIn(-maxY, maxY),
        )
    }
}
