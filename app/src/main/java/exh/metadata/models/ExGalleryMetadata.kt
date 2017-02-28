package exh.metadata.models

import android.net.Uri
import java.util.*

/**
 * Gallery metadata storage model
 */

class ExGalleryMetadata {
    var url: String? = null

    var exh: Boolean? = null

    var title: String? = null
    var altTitle: String? = null

    var thumbnailUrl: String? = null

    var genre: String? = null

    var uploader: String? = null
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

    //Being specific about which classes are used in generics to make deserialization easier
    var tags: HashMap<String, ArrayList<Tag>> = HashMap()

    private fun splitGalleryUrl()
            = url?.let {
        Uri.parse(it).pathSegments.filterNot(String::isNullOrBlank)
    }

    fun galleryId() = splitGalleryUrl()?.let { it[it.size - 2] }

    fun galleryToken() =
        splitGalleryUrl()?.last()

    fun galleryUniqueIdentifier() = exh?.let { exh ->
        url?.let {
            "${if(exh) "EXH" else "EX"}-${galleryId()}-${galleryToken()}"
        }
    }
}