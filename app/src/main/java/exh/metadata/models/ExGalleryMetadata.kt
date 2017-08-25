package exh.metadata.models

import android.net.Uri
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass
import java.util.*

/**
 * Gallery metadata storage model
 */

@RealmClass
open class ExGalleryMetadata : RealmObject(), SearchableGalleryMetadata {
    @PrimaryKey
    override var uuid: String = UUID.randomUUID().toString()

    var url: String? = null

    @Index
    var gId: String? = null
    @Index
    var gToken: String? = null

    @Index
    var exh: Boolean? = null

    var thumbnailUrl: String? = null

    @Index
    var title: String? = null
    @Index
    var altTitle: String? = null

    @Index
    override var uploader: String? = null

    var genre: String? = null

    var datePosted: Long? = null
    var parent: String? = null
    var visible: String? = null //Not a boolean
    var language: String? = null
    var translated: Boolean? = null
    var size: Long? = null
    var length: Int? = null
    var favorites: Int? = null
    var ratingCount: Int? = null
    var averageRating: Double? = null

    override var tags: RealmList<Tag> = RealmList()

    override fun getTitles() = listOf(title, altTitle).filterNotNull()

    @Ignore
    override val titleFields = listOf(
            ExGalleryMetadata::title.name,
            ExGalleryMetadata::altTitle.name
    )

    companion object {
        private fun splitGalleryUrl(url: String)
                = url.let {
                    Uri.parse(it).pathSegments
                            .filterNot(String::isNullOrBlank)
                }

        fun galleryId(url: String) = splitGalleryUrl(url).let { it[it.size - 2] }

        fun galleryToken(url: String) =
                splitGalleryUrl(url).last()
    }
}