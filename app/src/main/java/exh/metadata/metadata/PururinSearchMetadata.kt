package exh.metadata.metadata

import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.plusAssign

class PururinSearchMetadata : RaisedSearchMetadata() {
    var prId: Int? = null

    var prShortLink: String? = null

    var title by titleDelegate(TITLE_TYPE_TITLE)
    var altTitle by titleDelegate(TITLE_TYPE_ALT_TITLE)

    var thumbnailUrl: String? = null

    var uploaderDisp: String? = null

    var pages: Int? = null

    var fileSize: String? = null

    var ratingCount: Int? = null
    var averageRating: Double? = null

    override fun copyTo(manga: SManga) {
        prId?.let { prId ->
            prShortLink?.let { prShortLink ->
                manga.url = "$BASE_URL/gallery/$prId/$prShortLink"
            }
        }

        (title ?: altTitle)?.let {
            manga.title = it
        }

        thumbnailUrl?.let {
            manga.thumbnail_url = it
        }

        manga.artist = tags.ofNamespace(TAG_NAMESPACE_ARTIST).joinToString { it.name }

        manga.genre = tagsToGenreString()

        val titleDesc = StringBuilder()
        title?.let { titleDesc += "English Title: $it\n" }
        altTitle?.let { titleDesc += "Japanese Title: $it\n" }

        val detailsDesc = StringBuilder()
        (uploaderDisp ?: uploader)?.let { detailsDesc += "Uploader: $it\n"}
        pages?.let { detailsDesc += "Length: $it pages\n" }
        fileSize?.let { detailsDesc += "Size: $it\n" }
        ratingCount?.let { detailsDesc += "Rating: $averageRating ($ratingCount)\n" }

        val tagsDesc = tagsToDescription()

        manga.description = listOf(titleDesc.toString(), detailsDesc.toString(), tagsDesc.toString())
                .filter(String::isNotBlank)
                .joinToString(separator = "\n")
    }

    companion object {
        private const val TITLE_TYPE_TITLE = 0
        private const val TITLE_TYPE_ALT_TITLE = 1

        const val TAG_TYPE_DEFAULT = 0

        private const val TAG_NAMESPACE_ARTIST = "artist"

        val BASE_URL = "https://pururin.io"
    }
}