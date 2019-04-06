package xyz.nulldev.ts.api.http.serializer

import com.github.salomonbrys.kotson.*
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.isSubclassOf

class FilterSerializer {
    val serializers = listOf<Serializer<*>>(
            HeaderSerializer(this),
            SeparatorSerializer(this),
            SelectSerializer(this),
            TextSerializer(this),
            CheckboxSerializer(this),
            TriStateSerializer(this),
            GroupSerializer(this),
            SortSerializer(this)
    )

    fun serialize(filters: FilterList) = JsonArray().apply {
        filters.forEach {
            add(serialize(it as Filter<Any?>))
        }
    }

    fun serialize(filter: Filter<Any?>): JsonObject {
        val out = JsonObject()
        for(serializer in serializers) {
            if(filter::class.isSubclassOf(serializer.clazz)) {
                //TODO Not sure how to deal with the mess of types here
                serializer as Serializer<Filter<Any?>>

                serializer.serialize(out, filter)

                out[CLASS_MAPPINGS] = JsonObject()

                serializer.mappings().forEach {
                    val res = it.second.get(filter)
                    out[it.first] = res
                    out[CLASS_MAPPINGS][it.first] = res?.javaClass?.name ?: "null"
                }

                out[TYPE] = serializer.type

                return out
            }
        }
        throw IllegalArgumentException("Cannot serialize this Filter object!")
    }

    fun deserialize(filters: FilterList, json: JsonArray) {
        filters.zip(json).forEach { (filter, obj) ->
            deserialize(filter as Filter<Any?>, obj.obj)
        }
    }

    fun deserialize(filter: Filter<Any?>, json: JsonObject) {
        val serializer = serializers.find {
            it.type == json[TYPE].string
        } ?: throw IllegalArgumentException("Cannot deserialize this type!")

        //TODO Not sure how to deal with the mess of types here
        serializer as Serializer<Filter<Any?>>

        serializer.deserialize(json, filter)

        serializer.mappings().forEach {
            if(it.second is KMutableProperty1) {
                val obj = json[it.first]
                val res: Any? = when(json[CLASS_MAPPINGS][it.first].string) {
                    java.lang.Integer::class.java.name -> obj.int
                    java.lang.Long::class.java.name -> obj.long
                    java.lang.Float::class.java.name -> obj.float
                    java.lang.Double::class.java.name -> obj.double
                    java.lang.String::class.java.name -> obj.string
                    java.lang.Boolean::class.java.name -> obj.bool
                    java.lang.Byte::class.java.name -> obj.byte
                    java.lang.Short::class.java.name -> obj.short
                    java.lang.Character::class.java.name -> obj.char
                    "null" -> null
                    else -> throw IllegalArgumentException("Cannot deserialize this type!")
                }
                (it.second as KMutableProperty1<in Filter<Any?>, in Any?>).set(filter, res)
            }
        }
    }

    companion object {
        const val TYPE = "_type"
        const val CLASS_MAPPINGS = "_cmaps"
    }
}
