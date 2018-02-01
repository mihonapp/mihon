package exh.metadata.models

import io.realm.Case
import io.realm.Realm
import io.realm.RealmQuery
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

abstract class GalleryQuery<T : SearchableGalleryMetadata>(val clazz: KClass<T>) {
    open fun map(): Map<*, *> = emptyMap<KProperty<T>, KProperty1<GalleryQuery<T>, *>>()

    open fun transform(): GalleryQuery<T>? = this

    open fun override(meta: RealmQuery<T>): RealmQuery<T> = meta

    fun query(realm: Realm, meta: RealmQuery<T>? = null): RealmQuery<T>
            = (meta ?: realm.where(clazz.java)).let {
        val visited = mutableListOf<GalleryQuery<T>>()

        var top: GalleryQuery<T>? = null
        var newMeta = it
        while(true) {
            //DIFFERENT BEHAVIOR from: top?.transform() ?: this
            top = if(top != null) top.transform() else this

            if(top == null) break

            if(top in visited) break

            newMeta = top.applyMap(newMeta)
            newMeta = top.override(newMeta)

            visited += top
        }

        newMeta
    }!!

    fun applyMap(meta: RealmQuery<T>): RealmQuery<T> {
        var newMeta = meta

        map().forEach { (t, u) ->
            t as KProperty<T>
            u as KProperty1<GalleryQuery<T>, *>

            val v = u.get(this)
            val n = t.name

            if(v != null) {
                newMeta = when (v) {
                    is Date -> newMeta.equalTo(n, v)
                    is Boolean -> newMeta.equalTo(n, v)
                    is Byte -> newMeta.equalTo(n, v)
                    is ByteArray -> newMeta.equalTo(n, v)
                    is Double -> newMeta.equalTo(n, v)
                    is Float -> newMeta.equalTo(n, v)
                    is Int -> newMeta.equalTo(n, v)
                    is Long -> newMeta.equalTo(n, v)
                    is Short -> newMeta.equalTo(n, v)
                    is String -> newMeta.equalTo(n, v, Case.INSENSITIVE)
                    else -> throw IllegalArgumentException("Unknown type: ${v::class.java.name}!")
                }
            }
        }

        return newMeta
    }
}