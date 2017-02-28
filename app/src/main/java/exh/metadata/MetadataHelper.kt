package exh.metadata

import exh.metadata.models.ExGalleryMetadata
import io.paperdb.Paper

class MetadataHelper {

    fun writeGallery(galleryMetadata: ExGalleryMetadata)
            = exGalleryBook().write(galleryMetadata.galleryUniqueIdentifier(), galleryMetadata)!!

    fun fetchMetadata(url: String, exh: Boolean): ExGalleryMetadata?
            = ExGalleryMetadata().let {
        it.url = url
        it.exh = exh
        return exGalleryBook().read<ExGalleryMetadata>(it.galleryUniqueIdentifier())
    }

    fun getAllGalleries() = exGalleryBook().allKeys.map {
        exGalleryBook().read<ExGalleryMetadata>(it)
    }

    fun hasMetadata(url: String, exh: Boolean): Boolean
            = ExGalleryMetadata().let {
        it.url = url
        it.exh = exh
        return exGalleryBook().exist(it.galleryUniqueIdentifier())
    }

    fun exGalleryBook() = Paper.book("gallery-ex")!!
}