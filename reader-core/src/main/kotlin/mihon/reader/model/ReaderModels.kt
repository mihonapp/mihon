package mihon.reader.model

enum class ReadingMode(
    val isRightToLeft: Boolean,
    val isDualPage: Boolean,
) {
    LEFT_TO_RIGHT(isRightToLeft = false, isDualPage = false),
    RIGHT_TO_LEFT(isRightToLeft = true, isDualPage = false),
    VERTICAL(isRightToLeft = false, isDualPage = false),
    WEBTOON(isRightToLeft = false, isDualPage = false),
    DOUBLE_PAGE_LTR(isRightToLeft = false, isDualPage = true),
    DOUBLE_PAGE_RTL(isRightToLeft = true, isDualPage = true),
}

enum class ScaleMode {
    FIT_PAGE,
    FIT_WIDTH,
    FIT_HEIGHT,
    ORIGINAL_SIZE,
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
