package mihon.graphql.kitsu.adapter

import com.apollographql.apollo.api.Adapter
import com.apollographql.apollo.api.AnyAdapter
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.json.JsonReader
import com.apollographql.apollo.api.json.JsonWriter

object KitsuMapAdapter : Adapter<Map<String, Any?>> {

    override fun fromJson(
        reader: JsonReader,
        customScalarAdapters: CustomScalarAdapters,
    ): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return AnyAdapter.fromJson(reader, customScalarAdapters) as Map<String, Any?>
    }

    override fun toJson(
        writer: JsonWriter,
        customScalarAdapters: CustomScalarAdapters,
        value: Map<String, Any?>,
    ) {
        AnyAdapter.toJson(writer, customScalarAdapters, value)
    }
}
