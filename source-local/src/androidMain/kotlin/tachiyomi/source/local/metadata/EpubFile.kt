package tachiyomi.source.local.metadata

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.storage.EpubFile
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Fills manga and chapter metadata using this epub file's metadata.
 */
fun EpubFile.fillMetadata(manga: SManga, chapter: SChapter) {
    val ref = getPackageHref()
    val doc = getPackageDocument(ref)

    val title = doc.getElementsByTag("dc:title").first()
    val publisher = doc.getElementsByTag("dc:publisher").first()
    val creator = doc.getElementsByTag("dc:creator").first()
    val description = doc.getElementsByTag("dc:description").first()
    var date = doc.getElementsByTag("dc:date").first()
    if (date == null) {
        date = doc.select("meta[property=dcterms:modified]").first()
    }

    creator?.text()?.let { manga.author = it }
    description?.text()?.let { manga.description = it }

    title?.text()?.let { chapter.name = it }

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
}
