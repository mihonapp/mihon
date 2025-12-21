package tachiyomi.source.local.metadata

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import mihon.core.archive.EpubReader
import org.jsoup.Jsoup
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Fills manga and chapter metadata using this epub file's metadata.
 */
fun EpubReader.fillMetadata(manga: SManga, chapter: SChapter) {
    val ref = getPackageHref()
    val doc = getPackageDocument(ref)

    var title = doc.getElementsByTag("dc:title").firstOrNull()?.text()

    // Fallback: try to get title from docTitle metadata
    if (title.isNullOrBlank()) {
        title = doc.select("docTitle").firstOrNull()?.text()
    }

    // Fallback: try to get title from meta name="title"
    if (title.isNullOrBlank()) {
        title = doc.select("meta[name=title]").firstOrNull()?.attr("content")
    }

    val publisher = doc.getElementsByTag("dc:publisher").firstOrNull()
    val creator = doc.getElementsByTag("dc:creator").firstOrNull()
    var description = doc.getElementsByTag("dc:description").firstOrNull()?.text()

    var date = doc.getElementsByTag("dc:date").firstOrNull()
    if (date == null) {
        date = doc.select("meta[property=dcterms:modified]").firstOrNull()
    }

    creator?.text()?.let { manga.author = it }
    description?.let { if (it.isNotBlank()) manga.description = it }

    title?.let { if (it.isNotBlank()) chapter.name = it }

    if (publisher != null) {
        chapter.scanlator = publisher.text()
    } else if (creator != null) {
        chapter.scanlator = creator.text()
    }

    if (date != null) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
        try {
            val parsedDate = dateFormat.parse(date.text())
            if (parsedDate != null) {
                chapter.date_upload = parsedDate.time
            }
        } catch (e: ParseException) {
            // Empty
        }
    }

    // Extract and set cover image
    extractCoverUrl(manga, doc, ref)
}

/**
 * Extracts the cover image from the EPUB and sets it as thumbnail.
 */
private fun EpubReader.extractCoverUrl(manga: SManga, doc: org.jsoup.nodes.Document, packageRef: String) {
    try {
        // Try to find cover via manifest cover property
        var coverId = doc.select("meta[name=cover]").firstOrNull()?.attr("content")

        // Fallback: look for cover-image id in manifest
        if (coverId.isNullOrBlank()) {
            coverId = doc.select("manifest > item[properties*=cover-image]").firstOrNull()?.attr("id")
        }

        // If we found a cover id, get its href
        if (!coverId.isNullOrBlank()) {
            val coverHref = doc.select("manifest > item#$coverId").firstOrNull()?.attr("href")
            if (!coverHref.isNullOrBlank()) {
                // Store the cover href as thumbnail URL (will be extracted during import)
                manga.thumbnail_url = coverHref
                return
            }
        }

        // Fallback: look for cover in spine (first image in first chapter)
        val pages = getPagesFromDocument(doc)
        if (pages.isNotEmpty()) {
            val coverImages = getImagesFromPages()
            if (coverImages.isNotEmpty()) {
                manga.thumbnail_url = coverImages.first()
            }
        }
    } catch (e: Exception) {
        // Continue without cover
    }
}
