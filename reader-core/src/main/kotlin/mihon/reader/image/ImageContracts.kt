package mihon.reader.image

import mihon.reader.memory.MemoryLease
import mihon.reader.model.FrameId
import mihon.reader.model.PageId
import mihon.reader.source.BoundedPageInput
import java.io.Closeable

data class IntRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(left >= 0) { "left must not be negative" }
        require(top >= 0) { "top must not be negative" }
        require(right > left) { "right must be greater than left" }
        require(bottom > top) { "bottom must be greater than top" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class ImageMetadata(
    val width: Int,
    val height: Int,
    val frameCount: Int = 1,
    val durationMillis: Long = 0,
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        require(frameCount > 0) { "frameCount must be positive" }
        require(durationMillis >= 0) { "durationMillis must not be negative" }
    }
}

data class TileKey(
    val pageId: PageId,
    val frameId: FrameId?,
    val bounds: IntRect,
    val sampleSize: Int = 1,
) {
    init {
        require(frameId == null || frameId.pageId == pageId) { "frameId must belong to pageId" }
        require(sampleSize > 0) { "sampleSize must be positive" }
    }
}

data class TileRequest(
    val key: TileKey,
    val targetWidth: Int,
    val targetHeight: Int,
) {
    init {
        require(targetWidth > 0) { "targetWidth must be positive" }
        require(targetHeight > 0) { "targetHeight must be positive" }
    }
}

interface DecodedTile : Closeable {
    val key: TileKey
    val outputReservation: MemoryLease

    fun adoptAsCacheResident()

    override fun close()
}

fun interface PageDecoder {
    suspend fun decode(input: BoundedPageInput, request: TileRequest): DecodedTile
}
