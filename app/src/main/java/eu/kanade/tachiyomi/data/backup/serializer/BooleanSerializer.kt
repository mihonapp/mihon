package eu.kanade.tachiyomi.data.backup.serializer

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

class BooleanSerializer : JsonSerializer<Boolean> {

    override fun serialize(value: Boolean?, type: Type, context: JsonSerializationContext): JsonElement? {
        if (value != null && value != false)
            return JsonPrimitive(value)
        return null
    }
}
