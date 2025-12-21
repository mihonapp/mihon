package mihon.core.archive

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.Closeable
import java.io.File
import java.io.InputStream

/**
 * Wrapper over ArchiveReader to load files in epub format.
 */
class EpubReader(private val reader: ArchiveReader) : Closeable by reader {

    /**
     * Path separator used by this epub.
     */
    private val pathSeparator = getPathSeparator()

    /**
     * Returns an input stream for reading the contents of the specified zip file entry.
     */
    fun getInputStream(entryName: String): InputStream? {
        return reader.getInputStream(entryName)
    }

    /**
     * Returns the path of all the images found in the epub file.
     */
    fun getImagesFromPages(): List<String> {
        val ref = getPackageHref()
        val doc = getPackageDocument(ref)
        val pages = getPagesFromDocument(doc)
        return getImagesFromPages(pages, ref)
    }

    /**
     * Returns the path to the package document.
     */
    fun getPackageHref(): String {
        val meta = getInputStream(resolveZipPath("META-INF", "container.xml"))
        if (meta != null) {
            val metaDoc = meta.use { Jsoup.parse(it, null, "", Parser.xmlParser()) }
            val path = metaDoc.getElementsByTag("rootfile").first()?.attr("full-path")
            if (path != null) {
                return path
            }
        }
        return resolveZipPath("OEBPS", "content.opf")
    }

    /**
     * Returns the package document where all the files are listed.
     */
    fun getPackageDocument(ref: String): Document {
        return getInputStream(ref)!!.use { Jsoup.parse(it, null, "", Parser.xmlParser()) }
    }

    /**
     * Returns all the pages from the epub.
     */
    fun getPagesFromDocument(document: Document): List<String> {
        val pages = document.select("manifest > item")
            .filter { node -> "application/xhtml+xml" == node.attr("media-type") }
            .associateBy { it.attr("id") }

        val spine = document.select("spine > itemref").map { it.attr("idref") }
        return spine.mapNotNull { pages[it] }.map { it.attr("href") }
    }

    /**
     * Returns all the images contained in every page from the epub.
     */
    private fun getImagesFromPages(pages: List<String>, packageHref: String): List<String> {
        val result = mutableListOf<String>()
        val basePath = getParentDirectory(packageHref)
        pages.forEach { page ->
            val entryPath = resolveZipPath(basePath, page)
            val document = getInputStream(entryPath)!!.use { Jsoup.parse(it, null, "") }
            val imageBasePath = getParentDirectory(entryPath)

            document.allElements.forEach {
                when (it.tagName()) {
                    "img" -> result.add(resolveZipPath(imageBasePath, it.attr("src")))
                    "image" -> result.add(resolveZipPath(imageBasePath, it.attr("xlink:href")))
                }
            }
        }

        return result
    }

    /**
     * Returns the path separator used by the epub file.
     */
    private fun getPathSeparator(): String {
        val meta = getInputStream("META-INF\\container.xml")
        return if (meta != null) {
            meta.close()
            "\\"
        } else {
            "/"
        }
    }

    /**
     * Resolves a zip path from base and relative components and a path separator.
     */
    private fun resolveZipPath(basePath: String, relativePath: String): String {
        if (relativePath.startsWith(pathSeparator)) {
            // Path is absolute, so return as-is.
            return relativePath
        }

        var fixedBasePath = basePath.replace(pathSeparator, File.separator)
        if (!fixedBasePath.startsWith(File.separator)) {
            fixedBasePath = "${File.separator}$fixedBasePath"
        }

        val fixedRelativePath = relativePath.replace(pathSeparator, File.separator)
        val resolvedPath = File(fixedBasePath, fixedRelativePath).canonicalPath
        return resolvedPath.replace(File.separator, pathSeparator).substring(1)
    }

    /**
     * Gets the parent directory of a path.
     */
    private fun getParentDirectory(path: String): String {
        val separatorIndex = path.lastIndexOf(pathSeparator)
        return if (separatorIndex >= 0) {
            path.substring(0, separatorIndex)
        } else {
            ""
        }
    }

