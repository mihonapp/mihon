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
import java.util.Locale
import tachiyomi.data.Translation_jobs
import tachiyomi.domain.translation.service.TranslationPreferences

const val DEFAULT_GEMINI_TRANSLATION_MODEL = "gemini-3-flash-preview"
const val DEFAULT_GEMINI_MAX_OUTPUT_TOKENS = 65_536
const val DEFAULT_TRANSLATION_MAX_IMAGES_PER_BATCH = 38
const val TRANSLATION_BATCH_ALL = 0

object TranslationLanguages {
    const val SOURCE_AUTO = "auto"
    const val SOURCE_JAPANESE = "ja"
    const val SOURCE_KOREAN = "ko"
    const val SOURCE_CHINESE = "zh"

    fun defaultTargetLanguage(): String {
        return Locale.getDefault().displayLanguage.ifBlank { "English" }
    }

    fun sourcePromptLabel(value: String?): String? {
        return when (value?.trim().orEmpty().lowercase(Locale.ROOT)) {
            "", SOURCE_AUTO -> null
            SOURCE_JAPANESE -> "Japanese"
            SOURCE_KOREAN -> "Korean"
            SOURCE_CHINESE -> "Chinese"
            else -> value?.trim()
        }
    }

    fun sourceDisplayLabel(value: String?): String {
        return sourcePromptLabel(value) ?: "Auto"
    }
}

fun TranslationPreferences.resolvedTargetLanguage(): String {
    return targetLanguage.get().ifBlank { TranslationLanguages.defaultTargetLanguage() }
}

fun TranslationPreferences.resolvedSourceLanguageOrNull(): String? {
    return TranslationLanguages.sourcePromptLabel(sourceLanguage.get())
}

fun TranslationPreferences.normalizedMaxImagesPerBatch(): Int {
    val value = maxImagesPerBatch.get()
    return if (value == TRANSLATION_BATCH_ALL) TRANSLATION_BATCH_ALL else value.coerceAtLeast(1)
}

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
    private val apiKeyRegex = Regex("""([?&]key=)[^"&\s]+""", RegexOption.IGNORE_CASE)
    private val sensitiveHeaderJsonRegex = Regex(
        """"(x-goog-api-key|authorization)"\s*:\s*"[^"]*"""",
        RegexOption.IGNORE_CASE,
    )
    private val sensitiveHeaderTextRegex = Regex(
        """(?i)\b(x-goog-api-key|authorization)\s*[:=]\s*[^\s,]+""",
    )
    private val inlineDataRegex = Regex(
        """"(inlineData|inline_data)"\s*:\s*\{\s*"(mimeType|mime_type)"\s*:\s*"([^"]+)"\s*,\s*"data"\s*:\s*"[^"]*"\s*\}""",
    )
    private val longDataFieldRegex = Regex(
        """"data"\s*:\s*"[A-Za-z0-9+/=_-]{128,}"""",
    )

    fun redact(value: String): String {
        return value
            .replace(apiKeyRegex) { match -> match.groupValues[1] + "<redacted>" }
            .replace(sensitiveHeaderJsonRegex) { match ->
                """"${match.groupValues[1]}": "<redacted>""""
            }
            .replace(sensitiveHeaderTextRegex) { match ->
                "${match.groupValues[1]}=<redacted>"
            }
            .replace(inlineDataRegex) { match ->
                """"${match.groupValues[1]}": {"${match.groupValues[2]}": "${match.groupValues[3]}", "data": "<redacted-image>"}"""
            }
            .replace(longDataFieldRegex) {
                """"data": "<redacted-image>""""
            }
    }
}

enum class TranslationWorkStartPolicy {
    Keep,
    Replace,
}

