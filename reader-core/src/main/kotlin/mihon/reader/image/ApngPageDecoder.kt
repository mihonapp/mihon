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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import javax.imageio.ImageIO

/** Pure-Java APNG decoder. Frames are reconstructed as ordinary PNGs and composited on the canvas. */
class ApngPageDecoder(
    private val budget: BoundedReaderMemoryBudget,
) : PageDecoder {
    override suspend fun probe(input: BoundedPageInput): ImageMetadata = withContext(Dispatchers.IO) {
        input.use { pageInput ->
            val animation = parse(pageInput)
            ImageMetadata(
                width = animation.width,
                height = animation.height,
                frameCount = animation.frames.size,
                frameDurationsMillis = animation.frames.map { it.control.durationMillis },
                supportsRegionDecode = false,
                format = ReaderImageFormat.PNG,
                hasAlpha = animation.hasAlpha,
            )
        }
    }

    override suspend fun decodeFull(
        input: BoundedPageInput,
        metadata: ImageMetadata,
        frameId: FrameId,
    ): DecodedTile = decode(input, metadata, frameId, null)

    override suspend fun decodeRegion(
        input: BoundedPageInput,
        metadata: ImageMetadata,
        request: TileRequest,
    ): DecodedTile = decode(input, metadata, request.key.frameId ?: FrameId(request.key.pageId, 0), request)

    private suspend fun decode(
        input: BoundedPageInput,
        metadata: ImageMetadata,
        frameId: FrameId,
        request: TileRequest?,
    ): DecodedTile = withContext(Dispatchers.IO) {
        input.use { pageInput ->
            val animation = parse(pageInput)
            if (animation.width != metadata.width || animation.height != metadata.height) {
                throw ReaderFailure.CorruptImage()
            }
            val frameIndex = frameId.frameIndex
            if (frameIndex !in animation.frames.indices) throw ReaderFailure.CorruptImage()
            val requestedRegion = request?.key?.bounds
            val region = requestedRegion?.let {
                val right = it.right.coerceAtMost(animation.width)
                val bottom = it.bottom.coerceAtMost(animation.height)
                if (it.left >= right || it.top >= bottom) {
                    throw ReaderFailure.RegionUnavailable("region lies outside the image")
                }
                IntRect(it.left, it.top, right, bottom)
            }
            val sampleSize = request?.let { chooseSubsampling(region!!, it) } ?: 1
            val outputWidth = region?.let { (it.width + sampleSize - 1) / sampleSize } ?: animation.width
            val outputHeight = region?.let { (it.height + sampleSize - 1) / sampleSize } ?: animation.height
            val outputBytes = checkedRasterBytes(outputWidth, outputHeight)
            val canvasBytes = checkedRasterBytes(animation.width, animation.height)
            if (canvasBytes > ReaderLimits.FULL_DECODE_MAX_BYTES) {
                throw ReaderFailure.LimitExceeded(
                    "APNG composition canvas",
                    ReaderLimits.FULL_DECODE_MAX_BYTES,
                    canvasBytes,
                )
            }
            val outputLease = budget.reserve(MemoryKind.DECODED_OUTPUT, outputBytes)
            val scratchLease = reserveScratch(outputLease, canvasBytes * 3L)
            var canvas: BufferedImage? = null
            var snapshot: BufferedImage? = null
            var output: BufferedImage? = null
            var failed = true
            try {
                canvas = BufferedImage(animation.width, animation.height, BufferedImage.TYPE_INT_ARGB_PRE)
                var previousControl: FrameControl? = null
                for (index in 0..frameIndex) {
                    currentCoroutineContext().ensureActive()
                    val frame = animation.frames[index]
                    previousControl?.let { previous ->
                        when (previous.disposeOp) {
                            DISPOSE_BACKGROUND -> clear(canvas, previous.bounds)
                            DISPOSE_PREVIOUS -> {
                                val restore = snapshot ?: throw ReaderFailure.CorruptImage()
                                draw(canvas, restore, 0, 0, AlphaComposite.Src)
                                restore.flush()
                                snapshot = null
                            }
                        }
                    }
                    if (frame.control.disposeOp == DISPOSE_PREVIOUS) {
                        snapshot?.flush()
                        snapshot = copyOf(canvas)
                    }
                    val decoded = ImageIO.read(ByteArrayInputStream(frame.pngBytes))
                        ?: throw ReaderFailure.CorruptImage()
                    try {
                        if (decoded.width != frame.control.width || decoded.height != frame.control.height) {
                            throw ReaderFailure.CorruptImage()
                        }
                        if (frame.control.blendOp == BLEND_SOURCE) clear(canvas, frame.control.bounds)
                        draw(
                            canvas,
                            decoded,
                            frame.control.xOffset,
                            frame.control.yOffset,
                            if (frame.control.blendOp == BLEND_SOURCE) AlphaComposite.Src else AlphaComposite.SrcOver,
                        )
                    } finally {
                        decoded.flush()
                    }
                    previousControl = frame.control
                }
                output = if (region == null && sampleSize == 1) {
                    canvas.also { canvas = null }
                } else {
                    val result = BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB_PRE)
                    val graphics = result.createGraphics()
                    try {
                        graphics.composite = AlphaComposite.Src
                        graphics.drawImage(
                            canvas,
                            0,
                            0,
                            outputWidth,
                            outputHeight,
                            region!!.left,
                            region.top,
                            region.right,
                            region.bottom,
                            null,
                        )
                    } finally {
                        graphics.dispose()
                    }
                    canvas?.flush()
                    canvas = null
                    result
                }
                snapshot?.flush()
                snapshot = null
                scratchLease.close()
                val key = request?.key ?: TileKey(
                    frameId.pageId,
                    frameId,
                    IntRect(0, 0, animation.width, animation.height),
                )
                val tile = BudgetedDecodedTile(key, output, outputLease)
                output = null
                failed = false
                tile
            } finally {
                if (failed) {
                    output?.flush()
                    canvas?.flush()
                    snapshot?.flush()
                    scratchLease.close()
                    outputLease.close()
                }
            }
        }
    }

    private fun parse(input: BoundedPageInput): Animation {
        if (input.byteCount > MAX_APNG_ENCODED_BYTES) {
            throw ReaderFailure.LimitExceeded("APNG encoded bytes", MAX_APNG_ENCODED_BYTES, input.byteCount)
        }
        val bytes = input.input.readNBytes(input.byteCount.toInt())
        if (bytes.size.toLong() != input.byteCount) throw ReaderFailure.CorruptImage()
        return ApngParser(bytes).parse()
    }

    private suspend fun reserveScratch(output: MemoryLease, bytes: Long): MemoryLease = try {
        budget.reserve(MemoryKind.DECODED_OUTPUT, bytes)
    } catch (error: Throwable) {
        output.close()
        throw error
    }

    private fun checkedRasterBytes(width: Int, height: Int): Long {
        if (width <= 0 || height <= 0) throw ReaderFailure.CorruptImage()
        val largest = maxOf(width, height).toLong()
        if (largest > ReaderLimits.MAX_IMAGE_DIMENSION) {
            throw ReaderFailure.LimitExceeded("image dimension", ReaderLimits.MAX_IMAGE_DIMENSION.toLong(), largest)
        }
        val pixels = width.toLong() * height.toLong()
        if (pixels > ReaderLimits.MAX_IMAGE_PIXELS) {
            throw ReaderFailure.LimitExceeded("image pixel count", ReaderLimits.MAX_IMAGE_PIXELS, pixels)
        }
        return Math.multiplyExact(pixels, 4L)
    }

    private fun chooseSubsampling(region: IntRect, request: TileRequest): Int {
        val fit = Integer.highestOneBit(
            maxOf(1, minOf(region.width / request.targetWidth, region.height / request.targetHeight)),
        )
        return maxOf(fit, Integer.highestOneBit(maxOf(1, request.key.sampleSize))).coerceAtMost(64)
    }

    private fun clear(image: BufferedImage, bounds: Rectangle) {
        val graphics = image.createGraphics()
        try {
            graphics.composite = AlphaComposite.Clear
            graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
        } finally {
            graphics.dispose()
        }
    }

    private fun draw(target: BufferedImage, source: BufferedImage, x: Int, y: Int, composite: AlphaComposite) {
        val graphics = target.createGraphics()
        try {
            graphics.composite = composite
            graphics.drawImage(source, x, y, null)
        } finally {
            graphics.dispose()
        }
    }

    private fun copyOf(source: BufferedImage): BufferedImage =
        BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB_PRE).also {
            draw(it, source, 0, 0, AlphaComposite.Src)
        }

    private data class Animation(
        val width: Int,
        val height: Int,
        val hasAlpha: Boolean,
        val frames: List<Frame>,
    )

    private data class Frame(val control: FrameControl, val pngBytes: ByteArray)

    private data class FrameControl(
        val width: Int,
        val height: Int,
        val xOffset: Int,
        val yOffset: Int,
        val delayNumerator: Int,
        val delayDenominator: Int,
        val disposeOp: Int,
        val blendOp: Int,
    ) {
        val durationMillis: Long
            get() = ((delayNumerator.toLong() * 1000L) / if (delayDenominator == 0) 100L else delayDenominator.toLong())
                .coerceIn(20L, 10_000L)
        val bounds: Rectangle get() = Rectangle(xOffset, yOffset, width, height)
    }

    private class ApngParser(private val bytes: ByteArray) {
        private lateinit var ihdr: ByteArray
        private val globalChunks = mutableListOf<Chunk>()
        private val frames = mutableListOf<MutableFrame>()
        private var current: MutableFrame? = null
        private var width = 0
        private var height = 0
        private var colorType = 0
        private var hasTransparency = false
        private var declaredFrames = -1
        private var seenImageData = false

        fun parse(): Animation {
            if (bytes.size < SIGNATURE.size || !bytes.copyOfRange(0, SIGNATURE.size).contentEquals(SIGNATURE)) {
                throw ReaderFailure.CorruptImage()
            }
            var offset = SIGNATURE.size
            var ended = false
            while (offset < bytes.size) {
                if (bytes.size - offset < 12) throw ReaderFailure.CorruptImage()
                val length = readInt(bytes, offset)
                if (length < 0 || length > bytes.size - offset - 12) throw ReaderFailure.CorruptImage()
                val type = bytes.copyOfRange(offset + 4, offset + 8).decodeToString()
                val data = bytes.copyOfRange(offset + 8, offset + 8 + length)
                validateCrc(type, data, readInt(bytes, offset + 8 + length))
                when (type) {
                    "IHDR" -> readHeader(data)
                    "acTL" -> {
                        if (data.size != 8) throw ReaderFailure.CorruptImage()
                        declaredFrames = readInt(data, 0)
                    }
                    "fcTL" -> beginFrame(data)
                    "IDAT" -> {
                        seenImageData = true
                        current?.dataChunks?.add(data)
                    }
                    "fdAT" -> {
                        seenImageData = true
                        if (data.size < 4) throw ReaderFailure.CorruptImage()
                        (current ?: throw ReaderFailure.CorruptImage()).dataChunks.add(data.copyOfRange(4, data.size))
                    }
                    "IEND" -> ended = true
                    "tRNS" -> {
                        hasTransparency = true
                        if (!seenImageData) globalChunks += Chunk(type, data)
                    }
                    else -> if (!seenImageData && type !in APNG_CONTROL_CHUNKS) globalChunks += Chunk(type, data)
                }
                offset += length + 12
                if (ended) break
            }
            if (!ended || width <= 0 || height <= 0 || declaredFrames <= 0 || frames.size != declaredFrames) {
                throw ReaderFailure.CorruptImage()
            }
            val builtFrames = frames.map { frame ->
                if (frame.dataChunks.isEmpty()) throw ReaderFailure.CorruptImage()
                Frame(frame.control, buildPng(frame))
            }
            return Animation(width, height, colorType == 4 || colorType == 6 || hasTransparency, builtFrames)
        }

        private fun readHeader(data: ByteArray) {
            if (::ihdr.isInitialized || data.size != 13) throw ReaderFailure.CorruptImage()
            ihdr = data
            width = readInt(data, 0)
            height = readInt(data, 4)
            colorType = data[9].toInt() and 0xff
            validateFrameBounds(width, height, 0, 0)
        }

        private fun beginFrame(data: ByteArray) {
            if (data.size != 26 || !::ihdr.isInitialized) throw ReaderFailure.CorruptImage()
            val control = FrameControl(
                width = readInt(data, 4),
                height = readInt(data, 8),
                xOffset = readInt(data, 12),
                yOffset = readInt(data, 16),
                delayNumerator = readUnsignedShort(data, 20),
                delayDenominator = readUnsignedShort(data, 22),
                disposeOp = data[24].toInt() and 0xff,
                blendOp = data[25].toInt() and 0xff,
            )
            validateFrameBounds(control.width, control.height, control.xOffset, control.yOffset)
            if (control.disposeOp !in 0..2 || control.blendOp !in 0..1) throw ReaderFailure.CorruptImage()
            current?.let { if (it.dataChunks.isEmpty()) throw ReaderFailure.CorruptImage() }
            current = MutableFrame(control).also { frames += it }
        }

        private fun validateFrameBounds(frameWidth: Int, frameHeight: Int, x: Int, y: Int) {
            if (frameWidth <= 0 || frameHeight <= 0 || x < 0 || y < 0) throw ReaderFailure.CorruptImage()
            if (x.toLong() + frameWidth > width.toLong() || y.toLong() + frameHeight > height.toLong()) {
                throw ReaderFailure.CorruptImage()
            }
        }

        private fun buildPng(frame: MutableFrame): ByteArray {
            val header = ihdr.copyOf()
            writeInt(header, 0, frame.control.width)
            writeInt(header, 4, frame.control.height)
            return ByteArrayOutputStream().use { bytesOut ->
                DataOutputStream(bytesOut).use { output ->
                    output.write(SIGNATURE)
                    writeChunk(output, "IHDR", header)
                    globalChunks.forEach { writeChunk(output, it.type, it.data) }
                    frame.dataChunks.forEach { writeChunk(output, "IDAT", it) }
                    writeChunk(output, "IEND", ByteArray(0))
                }
                bytesOut.toByteArray()
            }
        }

        private data class Chunk(val type: String, val data: ByteArray)
        private data class MutableFrame(
            val control: FrameControl,
            val dataChunks: MutableList<ByteArray> = mutableListOf(),
        )
    }

    companion object {
        private val SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        private val APNG_CONTROL_CHUNKS = setOf("acTL", "fcTL", "fdAT")
        private const val DISPOSE_BACKGROUND = 1
        private const val DISPOSE_PREVIOUS = 2
        private const val BLEND_SOURCE = 0
        private const val MAX_APNG_ENCODED_BYTES = 64L * 1024L * 1024L

        private fun readInt(bytes: ByteArray, offset: Int): Int =
            (bytes[offset].toInt() and 0xff shl 24) or
                (bytes[offset + 1].toInt() and 0xff shl 16) or
                (bytes[offset + 2].toInt() and 0xff shl 8) or
                (bytes[offset + 3].toInt() and 0xff)

        private fun readUnsignedShort(bytes: ByteArray, offset: Int): Int =
            (bytes[offset].toInt() and 0xff shl 8) or (bytes[offset + 1].toInt() and 0xff)

        private fun writeInt(bytes: ByteArray, offset: Int, value: Int) {
            bytes[offset] = (value ushr 24).toByte()
            bytes[offset + 1] = (value ushr 16).toByte()
            bytes[offset + 2] = (value ushr 8).toByte()
            bytes[offset + 3] = value.toByte()
        }

        private fun writeChunk(output: DataOutputStream, type: String, data: ByteArray) {
            val typeBytes = type.encodeToByteArray()
            output.writeInt(data.size)
            output.write(typeBytes)
            output.write(data)
            val crc = CRC32()
            crc.update(typeBytes)
            crc.update(data)
            output.writeInt(crc.value.toInt())
        }

        private fun validateCrc(type: String, data: ByteArray, expected: Int) {
            val crc = CRC32()
            crc.update(type.encodeToByteArray())
            crc.update(data)
            if (crc.value.toInt() != expected) throw ReaderFailure.CorruptImage()
        }
    }
}
