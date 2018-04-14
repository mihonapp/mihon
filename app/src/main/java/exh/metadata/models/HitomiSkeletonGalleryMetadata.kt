package exh.metadata.models

import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.models.HitomiGalleryMetadata.Companion.hlIdFromUrl
import exh.metadata.models.HitomiGalleryMetadata.Companion.urlFromHlId
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.Index
import io.realm.annotations.RealmClass

@RealmClass
open class HitomiSkeletonGalleryMetadata : RealmObject(), SearchableGalleryMetadata {
    override var uuid: String
        set(value) {}
        get() = throw UnsupportedOperationException()

    var hlId: String? = null

    var thumbnailUrl: String? = null

    var artist: String? = null

    var group: String? = null

    var type: String? = null

    var language: String? = null

    var languageSimple: String? = null

    var series: RealmList<String> = RealmList()

    var characters: RealmList<String> = RealmList()

    var buyLink: String? = null

    var uploadDate: Long? = null

    override var tags: RealmList<Tag> = RealmList()

    // Sites does not show uploader
    override var uploader: String? = "admin"

    var url get() = hlId?.let { urlFromHlId(it) }
        set(a) {
            a?.let {
                hlId = hlIdFromUrl(a)
            }
        }

    override var mangaId: Long? = null

    @Index
    var title: String? = null

    override fun getTitles() = listOfNotNull(title)

    @Ignore
    override val titleFields = listOf(
            ::title.name
    )
    override fun copyTo(manga: SManga) {
        throw UnsupportedOperationException("This operation cannot be performed on skeleton galleries!")
    }
}

