package eu.kanade.tachiyomi.data.backup.serializer

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer

import java.lang.reflect.Type

class IntegerSerializer : JsonSerializer<Int> {

    override fun serialize(value: Int?, type: Type, context: JsonSerializationContext): JsonElement? {
        if (value != null && value !== 0)
            return JsonPrimitive(value)
        return null
    }
}
