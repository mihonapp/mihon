package eu.kanade.domain.translation

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object AiModelCatalog {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(models: List<AiModel>): String = json.encodeToString(ListSerializer(AiModel.serializer()), models)

    fun decode(value: String): List<AiModel> = runCatching {
        json.decodeFromString(ListSerializer(AiModel.serializer()), value)
    }.getOrDefault(emptyList())
}
