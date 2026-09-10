package mihon.desktop.reader.codec

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.reader.image.BudgetedDecodedTile
import mihon.reader.image.DecodedTile
import mihon.reader.image.ImageMetadata
import mihon.reader.image.PageDecoder
import mihon.reader.image.ReaderImageFormat
import mihon.reader.image.TileKey
import mihon.reader.image.TileRequest
import mihon.reader.memory.BoundedReaderMemoryBudget
import mihon.reader.memory.MemoryKind
import mihon.reader.model.FrameId
import mihon.reader.source.BoundedPageInput
import mihon.reader.source.ReaderFailure
import mihon.reader.source.ReaderLimits
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

class PackagedCodecPageDecoder(
    private val budget: BoundedReaderMemoryBudget,
    private val executable: Path,
    private val runner: CodecCommandRunner = ProcessCodecCommandRunner,
    private val temporaryRoot: Path = Path.of(System.getProperty("java.io.tmpdir"), "mihon-reader-codec"),
) : PageDecoder {
    override suspend fun probe(input: BoundedPageInput): ImageMetadata = withEncodedInput(input, "img") { path, work ->
        val result = runner.run(
            command(
                arguments = buildList {
                    addAll(resourceLimits())
                    add(path.toString())
                    add("-auto-orient")
                    add("-format")
                    add("%w\t%h\t%T\t%[channels]\n")
                    add("info:")
                },
                workingDirectory = work,
                timeoutMillis = PROBE_TIMEOUT_MILLIS,
                maxStdoutBytes = METADATA_OUTPUT_LIMIT,
            ),
        )
        ensureSuccess(result)
        CodecWorkerProtocol.parseMetadata(
            result.stdout.decodeToString(),
            ReaderImageFormat.UNKNOWN,
            orientationApplied = true,
        )
    }

    override suspend fun decodeFull(
        input: BoundedPageInput,
        metadata: ImageMetadata,
        frameId: FrameId,
    ): DecodedTile {
        require(frameId.frameIndex in 0 until metadata.frameCount) { "frame index is outside metadata" }
        return decode(input, metadata, frameId, null, null)
    }

    override suspend fun decodeRegion(
        input: BoundedPageInput,
        metadata: ImageMetadata,
        request: TileRequest,
    ): DecodedTile {
        val frameId = request.key.frameId ?: FrameId(request.key.pageId, 0)
        require(frameId.frameIndex in 0 until metadata.frameCount) { "frame index is outside metadata" }
        val bounds = request.key.bounds
        val right = minOf(bounds.right, metadata.width)
        val bottom = minOf(bounds.bottom, metadata.height)
        if (bounds.left >= right || bounds.top >= bottom) {
            input.close()
            throw ReaderFailure.RegionUnavailable("region lies outside the image")
        }
        val width = (right - bounds.left + request.key.sampleSize - 1) / request.key.sampleSize
        val height = (bottom - bounds.top + request.key.sampleSize - 1) / request.key.sampleSize
        return decode(
            input,
            metadata,
            frameId,
            Region(bounds.left, bounds.top, right - bounds.left, bottom - bounds.top, width, height),
            request.key,
        )
    }

    private suspend fun decode(
        input: BoundedPageInput,
        metadata: ImageMetadata,
        frameId: FrameId,
        region: Region?,
        outputKey: TileKey?,
    ): DecodedTile {
        val width = region?.outputWidth ?: metadata.width
        val height = region?.outputHeight ?: metadata.height
        val outputBytes = CodecWorkerProtocol.validateBgraPayload(width, height, width.toLong() * height * 4L)
        val lease = budget.reserve(MemoryKind.DECODED_OUTPUT, outputBytes.toLong())
        var image: BufferedImage? = null
        try {
            val pixels = withEncodedInput(input, metadata.format.extension) { path, work ->
                val arguments = buildList {
                    addAll(resourceLimits())
                    add(path.toString())
                    add("-auto-orient")
                    if (metadata.isAnimated) {
                        add("-coalesce")
                        if (frameId.frameIndex > 0) {
                            add("-delete")
                            add("0-${frameId.frameIndex - 1}")
                        }
                        add("-delete")
                        add("1--1")
                    } else {
                        add("-delete")
                        add("1--1")
                    }
                    add("-colorspace")
                    add("sRGB")
                    region?.let {
                        add("-crop")
                        add("${it.sourceWidth}x${it.sourceHeight}+${it.left}+${it.top}")
                        add("+repage")
                        if (it.outputWidth != it.sourceWidth || it.outputHeight != it.sourceHeight) {
                            add("-sample")
                            add("${it.outputWidth}x${it.outputHeight}!")
                        }
                    }
                    add("-alpha")
                    add("on")
                    add("-depth")
                    add("8")
                    add("BGRA:-")
                }
                val result = runner.run(
                    command(arguments, work, DECODE_TIMEOUT_MILLIS, outputBytes),
                )
                ensureSuccess(result)
                CodecWorkerProtocol.validateBgraPayload(width, height, result.stdout.size.toLong())
                result.stdout
            }
            image = pixels.toPremultipliedImage(width, height)
            val key = outputKey
                ?: TileKey(
                    frameId.pageId,
                    frameId.takeIf {
                        metadata.isAnimated
                    },
                    mihon.reader.image.IntRect(0, 0, metadata.width, metadata.height),
                )
            return BudgetedDecodedTile(key, image, lease).also { image = null }
        } catch (error: Throwable) {
            image?.flush()
            lease.close()
            throw error
        }
    }

    private suspend fun <T> withEncodedInput(
        input: BoundedPageInput,
        extension: String,
        block: suspend (Path, Path) -> T,
    ): T = withContext(Dispatchers.IO) {
        Files.createDirectories(temporaryRoot)
        val work = Files.createTempDirectory(temporaryRoot, "request-")
        try {
            val encoded = work.resolve("page.$extension")
            input.use { bounded ->
                Files.newOutputStream(encoded).use { output ->
                    val copied = bounded.input.copyTo(output, COPY_BUFFER_SIZE)
                    if (copied != bounded.byteCount) throw ReaderFailure.CorruptImage()
                    if (copied > ReaderLimits.MAX_PAGE_BYTES) {
                        throw ReaderFailure.LimitExceeded("encoded page", ReaderLimits.MAX_PAGE_BYTES, copied)
                    }
                }
            }
            block(encoded, work)
        } finally {
            deleteTree(work)
        }
    }

    private fun command(arguments: List<String>, workingDirectory: Path, timeoutMillis: Long, maxStdoutBytes: Int) =
        CodecCommand(executable, arguments, workingDirectory, timeoutMillis, maxStdoutBytes)

    private fun ensureSuccess(result: CodecCommandResult) {
        if (result.exitCode != 0) {
            val message = result.stderr.decodeToString().take(ERROR_MESSAGE_LIMIT)
            throw ReaderFailure.CorruptImage(IOException("codec exited ${result.exitCode}: $message"))
        }
    }

    private fun resourceLimits() = listOf(
        "-limit", "memory", "256MiB",
        "-limit", "map", "0",
        "-limit", "disk", "512MiB",
        "-limit", "thread", "2",
        "-limit", "time", "30",
    )

    private fun ByteArray.toPremultipliedImage(width: Int, height: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE)
        val output = (image.raster.dataBuffer as DataBufferInt).data
        repeat(output.size) { index ->
            val offset = index * 4
            val blue = this[offset].toInt() and 0xff
            val green = this[offset + 1].toInt() and 0xff
            val red = this[offset + 2].toInt() and 0xff
            val alpha = this[offset + 3].toInt() and 0xff
            output[index] = alpha shl 24 or
                (red * alpha / 255 shl 16) or
                (green * alpha / 255 shl 8) or
                (blue * alpha / 255)
        }
        return image
    }

    private fun deleteTree(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private data class Region(
        val left: Int,
        val top: Int,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val outputWidth: Int,
        val outputHeight: Int,
    )

    private val ReaderImageFormat.extension: String
        get() = name.lowercase().takeUnless { this == ReaderImageFormat.UNKNOWN } ?: "img"

    private companion object {
        const val PROBE_TIMEOUT_MILLIS = 5_000L
        const val DECODE_TIMEOUT_MILLIS = 30_000L
        const val METADATA_OUTPUT_LIMIT = 64 * 1024
        const val ERROR_MESSAGE_LIMIT = 2_048
        const val COPY_BUFFER_SIZE = 64 * 1024
    }
}
