package exh.metadata.metadata

import android.net.Uri
import eu.kanade.tachiyomi.source.model.SManga
import exh.PERV_EDEN_EN_SOURCE_ID
import exh.PERV_EDEN_IT_SOURCE_ID
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTitle
import exh.plusAssign

class PervEdenSearchMetadata : RaisedSearchMetadata() {
    var pvId: String? = null

    var url: String? = null
    var thumbnailUrl: String? = null

    var title by titleDelegate(TITLE_TYPE_MAIN)
    var altTitles
        get() = titles.filter { it.type == TITLE_TYPE_ALT }.map { it.title }
        set(value) {
            titles.removeAll { it.type == TITLE_TYPE_ALT }
            titles += value.map { RaisedTitle(it, TITLE_TYPE_ALT) }
        }

    var artist: String? = null

    var type: String? = null

    var rating: Float? = null

    var status: String? = null

    var lang: String? = null

    override fun copyTo(manga: SManga) {
        url?.let { manga.url = it }
        thumbnailUrl?.let { manga.thumbnail_url = it }

        val titleDesc = StringBuilder()
        title?.let {
            manga.title = it
            titleDesc += "Title: $it\n"
        }
        if(altTitles.isNotEmpty())
            titleDesc += "Alternate Titles: \n" + altTitles
                    .joinToString(separator = "\n", postfix = "\n") {
                "▪ $it"
            }

        val detailsDesc = StringBuilder()
        artist?.let {
            manga.artist = it
            detailsDesc += "Artist: $it\n"
        }

        type?.let {
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

        //Copy tags -> genres
        manga.genre = tagsToGenreString()

        val tagsDesc = tagsToDescription()

        manga.description = listOf(titleDesc.toString(), detailsDesc.toString(), tagsDesc.toString())
                .filter(String::isNotBlank)
                .joinToString(separator = "\n")
    }


    companion object {
        private const val TITLE_TYPE_MAIN = 0
        private const val TITLE_TYPE_ALT = 1

        const val TAG_TYPE_DEFAULT = 0

        private fun splitGalleryUrl(url: String)
                = url.let {
            Uri.parse(it).pathSegments.filterNot(String::isNullOrBlank)
        }

        fun pvIdFromUrl(url: String) = splitGalleryUrl(url).last()
    }
}

enum class PervEdenLang(val id: Long) {
    //DO NOT RENAME THESE TO CAPITAL LETTERS! The enum names are used to build URLs
    en(PERV_EDEN_EN_SOURCE_ID),
    it(PERV_EDEN_IT_SOURCE_ID);

    companion object {
        fun source(id: Long)
                = values().find { it.id == id }
                ?: throw IllegalArgumentException("Unknown source ID: $id!")
    }
}
