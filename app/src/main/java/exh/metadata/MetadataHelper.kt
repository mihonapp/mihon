package exh.metadata

import exh.*
import exh.metadata.models.ExGalleryMetadata
import exh.metadata.models.NHentaiMetadata
import exh.metadata.models.PervEdenGalleryMetadata
import exh.metadata.models.SearchableGalleryMetadata
import io.paperdb.Paper

class MetadataHelper {

    fun writeGallery(galleryMetadata: SearchableGalleryMetadata, source: Long)
            = (if(isExSource(source) || isEhSource(source)) exGalleryBook()
    else if(isPervEdenSource(source)) pervEdenGalleryBook()
        else if(isNhentaiSource(source)) nhentaiGalleryBook()
    else null)?.write(galleryMetadata.galleryUniqueIdentifier(), galleryMetadata)!!

    fun fetchEhMetadata(url: String, exh: Boolean): ExGalleryMetadata?
            = ExGalleryMetadata().let {
        it.url = url
        it.exh = exh
        return exGalleryBook().read<ExGalleryMetadata>(it.galleryUniqueIdentifier())
    }

    fun fetchPervEdenMetadata(url: String, source: Long): PervEdenGalleryMetadata?
            = PervEdenGalleryMetadata().let {
        it.url = url
        if(source == PERV_EDEN_EN_SOURCE_ID)
            it.lang = "en"
        else if(source == PERV_EDEN_IT_SOURCE_ID)
            it.lang = "it"
        else throw IllegalArgumentException("Invalid source id!")
        return pervEdenGalleryBook().read<PervEdenGalleryMetadata>(it.galleryUniqueIdentifier())
    }

    fun fetchNhentaiMetadata(url: String) = NHentaiMetadata().let {
        it.url = url
        nhentaiGalleryBook().read<NHentaiMetadata>(it.galleryUniqueIdentifier())
    }

    fun fetchMetadata(url: String, source: Long): SearchableGalleryMetadata? {
        if(isExSource(source) || isEhSource(source)) {
            return fetchEhMetadata(url, isExSource(source))
        } else if(isPervEdenSource(source)) {
            return fetchPervEdenMetadata(url, source)
        } else if(isNhentaiSource(source)) {
            return fetchNhentaiMetadata(url)
        } else {
            return null
        }
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

    //TODO Problem, our new metadata structures are incompatible.
    //TODO We will probably just delete the old metadata structures
    fun exGalleryBook() = Paper.book("gallery-ex")!!

    fun pervEdenGalleryBook() = Paper.book("gallery-perveden")!!

    fun nhentaiGalleryBook() = Paper.book("gallery-nhentai")!!
}