object TranslationLogDetailsFormatter {
    fun queueState(
        action: String,
        jobId: Long?,
        previousStatus: String?,
        nextStatus: String?,
        reason: String? = null,
        extra: Map<String, Any?> = emptyMap(),
    ): String {
        return buildString {
            appendLine("action=$action")
            appendLine("job_id=${jobId ?: "-"}")
            appendLine("previous_status=${previousStatus ?: "-"}")
            appendLine("next_status=${nextStatus ?: "-"}")
            reason?.takeIf { it.isNotBlank() }?.let { appendLine("reason=$it") }
            extra.forEach { (key, value) ->
                appendLine("$key=${value ?: "-"}")
            }
        }.trimEnd()
    }

    fun apiCall(
        operation: String,
        method: String,
        endpoint: String,
        model: String?,
        statusCode: Int?,
        elapsedMs: Long,
        requestSummary: String,
        responseSummary: String? = null,
        errorBody: String? = null,
        rawRequestJson: String? = null,
        rawResponseJson: String? = null,
    ): String {
        return TranslationLogRedactor.redact(
            buildString {
                appendLine("operation=$operation")
                appendLine("method=$method")
                appendLine("endpoint=$endpoint")
                appendLine("model=${model ?: "-"}")
                appendLine("status_code=${statusCode ?: "-"}")
                appendLine("elapsed_ms=$elapsedMs")
                appendLine("request:")
                appendLine(requestSummary)
                responseSummary?.takeIf { it.isNotBlank() }?.let {
                    appendLine("response:")
                    appendLine(it)
                }
                errorBody?.takeIf { it.isNotBlank() }?.let {
                    appendLine("error_body:")
                    appendLine(it)
                }
                rawRequestJson?.takeIf { it.isNotBlank() }?.let {
                    appendLine("raw_request_json:")
                    appendLine(it)
                }
                rawResponseJson?.takeIf { it.isNotBlank() }?.let {
                    appendLine("raw_response_json:")
                    appendLine(it)
                }
            }.trimEnd(),
        )
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
    val hasActiveJob: Boolean = false,
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
        return TranslationBatchPlanner.pagesToQueue(
            pages = pages,
            overwrite = overwrite,
            maxImagesPerBatch = TRANSLATION_BATCH_ALL,
        )
    }
}

object TranslationBatchPlanner {
    fun pagesToQueue(
        pages: List<TranslationPageCandidate>,
        overwrite: Boolean,
        maxImagesPerBatch: Int,
    ): List<TranslationPageCandidate> {
        val eligible = pages.filter { page ->
            (overwrite || !page.hasOverlay) && !page.hasActiveJob
        }
        return if (maxImagesPerBatch == TRANSLATION_BATCH_ALL) {
            eligible
        } else {
            eligible.take(maxImagesPerBatch.coerceAtLeast(1))
        }
    }
}

object TranslationPromptPolicy {
    fun systemPrompt(userPrompt: String): String {
        return buildString {
            appendLine("You are a manga, manhwa, and manhua translation assistant.")
            appendLine("Only translate relevant or critical information: speech bubbles, thought bubbles, signs, captions, narration, and author's notes.")
            appendLine("Ignore sound effects, decorative text, unrelated background text, watermark text, and punctuation-only symbols.")
            appendLine("Return no box for ignored text.")
            userPrompt.trim().takeIf { it.isNotBlank() }?.let {
                appendLine("Additional user system prompt:")
                appendLine(it)
            }
        }.trimEnd()
    }

    fun pagePrompt(targetLanguage: String, sourceLanguage: String?): String {
        return buildString {
            appendLine("Translate the relevant manga text into ${targetLanguage.ifBlank { "the app language" }}.")
            appendLine("Source language: ${TranslationLanguages.sourcePromptLabel(sourceLanguage) ?: "auto-detect"}.")
            appendLine("Return only JSON matching the schema. Coordinates must be normalized 0.0 to 1.0.")
            appendLine("Each box needs x, y, width, height, originalText, translatedText, textType, confidence.")
        }.trimEnd()
    }
}

