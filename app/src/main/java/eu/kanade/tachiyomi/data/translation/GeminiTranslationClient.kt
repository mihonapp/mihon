package eu.kanade.tachiyomi.data.translation

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers.Companion.headersOf
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GeminiTranslationClient(
    private val network: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
) {
    suspend fun listModels(apiKey: String): List<GeminiModel> {
        val response = executeGemini(
            GET(
                "$BASE_URL/models?pageSize=1000",
                headers = apiHeaders(apiKey),
            ),
        )
        return with(json) {
            response.parseAs<GeminiListModelsResponse>()
        }.models.generateContentModels()
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
    ): TranslationOverlayResult {
        val request = GeminiGenerateContentRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = pageTranslationPrompt(targetLanguage, sourceLanguage, extraInstructions)),
                        GeminiPart(
                            inlineData = GeminiInlineData(
                                mimeType = mimeType,
                                data = Base64.encodeToString(imageBytes, Base64.NO_WRAP),
                            ),
                        ),
                    ),
                ),
            ),
            generationConfig = generationConfig
                .copy(rawJsonOverride = generationConfig.rawJsonOverride)
                .toGeminiJson(json)
                .withStructuredOverlaySchema(),
        )
        return generateOverlay(apiKey, model, request)
    }

    suspend fun translateOcrBlocks(
        apiKey: String,
        model: String,
        blocks: List<OcrTextBlock>,
        targetLanguage: String,
        sourceLanguage: String?,
        generationConfig: TranslationGenerationConfig,
        extraInstructions: String,
    ): TranslationOverlayResult {
        val prompt = buildString {
            appendLine(pageTranslationPrompt(targetLanguage, sourceLanguage, extraInstructions))
            appendLine("Translate these OCR blocks and preserve each id and box:")
            blocks.forEach { block ->
                appendLine(
                    "${block.id}: [${block.x},${block.y},${block.width},${block.height}] ${block.text}",
                )
            }
        }
        val request = GeminiGenerateContentRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = generationConfig.toGeminiJson(json).withStructuredOverlaySchema(),
        )
        return generateOverlay(apiKey, model, request)
    }

    suspend fun generateInpaintImage(
        apiKey: String,
        model: String,
        imageBytes: ByteArray,
        mimeType: String,
        overlay: TranslationOverlayResult,
        targetLanguage: String,
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
        val response = generateContent(apiKey, model, request)
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
    ): TranslationOverlayResult {
        val response = generateContent(apiKey, model, request)
        val text = response.candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.firstNotNullOfOrNull { it.text }
            ?: error("Gemini response did not include text")
        return json.decodeFromString<TranslationOverlayResult>(text)
    }

    private suspend fun generateContent(
        apiKey: String,
        model: String,
        request: GeminiGenerateContentRequest,
    ): GeminiGenerateContentResponse {
        val modelId = model.removePrefix("models/")
        val response = executeGemini(
            POST(
                "$BASE_URL/models/$modelId:generateContent",
                headers = apiHeaders(apiKey),
                body = json.encodeToString(request).toRequestBody(jsonMime),
            ),
        )
        return with(json) {
            response.parseAs<GeminiGenerateContentResponse>()
        }
    }

    private suspend fun executeGemini(request: Request): Response {
        val response = network.client.newCall(request).await()
        if (response.isSuccessful) {
            return response
        }
        val errorBody = response.body.string()
        response.close()
        throw GeminiApiException(
            code = response.code,
            errorBody = TranslationLogRedactor.redact(errorBody),
        )
    }

    private fun apiHeaders(apiKey: String) = headersOf(
        "x-goog-api-key",
        apiKey,
        "Content-Type",
        "application/json",
    )

    private fun pageTranslationPrompt(
        targetLanguage: String,
        sourceLanguage: String?,
        extraInstructions: String,
    ): String {
        return buildString {
            appendLine("Translate all visible manga text into ${targetLanguage.ifBlank { "the app language" }}.")
            appendLine("Source language: ${sourceLanguage?.takeIf { it.isNotBlank() } ?: "auto-detect"}.")
            appendLine("Include dialogue, captions, signs, and sound effects.")
            appendLine("Return only JSON matching the schema. Coordinates must be normalized 0.0 to 1.0.")
            appendLine("Each box needs x, y, width, height, originalText, translatedText, textType, confidence.")
            if (extraInstructions.isNotBlank()) {
                appendLine("User glossary/instructions:")
                appendLine(extraInstructions)
            }
        }
    }

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
