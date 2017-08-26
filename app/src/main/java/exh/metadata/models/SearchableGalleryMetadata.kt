package exh.metadata.models

import io.realm.RealmList
import io.realm.RealmModel
import io.realm.annotations.Index
import java.util.ArrayList
import java.util.HashMap
import kotlin.reflect.KCallable

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
}