package exh.metadata

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.all.PervEden
import exh.metadata.models.ExGalleryMetadata
import exh.metadata.models.PervEdenGalleryMetadata
import exh.metadata.models.SearchableGalleryMetadata
import exh.metadata.models.Tag
import exh.plusAssign
import uy.kohesive.injekt.injectLazy
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

private val prefs: PreferencesHelper by injectLazy()

fun ExGalleryMetadata.copyTo(manga: SManga) {
    //TODO Find some way to do this with SManga
    /*exh?.let {
        manga.source = if(it)
            2
        else
            1
    }*/
    url?.let { manga.url = it }
    thumbnailUrl?.let { manga.thumbnail_url = it }

    //No title bug?
    val titleObj = if(prefs.useJapaneseTitle().getOrDefault())
        altTitles.firstOrNull() ?: title
    else
        title
    titleObj?.let { manga.title = it }

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
    manga.status = SManga.COMPLETED
    title?.let { t ->
        ONGOING_SUFFIX.find {
            t.endsWith(it, ignoreCase = true)
        }?.let {
            manga.status = SManga.ONGOING
        }
    }

    //Build a nice looking description out of what we know
    val titleDesc = StringBuilder()
    title?.let { titleDesc += "Title: $it\n" }
    altTitles.firstOrNull()?.let { titleDesc += "Alternate Title: $it\n" }

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


    val tagsDesc = buildTagsDescription(this)

    manga.description = listOf(titleDesc.toString(), detailsDesc.toString(), tagsDesc.toString())
            .filter(String::isNotBlank)
            .joinToString(separator = "\n")
}

fun PervEdenGalleryMetadata.copyTo(manga: SManga) {
    url?.let { manga.url = it }
    thumbnailUrl?.let { manga.thumbnail_url = it }

    val titleDesc = StringBuilder()
    title?.let {
        manga.title = it
        titleDesc += "Title: $it\n"
    }
    if(altTitles.isNotEmpty())
        titleDesc += "Alternate Titles: \n" + altTitles.map {
            "▪ $it"
        }.joinToString(separator = "\n", postfix = "\n")

    val detailsDesc = StringBuilder()
    artist?.let {
        manga.artist = it
        detailsDesc += "Artist: $it\n"
    }

    type?.let {
        manga.genre = it
        detailsDesc += "Type: $it\n"
    }

    status?.let {
        manga.status = when(it) {
            "Ongoing" -> SManga.ONGOING
            "Completed", "Suspended" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        detailsDesc += "Status: $it\n"
    }

    rating?.let {
        detailsDesc += "Rating: %.2\n".format(it)
    }

    val tagsDesc = buildTagsDescription(this)

    manga.description = listOf(titleDesc.toString(), detailsDesc.toString(), tagsDesc.toString())
            .filter(String::isNotBlank)
            .joinToString(separator = "\n")
}

private fun buildTagsDescription(metadata: SearchableGalleryMetadata)
        = StringBuilder("Tags:\n").apply {
        //BiConsumer only available in Java 8, don't bother calling forEach directly on 'tags'
        metadata.tags.entries.forEach { namespace, tags ->
            if (tags.isNotEmpty()) {
                val joinedTags = tags.joinToString(separator = " ", transform = { "<${it.name}>" })
                this += "▪ $namespace: $joinedTags\n"
            }
        }
    }
