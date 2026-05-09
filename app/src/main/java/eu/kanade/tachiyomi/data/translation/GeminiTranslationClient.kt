package eu.kanade.tachiyomi.data.translation

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.jsonMime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers.Companion.headersOf
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.domain.translation.service.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.TimeSource

class GeminiTranslationClient(
    private val network: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
    private val repository: TranslationRepository = Injekt.get(),
    private val preferences: TranslationPreferences = Injekt.get(),
) {
    suspend fun listModels(apiKey: String): List<GeminiModel> {
        val response = executeGeminiText(
            GET(
                "$BASE_URL/models?pageSize=1000",
                headers = apiHeaders(apiKey),
            ),
            operation = "listModels",
            model = null,
            requestJson = null,
            requestSummary = "page_size=1000",
            jobId = null,
            pageId = null,
        )
        return with(json) {
            decodeFromString<GeminiListModelsResponse>(response)
        }.models.generateContentModels()
    }

    suspend fun testGenerateContent(apiKey: String, model: String) {
        val request = GeminiGenerateContentRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = "Reply with OK.")),
                ),
            ),
            generationConfig = buildJsonObject {
                put("temperature", 0)
                put("maxOutputTokens", 8)
            },
        )
        val response = generateContent(
            apiKey = apiKey,
            model = model,
            request = request,
            operation = "testGenerateContent",
            requestSummary = "prompt=Reply with OK.\nconfig=temperature=0,maxOutputTokens=8",
        )
        val hasText = response.candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.any { !it.text.isNullOrBlank() }
            ?: false
        check(hasText) { "Gemini model test returned no text" }
    }

    suspend fun translatePageImage(
        apiKey: String,
        model: String,
        imageBytes: ByteArray,
        mimeType: String,
        targetLanguage: String,
        sourceLanguage: String?,
        generationConfig: TranslationGenerationConfig,
        extraInstructions: String,
        jobId: Long? = null,
        pageId: Long? = null,
    ): TranslationOverlayResult {
        val prompt = TranslationPromptPolicy.pagePrompt(targetLanguage, sourceLanguage)
        val systemPrompt = TranslationPromptPolicy.systemPrompt(extraInstructions)
        val config = generationConfig
            .copy(rawJsonOverride = generationConfig.rawJsonOverride)
            .toGeminiJson(json)
            .withStructuredOverlaySchema()
        val request = GeminiGenerateContentRequest(
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = systemPrompt)),
            ),
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt),
                        GeminiPart(
                            inlineData = GeminiInlineData(
                                mimeType = mimeType,
                                data = Base64.encodeToString(imageBytes, Base64.NO_WRAP),
                            ),
                        ),
                    ),
                ),
            ),
            generationConfig = config,
        )
        return generateOverlay(
            apiKey = apiKey,
            model = model,
            request = request,
            operation = "translatePageImage",
            requestSummary = buildString {
                appendLine("system_prompt:")
                appendLine(systemPrompt)
                appendLine("prompt:")
                appendLine(prompt)
                appendLine("image_mime=$mimeType")
                appendLine("image_bytes=${imageBytes.size}")
                appendLine("generation_config=$config")
            },
            jobId = jobId,
            pageId = pageId,
        )
    }

    suspend fun translateOcrBlocks(
        apiKey: String,
        model: String,
        blocks: List<OcrTextBlock>,
        targetLanguage: String,
        sourceLanguage: String?,
        generationConfig: TranslationGenerationConfig,
        extraInstructions: String,
        jobId: Long? = null,
        pageId: Long? = null,
    ): TranslationOverlayResult {
        val prompt = buildString {
            appendLine(TranslationPromptPolicy.pagePrompt(targetLanguage, sourceLanguage))
            appendLine("Translate these OCR blocks and preserve each id and box:")
            blocks.forEach { block ->
                appendLine(
                    "${block.id}: [${block.x},${block.y},${block.width},${block.height}] ${block.text}",
                )
            }
        }
        val systemPrompt = TranslationPromptPolicy.systemPrompt(extraInstructions)
        val request = GeminiGenerateContentRequest(
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = systemPrompt)),
            ),
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = generationConfig.toGeminiJson(json).withStructuredOverlaySchema(),
        )
        return generateOverlay(
            apiKey = apiKey,
            model = model,
            request = request,
            operation = "translateOcrBlocks",
            requestSummary = buildString {
                appendLine("system_prompt:")
                appendLine(systemPrompt)
                appendLine("prompt:")
                appendLine(prompt)
                appendLine("ocr_blocks=${blocks.size}")
                appendLine("generation_config=${request.generationConfig}")
            },
            jobId = jobId,
            pageId = pageId,
        )
    }

    suspend fun generateInpaintImage(
        apiKey: String,
        model: String,
        imageBytes: ByteArray,
        mimeType: String,
        overlay: TranslationOverlayResult,
        targetLanguage: String,
        jobId: Long? = null,
        pageId: Long? = null,
    ): ByteArray? {
        val prompt = buildString {
            appendLine("Edit this manga page by replacing original text with the translated text.")
            appendLine("Target language: $targetLanguage.")
            appendLine("Preserve art, panel layout, tone, and reading order.")
            overlay.boxes.forEachIndexed { index, box ->
                appendLine("${index + 1}. ${box.translatedText}")
            }
        }
        val request = GeminiGenerateContentRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt),
                        GeminiPart(
                            inlineData = GeminiInlineData(
                                mimeType = mimeType,
                                data = Base64.encodeToString(imageBytes, Base64.NO_WRAP),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val response = generateContent(
            apiKey = apiKey,
            model = model,
            request = request,
            operation = "generateInpaintImage",
            requestSummary = buildString {
                appendLine("prompt:")
                appendLine(prompt)
                appendLine("image_mime=$mimeType")
                appendLine("image_bytes=${imageBytes.size}")
                appendLine("overlay_boxes=${overlay.boxes.size}")
            },
            jobId = jobId,
            pageId = pageId,
        )
        val imageData = response.candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.firstNotNullOfOrNull { it.inlineData?.data }
            ?: return null
        return Base64.decode(imageData, Base64.DEFAULT)
    }

    private suspend fun generateOverlay(
        apiKey: String,
        model: String,
        request: GeminiGenerateContentRequest,
        operation: String,
        requestSummary: String,
        jobId: Long? = null,
        pageId: Long? = null,
    ): TranslationOverlayResult {
        val response = generateContent(apiKey, model, request, operation, requestSummary, jobId, pageId)
        val text = response.candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.firstNotNullOfOrNull { it.text }
            ?: error("Gemini response did not include text")
        val overlay = TranslationOverlaySanitizer.sanitize(json.decodeFromString<TranslationOverlayResult>(text))
        repository.insertLog(
            jobId = jobId,
            pageId = pageId,
            level = TranslationLogLevel.Debug,
            tag = "api",
            message = "Gemini overlay response parsed",
            details = buildString {
                appendLine("operation=$operation")
                appendLine("model=$model")
                appendLine("source_language=${overlay.sourceLanguage ?: "-"}")
                appendLine("target_language=${overlay.targetLanguage ?: "-"}")
                appendLine("boxes=${overlay.boxes.size}")
                overlay.boxes.forEachIndexed { index, box ->
                    appendLine("${index + 1}. ${box.originalText} => ${box.translatedText}")
                }
            },
        )
        return overlay
    }

    private suspend fun generateContent(
        apiKey: String,
        model: String,
        request: GeminiGenerateContentRequest,
        operation: String,
        requestSummary: String,
        jobId: Long? = null,
        pageId: Long? = null,
    ): GeminiGenerateContentResponse {
        val modelId = model.removePrefix("models/")
        val requestJson = json.encodeToString(request)
        val response = executeGeminiText(
            POST(
                "$BASE_URL/models/$modelId:generateContent",
                headers = apiHeaders(apiKey),
                body = requestJson.toRequestBody(jsonMime),
            ),
            operation = operation,
            model = modelId,
            requestJson = requestJson,
            requestSummary = requestSummary,
            jobId = jobId,
            pageId = pageId,
        )
        val parsed = json.decodeFromString<GeminiGenerateContentResponse>(response)
        repository.insertLog(
            jobId = jobId,
            pageId = pageId,
            level = TranslationLogLevel.Debug,
            tag = "api",
            message = "Gemini generateContent response parsed",
            details = buildString {
                appendLine("operation=$operation")
                appendLine("model=$modelId")
                appendLine("candidate_count=${parsed.candidates.size}")
                parsed.candidates.forEachIndexed { candidateIndex, candidate ->
                    val texts = candidate.content?.parts.orEmpty().mapNotNull { it.text }
                    appendLine("candidate_${candidateIndex + 1}_text_parts=${texts.size}")
                    texts.forEachIndexed { partIndex, text ->
                        appendLine("candidate_${candidateIndex + 1}_text_${partIndex + 1}:")
                        appendLine(text)
                    }
                    val images = candidate.content?.parts.orEmpty().count { it.inlineData?.data != null }
                    appendLine("candidate_${candidateIndex + 1}_image_parts=$images")
                }
            },
        )
        return parsed
    }

    private suspend fun executeGeminiText(
        request: Request,
        operation: String,
        model: String?,
        requestJson: String?,
        requestSummary: String,
        jobId: Long?,
        pageId: Long?,
    ): String {
        val mark = TimeSource.Monotonic.markNow()
        val response = network.client.newCall(request).await()
        val body = response.body.string()
        val elapsedMs = mark.elapsedNow().inWholeMilliseconds
        val endpoint = buildString {
            append(request.url.encodedPath)
            request.url.encodedQuery?.let { query ->
                append("?")
                append(query)
            }
        }
        val rawRequest = requestJson.takeIf { preferences.rawDebugLogging.get() }
        val rawResponse = body.takeIf { preferences.rawDebugLogging.get() }
        return try {
            if (response.isSuccessful) {
                repository.insertLog(
                    jobId = jobId,
                    pageId = pageId,
                    level = TranslationLogLevel.Info,
                    tag = "api",
                    message = "Gemini API call succeeded",
                    details = TranslationLogDetailsFormatter.apiCall(
                        operation = operation,
                        method = request.method,
                        endpoint = endpoint,
                        model = model,
                        statusCode = response.code,
                        elapsedMs = elapsedMs,
                        requestSummary = requestSummary,
                        responseSummary = "response_bytes=${body.length}",
                        rawRequestJson = rawRequest,
                        rawResponseJson = rawResponse,
                    ),
                )
                body
            } else {
                val redactedBody = TranslationLogRedactor.redact(body)
                repository.insertLog(
                    jobId = jobId,
                    pageId = pageId,
                    level = TranslationLogLevel.Error,
                    tag = "api",
                    message = "Gemini API call failed",
                    details = TranslationLogDetailsFormatter.apiCall(
                        operation = operation,
                        method = request.method,
                        endpoint = endpoint,
                        model = model,
                        statusCode = response.code,
                        elapsedMs = elapsedMs,
                        requestSummary = requestSummary,
                        errorBody = redactedBody,
                        rawRequestJson = rawRequest,
                        rawResponseJson = rawResponse,
                    ),
                )
                throw GeminiApiException(
                    code = response.code,
                    errorBody = redactedBody,
                )
            }
        } finally {
            response.close()
        }
    }

    private fun apiHeaders(apiKey: String) = headersOf(
        "x-goog-api-key",
        apiKey,
        "Content-Type",
        "application/json",
    )

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    }
}

