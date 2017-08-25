package exh.metadata.models

import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.RealmClass

/**
 * Simple tag model
 */

@RealmClass
open class Tag(@Index var namespace: String? = null,
               @Index var name: String? = null,
               var light: Boolean? = null): RealmObject() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Tag

        if (namespace != other.namespace) return false
        if (name != other.name) return false
        if (light != other.light) return false

        return true
    }

    override fun hashCode(): Int {
        var result = namespace?.hashCode() ?: 0
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (light?.hashCode() ?: 0)
        return result
    }

    override fun toString() = "Tag(namespace=$namespace, name=$name, light=$light)"
}
