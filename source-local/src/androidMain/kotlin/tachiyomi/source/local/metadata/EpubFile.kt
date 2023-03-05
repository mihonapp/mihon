package tachiyomi.source.local.metadata

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.storage.EpubFile
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Fills manga metadata using this epub file's metadata.
 */
fun EpubFile.fillMangaMetadata(manga: SManga) {
    val ref = getPackageHref()
    val doc = getPackageDocument(ref)

    val creator = doc.getElementsByTag("dc:creator").first()
    val description = doc.getElementsByTag("dc:description").first()

    manga.author = creator?.text()
    manga.description = description?.text()
}

/**
 * Fills chapter metadata using this epub file's metadata.
 */
fun EpubFile.fillChapterMetadata(chapter: SChapter) {
    val ref = getPackageHref()
    val doc = getPackageDocument(ref)

    val title = doc.getElementsByTag("dc:title").first()
    val publisher = doc.getElementsByTag("dc:publisher").first()
    val creator = doc.getElementsByTag("dc:creator").first()
    var date = doc.getElementsByTag("dc:date").first()
    if (date == null) {
        date = doc.select("meta[property=dcterms:modified]").first()
    }

    if (title != null) {
        chapter.name = title.text()
    }

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
