package mihon.core.archive

import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * EPUB writer for creating EPUB 3.0 files.
 * Creates a valid EPUB with proper structure: mimetype, META-INF/container.xml, content.opf, nav.xhtml, and chapter HTML files.
 */
class EpubWriter {

    data class Chapter(
        val title: String,
        val content: String,
        val order: Int = 0,
    )

    data class Metadata(
        val title: String,
        val author: String? = null,
        val description: String? = null,
        val language: String = "en",
        val coverImagePath: String? = null,
        val genres: List<String> = emptyList(),
        val publisher: String? = null,
    )

    /**
     * Write an EPUB file to the given output stream.
     *
     * @param outputStream The output stream to write to
     * @param metadata The book metadata
     * @param chapters The chapters to include
     * @param coverImage Optional cover image bytes
     */
    fun write(
        outputStream: OutputStream,
        metadata: Metadata,
        chapters: List<Chapter>,
        coverImage: ByteArray? = null,
    ) {
        val bookId = UUID.randomUUID().toString()

        ZipOutputStream(outputStream).use { zip ->
            // mimetype must be first entry and stored uncompressed
            writeMimetype(zip)

            // META-INF/container.xml
            writeContainerXml(zip)

            // Cover image (if provided)
            if (coverImage != null) {
                writeEntry(zip, "OEBPS/images/cover.jpg", coverImage)
            }

            // Chapter files
            chapters.forEachIndexed { index, chapter ->
                writeChapter(zip, index, chapter)
            }

            // Navigation document (EPUB 3)
            writeNavDocument(zip, chapters)

            // Package document (content.opf)
            writePackageDocument(zip, metadata, chapters, coverImage != null, bookId)
        }
    }

    private fun writeMimetype(zip: ZipOutputStream) {
        val content = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = content.size.toLong()
            compressedSize = content.size.toLong()
            crc = CRC32().apply { update(content) }.value
        }
        zip.putNextEntry(entry)
        zip.write(content)
        zip.closeEntry()
    }

    private fun writeContainerXml(zip: ZipOutputStream) {
        val content = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
    <rootfiles>
        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
    </rootfiles>
</container>"""
        writeEntry(zip, "META-INF/container.xml", content)
    }

    private fun writeChapter(zip: ZipOutputStream, index: Int, chapter: Chapter) {
        val safeContent = chapter.content
            .replace("&(?!amp;|lt;|gt;|quot;|apos;|#\\d+;|#x[0-9a-fA-F]+;)".toRegex(), "&amp;")

        val content = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="en">
<head>
    <meta charset="UTF-8"/>
    <title>${escapeXml(chapter.title)}</title>
    <style type="text/css">
        body { font-family: serif; line-height: 1.6; margin: 1em; }
        h1, h2, h3 { font-family: sans-serif; }
        p { margin: 0.5em 0; text-indent: 1em; }
        .chapter-title { text-align: center; margin-bottom: 2em; }
    </style>
</head>
<body>
    <h1 class="chapter-title">${escapeXml(chapter.title)}</h1>
    <div class="chapter-content">
        $safeContent
    </div>
</body>
</html>"""
        writeEntry(zip, "OEBPS/chapter${index.toString().padStart(4, '0')}.xhtml", content)
    }

    private fun writeNavDocument(zip: ZipOutputStream, chapters: List<Chapter>) {
        val tocItems = chapters.mapIndexed { index, chapter ->
            val filename = "chapter${index.toString().padStart(4, '0')}.xhtml"
            """            <li><a href="$filename">${escapeXml(chapter.title)}</a></li>"""
        }.joinToString("\n")

        val content = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="en">
<head>
    <meta charset="UTF-8"/>
    <title>Table of Contents</title>
</head>
<body>
    <nav epub:type="toc" id="toc">
        <h1>Table of Contents</h1>
        <ol>
$tocItems
        </ol>
    </nav>
</body>
</html>"""
        writeEntry(zip, "OEBPS/nav.xhtml", content)
    }

    private fun writePackageDocument(
        zip: ZipOutputStream,
        metadata: Metadata,
        chapters: List<Chapter>,
        hasCover: Boolean,
        bookId: String,
    ) {
        val manifestItems = buildString {
            appendLine(
                """        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""",
            )
            if (hasCover) {
                appendLine(
                    """        <item id="cover-image" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>""",
                )
            }
            chapters.forEachIndexed { index, _ ->
                val id = "chapter${index.toString().padStart(4, '0')}"
                appendLine("""        <item id="$id" href="$id.xhtml" media-type="application/xhtml+xml"/>""")
            }
        }.trimEnd()

        val spineItems = chapters.mapIndexed { index, _ ->
            val id = "chapter${index.toString().padStart(4, '0')}"
            """        <itemref idref="$id"/>"""
        }.joinToString("\n")

        val genresMetadata = metadata.genres.joinToString("\n") { genre ->
            """        <dc:subject>${escapeXml(genre)}</dc:subject>"""
        }

        val content = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="BookId">
    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
        <dc:identifier id="BookId">urn:uuid:$bookId</dc:identifier>
        <dc:title>${escapeXml(metadata.title)}</dc:title>
        <dc:language>${metadata.language}</dc:language>
${metadata.author?.let { """        <dc:creator>${escapeXml(it)}</dc:creator>""" } ?: ""}
${metadata.description?.let { """        <dc:description>${escapeXml(it)}</dc:description>""" } ?: ""}
${metadata.publisher?.let { """        <dc:publisher>${escapeXml(it)}</dc:publisher>""" } ?: ""}
$genresMetadata
        <meta property="dcterms:modified">${java.time.Instant.now().toString().substringBefore('.') + "Z"}</meta>
    </metadata>
    <manifest>
$manifestItems
    </manifest>
    <spine>
$spineItems
    </spine>
</package>"""
        writeEntry(zip, "OEBPS/content.opf", content)
    }

    private fun writeEntry(zip: ZipOutputStream, path: String, content: String) {
        writeEntry(zip, path, content.toByteArray(Charsets.UTF_8))
    }

    private fun writeEntry(zip: ZipOutputStream, path: String, content: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content)
        zip.closeEntry()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    companion object {
        /**
         * Convenience method to write EPUB to a file.
         */
        fun writeToFile(
            file: File,
            metadata: Metadata,
            chapters: List<Chapter>,
            coverImage: ByteArray? = null,
        ) {
            file.outputStream().use { outputStream ->
                EpubWriter().write(outputStream, metadata, chapters, coverImage)
            }
        }
    }
}
