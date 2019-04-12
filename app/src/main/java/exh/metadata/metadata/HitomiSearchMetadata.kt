package exh.metadata.metadata

import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.EX_DATE_FORMAT
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.plusAssign
import java.util.*

class HitomiSearchMetadata: RaisedSearchMetadata() {
    var url get() = hlId?.let { urlFromHlId(it) }
        set(a) {
            a?.let {
                hlId = hlIdFromUrl(a)
            }
        }

    var hlId: String? = null

    var title by titleDelegate(TITLE_TYPE_MAIN)

    var thumbnailUrl: String? = null

    var artists: List<String> = emptyList()

    var group: String? = null

    var type: String? = null

    var language: String? = null

    var series: List<String> = emptyList()

    var characters: List<String> = emptyList()

    var uploadDate: Long? = null

    override fun copyTo(manga: SManga) {
        thumbnailUrl?.let { manga.thumbnail_url = it }

        val titleDesc = StringBuilder()

        title?.let {
            manga.title = it
            titleDesc += "Title: $it\n"
        }

        val detailsDesc = StringBuilder()

        manga.artist = artists.joinToString()

        detailsDesc += "Artist(s): ${manga.artist}\n"

        group?.let {
            detailsDesc += "Group: $it\n"
        }

        type?.let {
            detailsDesc += "Type: ${it.capitalize()}\n"
        }

        (language ?: "unknown").let {
            detailsDesc += "Language: ${it.capitalize()}\n"
        }

        if(series.isNotEmpty())
            detailsDesc += "Series: ${series.joinToString()}\n"

        if(characters.isNotEmpty())
            detailsDesc += "Characters: ${characters.joinToString()}\n"

        uploadDate?.let {
            detailsDesc += "Upload date: ${EX_DATE_FORMAT.format(Date(it))}\n"
        }

        manga.status = SManga.UNKNOWN

        //Copy tags -> genres
        manga.genre = tagsToGenreString()

        val tagsDesc = tagsToDescription()

        manga.description = listOf(titleDesc.toString(), detailsDesc.toString(), tagsDesc.toString())
                .filter(String::isNotBlank)
                .joinToString(separator = "\n")
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0

        const val TAG_TYPE_DEFAULT = 0

        const val LTN_BASE_URL = "https://ltn.hitomi.la"
        const val BASE_URL = "https://hitomi.la"

        fun hlIdFromUrl(url: String)
                = url.split('/').last().substringBeforeLast('.')

        fun urlFromHlId(id: String)
                = "$BASE_URL/galleries/$id.html"
    }
}