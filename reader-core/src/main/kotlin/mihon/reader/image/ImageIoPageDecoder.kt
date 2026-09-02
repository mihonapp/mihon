package mihon.reader.image

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import mihon.reader.memory.BoundedReaderMemoryBudget
import mihon.reader.memory.MemoryKind
import mihon.reader.memory.MemoryLease
import mihon.reader.model.FrameId
import mihon.reader.source.BoundedPageInput
import mihon.reader.source.ReaderFailure
import mihon.reader.source.ReaderLimits
import java.awt.AlphaComposite
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.IOException
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.metadata.IIOMetadataNode

class ImageIoPageDecoder(
    private val budget: BoundedReaderMemoryBudget,
) : PageDecoder {

    override suspend fun probe(input: BoundedPageInput): ImageMetadata = withContext(Dispatchers.IO) {
        input.use { pageInput ->
            withReader(pageInput) { reader ->
                val isGif = reader.formatName.equals("gif", ignoreCase = true)
                val (width, height) = if (isGif) gifCanvasSize(reader) else readFrameSize(reader)
                validateDimensions(width, height)
                val frameCount = if (isGif) {
                    guarded { reader.getNumImages(true) }.coerceAtLeast(1)
                } else {
                    1
                }
                val durations = if (isGif && frameCount > 1) {
                    (0 until frameCount).map { gifDelayMillis(reader, it) }
                } else {
                    List(frameCount) { 0L }
                }
                val supportsRegion = !isGif || gifRegionPeakBytes(width, height) <= budget.metrics.limitBytes
                ImageMetadata(width, height, frameCount, durations, supportsRegion)
            }
        }
    }

    override suspend fun decodeFull(
        input: BoundedPageInput,
        metadata: ImageMetadata,
        frameId: FrameId,
    ): DecodedTile = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        input.use { pageInput ->
            withReader(pageInput) { reader ->
                val isGif = reader.formatName.equals("gif", ignoreCase = true)
                val (width, height) = if (isGif) gifCanvasSize(reader) else readFrameSize(reader)
                if (width != metadata.width || height != metadata.height) throw ReaderFailure.CorruptImage()
                val outputBytes = checkedRasterBytes(width, height)
                if (outputBytes > ReaderLimits.FULL_DECODE_MAX_BYTES) {
                    throw ReaderFailure.LimitExceeded(
                        "full decode raster",
                        ReaderLimits.FULL_DECODE_MAX_BYTES,
                        outputBytes,
                    )
                }
                val key = TileKey(
                    pageId = frameId.pageId,
                    frameId = frameId.takeIf { metadata.isAnimated },
                    bounds = IntRect(0, 0, width, height),
                )
                if (isGif) {
                    decodeGifFrame(reader, width, height, frameId.frameIndex, region = null, key = key)
                } else {
                    require(frameId.frameIndex == 0) { "still images only expose frame zero" }
                    decodeStillFull(reader, width, height, key)
                }
            }
        }
    }

    private suspend fun decodeStillFull(
        reader: ImageReader,
        width: Int,
        height: Int,
        key: TileKey,
    ): DecodedTile {
        val rasterBytes = checkedRasterBytes(width, height)
        val (finalLease, scratchLease) = reservePeak(rasterBytes, rasterBytes)
        var decoded: BufferedImage? = null
        var normalized: BufferedImage? = null
        try {
            decoded = guarded { reader.read(0) } ?: throw ReaderFailure.CorruptImage()
            if (decoded.width > width || decoded.height > height) {
                throw ReaderFailure.CorruptImage()
            }
            normalized = normalizeToPremultipliedArgb(decoded)
            if (normalized !== decoded) {
                decoded.flush()
                decoded = null
            }
            currentCoroutineContext().ensureActive()
            scratchLease.close()
            val tile = BudgetedDecodedTile(key, normalized, finalLease)
            normalized = null
            decoded = null
            return tile
        } finally {
            if (normalized != null || decoded != null) {
                normalized?.flush()
                if (decoded !== normalized) decoded?.flush()
                finalLease.close()
                scratchLease.close()
            }
        }
    }

    private suspend fun decodeGifFrame(
        reader: ImageReader,
        canvasWidth: Int,
        canvasHeight: Int,
        frameIndex: Int,
        region: IntRect?,
        key: TileKey,
    ): DecodedTile {
        val frameCount = guarded { reader.getNumImages(true) }
        require(frameIndex in 0 until frameCount) { "frame index $frameIndex out of $frameCount frames" }
        val infos = (0..frameIndex).map { gifFrameInfo(reader, it) }
        val needsSnapshot = (0 until frameIndex).any { infos[it].disposal == DISPOSAL_RESTORE_TO_PREVIOUS }
        val canvasBytes = checkedRasterBytes(canvasWidth, canvasHeight)
        val outputBytes = region?.let { checkedRasterBytes(it.width, it.height) } ?: canvasBytes
        // Canvas + frame intermediate (+ restore-to-previous snapshot) before the output crop.
        val scratchBytes = Math.multiplyExact(canvasBytes, if (needsSnapshot) 3L else 2L)
        val (finalLease, scratchLease) = reservePeak(outputBytes, scratchBytes)
        var canvas: BufferedImage? = null
        var snapshot: BufferedImage? = null
        var frame: BufferedImage? = null
        var output: BufferedImage? = null
        var failed = true
        try {
            canvas = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB_PRE)
            val graphics = canvas.createGraphics()
            try {
                var pendingClear: Rectangle? = null
                var pendingRestore = false
                for (index in 0..frameIndex) {
                    if (pendingClear != null) {
                        graphics.composite = AlphaComposite.Clear
                        graphics.fillRect(pendingClear.x, pendingClear.y, pendingClear.width, pendingClear.height)
                        graphics.composite = AlphaComposite.SrcOver
                        pendingClear = null
                    }
                    if (pendingRestore) {
                        val restore = snapshot ?: throw ReaderFailure.CorruptImage()
                        graphics.composite = AlphaComposite.Src
                        graphics.drawImage(restore, 0, 0, null)
                        graphics.composite = AlphaComposite.SrcOver
                        pendingRestore = false
                    }
                    val info = infos[index]
                    if (index < frameIndex && info.disposal == DISPOSAL_RESTORE_TO_PREVIOUS) {
                        snapshot?.flush()
                        val copy = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB_PRE)
                        val copyGraphics = copy.createGraphics()
                        copyGraphics.composite = AlphaComposite.Src
                        copyGraphics.drawImage(canvas, 0, 0, null)
                        copyGraphics.dispose()
                        snapshot = copy
                    }
                    frame = guarded { reader.read(index) } ?: throw ReaderFailure.CorruptImage()
                    graphics.drawImage(frame, info.left, info.top, null)
                    frame.flush()
                    frame = null
                    when (info.disposal) {
                        DISPOSAL_RESTORE_TO_BACKGROUND ->
                            pendingClear = Rectangle(info.left, info.top, info.width, info.height)
                        DISPOSAL_RESTORE_TO_PREVIOUS -> pendingRestore = true
                    }
                }
            } finally {
                graphics.dispose()
            }
            snapshot?.flush()
            snapshot = null
            output = if (region != null) {
                val crop = BufferedImage(region.width, region.height, BufferedImage.TYPE_INT_ARGB_PRE)
                val cropGraphics = crop.createGraphics()
                try {
                    cropGraphics.drawImage(
                        canvas,
                        0,
                        0,
                        region.width,
                        region.height,
                        region.left,
                        region.top,
                        region.right,
                        region.bottom,
                        null,
                    )
                } finally {
                    cropGraphics.dispose()
                }
                canvas.flush()
                canvas = null
                crop
            } else {
                canvas.also { canvas = null }
            }
            currentCoroutineContext().ensureActive()
            scratchLease.close()
            val tile = BudgetedDecodedTile(key, output, finalLease)
            output = null
            failed = false
            return tile
        } finally {
            if (failed) {
                canvas?.flush()
                snapshot?.flush()
                frame?.flush()
                output?.flush()
                finalLease.close()
                scratchLease.close()
            }
        }
    }

    private fun gifFrameInfo(reader: ImageReader, frameIndex: Int): GifFrameInfo {
        val metadata = guarded { reader.getImageMetadata(frameIndex) }
        val root = runCatching { metadata.getAsTree("javax_imageio_gif_image_1.0") }
            .getOrNull() as? IIOMetadataNode
        val descriptor = root?.getElementsByTagName("ImageDescriptor")?.item(0) as? IIOMetadataNode
        val control = root?.getElementsByTagName("GraphicControlExtension")?.item(0) as? IIOMetadataNode
        return GifFrameInfo(
            left = descriptor?.getAttribute("imageLeftPosition")?.toIntOrNull() ?: 0,
            top = descriptor?.getAttribute("imageTopPosition")?.toIntOrNull() ?: 0,
            width = descriptor?.getAttribute("imageWidth")?.toIntOrNull()
                ?: guarded { reader.getWidth(frameIndex) },
            height = descriptor?.getAttribute("imageHeight")?.toIntOrNull()
                ?: guarded { reader.getHeight(frameIndex) },
            disposal = control?.getAttribute("disposalMethod") ?: "none",
        )
    }

    private data class GifFrameInfo(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val disposal: String,
    )

    private suspend fun reservePeak(finalBytes: Long, scratchBytes: Long): Pair<MemoryLease, MemoryLease> {
        val total = finalBytes + scratchBytes
        val limit = budget.metrics.limitBytes
        if (total > limit) throw ReaderFailure.LimitExceeded("decode peak", limit, total)
        val finalLease = budget.reserve(MemoryKind.DECODED_OUTPUT, finalBytes)
        val scratchLease = try {
            budget.reserve(MemoryKind.DECODED_OUTPUT, scratchBytes)
        } catch (error: Throwable) {
            finalLease.close()
            throw error
        }
        return finalLease to scratchLease
    }

    private fun normalizeToPremultipliedArgb(image: BufferedImage): BufferedImage {
        if (image.type == BufferedImage.TYPE_INT_ARGB_PRE) return image
        val converted = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB_PRE)
        val graphics = converted.createGraphics()
        try {
            graphics.drawImage(image, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        return converted
    }

    override suspend fun decodeRegion(
        input: BoundedPageInput,
        metadata: ImageMetadata,
        request: TileRequest,
    ): DecodedTile = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        input.use { pageInput ->
            withReader(pageInput) { reader ->
                val isGif = reader.formatName.equals("gif", ignoreCase = true)
                val (width, height) = if (isGif) gifCanvasSize(reader) else readFrameSize(reader)
                if (width != metadata.width || height != metadata.height) throw ReaderFailure.CorruptImage()
                val bounds = request.key.bounds
                val right = bounds.right.coerceAtMost(width)
                val bottom = bounds.bottom.coerceAtMost(height)
                if (bounds.left >= right || bounds.top >= bottom) {
                    throw ReaderFailure.RegionUnavailable("region lies outside the image")
                }
                val region = IntRect(bounds.left, bounds.top, right, bottom)
                if (isGif) {
                    if (!metadata.supportsRegionDecode) {
                        throw ReaderFailure.RegionUnavailable(
                            "gif composition canvas exceeds the reader memory budget",
                        )
                    }
                    decodeGifFrame(
                        reader,
                        width,
                        height,
                        request.key.frameId?.frameIndex ?: 0,
                        region = region,
                        key = request.key,
                    )
                } else {
                    decodeStillRegion(reader, region, request)
                }
            }
        }
    }

    private suspend fun decodeStillRegion(
        reader: ImageReader,
        region: IntRect,
        request: TileRequest,
    ): DecodedTile {
        val subsampling = chooseSubsampling(region, request)
        val expectedWidth = (region.width + subsampling - 1) / subsampling
        val expectedHeight = (region.height + subsampling - 1) / subsampling
        val rasterBytes = checkedRasterBytes(expectedWidth, expectedHeight)
        val (finalLease, scratchLease) = reservePeak(rasterBytes, rasterBytes)
        var decoded: BufferedImage? = null
        var output: BufferedImage? = null
        try {
            val param = reader.defaultReadParam
            param.setSourceRegion(Rectangle(region.left, region.top, region.width, region.height))
            if (subsampling > 1) param.setSourceSubsampling(subsampling, subsampling, 0, 0)
            decoded = guarded { reader.read(0, param) } ?: throw ReaderFailure.CorruptImage()
            if (decoded.width > expectedWidth || decoded.height > expectedHeight) {
                throw ReaderFailure.CorruptImage()
            }
            output = BufferedImage(expectedWidth, expectedHeight, BufferedImage.TYPE_INT_ARGB_PRE)
            val graphics = output.createGraphics()
            try {
                graphics.drawImage(decoded, 0, 0, null)
            } finally {
                graphics.dispose()
            }
            decoded.flush()
            decoded = null
            currentCoroutineContext().ensureActive()
            scratchLease.close()
            val tile = BudgetedDecodedTile(request.key, output, finalLease)
            output = null
            return tile
        } finally {
            if (output != null || decoded != null) {
                output?.flush()
                decoded?.flush()
                finalLease.close()
                scratchLease.close()
            }
        }
    }

    private fun chooseSubsampling(region: IntRect, request: TileRequest): Int {
        val fitWidth = region.width / request.targetWidth
        val fitHeight = region.height / request.targetHeight
        val fit = Integer.highestOneBit(maxOf(1, minOf(fitWidth, fitHeight)))
        val requested = Integer.highestOneBit(maxOf(1, request.key.sampleSize))
        return maxOf(fit, requested).coerceAtMost(MAX_SUBSAMPLING)
    }

    private suspend fun <T> withReader(input: BoundedPageInput, block: suspend (ImageReader) -> T): T {
        val imageInput = ImageIO.createImageInputStream(input.input)
            ?: throw ReaderFailure.UnsupportedImage("no image input stream")
        try {
            val readers = ImageIO.getImageReaders(imageInput)
            if (!readers.hasNext()) throw ReaderFailure.UnsupportedImage("no ImageIO reader for the stream")
            val reader = readers.next()
            try {
                reader.input = imageInput
                return block(reader)
            } finally {
                reader.dispose()
            }
        } finally {
            imageInput.close()
        }
    }

    private fun readFrameSize(reader: ImageReader): Pair<Int, Int> =
        guarded { reader.getWidth(0) } to guarded { reader.getHeight(0) }

    private fun gifCanvasSize(reader: ImageReader): Pair<Int, Int> {
        val frameSize = readFrameSize(reader)
        val streamMetadata = guarded { reader.streamMetadata } ?: return frameSize
        val root = runCatching { streamMetadata.getAsTree("javax_imageio_gif_stream_1.0") }
            .getOrNull() as? IIOMetadataNode ?: return frameSize
        val descriptor = root.getElementsByTagName("LogicalScreenDescriptor").item(0) as? IIOMetadataNode
            ?: return frameSize
        val width = descriptor.getAttribute("logicalScreenWidth").toIntOrNull() ?: return frameSize
        val height = descriptor.getAttribute("logicalScreenHeight").toIntOrNull() ?: return frameSize
        if (width <= 0 || height <= 0) return frameSize
        return maxOf(width, frameSize.first) to maxOf(height, frameSize.second)
    }

    private fun gifDelayMillis(reader: ImageReader, frameIndex: Int): Long {
        val metadata = guarded { reader.getImageMetadata(frameIndex) }
        val root = runCatching { metadata.getAsTree("javax_imageio_gif_image_1.0") }
            .getOrNull() as? IIOMetadataNode ?: return GIF_MIN_DURATION_MILLIS
        val control = root.getElementsByTagName("GraphicControlExtension").item(0) as? IIOMetadataNode
            ?: return GIF_MIN_DURATION_MILLIS
        val hundredths = control.getAttribute("delayTime").toLongOrNull() ?: 0L
        return (hundredths * 10L).coerceIn(GIF_MIN_DURATION_MILLIS, GIF_MAX_DURATION_MILLIS)
    }

    private fun gifRegionPeakBytes(width: Int, height: Int): Long {
        val canvas = checkedRasterBytes(width, height)
        // Canvas + frame intermediate + restore-to-previous snapshot + cropped output.
        return Math.multiplyExact(canvas, 4L)
    }

    private fun validateDimensions(width: Int, height: Int) {
        if (width <= 0 || height <= 0) throw ReaderFailure.CorruptImage()
        val largest = maxOf(width, height).toLong()
        if (largest > ReaderLimits.MAX_IMAGE_DIMENSION) {
            throw ReaderFailure.LimitExceeded("image dimension", ReaderLimits.MAX_IMAGE_DIMENSION.toLong(), largest)
        }
        checkedRasterBytes(width, height)
    }

    private fun checkedRasterBytes(width: Int, height: Int): Long = try {
        Math.multiplyExact(Math.multiplyExact(width.toLong(), height.toLong()), 4L)
    } catch (overflow: ArithmeticException) {
        throw ReaderFailure.LimitExceeded("image raster", Long.MAX_VALUE, Long.MAX_VALUE)
    }

    private fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (error: IOException) {
        throw ReaderFailure.CorruptImage(error)
    }

    companion object {
        const val GIF_MIN_DURATION_MILLIS: Long = 20L
        const val GIF_MAX_DURATION_MILLIS: Long = 10_000L
        const val MAX_SUBSAMPLING: Int = 64
        private const val DISPOSAL_RESTORE_TO_BACKGROUND = "restoreToBackgroundColor"
        private const val DISPOSAL_RESTORE_TO_PREVIOUS = "restoreToPrevious"
    }
}
