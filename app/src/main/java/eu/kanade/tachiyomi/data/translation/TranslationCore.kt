package eu.kanade.tachiyomi.data.translation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

const val DEFAULT_GEMINI_TRANSLATION_MODEL = "gemini-3-flash-preview"

@Serializable
data class GeminiModel(
    val name: String,
    val baseModelId: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val inputTokenLimit: Int? = null,
    val outputTokenLimit: Int? = null,
    val supportedGenerationMethods: List<String> = emptyList(),
    val version: String? = null,
    val thinking: Boolean? = null,
    val temperature: Float? = null,
    val maxTemperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
) {
    val id: String
        get() = name.removePrefix("models/")
}

fun List<GeminiModel>.generateContentModels(): List<GeminiModel> {
    return filter { model ->
        model.supportedGenerationMethods.any { it.equals("generateContent", ignoreCase = true) }
    }
}

object TranslationLogRedactor {
    private val apiKeyRegex = Regex("""([?&]key=)[^"&\s]+""")
    private val inlineDataRegex = Regex(
        """"(inlineData|inline_data)"\s*:\s*\{\s*"(mimeType|mime_type)"\s*:\s*"([^"]+)"\s*,\s*"data"\s*:\s*"[^"]*"\s*}""",
    )

    fun redact(value: String): String {
        return value
            .replace(apiKeyRegex) { match -> match.groupValues[1] + "<redacted>" }
            .replace(inlineDataRegex) { match ->
                """"${match.groupValues[1]}": {"${match.groupValues[2]}": "${match.groupValues[3]}", "data": "<redacted-image>"}"""
            }
    }
}

data class TranslationGenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxOutputTokens: Int? = null,
    val thinkingBudget: Int? = null,
    val rawJsonOverride: String = "",
) {
    fun toGeminiJson(json: Json): JsonElement {
        val base = buildJsonObject {
            temperature?.let { put("temperature", it) }
            topP?.let { put("topP", it) }
            topK?.let { put("topK", it) }
            maxOutputTokens?.let { put("maxOutputTokens", it) }
            thinkingBudget?.let {
                put(
                    "thinkingConfig",
                    buildJsonObject {
                        put("thinkingBudget", it)
                    },
                )
            }
        }
        val override = rawJsonOverride.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { json.parseToJsonElement(it).jsonObject }
            ?: JsonObject(emptyMap())
        return JsonObject(base + override)
    }
}

data class TranslationPageCandidate(
    val chapterId: Long,
    val pageIndex: Int,
    val hasOverlay: Boolean,
)

data class TranslationBoxEdit(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val originalText: String,
    val translatedText: String,
    val textType: String,
    val confidence: Double? = null,
    val styleJson: String? = null,
)

object TranslationEnqueuePlanner {
    fun pagesToQueue(
        pages: List<TranslationPageCandidate>,
        overwrite: Boolean,
    ): List<TranslationPageCandidate> {
        return if (overwrite) {
            pages
        } else {
            pages.filterNot { it.hasOverlay }
        }
    }
}
