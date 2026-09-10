package mihon.desktop.reader.codec

import mihon.reader.image.ImageMetadata
import mihon.reader.image.ReaderImageFormat
import mihon.reader.source.ReaderFailure
import mihon.reader.source.ReaderLimits

object CodecWorkerProtocol {
    fun parseMetadata(
        output: String,
        format: ReaderImageFormat,
        orientationApplied: Boolean = false,
    ): ImageMetadata {
        val frames = output.lineSequence().filter { it.isNotBlank() }.map { line ->
            val fields = line.split('\t')
            if (fields.size != FIELD_COUNT) throw ReaderFailure.CorruptImage()
            FrameMetadata(
                width = fields[0].toIntOrNull() ?: throw ReaderFailure.CorruptImage(),
                height = fields[1].toIntOrNull() ?: throw ReaderFailure.CorruptImage(),
                delayCentiseconds = fields[2].toLongOrNull() ?: throw ReaderFailure.CorruptImage(),
                channels = fields[3],
            )
        }.toList()
        if (frames.isEmpty()) throw ReaderFailure.CorruptImage()
        val first = frames.first()
        validateDimensions(first.width, first.height)
        if (frames.any { it.width != first.width || it.height != first.height }) {
            throw ReaderFailure.CorruptImage()
        }
        return ImageMetadata(
            width = first.width,
            height = first.height,
            frameCount = frames.size,
            frameDurationsMillis = frames.map { frame ->
                Math.multiplyExact(frame.delayCentiseconds, 10L).coerceIn(MIN_FRAME_MILLIS, MAX_FRAME_MILLIS)
            },
            supportsRegionDecode = true,
            format = format,
            hasAlpha = frames.any { 'a' in it.channels.lowercase() },
            orientationApplied = orientationApplied,
        )
    }

    fun validateBgraPayload(width: Int, height: Int, payloadBytes: Long): Int {
        validateDimensions(width, height)
        val required = try {
            Math.multiplyExact(Math.multiplyExact(width.toLong(), height.toLong()), 4L)
        } catch (error: ArithmeticException) {
            throw ReaderFailure.LimitExceeded(
                "decoded image raster",
                ReaderLimits.FULL_DECODE_MAX_BYTES,
                Long.MAX_VALUE,
            )
        }
        if (required > ReaderLimits.FULL_DECODE_MAX_BYTES) {
            throw ReaderFailure.LimitExceeded("decoded image raster", ReaderLimits.FULL_DECODE_MAX_BYTES, required)
        }
        if (payloadBytes != required) throw ReaderFailure.CorruptImage()
        return try {
            Math.toIntExact(required)
        } catch (error: ArithmeticException) {
            throw ReaderFailure.LimitExceeded("decoded image raster", Int.MAX_VALUE.toLong(), required)
        }
    }

    private fun validateDimensions(width: Int, height: Int) {
        if (width <= 0 || height <= 0) throw ReaderFailure.CorruptImage()
        val largest = maxOf(width, height).toLong()
        if (largest > ReaderLimits.MAX_IMAGE_DIMENSION) {
            throw ReaderFailure.LimitExceeded("image dimension", ReaderLimits.MAX_IMAGE_DIMENSION.toLong(), largest)
        }
        val pixels = width.toLong() * height.toLong()
        if (pixels > ReaderLimits.MAX_IMAGE_PIXELS) {
            throw ReaderFailure.LimitExceeded("image pixels", ReaderLimits.MAX_IMAGE_PIXELS, pixels)
        }
    }

    private data class FrameMetadata(
        val width: Int,
        val height: Int,
        val delayCentiseconds: Long,
        val channels: String,
    )

    private const val FIELD_COUNT = 4
    private const val MIN_FRAME_MILLIS = 20L
    private const val MAX_FRAME_MILLIS = 10_000L
}
