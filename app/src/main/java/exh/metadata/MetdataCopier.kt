package exh.metadata

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.util.UrlUtil
import exh.metadata.models.ExGalleryMetadata
import exh.metadata.models.Tag
import exh.plusAssign
import java.text.SimpleDateFormat
import java.util.*

/**
 * Copies gallery metadata to a manga object
 */

private const val ARTIST_NAMESPACE = "artist"
private const val AUTHOR_NAMESPACE = "author"

private val ONGOING_SUFFIX = arrayOf(
        "[ongoing]",
        "(ongoing)",
        "{ongoing}"
)

val EX_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

fun ExGalleryMetadata.copyTo(manga: Manga) {
    exh?.let {
        manga.source = if(it)
            2
        else
            1
    }
    url?.let { manga.url = it }
    thumbnailUrl?.let { manga.thumbnail_url = it }
    title?.let { manga.title = it }

    //Set artist (if we can find one)
    tags[ARTIST_NAMESPACE]?.let {
        if(it.isNotEmpty()) manga.artist = it.joinToString(transform = Tag::name)
    }
    //Set author (if we can find one)
    tags[AUTHOR_NAMESPACE]?.let {
        if(it.isNotEmpty()) manga.author = it.joinToString(transform = Tag::name)
    }
    //Set genre
    genre?.let { manga.genre = it }

    //Try to automatically identify if it is ongoing, we try not to be too lenient here to avoid making mistakes
    //We default to completed
    manga.status = Manga.COMPLETED
    title?.let { t ->
        ONGOING_SUFFIX.find {
            t.endsWith(it, ignoreCase = true)
        }?.let {
            manga.status = Manga.ONGOING
        }
    }

    //Build a nice looking description out of what we know
    val titleDesc = StringBuilder()
    title?.let { titleDesc += "Title: $it\n" }
    altTitle?.let { titleDesc += "Japanese Title: $it\n" }

    val detailsDesc = StringBuilder()
    uploader?.let { detailsDesc += "Uploader: $it\n" }
    datePosted?.let { detailsDesc += "Posted: ${EX_DATE_FORMAT.format(Date(it))}\n" }
    visible?.let { detailsDesc += "Visible: $it\n" }
    language?.let {
        detailsDesc += "Language: $it"
        if(translated == true) detailsDesc += " TR"
        detailsDesc += "\n"
    }
    size?.let { detailsDesc += "File Size: ${humanReadableByteCount(it, true)}\n" }
    length?.let { detailsDesc += "Length: $it pages\n" }
    favorites?.let { detailsDesc += "Favorited: $it times\n" }
    averageRating?.let {
        detailsDesc += "Rating: $it"
        ratingCount?.let { detailsDesc += " ($it)" }
        detailsDesc += "\n"
    }

    val tagsDesc = StringBuilder("Tags:\n")
    //BiConsumer only available in Java 8, don't bother calling forEach directly on 'tags'
    tags.entries.forEach { namespace, tags ->
        if(tags.isNotEmpty()) {
            val joinedTags = tags.joinToString(separator = " ", transform = { "<${it.name}>" })
            tagsDesc += "▪ $namespace: $joinedTags\n"
        }
    }

    manga.description = listOf(titleDesc, detailsDesc, tagsDesc)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
}