    /**
     * Returns the text content of all pages in the epub as HTML.
     */
    fun getTextContent(): String {
        val ref = getPackageHref()
        val doc = getPackageDocument(ref)
        val pages = getPagesFromDocument(doc)
        val basePath = getParentDirectory(ref)

        val content = StringBuilder()
        pages.forEach { page ->
            val entryPath = resolveZipPath(basePath, page)
            getInputStream(entryPath)?.use { inputStream ->
                val document = Jsoup.parse(inputStream, null, "")
                // Get body content, preserving HTML structure for proper rendering
                document.body()?.let { body ->
                    content.append(body.html())
                    content.append("\n\n")
                }
            }
        }

        return content.toString()
    }

    /**
     * Data class representing an EPUB chapter/section from TOC.
     */
    data class EpubChapter(
        val title: String,
        val href: String,
        val order: Int,
    )

    /**
     * Returns the table of contents (chapters) from the EPUB.
     * Tries EPUB 3 NAV first, then falls back to EPUB 2 NCX.
     */
    fun getTableOfContents(): List<EpubChapter> {
        val ref = getPackageHref()
        val doc = getPackageDocument(ref)
        val basePath = getParentDirectory(ref)

        // Try EPUB 3 NAV first
        val navHref = doc.select("manifest > item[properties*=nav]").attr("href")
        if (navHref.isNotEmpty()) {
            val navChapters = parseEpub3Nav(resolveZipPath(basePath, navHref))
            if (navChapters.isNotEmpty()) return navChapters
        }

        // Fall back to EPUB 2 NCX
        val ncxHref = doc.select("manifest > item[media-type='application/x-dtbncx+xml']").attr("href")
        if (ncxHref.isNotEmpty()) {
            val ncxChapters = parseEpub2Ncx(resolveZipPath(basePath, ncxHref))
            if (ncxChapters.isNotEmpty()) return ncxChapters
        }

        // If no TOC found, create chapters from spine pages
        val pages = getPagesFromDocument(doc)
        return pages.mapIndexed { index, page ->
            EpubChapter(
                title = "Chapter ${index + 1}",
                href = page,
                order = index,
            )
        }
    }

    /**
     * Parse EPUB 3 NAV document for TOC.
     */
    private fun parseEpub3Nav(navPath: String): List<EpubChapter> {
        val chapters = mutableListOf<EpubChapter>()
        getInputStream(navPath)?.use { inputStream ->
            val doc = Jsoup.parse(inputStream, null, "", Parser.xmlParser())
            // EPUB 3 NAV uses <nav epub:type="toc"> or <nav id="toc">
            val navElement = doc.selectFirst("nav[*|type=toc], nav#toc, nav[epub\\:type=toc]")
            navElement?.select("li a")?.forEachIndexed { index, element ->
                val title = element.text().trim()
                val href = element.attr("href").substringBefore("#") // Remove fragment
                if (title.isNotEmpty() && href.isNotEmpty()) {
                    chapters.add(EpubChapter(title, href, index))
                }
            }
        }
        return chapters
    }

    /**
     * Parse EPUB 2 NCX document for TOC.
     */
    private fun parseEpub2Ncx(ncxPath: String): List<EpubChapter> {
        val chapters = mutableListOf<EpubChapter>()
        getInputStream(ncxPath)?.use { inputStream ->
            val doc = Jsoup.parse(inputStream, null, "", Parser.xmlParser())
            doc.select("navPoint").forEachIndexed { index, navPoint ->
                val title = navPoint.selectFirst("navLabel > text")?.text()?.trim() ?: ""
                val href = navPoint.selectFirst("content")?.attr("src")?.substringBefore("#") ?: ""
                if (title.isNotEmpty() && href.isNotEmpty()) {
                    chapters.add(EpubChapter(title, href, index))
                }
            }
        }
        return chapters
    }

    /**
     * Returns the text content of a specific chapter/page from the EPUB.
     * @param chapterHref The relative path to the chapter within the EPUB
     */
    fun getChapterContent(chapterHref: String): String {
        val ref = getPackageHref()
        val basePath = getParentDirectory(ref)
        val entryPath = resolveZipPath(basePath, chapterHref)

        return getInputStream(entryPath)?.use { inputStream ->
            val document = Jsoup.parse(inputStream, null, "")
            document.body()?.html() ?: ""
        } ?: ""
    }
}
