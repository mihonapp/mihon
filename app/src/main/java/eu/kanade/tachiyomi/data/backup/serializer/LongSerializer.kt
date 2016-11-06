package eu.kanade.tachiyomi.data.backup.serializer

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

class LongSerializer : JsonSerializer<Long> {

    override fun serialize(value: Long?, type: Type, context: JsonSerializationContext): JsonElement? {
        if (value != null && value !== 0L)
            return JsonPrimitive(value)
        return null
    }
}
