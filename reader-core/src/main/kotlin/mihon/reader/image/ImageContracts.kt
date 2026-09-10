package mihon.reader.image

import mihon.reader.memory.MemoryLease
import mihon.reader.model.FrameId
import mihon.reader.model.PageId
import mihon.reader.source.BoundedPageInput
import java.awt.image.BufferedImage
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
    val frameDurationsMillis: List<Long> = List(frameCount) { 0L },
    val supportsRegionDecode: Boolean = true,
    val format: ReaderImageFormat = ReaderImageFormat.UNKNOWN,
    val hasAlpha: Boolean = false,
    val orientationApplied: Boolean = false,
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        require(frameCount > 0) { "frameCount must be positive" }
        require(frameDurationsMillis.size == frameCount) { "frameDurationsMillis must cover every frame" }
        require(frameDurationsMillis.all { it >= 0L }) { "frame durations must not be negative" }
    }

    val isAnimated: Boolean get() = frameCount > 1
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
    val image: BufferedImage
    val outputReservation: MemoryLease

    fun adoptAsCacheResident()

    override fun close()
}

interface PageDecoder {
    suspend fun probe(input: BoundedPageInput): ImageMetadata

    suspend fun decodeFull(input: BoundedPageInput, metadata: ImageMetadata, frameId: FrameId): DecodedTile

    suspend fun decodeRegion(input: BoundedPageInput, metadata: ImageMetadata, request: TileRequest): DecodedTile
}
