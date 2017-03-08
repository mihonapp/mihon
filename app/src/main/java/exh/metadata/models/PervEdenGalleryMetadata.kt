package exh.metadata.models

import android.net.Uri
import timber.log.Timber

//TODO Add artificial artist tag
class PervEdenGalleryMetadata : SearchableGalleryMetadata() {
    var url: String? = null
    var thumbnailUrl: String? = null

    var artist: String? = null

    var type: String? = null

    var rating: Float? = null

    var status: String? = null

    var lang: String? = null

    private fun splitGalleryUrl()
            = url?.let {
        Uri.parse(it).pathSegments.filterNot(String::isNullOrBlank)
    }

    override fun galleryUniqueIdentifier() = splitGalleryUrl()?.let {
        Timber.d(
                "PERVEDEN-${lang?.toUpperCase()}-${it.last()}"
        )
        "PERVEDEN-${lang?.toUpperCase()}-${it.last()}"
    }
}