class GeminiApiException(
    val code: Int,
    val errorBody: String,
) : IllegalStateException("Gemini API error $code")

private fun JsonElement.withStructuredOverlaySchema(): JsonElement {
    val base = jsonObject
    return buildJsonObject {
        base.forEach { (key, value) -> put(key, value) }
        put("responseMimeType", "application/json")
        put(
            "responseJsonSchema",
            buildJsonObject {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put("sourceLanguage", buildJsonObject { put("type", "string") })
                        put("targetLanguage", buildJsonObject { put("type", "string") })
                        put(
                            "boxes",
                            buildJsonObject {
                                put("type", "array")
                                put(
                                    "items",
                                    buildJsonObject {
                                        put("type", "object")
                                        put(
                                            "properties",
                                            buildJsonObject {
                                                listOf("x", "y", "width", "height", "confidence").forEach {
                                                    put(it, buildJsonObject { put("type", "number") })
                                                }
                                                listOf("originalText", "translatedText", "textType").forEach {
                                                    put(it, buildJsonObject { put("type", "string") })
                                                }
                                            },
                                        )
                                        put(
                                            "required",
                                            kotlinx.serialization.json.buildJsonArray {
                                                listOf(
                                                    "x",
                                                    "y",
                                                    "width",
                                                    "height",
                                                    "originalText",
                                                    "translatedText",
                                                    "textType",
                                                ).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
                put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("boxes")) })
            },
        )
    }
}

@Serializable
private data class GeminiListModelsResponse(
    val models: List<GeminiModel> = emptyList(),
)

@Serializable
private data class GeminiGenerateContentRequest(
    @SerialName("system_instruction")
    val systemInstruction: GeminiContent? = null,
    val contents: List<GeminiContent>,
    val generationConfig: JsonElement? = null,
)

@Serializable
private data class GeminiContent(
    val parts: List<GeminiPart>,
)

@Serializable
private data class GeminiPart(
    val text: String? = null,
    @SerialName("inline_data")
    val inlineData: GeminiInlineData? = null,
)

@Serializable
private data class GeminiInlineData(
    @SerialName("mime_type")
    val mimeType: String,
    val data: String,
)

@Serializable
private data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
)

@Serializable
private data class GeminiCandidate(
    val content: GeminiContent? = null,
)

@Serializable
data class TranslationOverlayResult(
    val sourceLanguage: String? = null,
    val targetLanguage: String? = null,
    val boxes: List<TranslationOverlayBox> = emptyList(),
)

@Serializable
data class TranslationOverlayBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val originalText: String = "",
    val translatedText: String,
    val textType: String = "dialogue",
    val confidence: Float? = null,
)

data class OcrTextBlock(
    val id: String,
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)
