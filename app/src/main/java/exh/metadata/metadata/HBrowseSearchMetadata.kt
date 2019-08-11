package exh.metadata.metadata

import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.metadata.EightMusesSearchMetadata.Companion.ARTIST_NAMESPACE
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.plusAssign

class HBrowseSearchMetadata : RaisedSearchMetadata() {
    var hbId: Long? = null

    var title: String? by titleDelegate(TITLE_TYPE_MAIN)

    // Length in pages
    var length: Int? = null

    override fun copyTo(manga: SManga) {
        manga.url = "/$hbId"

        title?.let {
            manga.title = it
        }

        // Guess thumbnail URL if manga does not have thumbnail URL
        if(manga.thumbnail_url.isNullOrBlank()) {
            manga.thumbnail_url = guessThumbnailUrl(hbId.toString())
        }

        manga.artist = tags.ofNamespace(ARTIST_NAMESPACE).joinToString { it.name }

        val titleDesc = StringBuilder()
        title?.let { titleDesc += "Title: $it\n" }
        length?.let { titleDesc += "Length: $it page(s)\n" }

        val tagsDesc = tagsToDescription()

        manga.description = listOf(titleDesc.toString(), tagsDesc.toString())
                .filter(String::isNotBlank)
                .joinToString(separator = "\n")
    }

    companion object {
        const val BASE_URL = "https://www.hbrowse.com"

        private const val TITLE_TYPE_MAIN = 0

        const val TAG_TYPE_DEFAULT = 0

        fun guessThumbnailUrl(hbid: String): String {
            return "$BASE_URL/thumbnails/${hbid}_1.jpg#guessed"
        }
    }
}