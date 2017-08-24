package exh.metadata.models

/**
 * NHentai metadata
 */

class NHentaiMetadata : SearchableGalleryMetadata() {

    var id: Long? = null

    var url get() = id?.let { "$BASE_URL/g/$it" }
    set(a) {
        a?.let {
            id = a.split("/").last { it.isNotBlank() }.toLong()
        }
    }

    var uploadDate: Long? = null

    var favoritesCount: Long? = null

    var mediaId: String? = null

    var japaneseTitle: String? = null
    var englishTitle: String? = null
    var shortTitle: String? = null

    var coverImageType: String? = null
    var pageImageTypes: MutableList<String> = mutableListOf()
    var thumbnailImageType: String? = null

    var scanlator: String? = null

    override fun galleryUniqueIdentifier(): String? = "NHENTAI-$id"

    override fun getTitles() = listOf(japaneseTitle, englishTitle, shortTitle).filterNotNull()

    companion object {
        val BASE_URL = "https://nhentai.net"

        fun typeToExtension(t: String?) =
            when(t) {
                "p" -> "png"
                "j" -> "jpg"
                else -> null
            }
    }
}
