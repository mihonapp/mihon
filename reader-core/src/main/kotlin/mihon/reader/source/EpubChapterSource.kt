package mihon.reader.source

import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Locale

class EpubChapterSource(
    asset: ReaderChapterAsset,
    private val securePath: SecureLocalPath = SecureLocalPath(asset.storageRoot),
    expansionBudget: ChapterExpansionBudget = ChapterExpansionBudget(),
) : ManagedChapterSource(asset, expansionBudget) {
    private val entries: List<SourceEntry> = enumerateSpineImages()

    override suspend fun pages(): List<PageDescriptor> {
        checkOpenAndCancellation()
        return entries.map { it.descriptor(asset) }
    }

    override suspend fun open(pageId: PageId): BoundedPageInput {
        checkOpenAndCancellation()
        val wanted = requireEntry(pageId, entries)
        val archive = openZip()
        try {
            val entry = archive.entries.asSequence().singleOrNull { it.name == wanted.rawName }
                ?: throw ReaderFailure.PageNotFound(wanted.logicalName)
            validateZipEntry(entry)
            if (!archive.canReadEntryData(entry)) throw ReaderFailure.UnsupportedFormat("ZIP entry compression")
            return boundedInput(archive.getInputStream(entry), entry.size) { archive.close() }
        } catch (error: Throwable) {
            archive.close()
            throw classifyZipFailure(error)
        }
    }

    private fun enumerateSpineImages(): List<SourceEntry> {
        val archive = openZip()
        return try {
            val allEntries = validateAndIndex(archive)
            val containerName = "META-INF/container.xml"
            val containerEntry = allEntries[ImageEntryPolicy.duplicateKey(containerName)]
                ?: throw ReaderFailure.CorruptContainer("EPUB")
            val container = parseEntry(archive, containerEntry, containerName)
            val opfReference = container.elements("rootfile")
                .firstOrNull()
                ?.getAttribute("full-path")
                ?.takeIf { it.isNotBlank() }
                ?: throw ReaderFailure.XmlRejected(containerName)
            val opfName = resolveReference("", opfReference)
            val opfEntry = allEntries[ImageEntryPolicy.duplicateKey(opfName)]
                ?: throw ReaderFailure.CorruptContainer("EPUB")
            val opf = parseEntry(archive, opfEntry, opfName)
            buildSpine(archive, allEntries, opfName, opf)
        } catch (error: ReaderFailure) {
            throw error
        } catch (error: Throwable) {
            throw ReaderFailure.CorruptContainer("EPUB", error)
        } finally {
            archive.close()
        }
    }

    private fun validateAndIndex(archive: ZipFile): Map<String, ZipArchiveEntry> {
        val indexed = linkedMapOf<String, ZipArchiveEntry>()
        var count = 0
        archive.entries.asSequence().forEach { entry ->
            count += 1
            if (count > ReaderLimits.MAX_ENTRIES) throw ReaderFailure.TooManyEntries(ReaderLimits.MAX_ENTRIES)
            validateZipEntry(entry)
            if (!archive.canReadEntryData(entry)) throw ReaderFailure.UnsupportedFormat("ZIP entry compression")
            val normalized = ImageEntryPolicy.normalize(entry.name)
            val key = ImageEntryPolicy.duplicateKey(normalized)
            if (indexed.put(key, entry) != null) throw ReaderFailure.DuplicateEntry(normalized)
        }
        return indexed
    }

    private fun buildSpine(
        archive: ZipFile,
        allEntries: Map<String, ZipArchiveEntry>,
        opfName: String,
        opf: Document,
    ): List<SourceEntry> {
        val manifest = opf.elements("item").associateBy { it.getAttribute("id") }
        val result = mutableListOf<SourceEntry>()
        val emitted = mutableSetOf<String>()
        opf.elements("itemref").forEach { itemRef ->
            val item = manifest[itemRef.getAttribute("idref")] ?: throw ReaderFailure.XmlRejected(opfName)
            val mediaType = item.getAttribute("media-type").lowercase(Locale.ROOT)
            val itemName = resolveReference(parentOf(opfName), item.getAttribute("href"))
            when {
                mediaType.startsWith("image/") -> addImage(allEntries, itemName, result, emitted)
                mediaType == "application/xhtml+xml" || mediaType == "text/html" -> {
                    val xhtmlEntry = allEntries[ImageEntryPolicy.duplicateKey(itemName)]
                        ?: throw ReaderFailure.CorruptContainer("EPUB")
                    val xhtml = parseEntry(archive, xhtmlEntry, itemName)
                    xhtml.elementsInDocumentOrder().forEach { element ->
                        listOf(
                            element.getAttribute("src"),
                            element.getAttribute("href"),
                            element.getAttributeNS("http://www.w3.org/1999/xlink", "href"),
                        ).filter(String::isNotBlank).forEach { reference ->
                            validateLocalReference(parentOf(itemName), reference)
                        }
                        val localName = element.localName ?: element.tagName.substringAfter(':')
                        val reference = when (localName.lowercase(Locale.ROOT)) {
                            "img" -> element.getAttribute("src")
                            "image" -> element.getAttribute("href").ifBlank {
                                element.getAttributeNS("http://www.w3.org/1999/xlink", "href")
                            }
                            else -> ""
                        }
                        if (reference.isNotBlank()) {
                            val imageName = resolveReference(parentOf(itemName), reference)
                            addImage(allEntries, imageName, result, emitted)
                        }
                    }
                }
            }
        }
        return result
    }

    private fun addImage(
        allEntries: Map<String, ZipArchiveEntry>,
        logicalName: String,
        result: MutableList<SourceEntry>,
        emitted: MutableSet<String>,
    ) {
        if (!ImageEntryPolicy.isSupportedImage(logicalName)) throw ReaderFailure.UnsupportedFormat("EPUB image")
        val key = ImageEntryPolicy.duplicateKey(logicalName)
        val entry = allEntries[key] ?: throw ReaderFailure.CorruptContainer("EPUB")
        if (emitted.add(key)) result += SourceEntry(logicalName, entry.name, entry.size)
    }

    private fun parseEntry(archive: ZipFile, entry: ZipArchiveEntry, documentName: String): Document =
        archive.getInputStream(entry).use { SecureXml.parse(documentName, it) }

    private fun openZip(): ZipFile {
        val channel = securePath.openRegularFile(asset.relativePath)
        return try {
            ZipFile.builder().setSeekableByteChannel(channel).get()
        } catch (error: Throwable) {
            channel.close()
            throw classifyZipFailure(error)
        }
    }

    companion object {
        fun looksLikeEpub(asset: ReaderChapterAsset, securePath: SecureLocalPath): Boolean {
            val channel = securePath.openRegularFile(asset.relativePath)
            val archive = try {
                ZipFile.builder().setSeekableByteChannel(channel).get()
            } catch (error: Throwable) {
                channel.close()
                throw classifyZipFailure(error)
            }
            return try {
                val mimetype = archive.getEntry("mimetype")
                val hasContainer = archive.getEntry("META-INF/container.xml") != null
                val typeMatches = mimetype?.let {
                    validateZipEntry(it)
                    archive.getInputStream(it).use { input ->
                        val bytes = input.readNBytes(65)
                        bytes.size <= 64 && bytes.toString(Charsets.US_ASCII).trim() == "application/epub+zip"
                    }
                } ?: false
                typeMatches && hasContainer
            } finally {
                archive.close()
            }
        }
    }
}

