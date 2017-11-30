package exh.metadata.models

import eu.kanade.tachiyomi.source.model.SManga
import io.realm.RealmList
import io.realm.RealmModel

/**
 * A gallery that can be searched using the EH search engine
 */
interface SearchableGalleryMetadata: RealmModel {
    var uuid: String

    var uploader: String?

    //Being specific about which classes are used in generics to make deserialization easier
    var tags: RealmList<Tag>

    fun getTitles(): List<String>

    val titleFields: List<String>

    var mangaId: Long?

    fun copyTo(manga: SManga)
}