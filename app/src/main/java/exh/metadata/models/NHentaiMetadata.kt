package exh.metadata.models

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass
import java.util.*

/**
 * NHentai metadata
 */

@RealmClass
open class NHentaiMetadata : RealmObject(), SearchableGalleryMetadata {
    @PrimaryKey
    override var uuid: String = UUID.randomUUID().toString()

    var nhId: Long? = null

    var url get() = nhId?.let { "$BASE_URL/g/$it" }
    set(a) {
        a?.let {
            nhId = nhIdFromUrl(a)
        }
    }

    @Index
    override var uploader: String? = null

    var uploadDate: Long? = null

    var favoritesCount: Long? = null

    var mediaId: String? = null

    @Index
    var japaneseTitle: String? = null
    @Index
    var englishTitle: String? = null
    @Index
    var shortTitle: String? = null

    var coverImageType: String? = null
    var pageImageTypes: RealmList<PageImageType> = RealmList()
    var thumbnailImageType: String? = null

    var scanlator: String? = null

    override var tags: RealmList<Tag> = RealmList()

    override fun getTitles() = listOf(japaneseTitle, englishTitle, shortTitle).filterNotNull()

    @Ignore
    override val titleFields = TITLE_FIELDS

    @Index
    override var mangaId: Long? = null

    companion object {
        val BASE_URL = "https://nhentai.net"

        fun typeToExtension(t: String?) =
            when(t) {
                "p" -> "png"
                "j" -> "jpg"
                else -> null
            }

        fun nhIdFromUrl(url: String)
            = url.split("/").last { it.isNotBlank() }.toLong()

        val TITLE_FIELDS = listOf(
                NHentaiMetadata::japaneseTitle.name,
                NHentaiMetadata::englishTitle.name,
                NHentaiMetadata::shortTitle.name
        )
    }
}

@RealmClass
open class PageImageType(var type: String? = null): RealmObject() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PageImageType

        if (type != other.type) return false

        return true
    }


    override fun hashCode() = type?.hashCode() ?: 0

    override fun toString() = "PageImageType(type=$type)"
}