private fun Document.elements(localName: String): List<Element> =
    buildList {
        val nodes = getElementsByTagNameNS("*", localName)
        for (index in 0 until nodes.length) add(nodes.item(index) as Element)
    }

private fun Document.elementsInDocumentOrder(): List<Element> =
    buildList {
        val nodes = getElementsByTagName("*")
        for (index in 0 until nodes.length) add(nodes.item(index) as Element)
    }

private fun parentOf(name: String): String = name.substringBeforeLast('/', missingDelimiterValue = "")

private fun resolveReference(base: String, rawReference: String): String {
    val trimmed = rawReference.trim()
    if (trimmed.isEmpty()) throw ReaderFailure.UnsafePath(rawReference, "empty EPUB reference")
    val uri = try {
        URI(trimmed)
    } catch (error: Exception) {
        throw ReaderFailure.UnsafePath(rawReference, "invalid EPUB URI")
    }
    if (uri.isAbsolute || uri.rawAuthority != null || trimmed.startsWith("//") || trimmed.startsWith('\\')) {
        throw ReaderFailure.UnsafePath(rawReference, "external EPUB reference")
    }
    val rawPath = uri.rawPath ?: throw ReaderFailure.UnsafePath(rawReference, "non-local EPUB reference")
    val decoded = try {
        URLDecoder.decode(rawPath.replace("+", "%2B"), StandardCharsets.UTF_8)
    } catch (error: Exception) {
        throw ReaderFailure.UnsafePath(rawReference, "invalid percent encoding")
    }
    if (decoded.startsWith('/') || decoded.startsWith('\\') || Regex("^[A-Za-z]:").containsMatchIn(decoded)) {
        throw ReaderFailure.UnsafePath(rawReference, "absolute EPUB reference")
    }
    val resolved: Path = Path.of(base.ifEmpty { "." }).resolve(decoded.replace('\\', '/')).normalize()
    if (resolved.isAbsolute || resolved.startsWith("..")) {
        throw ReaderFailure.UnsafePath(rawReference, "EPUB path escape")
    }
    return ImageEntryPolicy.normalize(resolved.toString())
}

private fun validateLocalReference(base: String, rawReference: String) {
    val uri = try {
        URI(rawReference.trim())
    } catch (error: Exception) {
        throw ReaderFailure.UnsafePath(rawReference, "invalid EPUB URI")
    }
    if (uri.isAbsolute || uri.rawAuthority != null || rawReference.startsWith("//") || rawReference.startsWith('\\')) {
        throw ReaderFailure.UnsafePath(rawReference, "external EPUB reference")
    }
    if (!uri.rawPath.isNullOrEmpty()) resolveReference(base, rawReference)
}
