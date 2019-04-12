package exh.metadata.metadata

import android.net.Uri
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.EX_DATE_FORMAT
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.plusAssign
import java.util.*

class TsuminoSearchMetadata : RaisedSearchMetadata() {
    var tmId: Int? = null

    var title by titleDelegate(TITLE_TYPE_MAIN)

    var artist: String? = null

    var uploadDate: Long? = null

    var length: Int? = null

    var ratingString: String? = null

    var category: String? = null

    var collection: String? = null

    var group: String? = null

    var parody: List<String> = emptyList()

    var character: List<String> = emptyList()

    override fun copyTo(manga: SManga) {
        title?.let { manga.title = it }
        manga.thumbnail_url = BASE_URL + thumbUrlFromId(tmId.toString())

        artist?.let { manga.artist = it }

        manga.status = SManga.UNKNOWN

        val titleDesc = "Title: $title\n"

        val detailsDesc = StringBuilder()
        uploader?.let { detailsDesc += "Uploader: $it\n" }
        uploadDate?.let { detailsDesc += "Uploaded: ${EX_DATE_FORMAT.format(Date(it))}\n" }
        length?.let { detailsDesc += "Length: $it pages\n" }
        ratingString?.let { detailsDesc += "Rating: $it\n" }
        category?.let {
            detailsDesc += "Category: $it\n"
        }
        collection?.let { detailsDesc += "Collection: $it\n" }
        group?.let { detailsDesc += "Group: $it\n" }
        val parodiesString = parody.joinToString()
        if(parodiesString.isNotEmpty()) {
            detailsDesc += "Parody: $parodiesString\n"
        }
        val charactersString = character.joinToString()
        if(charactersString.isNotEmpty()) {
            detailsDesc += "Character: $charactersString\n"
        }

        //Copy tags -> genres
        manga.genre = tagsToGenreString()

        val tagsDesc = tagsToDescription()

        manga.description = listOf(titleDesc, detailsDesc.toString(), tagsDesc.toString())
                .filter(String::isNotBlank)
                .joinToString(separator = "\n")
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0

        const val TAG_TYPE_DEFAULT = 0

        val BASE_URL = "https://www.tsumino.com"

        fun tmIdFromUrl(url: String)
                = Uri.parse(url).pathSegments[2]

        fun mangaUrlFromId(id: String) = "/Book/Info/$id"

        fun thumbUrlFromId(id: String) = "/Image/Thumb/$id"
    }
}
