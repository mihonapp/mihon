package xyz.nulldev.ts.api.http.serializer

import com.github.salomonbrys.kotson.bool
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.nullObj
import com.github.salomonbrys.kotson.set
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.source.model.Filter
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

interface Serializer<in T : Filter<out Any?>> {
    fun serialize(json: JsonObject, filter: T) {}
    fun deserialize(json: JsonObject, filter: T) {}

    /**
     * Automatic two-way mappings between fields and JSON
     */
    fun mappings(): List<Pair<String, KProperty1<in T, *>>> = emptyList()

    val serializer: FilterSerializer
    val type: String
    val clazz: KClass<in T>
}

class HeaderSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Header> {
    override val type = "HEADER"
    override val clazz = Filter.Header::class

    override fun mappings() = listOf(
            Pair(NAME, Filter.Header::name)
    )

    companion object {
        const val NAME = "name"
    }
}

class SeparatorSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Separator> {
    override val type = "SEPARATOR"
    override val clazz = Filter.Separator::class

    override fun mappings() = listOf(
            Pair(NAME, Filter.Separator::name)
    )

    companion object {
        const val NAME = "name"
    }
}

class SelectSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Select<Any>> {
    override val type = "SELECT"
    override val clazz = Filter.Select::class

    override fun serialize(json: JsonObject, filter: Filter.Select<Any>) {
        //Serialize values to JSON
        json[VALUES] = JsonArray().apply {
            filter.values.map {
                it.toString()
            }.forEach { add(it) }
        }
    }

    override fun mappings() = listOf(
            Pair(NAME, Filter.Select<Any>::name),
            Pair(STATE, Filter.Select<Any>::state)
    )

    companion object {
        const val NAME = "name"
        const val VALUES = "values"
        const val STATE = "state"
    }
}

class TextSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Text> {
    override val type = "TEXT"
    override val clazz = Filter.Text::class

    override fun mappings() = listOf(
            Pair(NAME, Filter.Text::name),
            Pair(STATE, Filter.Text::state)
    )

    companion object {
        const val NAME = "name"
        const val STATE = "state"
    }
}

class CheckboxSerializer(override val serializer: FilterSerializer) : Serializer<Filter.CheckBox> {
    override val type = "CHECKBOX"
    override val clazz = Filter.CheckBox::class

    override fun mappings() = listOf(
            Pair(NAME, Filter.CheckBox::name),
            Pair(STATE, Filter.CheckBox::state)
    )

    companion object {
        const val NAME = "name"
        const val STATE = "state"
    }
}

class TriStateSerializer(override val serializer: FilterSerializer) : Serializer<Filter.TriState> {
    override val type = "TRISTATE"
    override val clazz = Filter.TriState::class

    override fun mappings() = listOf(
            Pair(NAME, Filter.TriState::name),
            Pair(STATE, Filter.TriState::state)
    )

    companion object {
        const val NAME = "name"
        const val STATE = "state"
    }
}

class GroupSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Group<Any?>> {
    override val type = "GROUP"
    override val clazz = Filter.Group::class

    override fun serialize(json: JsonObject, filter: Filter.Group<Any?>) {
        json[STATE] = JsonArray().apply {
            filter.state.forEach {
                add(if(it is Filter<*>)
                    serializer.serialize(it as Filter<Any?>)
                else
                    JsonNull.INSTANCE
                )
            }
        }
    }

    override fun deserialize(json: JsonObject, filter: Filter.Group<Any?>) {
        json[STATE].asJsonArray.forEachIndexed { index, jsonElement ->
            if(!jsonElement.isJsonNull)
                serializer.deserialize(filter.state[index] as Filter<Any?>, jsonElement.asJsonObject)
        }
    }

    override fun mappings() = listOf(
            Pair(NAME, Filter.Group<Any?>::name)
    )

    companion object {
        const val NAME = "name"
        const val STATE = "state"
    }
}

class SortSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Sort> {
    override val type = "SORT"
    override val clazz = Filter.Sort::class

    override fun serialize(json: JsonObject, filter: Filter.Sort) {
        //Serialize values
        json[VALUES] = JsonArray().apply {
            filter.values.forEach { add(it) }
        }

        //Serialize state
        json[STATE] = filter.state?.let { (index, ascending) ->
            JsonObject().apply {
                this[STATE_INDEX] = index
                this[STATE_ASCENDING] = ascending
            }
        } ?: JsonNull.INSTANCE
    }

    override fun deserialize(json: JsonObject, filter: Filter.Sort) {
        //Deserialize state
        filter.state = json[STATE].nullObj?.let {
            Filter.Sort.Selection(it[STATE_INDEX].int,
                    it[STATE_ASCENDING].bool)
        }
    }

    override fun mappings() = listOf(
            Pair(NAME, Filter.Sort::name)
    )

    companion object {
        const val NAME = "name"
        const val VALUES = "values"
        const val STATE = "state"

        const val STATE_INDEX = "index"
        const val STATE_ASCENDING = "ascending"
    }
}
