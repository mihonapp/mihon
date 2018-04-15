package exh.util

/**
 * Reads entire `JsonObject`s and `JsonArray`s from `JsonReader`s
 *
 * @author nulldev
 */

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.math.BigDecimal

fun JsonReader.nextJsonObject(): JsonObject {
    beginObject()

    val obj = JsonObject()

    while(hasNext()) {
        val name = nextName()

        when(peek()) {
            JsonToken.BEGIN_ARRAY -> obj.add(name, nextJsonArray())
            JsonToken.BEGIN_OBJECT -> obj.add(name, nextJsonObject())
            JsonToken.NULL -> {
                nextNull()
                obj.add(name, JsonNull.INSTANCE)
            }
            JsonToken.BOOLEAN -> obj.addProperty(name, nextBoolean())
            JsonToken.NUMBER -> obj.addProperty(name, BigDecimal(nextString()))
            JsonToken.STRING -> obj.addProperty(name, nextString())
            else -> skipValue()
        }
    }

    endObject()

    return obj
}

fun JsonReader.nextJsonArray(): JsonArray {
    beginArray()

    val arr = JsonArray()

    while(hasNext()) {
        when(peek()) {
            JsonToken.BEGIN_ARRAY -> arr.add(nextJsonArray())
            JsonToken.BEGIN_OBJECT -> arr.add(nextJsonObject())
            JsonToken.NULL -> {
                nextNull()
                arr.add(JsonNull.INSTANCE)
            }
            JsonToken.BOOLEAN -> arr.add(nextBoolean())
            JsonToken.NUMBER -> arr.add(BigDecimal(nextString()))
            JsonToken.STRING -> arr.add(nextString())
            else -> skipValue()
        }
    }

    endArray()

    return arr
}
