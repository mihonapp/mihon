package mihon.reader.image

import mihon.reader.model.FrameId
import mihon.reader.source.BoundedPageInput
import mihon.reader.source.ReaderFailure
import java.io.PushbackInputStream

class CompositePageDecoder(
    private val imageIo: PageDecoder,
    private val packagedCodec: PageDecoder,
    private val animatedPng: PageDecoder = packagedCodec,
) : PageDecoder {
    override suspend fun probe(input: BoundedPageInput): ImageMetadata {
        val peekable = PushbackInputStream(input.input, HEADER_BYTES)
        val header = peekable.readNBytes(HEADER_BYTES)
        peekable.unread(header)
        val wrapped = BoundedPageInput(peekable, input.byteCount)
        val format = ImageFormatDetector.detect(header)
        if (format == ReaderImageFormat.UNKNOWN) {
            wrapped.close()
            throw ReaderFailure.UnsupportedImage("unrecognized image signature")
        }
        return decoderFor(format, format == ReaderImageFormat.PNG && header.containsAscii("acTL"))
            .probe(wrapped)
            .copy(format = format)
    }

    override suspend fun decodeFull(
        input: BoundedPageInput,
        metadata: ImageMetadata,
        frameId: FrameId,
    ): DecodedTile = decoderForOrClose(metadata, input).decodeFull(input, metadata, frameId)

    override suspend fun decodeRegion(
        input: BoundedPageInput,
        metadata: ImageMetadata,
        request: TileRequest,
    ): DecodedTile = decoderForOrClose(metadata, input).decodeRegion(input, metadata, request)

    private fun decoderForOrClose(metadata: ImageMetadata, input: BoundedPageInput): PageDecoder = try {
        decoderFor(metadata.format, metadata.format == ReaderImageFormat.PNG && metadata.isAnimated)
    } catch (error: Throwable) {
        input.close()
        throw error
    }

    private fun decoderFor(format: ReaderImageFormat, animatedPng: Boolean = false): PageDecoder = when {
        animatedPng -> this.animatedPng
        format in packagedFormats -> packagedCodec
        format in imageIoFormats -> imageIo
        else -> throw ReaderFailure.UnsupportedImage("unrecognized image signature")
    }

    private fun ByteArray.containsAscii(value: String): Boolean {
        if (value.length > size) return false
        return (0..size - value.length).any { offset ->
            value.indices.all { index -> this[offset + index].toInt() and 0xff == value[index].code }
        }
    }

    private companion object {
        const val HEADER_BYTES = 64

        val packagedFormats = setOf(
            ReaderImageFormat.JPEG,
            ReaderImageFormat.WEBP,
            ReaderImageFormat.AVIF,
            ReaderImageFormat.HEIF,
            ReaderImageFormat.JXL,
            ReaderImageFormat.TIFF,
        )
        val imageIoFormats = setOf(
            ReaderImageFormat.PNG,
            ReaderImageFormat.GIF,
            ReaderImageFormat.BMP,
        )
    }
}