object TranslationOverlaySanitizer {
    private val ignoredTextTypes = setOf(
        "sfx",
        "soundeffect",
        "soundeffects",
        "sound_effect",
        "sound_effects",
        "punctuation",
        "decorative",
        "unrelated",
        "irrelevant",
        "watermark",
    )

    fun sanitize(overlay: TranslationOverlayResult): TranslationOverlayResult {
        return overlay.copy(
            boxes = overlay.boxes.filter(::isRelevantBox),
        )
    }

    fun isRelevantText(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.isNotEmpty() && trimmed.any { it.isLetterOrDigit() }
    }

    private fun isRelevantBox(box: TranslationOverlayBox): Boolean {
        val normalizedType = box.textType
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() || it == '_' }
        if (normalizedType in ignoredTextTypes) return false
        return isRelevantText(box.originalText.ifBlank { box.translatedText })
    }
}

data class TranslationNotificationState(
    val title: String,
    val text: String,
    val bigText: String,
    val progressMax: Int,
    val progressCurrent: Int,
    val indeterminate: Boolean,
)

object TranslationNotificationFormatter {
    fun format(
        item: TranslationQueueItem?,
        job: Translation_jobs,
        current: Long,
        total: Long,
        status: TranslationJobStatus,
        message: String?,
        hideContent: Boolean,
    ): TranslationNotificationState {
        val safeTotal = total.takeIf { it > 0 } ?: job.progress_total.takeIf { it > 0 } ?: 0
        val safeCurrent = current.coerceAtLeast(0)
        val progressText = if (safeTotal > 0) "$safeCurrent/$safeTotal" else "preparing"
        val title = if (hideContent) {
            "Translation queue"
        } else {
            item?.mangaTitle ?: "Translation queue"
        }
        val chapter = item?.chapterName ?: job.chapter_id?.let { "Chapter $it" } ?: "Unknown chapter"
        val page = job.page_index?.let { "page ${it + 1}" } ?: "chapter"
        val text = if (hideContent) {
            "${status.value} · $progressText"
        } else {
            "$chapter · $page · ${status.value} · $progressText"
        }
        val bigText = buildString {
            appendLine(text)
            appendLine("attempt=${job.attempts}")
            appendLine("model=${job.model}")
            appendLine("pipeline=${job.pipeline}")
            appendLine("target=${job.target_language.ifBlank { "app language" }}")
            appendLine("source=${TranslationLanguages.sourceDisplayLabel(job.source_language)}")
            message?.takeIf { it.isNotBlank() }?.let {
                appendLine("message=$it")
            }
        }.trimEnd()
        return TranslationNotificationState(
            title = title,
            text = text,
            bigText = if (hideContent) text else bigText,
            progressMax = safeTotal.toInt().coerceAtLeast(0),
            progressCurrent = safeCurrent.toInt().coerceAtLeast(0),
            indeterminate = safeTotal <= 0,
        )
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

data class TranslationRetryDecision(
    val allowed: Boolean,
    val nextStatus: TranslationJobStatus?,
    val startPolicy: TranslationWorkStartPolicy?,
)

object TranslationRetryPlanner {
    fun manualRetry(setupReady: Boolean): TranslationRetryDecision {
        return if (setupReady) {
            TranslationRetryDecision(
                allowed = true,
                nextStatus = TranslationJobStatus.Queued,
                startPolicy = TranslationWorkStartPolicy.Replace,
            )
        } else {
            TranslationRetryDecision(
                allowed = false,
                nextStatus = null,
                startPolicy = null,
            )
        }
    }

    fun autoRequeueAfterSetup(
        status: TranslationJobStatus,
        setupReady: Boolean,
    ): TranslationRetryDecision {
        return if (setupReady && status.canAutoRequeueAfterSetup()) {
            TranslationRetryDecision(
                allowed = true,
                nextStatus = TranslationJobStatus.Queued,
                startPolicy = TranslationWorkStartPolicy.Replace,
            )
        } else {
            TranslationRetryDecision(
                allowed = false,
                nextStatus = null,
                startPolicy = null,
            )
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
