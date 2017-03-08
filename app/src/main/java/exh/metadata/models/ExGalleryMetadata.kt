package exh.metadata.models

import android.net.Uri
import java.util.*

/**
 * Gallery metadata storage model
 */

class ExGalleryMetadata : SearchableGalleryMetadata() {
    var url: String? = null

    var exh: Boolean? = null

    var thumbnailUrl: String? = null

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


    private fun splitGalleryUrl()
            = url?.let {
        Uri.parse(it).pathSegments.filterNot(String::isNullOrBlank)
    }

    fun galleryId() = splitGalleryUrl()?.let { it[it.size - 2] }

    fun galleryToken() =
        splitGalleryUrl()?.last()

    override fun galleryUniqueIdentifier() = exh?.let { exh ->
        url?.let {
            //Fuck, this should be EXH and EH but it's too late to change it now...
            //TODO Change this during migration
            "${if(exh) "EXH" else "EX"}-${galleryId()}-${galleryToken()}"
        }
    }
}