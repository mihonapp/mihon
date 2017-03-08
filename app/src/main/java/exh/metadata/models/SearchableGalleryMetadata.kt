package exh.metadata.models

import java.util.ArrayList
import java.util.HashMap

/**
 * A gallery that can be searched using the EH search engine
 */
abstract class SearchableGalleryMetadata {
    var uploader: String? = null

    var title: String? = null
    val altTitles: MutableList<String> = mutableListOf()

    //Being specific about which classes are used in generics to make deserialization easier
    val tags: HashMap<String, ArrayList<Tag>> = HashMap()

    abstract fun galleryUniqueIdentifier(): String?
}