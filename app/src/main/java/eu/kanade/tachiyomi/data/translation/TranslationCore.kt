package eu.kanade.tachiyomi.data.translation

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.math.min

const val DEFAULT_GEMINI_TRANSLATION_MODEL = "gemini-3-flash-preview"
const val DEFAULT_GEMINI_MAX_OUTPUT_TOKENS = 65_536

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

object TranslationModelLimits {
    fun outputTokenLimitFor(selectedModel: String, cachedModels: List<GeminiModel>): Int? {
        return cachedModels
            .firstOrNull { it.matches(selectedModel) }
            ?.outputTokenLimit
            ?.takeIf { it > 0 }
    }

    fun maxOutputTokensFor(
        requested: Int,
        selectedModel: String,
        cachedModels: List<GeminiModel>,
    ): Int {
        val normalized = requested.coerceAtLeast(1)
        val modelLimit = outputTokenLimitFor(selectedModel, cachedModels) ?: return normalized
        return min(normalized, modelLimit)
    }

    fun encodeModels(models: List<GeminiModel>, json: Json): String {
        return json.encodeToString(models.generateContentModels())
    }

    fun decodeModels(value: String, json: Json): List<GeminiModel> {
        return runCatching {
            json.decodeFromString<List<GeminiModel>>(value)
        }.getOrDefault(emptyList())
            .generateContentModels()
    }

    private fun GeminiModel.matches(selectedModel: String): Boolean {
        val selected = selectedModel.removePrefix("models/")
        return id == selected || name == selectedModel || baseModelId == selected
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
    val thinkingLevel: String? = null,
    val rawJsonOverride: String = "",
) {
    fun toGeminiJson(json: Json): JsonElement {
        val base = buildJsonObject {
            temperature?.let { put("temperature", it) }
            topP?.let { put("topP", it) }
            topK?.let { put("topK", it) }
            maxOutputTokens?.let { put("maxOutputTokens", it) }
            thinkingLevel?.takeIf { it.isNotBlank() }?.let {
                put(
                    "thinkingConfig",
                    buildJsonObject {
                        put("thinkingLevel", it)
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

enum class TranslationThinkingLevel(val value: String) {
    High("high"),
    Medium("medium"),
    Low("low"),
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

data class TranslationJobSignature(
    val mangaId: Long,
    val chapterId: Long?,
    val pageIndex: Long?,
    val scope: TranslationScope,
    val pipeline: String,
    val mode: TranslationMode,
    val targetLanguage: String,
    val status: TranslationJobStatus,
)

object TranslationJobDedupe {
    fun findActiveDuplicate(
        jobs: List<TranslationJobSignature>,
        candidate: TranslationJobSignature,
    ): TranslationJobSignature? {
        return jobs.firstOrNull { job ->
            job.status.isActiveForDedupe() &&
                job.mangaId == candidate.mangaId &&
                job.chapterId == candidate.chapterId &&
                job.pageIndex == candidate.pageIndex &&
                job.scope == candidate.scope &&
                job.pipeline == candidate.pipeline &&
                job.mode == candidate.mode &&
                job.targetLanguage == candidate.targetLanguage
        }
    }
}

fun TranslationJobStatus.isActiveForDedupe(): Boolean {
    return this in ACTIVE_TRANSLATION_JOB_STATUSES
}

fun TranslationJobStatus.isRetryableFromQueue(): Boolean {
    return this in RETRYABLE_TRANSLATION_JOB_STATUSES
}

fun TranslationJobStatus.canAutoRequeueAfterSetup(): Boolean {
    return this == TranslationJobStatus.PausedAuth
}

private val ACTIVE_TRANSLATION_JOB_STATUSES = setOf(
    TranslationJobStatus.Queued,
    TranslationJobStatus.Running,
    TranslationJobStatus.Retrying,
    TranslationJobStatus.PausedAuth,
    TranslationJobStatus.PausedQuota,
)

private val RETRYABLE_TRANSLATION_JOB_STATUSES = setOf(
    TranslationJobStatus.Failed,
    TranslationJobStatus.PausedAuth,
    TranslationJobStatus.PausedQuota,
)
