package exh.metadata.metadata

import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.plusAssign

class EightMusesSearchMetadata : RaisedSearchMetadata() {
    var path: List<String> = emptyList()

    var title by titleDelegate(TITLE_TYPE_MAIN)

    var thumbnailUrl: String? = null

    override fun copyTo(manga: SManga) {
        manga.url = path.joinToString("/", prefix = "/")

        title?.let {
            manga.title = it
        }

        thumbnailUrl?.let {
            manga.thumbnail_url = it
        }

        manga.artist = tags.ofNamespace(ARTIST_NAMESPACE).joinToString { it.name }

        manga.genre = tagsToGenreString()

        val titleDesc = StringBuilder()
        title?.let { titleDesc += "Title: $it\n" }

        val tagsDesc = tagsToDescription()

        manga.description = listOf(titleDesc.toString(), tagsDesc.toString())
                .filter(String::isNotBlank)
                .joinToString(separator = "\n")

    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0

        const val TAG_TYPE_DEFAULT = 0

        const val BASE_URL = "https://www.8muses.com"

        const val TAGS_NAMESPACE = "tags"
        const val ARTIST_NAMESPACE = "artist"
    }
}