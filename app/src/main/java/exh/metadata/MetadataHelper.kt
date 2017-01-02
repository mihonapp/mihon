package exh.metadata

import exh.metadata.models.ExGalleryMetadata
import io.paperdb.Paper

class MetadataHelper {

    fun writeGallery(galleryMetadata: ExGalleryMetadata)
            = exGalleryBook().write(galleryMetadata.galleryUniqueIdentifier(), galleryMetadata)

    fun fetchMetadata(url: String, exh: Boolean) = ExGalleryMetadata().apply {
        this.url = url
        this.exh = exh
        return exGalleryBook().read<ExGalleryMetadata>(galleryUniqueIdentifier())
    }

    fun getAllGalleries() = exGalleryBook().allKeys.map {
        exGalleryBook().read<ExGalleryMetadata>(it)
    }

    fun exGalleryBook() = Paper.book("gallery-ex")!!
}