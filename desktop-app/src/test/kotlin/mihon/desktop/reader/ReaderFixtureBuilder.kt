package mihon.desktop.reader

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.cli.ReaderFixtureFileRecord
import mihon.desktop.cli.ReaderFixtureManifest
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.awt.image.BufferedImage
import java.awt.image.IndexColorModel
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.util.Arrays
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode

internal data class BuiltReaderFixture(
    val root: Path,
    val files: List<ReaderFixtureFileRecord>,
    val manifestSha256: String,
    val committedRarSha256: String,
)

internal object ReaderFixtureBuilder {
    fun build(
        root: Path,
        committedRar: Path,
        committedSource: Path = repositoryRoot()
            .resolve(COMMITTED_SOURCE_PATH)
            .toAbsolutePath()
            .normalize(),
    ): BuiltReaderFixture {
        val normalizedRoot = root.toAbsolutePath().normalize()
        Files.createDirectories(normalizedRoot)
        Files.newDirectoryStream(normalizedRoot).use { existing ->
            check(!existing.iterator().hasNext()) { "reader fixture target must be empty" }
        }
        check(sha256(committedRar) == COMMITTED_RAR_SHA256) { "committed Task 3 RAR hash changed" }
        check(sha256(committedSource) == COMMITTED_SOURCE_SHA256) { "committed Task 3 source hash changed" }

        val mangaRoot = normalizedRoot.resolve(MANGA_DIRECTORY)
        val directoryChapter = mangaRoot.resolve("01-directory")
        Files.createDirectories(directoryChapter.resolve("nested"))

        val opaque = opaqueJpeg(16, 12)
        val transparent = transparentPng(12, 10)
        val animated = animatedGif()
        val corrupt = corruptPng()
        val extreme = normalizedRoot.resolve("extreme-source.png")
        writeStreamedGrayscalePng(extreme, EXTREME_SIZE, EXTREME_SIZE)
        val extremeBytes = Files.readAllBytes(extreme)

        write(directoryChapter.resolve("10-opaque.jpg"), opaque)
        write(directoryChapter.resolve("20-transparent.png"), transparent)
        write(directoryChapter.resolve("30-animated.gif"), animated)
        write(directoryChapter.resolve("35-corrupt.png"), corrupt)
        write(directoryChapter.resolve("40-extreme.png"), extremeBytes)
        write(directoryChapter.resolve("nested/2-opaque.jpg"), opaque)
        write(directoryChapter.resolve("nested/10-transparent.png"), transparent)

        val commonEntries = listOf(
            "10-opaque.jpg" to opaque,
            "20-transparent.png" to transparent,
            "30-animated.gif" to animated,
            "35-corrupt.png" to corrupt,
            "40-opaque-after-corrupt.jpg" to opaque,
            "nested/2-opaque.jpg" to opaque,
            "nested/10-transparent.png" to transparent,
        )
        writeStoredZip(
            mangaRoot.resolve("02-pages.cbz"),
            commonEntries + ("45-extreme.png" to extremeBytes),
        )
        writeTar(mangaRoot.resolve("03-pages.cbt"), commonEntries)
        writeSevenZ(mangaRoot.resolve("04-pages.cb7"), commonEntries)
        Files.copy(committedRar, mangaRoot.resolve("05-pages.cbr"))
        writeEpub(
            mangaRoot.resolve("06-pages.epub"),
            commonEntries + ("45-extreme.png" to extremeBytes),
        )
        write(normalizedRoot.resolve(STANDALONE_IMAGE), opaque)
        Files.delete(extreme)

        val fixedTime = FileTime.fromMillis(FIXED_EPOCH_MILLIS)
        Files.walk(normalizedRoot).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.setLastModifiedTime(it, fixedTime) }
        }
        val files = Files.walk(normalizedRoot).use { paths ->
            paths.filter(Files::isRegularFile)
                .map { file ->
                    ReaderFixtureFileRecord(
                        relativePath = portable(normalizedRoot.relativize(file)),
                        sizeBytes = Files.size(file),
                        sha256 = sha256(file),
                    )
                }
                .sorted(compareBy(ReaderFixtureFileRecord::relativePath))
                .toList()
        }
        val manifest = ReaderFixtureManifest(
            format = FIXTURE_FORMAT,
            mangaDirectory = MANGA_DIRECTORY,
            standaloneImage = STANDALONE_IMAGE,
            committedRarPath = COMMITTED_RAR_PATH,
            committedRarSha256 = COMMITTED_RAR_SHA256,
            committedSourcePath = COMMITTED_SOURCE_PATH,
            committedSourceSha256 = COMMITTED_SOURCE_SHA256,
            files = files,
        )
        val manifestPath = normalizedRoot.resolve(MANIFEST_NAME)
        Files.writeString(manifestPath, JSON.encodeToString(manifest) + "\n", UTF_8)
        Files.setLastModifiedTime(manifestPath, fixedTime)
        return BuiltReaderFixture(
            root = normalizedRoot,
            files = files,
            manifestSha256 = sha256(manifestPath),
            committedRarSha256 = COMMITTED_RAR_SHA256,
        )
    }

    private fun write(path: Path, bytes: ByteArray) {
        Files.createDirectories(checkNotNull(path.parent))
        Files.write(path, bytes)
    }

    private fun writeStoredZip(path: Path, entries: List<Pair<String, ByteArray>>) {
        Files.newOutputStream(path).use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    val crc = CRC32().apply { update(bytes) }
                    val entry = ZipEntry(name).apply {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        compressedSize = bytes.size.toLong()
                        this.crc = crc.value
                        time = FIXED_EPOCH_MILLIS
                    }
                    zip.putNextEntry(entry)
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
    }

    private fun writeTar(path: Path, entries: List<Pair<String, ByteArray>>) {
        Files.newOutputStream(path).use { output ->
            TarArchiveOutputStream(output).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                entries.forEach { (name, bytes) ->
                    val entry = TarArchiveEntry(name).apply {
                        size = bytes.size.toLong()
                        mode = 0b110100100
                        userId = 0
                        groupId = 0
                        userName = ""
                        groupName = ""
                        setModTime(FIXED_EPOCH_MILLIS)
                    }
                    tar.putArchiveEntry(entry)
                    tar.write(bytes)
                    tar.closeArchiveEntry()
                }
                tar.finish()
            }
        }
    }

    private fun writeSevenZ(path: Path, entries: List<Pair<String, ByteArray>>) {
        SevenZOutputFile(path.toFile()).use { archive ->
            entries.forEach { (name, bytes) ->
                val entry = SevenZArchiveEntry().apply {
                    this.name = name
                    size = bytes.size.toLong()
                    setHasStream(true)
                    setHasLastModifiedDate(true)
                    setLastModifiedTime(FileTime.fromMillis(FIXED_EPOCH_MILLIS))
                }
                archive.putArchiveEntry(entry)
                archive.write(bytes)
                archive.closeArchiveEntry()
            }
        }
    }

    private fun writeEpub(path: Path, imageEntries: List<Pair<String, ByteArray>>) {
        val imageNames = imageEntries.map { (name, _) -> "OPS/images/$name" }
        val xhtmlImages = imageNames.joinToString("") { name ->
            "<img src=\"${name.removePrefix("OPS/")}\"/>"
        }
        val entries = buildList {
            add("mimetype" to "application/epub+zip".toByteArray(UTF_8))
            add("META-INF/container.xml" to CONTAINER_XML.toByteArray(UTF_8))
            add("OPS/book.opf" to OPF_XML.toByteArray(UTF_8))
            add(
                "OPS/chapter.xhtml" to
                    (
                        "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><body>" +
                            "$xhtmlImages</body></html>"
                        ).toByteArray(UTF_8),
            )
            imageEntries.forEach { (name, bytes) -> add("OPS/images/$name" to bytes) }
        }
        writeStoredZip(path, entries)
    }

    private fun opaqueJpeg(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().useGraphics { graphics ->
            graphics.color = java.awt.Color(0x5A, 0x32, 0x19)
            graphics.fillRect(0, 0, width, height)
        }
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }

    private fun transparentPng(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        repeat(height) { y ->
            repeat(width) { x ->
                val alpha = if (x == 0 && y == 0) 0 else 0xff
                image.setRGB(x, y, (alpha shl 24) or 0x224466)
            }
        }
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private fun animatedGif(): ByteArray {
        val palette = IndexColorModel(
            8,
            4,
            byteArrayOf(0, 0xcc.toByte(), 0, 0),
            byteArrayOf(0, 0x22, 0x66, 0),
            byteArrayOf(0, 0, 0xdd.toByte(), 0),
            0,
        )
        val frames = listOf(
            solidIndexed(8, 8, palette, 1),
            solidIndexed(8, 8, palette, 2).also { it.raster.setSample(0, 0, 0, 0) },
        )
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        val output = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(output).use { imageOutput ->
            writer.output = imageOutput
            writer.prepareWriteSequence(null)
            frames.forEachIndexed { index, image ->
                val metadata = writer.getDefaultImageMetadata(
                    ImageTypeSpecifier.createFromRenderedImage(image),
                    writer.defaultWriteParam,
                )
                val root = metadata.getAsTree(GIF_METADATA_FORMAT) as IIOMetadataNode
                val control = root.child("GraphicControlExtension")
                control.setAttribute("disposalMethod", "doNotDispose")
                control.setAttribute("userInputFlag", "FALSE")
                control.setAttribute("transparentColorFlag", if (index == 1) "TRUE" else "FALSE")
                control.setAttribute("delayTime", "5")
                control.setAttribute("transparentColorIndex", "0")
                metadata.setFromTree(GIF_METADATA_FORMAT, root)
                writer.writeToSequence(IIOImage(image, null, metadata), writer.defaultWriteParam)
            }
            writer.endWriteSequence()
        }
        writer.dispose()
        return output.toByteArray()
    }

    private fun solidIndexed(
        width: Int,
        height: Int,
        model: IndexColorModel,
        colorIndex: Int,
    ): BufferedImage = BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, model).also { image ->
        repeat(height) { y -> repeat(width) { x -> image.raster.setSample(x, y, 0, colorIndex) } }
    }

    private fun IIOMetadataNode.child(name: String): IIOMetadataNode {
        for (index in 0 until length) {
            val child = item(index) as IIOMetadataNode
            if (child.nodeName == name) return child
        }
        error("GIF metadata node $name is missing")
    }

    private fun corruptPng(): ByteArray = PNG_SIGNATURE + byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

    private fun writeStreamedGrayscalePng(path: Path, width: Int, height: Int) {
        Files.newOutputStream(path).buffered(1 shl 16).use { output ->
            output.write(PNG_SIGNATURE)
            writeChunk(output, "IHDR", pngHeader(width, height))
            val compressed = ByteArrayOutputStream()
            DeflaterOutputStream(compressed, Deflater(Deflater.BEST_COMPRESSION), 1 shl 16).use { deflater ->
                val row = ByteArray(width + 1)
                repeat(height) { y ->
                    Arrays.fill(row, 1, row.size, (y % 251).toByte())
                    deflater.write(row)
                }
            }
            val bytes = compressed.toByteArray()
            var offset = 0
            while (offset < bytes.size) {
                val length = minOf(1 shl 16, bytes.size - offset)
                writeChunk(output, "IDAT", bytes, offset, length)
                offset += length
            }
            writeChunk(output, "IEND", ByteArray(0))
        }
    }

    private fun pngHeader(width: Int, height: Int): ByteArray =
        ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(width)
            putInt(height)
            put(8)
            put(0)
            put(0)
            put(0)
            put(0)
        }.array()

    private fun writeChunk(
        output: OutputStream,
        type: String,
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size,
    ) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        output.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(length).array())
        output.write(typeBytes)
        output.write(bytes, offset, length)
        val crc = CRC32().apply {
            update(typeBytes)
            update(bytes, offset, length)
        }
        output.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(crc.value.toInt()).array())
    }

    private fun portable(path: Path): String =
        (0 until path.nameCount).joinToString("/") { path.getName(it).toString() }

    private fun repositoryRoot(): Path = generateSequence(
        Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
        Path::getParent,
    ).firstOrNull { candidate ->
        Files.isDirectory(candidate.resolve("reader-core")) && Files.isDirectory(candidate.resolve("desktop-app"))
    } ?: error("cannot locate repository root from user.dir")

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private inline fun java.awt.Graphics2D.useGraphics(block: (java.awt.Graphics2D) -> Unit) {
        try {
            block(this)
        } finally {
            dispose()
        }
    }

    private const val FIXTURE_FORMAT = "mihon-w-reader-fixture-v1"
    private const val MANIFEST_NAME = "reader-fixture-manifest.json"
    private const val MANGA_DIRECTORY = "reader-fixture-manga"
    private const val STANDALONE_IMAGE = "standalone.png"
    private const val COMMITTED_RAR_PATH =
        "reader-core/src/test/resources/mihon/reader/source/fixtures/valid-rar4.rar"
    private const val COMMITTED_RAR_SHA256 =
        "ccbac45f0afbc1bf543b59cefb22fd22c1f6af243721813fb4ffaf1a0cd4b693"
    private const val COMMITTED_SOURCE_PATH = ".superpowers/sdd/task-3-fixture-source/page.png"
    private const val COMMITTED_SOURCE_SHA256 =
        "0197651b31b314f8ee97130efa7427813db8520c65a373a0edc48cda884b0317"
    private const val FIXED_EPOCH_MILLIS = 315_532_800_000L
    private const val EXTREME_SIZE = 20_000
    private const val GIF_METADATA_FORMAT = "javax_imageio_gif_image_1.0"
    private const val CONTAINER_XML = """<?xml version="1.0"?>
<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
<rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""
    private const val OPF_XML = """<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf">
<manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest>
<spine><itemref idref="chapter"/></spine>
</package>"""
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4e,
        0x47,
        0x0d,
        0x0a,
        0x1a,
        0x0a,
    )
    private val JSON = Json {
        encodeDefaults = true
        prettyPrint = true
    }
}
