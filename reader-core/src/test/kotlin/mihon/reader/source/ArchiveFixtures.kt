package mihon.reader.source

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object ArchiveFixtures {
    val pageBytes = "safe-page-bytes".toByteArray()

    fun asset(root: Path, relative: String, kind: String = "ARCHIVE") = ReaderChapterAsset(
        mangaId = 1,
        chapterId = 2,
        mangaTitle = "Manga",
        chapterName = "Chapter",
        storageRoot = root,
        relativePath = Path.of(relative),
        assetKind = kind,
        sizeBytes = 0,
        modifiedAt = 0,
        lastPageRead = 0,
        read = false,
    )

    fun writeZip(path: Path, entries: List<Pair<String, ByteArray>>) {
        Files.newOutputStream(path).use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
    }

    fun writeZipWithEmptyEntries(path: Path, count: Int) {
        Files.newOutputStream(path).use { output ->
            ZipOutputStream(output).use { zip ->
                repeat(count) { index ->
                    zip.putNextEntry(ZipEntry("$index.png"))
                    zip.closeEntry()
                }
            }
        }
    }

    fun forgeZipDeclaredSize(path: Path, declaredSize: Int) {
        val bytes = Files.readAllBytes(path)
        val centralOffset = (0..bytes.size - 4).lastOrNull { index ->
            bytes[index] == 0x50.toByte() &&
                bytes[index + 1] == 0x4B.toByte() &&
                bytes[index + 2] == 0x01.toByte() &&
                bytes[index + 3] == 0x02.toByte()
        } ?: error("ZIP central directory not found")
        ByteBuffer.wrap(bytes, centralOffset + 24, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(declaredSize)
        Files.write(path, bytes)
    }

    fun writeUnixZip(path: Path, name: String, bytes: ByteArray, unixMode: Int) {
        ZipArchiveOutputStream(path).use { zip ->
            val entry = ZipArchiveEntry(name).apply { setUnixMode(unixMode) }
            zip.putArchiveEntry(entry)
            zip.write(bytes)
            zip.closeArchiveEntry()
        }
    }

    fun writeTar(path: Path, entries: List<Pair<TarArchiveEntry, ByteArray>>) {
        Files.newOutputStream(path).use { output ->
            TarArchiveOutputStream(output).use { tar ->
                entries.forEach { (entry, bytes) ->
                    entry.size = bytes.size.toLong()
                    tar.putArchiveEntry(entry)
                    tar.write(bytes)
                    tar.closeArchiveEntry()
                }
            }
        }
    }

    fun writeCompressedTar(
        path: Path,
        compression: TarCompression,
        entries: List<Pair<TarArchiveEntry, ByteArray>>,
    ) {
        Files.newOutputStream(path).use { file ->
            val compressed: OutputStream = when (compression) {
                TarCompression.NONE -> file
                TarCompression.GZIP -> GzipCompressorOutputStream(file)
                TarCompression.BZIP2 -> BZip2CompressorOutputStream(file)
                TarCompression.XZ -> XZCompressorOutputStream(file)
            }
            compressed.use { output ->
                TarArchiveOutputStream(output).use { tar ->
                    entries.forEach { (entry, bytes) ->
                        entry.size = bytes.size.toLong()
                        tar.putArchiveEntry(entry)
                        tar.write(bytes)
                        tar.closeArchiveEntry()
                    }
                }
            }
        }
    }

    fun writeSevenZ(path: Path, entries: List<Pair<String, ByteArray>>) {
        SevenZOutputFile(path.toFile()).use { archive ->
            entries.forEach { (name, bytes) ->
                val entry = SevenZArchiveEntry().apply {
                    this.name = name
                    size = bytes.size.toLong()
                    setHasStream(true)
                }
                archive.putArchiveEntry(entry)
                archive.write(bytes)
                archive.closeArchiveEntry()
            }
        }
    }

    fun writeSevenZSpecial(path: Path, name: String, unixMode: Int) {
        SevenZOutputFile(path.toFile()).use { archive ->
            val entry = SevenZArchiveEntry().apply {
                this.name = name
                size = 1
                setHasStream(true)
                setHasWindowsAttributes(true)
                windowsAttributes = unixMode shl 16
            }
            archive.putArchiveEntry(entry)
            archive.write(byteArrayOf(1))
            archive.closeArchiveEntry()
        }
    }

    fun copyCommittedFixture(root: Path, name: String): Path {
        val destination = root.resolve(name)
        val resource =
            requireNotNull(ArchiveFixtures::class.java.getResourceAsStream("/mihon/reader/source/fixtures/$name"))
        resource.use { Files.copy(it, destination) }
        return destination
    }

    fun writeEpub(
        path: Path,
        containerXml: String = DEFAULT_CONTAINER,
        opf: String = DEFAULT_OPF,
        xhtml: String = DEFAULT_XHTML,
    ) {
        writeZip(
            path,
            listOf(
                "mimetype" to "application/epub+zip".toByteArray(),
                "META-INF/container.xml" to containerXml.toByteArray(),
                "OPS/book.opf" to opf.toByteArray(),
                "OPS/chapter.xhtml" to xhtml.toByteArray(),
                "OPS/images/2.png" to "two".toByteArray(),
                "OPS/images/10.png" to "ten".toByteArray(),
            ),
        )
    }

    const val DEFAULT_CONTAINER = """<?xml version="1.0"?>
        <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles>
        </container>"""

    const val DEFAULT_OPF = """<?xml version="1.0"?>
        <package xmlns="http://www.idpf.org/2007/opf">
          <manifest><item id="c1" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest>
          <spine><itemref idref="c1"/></spine>
        </package>"""

    const val DEFAULT_XHTML = """<?xml version="1.0"?>
        <html xmlns="http://www.w3.org/1999/xhtml"><body>
          <img src="images/10.png"/><img src="images/2.png"/>
        </body></html>"""
}
