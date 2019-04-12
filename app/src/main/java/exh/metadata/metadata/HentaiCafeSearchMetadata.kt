package exh.metadata.metadata

import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.metadata.base.RaisedSearchMetadata

class HentaiCafeSearchMetadata : RaisedSearchMetadata() {
    var hcId: String? = null
    var readerId: String? = null

    var url get() = hcId?.let { "$BASE_URL/$it" }
        set(a) {
            a?.let {
                hcId = hcIdFromUrl(a)
            }
        }

    var thumbnailUrl: String? = null

    var title by titleDelegate(TITLE_TYPE_MAIN)

    var artist: String? = null

    override fun copyTo(manga: SManga) {
        thumbnailUrl?.let { manga.thumbnail_url = it }

        manga.title = title!!
        manga.artist = artist
        manga.author = artist

        //Not available
        manga.status = SManga.UNKNOWN

        val detailsDesc = "Title: $title\n" +
                "Artist: $artist\n"

        val tagsDesc = tagsToDescription()

        manga.genre = tagsToGenreString()

        manga.description = listOf(detailsDesc, tagsDesc.toString())
                .filter(String::isNotBlank)
                .joinToString(separator = "\n")
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0

        const val TAG_TYPE_DEFAULT = 0

        const val BASE_URL = "https://hentai.cafe"

        fun hcIdFromUrl(url: String)
                = url.split("/").last { it.isNotBlank() }
    }
}