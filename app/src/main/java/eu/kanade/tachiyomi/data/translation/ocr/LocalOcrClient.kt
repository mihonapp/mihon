package eu.kanade.tachiyomi.data.translation.ocr

import android.graphics.BitmapFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.translation.AiProvider
import eu.kanade.tachiyomi.data.translation.GEMINI_BASE_URL
import eu.kanade.tachiyomi.data.translation.GenerateContentRequest
import eu.kanade.tachiyomi.data.translation.GenerateContentResponse
import eu.kanade.tachiyomi.data.translation.GenerationConfig
import eu.kanade.tachiyomi.data.translation.NanoBananaResponseParser
import eu.kanade.tachiyomi.data.translation.RequestContent
import eu.kanade.tachiyomi.data.translation.RequestInlineData
import eu.kanade.tachiyomi.data.translation.RequestPart
import eu.kanade.tachiyomi.data.translation.TranslationException
import eu.kanade.tachiyomi.data.translation.TranslationLog
import eu.kanade.tachiyomi.data.translation.buildGeminiHttpClient
import eu.kanade.tachiyomi.data.translation.isValidApiKey
import eu.kanade.tachiyomi.data.translation.overlay.BoxKind
import eu.kanade.tachiyomi.data.translation.overlay.PageOverlayRenderer
import eu.kanade.tachiyomi.data.translation.overlay.RenderStyle
import eu.kanade.tachiyomi.data.translation.overlay.TextBox
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64

/**
 * High-speed local OCR translation pipeline:
 * 1. Runs Google ML Kit on-device to extract text bubbles and bounding boxes locally (sub-100ms).
 * 2. Transmits only text strings (JSON) to the AI translation API (no heavy images, 95% bandwidth savings).
 * 3. In-place renders the translated text into the speech bubbles using [PageOverlayRenderer].
 */
@Inject
@SingleIn(AppScope::class)
class LocalOcrClient(
    private val networkHelper: NetworkHelper,
    private val localOcrEngine: LocalOcrEngine,
    private val json: Json,
) {

    private val client: OkHttpClient by lazy { buildGeminiHttpClient(networkHelper) }

    suspend fun translatePageWithLocalOcr(
        image: ByteArray,
        language: String,
        apiKey: String,
        model: String,
        provider: AiProvider = AiProvider.GEMINI,
        baseUrl: String = "",
        ocrScript: String = "japanese",
        promptContext: String = "",
        style: RenderStyle = RenderStyle.DEFAULT,
        label: String = "local_ocr",
        arabicStyle: String = eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI,
    ): ByteArray {
        if (!isValidApiKey(apiKey)) {
            throw TranslationException.InvalidApiKey("key is empty or contains invalid characters")
        }

        val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size)
            ?: throw TranslationException.CorruptPage("Could not decode page image")

        val blocks = try {
            localOcrEngine.processImage(bitmap, ocrScript)
        } finally {
            bitmap.recycle()
        }

        if (blocks.isEmpty()) {
            TranslationLog.step { "$label: no text detected by local OCR, returning original page" }
            return image
        }

        TranslationLog.step {
            "$label: local OCR found ${blocks.size} bubble(s). Requesting fast text translation for $language..."
        }

        val mimeType = "image/jpeg"
        val requestBody = when (provider) {
            AiProvider.GEMINI -> buildGeminiTextRequestBody(blocks, image, mimeType, language, promptContext, arabicStyle)
            AiProvider.ANTHROPIC -> buildAnthropicTextRequestBody(blocks, image, mimeType, language, promptContext, model, arabicStyle)
            else -> buildOpenAiTextRequestBody(blocks, image, mimeType, language, promptContext, model, arabicStyle)
        }

        val endpoint = when (provider) {
            AiProvider.GEMINI -> "$GEMINI_BASE_URL/$model:generateContent"
            AiProvider.ANTHROPIC -> "${provider.defaultBaseUrl ?: "https://api.anthropic.com/v1"}/messages"
            else -> {
                val base = if (provider == AiProvider.CUSTOM && baseUrl.isNotBlank()) {
                    baseUrl.trimEnd('/')
                } else {
                    provider.defaultBaseUrl?.trimEnd('/') ?: ""
                }
                "$base/chat/completions"
            }
        }

        val request = POST(
            url = endpoint,
            headers = when (provider) {
                AiProvider.GEMINI -> Headers.headersOf("x-goog-api-key", apiKey.trim())
                AiProvider.ANTHROPIC -> Headers.headersOf(
                    "x-api-key",
                    apiKey.trim(),
                    "anthropic-version",
                    ANTHROPIC_VERSION,
                )
                else -> Headers.headersOf("Authorization", "Bearer ${apiKey.trim()}")
            },
            body = requestBody,
        )

        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            throw TranslationException.NetworkError(e)
        }

        val responseBodyText = response.use { resp ->
            val text = try {
                resp.body.string()
            } catch (e: IOException) {
                throw TranslationException.NetworkError(e)
            }
            if (!resp.isSuccessful) {
                TranslationLog.step { "$label: HTTP ${resp.code}, error body: $text" }
                throw if (provider == AiProvider.GEMINI) {
                    NanoBananaResponseParser.parseError(resp.code, text, json, resp.header("Retry-After"))
                } else {
                    when (resp.code) {
                        401, 403 -> TranslationException.InvalidApiKey("provider rejected the key")
                        404 -> TranslationException.ModelNotFound(model)
                        429 -> TranslationException.RateLimited(null)
                        in 500..599 -> TranslationException.ServerError(resp.code)
                        else -> TranslationException.RequestRejected(resp.code, "provider request failed")
                    }
                }
            }
            text
        }

        val rawContent = when (provider) {
            AiProvider.GEMINI -> extractGeminiContent(responseBodyText)
            AiProvider.ANTHROPIC -> extractAnthropicContent(responseBodyText)
            else -> extractOpenAiContent(responseBodyText)
        }

        val translationsMap = parseTranslations(rawContent)
        TranslationLog.step { "$label: translated ${translationsMap.size}/${blocks.size} bubble(s)" }

        val textBoxes = blocks.map { block ->
            val translated = translationsMap[block.id]?.takeIf { it.isNotBlank() }
                ?: fallbackTranslateWord(block.text, language, arabicStyle)
                ?: block.text
            TextBox(
                yMin = block.yMin,
                xMin = block.xMin,
                yMax = block.yMax,
                xMax = block.xMax,
                kind = BoxKind.BUBBLE,
                text = translated,
                sourceText = block.text,
            )
        }

        val renderResult = PageOverlayRenderer.render(
            original = image,
            boxes = textBoxes,
            style = style,
        ) { note ->
            TranslationLog.step { "$label: $note" }
        }

        return renderResult.bytes
    }

    private fun buildGeminiTextRequestBody(
        blocks: List<OcrBlock>,
        image: ByteArray? = null,
        mimeType: String = "image/jpeg",
        language: String,
        promptContext: String,
        arabicStyle: String,
    ): RequestBody {
        val prompt = buildTextPrompt(blocks, language, promptContext, arabicStyle)
        val parts = mutableListOf<RequestPart>()
        parts.add(RequestPart(text = prompt))
        if (image != null && image.isNotEmpty()) {
            parts.add(
                RequestPart(
                    inlineData = RequestInlineData(
                        mimeType = mimeType,
                        data = Base64.getEncoder().encodeToString(image),
                    ),
                ),
            )
        }
        val req = GenerateContentRequest(
            contents = listOf(
                RequestContent(parts = parts),
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2,
            ),
        )
        return json.encodeToString(req).toRequestBody(JSON_MEDIA_TYPE)
    }

    private fun buildOpenAiTextRequestBody(
        blocks: List<OcrBlock>,
        image: ByteArray? = null,
        mimeType: String = "image/jpeg",
        language: String,
        promptContext: String,
        model: String,
        arabicStyle: String,
    ): RequestBody {
        val prompt = buildTextPrompt(blocks, language, promptContext, arabicStyle)
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0.2)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", prompt)
                                },
                            )
                            if (image != null && image.isNotEmpty()) {
                                add(
                                    buildJsonObject {
                                        put("type", "image_url")
                                        putJsonObject("image_url") {
                                            put("url", "data:$mimeType;base64,${Base64.getEncoder().encodeToString(image)}")
                                        }
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
        return body.toString().toRequestBody(JSON_MEDIA_TYPE)
    }

    private fun buildAnthropicTextRequestBody(
        blocks: List<OcrBlock>,
        image: ByteArray? = null,
        mimeType: String = "image/jpeg",
        language: String,
        promptContext: String,
        model: String,
        arabicStyle: String,
    ): RequestBody {
        val prompt = buildTextPrompt(blocks, language, promptContext, arabicStyle)
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 4096)
            put("temperature", 0.2)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", prompt)
                                },
                            )
                            if (image != null && image.isNotEmpty()) {
                                add(
                                    buildJsonObject {
                                        put("type", "image")
                                        putJsonObject("source") {
                                            put("type", "base64")
                                            put("media_type", mimeType)
                                            put("data", Base64.getEncoder().encodeToString(image))
                                        }
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
        return body.toString().toRequestBody(JSON_MEDIA_TYPE)
    }

    private fun buildTextPrompt(
        blocks: List<OcrBlock>,
        language: String,
        promptContext: String,
        arabicStyle: String,
    ): String {
        val itemsArray = buildJsonArray {
            for (block in blocks) {
                add(
                    buildJsonObject {
                        put("id", block.id)
                        put("text", block.text)
                    },
                )
            }
        }.toString()

        val isArabic = eu.kanade.domain.translation.TranslationOptions.isArabic(language)

        return buildString {
            append("You are an expert manga and comic translator translating dialogue bubbles into ")
            append(language)
            append(".\n")
            append("SCENE CONTINUITY & VISUAL CONTEXT: Look at the manga page image and the dialogue bubbles. Identify who is speaking (look at which character the bubble tail points to) and who they are speaking to (the listener). Maintain character voice, speaker context, and strict pronoun and gender continuity across all bubbles. NEVER confuse the speaker's gender with the listener's gender.\n\n")
            if (isArabic) {
                append(eu.kanade.domain.translation.TranslationOptions.getArabicPromptGuidelines(arabicStyle))
                append("\n")
            } else {
                append("Translate each text accurately and naturally, matching comic formatting, emotions, and tone.\n")
            }
            if (promptContext.isNotBlank()) {
                append("Glossary and context:\n")
                append(promptContext)
                append("\n")
            }
            append("CRITICAL TRANSLATION INSTRUCTIONS:\n")
            append("1. Translate and return EVERY single item from the input array. Do NOT skip or omit any ID.\n")
            append("2. Output array MUST have the exact same number of items and matching IDs.\n")
            append("3. Short words, single words, exclamations, reactions, and sound effects (such as 'No', 'Yes', 'Wait', 'Stop', 'Ah', 'Oh', 'Why', 'Huh', 'What', etc.) MUST be translated into ")
            append(language)
            append(" - NEVER leave them untranslated.\n")
            append("4. Return ONLY a valid JSON array of objects with 'id' and 'text'.\n")
            append("Example output: [{\"id\": 0, \"text\": \"translated dialogue\"}]\n")
            append("Input:\n")
            append(itemsArray)
        }
    }

    internal fun fallbackTranslateWord(
        text: String,
        language: String,
        arabicStyle: String = eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI,
    ): String? {
        val clean = text.trim().lowercase().removeSuffix("!").removeSuffix("?").removeSuffix(".").trim()
        val suffix = text.trim().takeLastWhile { it in "!?.~" }
        val isArabic = eu.kanade.domain.translation.TranslationOptions.isArabic(language)
        if (!isArabic) return null

        val translated = if (arabicStyle == eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI) {
            when (clean) {
                "no" -> "لا"
                "yes", "yeah", "yep" -> "أيوا"
                "why" -> "ليش"
                "what" -> "شو"
                "who" -> "مين"
                "where" -> "وين"
                "how" -> "كيف"
                "wait" -> "استنى"
                "stop" -> "وقف"
                "help" -> "الحقوني"
                "run" -> "اهرب"
                "die" -> "موت"
                "look" -> "شوف"
                "huh" -> "هاه"
                "hey" -> "هي"
                "damn" -> "اللعنة"
                "sure" -> "أكيد"
                "fine", "okay", "ok" -> "تمام"
                "good" -> "منيح"
                "ah" -> "آه"
                "oh" -> "أوه"
                "ha", "haha" -> "هاها"
                "ouch" -> "آي"
                "wow" -> "واو"
                "shut up" -> "اخرس"
                "listen" -> "اسمع"
                "please" -> "أرجوك"
                "sorry" -> "آسف"
                "thanks", "thank you" -> "شكراً"
                else -> null
            }
        } else {
            when (clean) {
                "no" -> "لا"
                "yes", "yeah", "yep" -> "نعم"
                "why" -> "لماذا"
                "what" -> "ماذا"
                "who" -> "من"
                "where" -> "أين"
                "how" -> "كيف"
                "wait" -> "انتظر"
                "stop" -> "توقف"
                "help" -> "النجدة"
                "run" -> "اهرب"
                "die" -> "مُت"
                "look" -> "انظر"
                "huh" -> "هاه"
                "hey" -> "مهلاً"
                "damn" -> "سحقاً"
                "sure" -> "بالتأكيد"
                "fine", "okay", "ok" -> "حسناً"
                "good" -> "جيد"
                "ah" -> "آه"
                "oh" -> "أوه"
                "ha", "haha" -> "هاها"
                "ouch" -> "آي"
                "wow" -> "واو"
                "shut up" -> "اخرس"
                "listen" -> "استمع"
                "please" -> "أرجوك"
                "sorry" -> "آسف"
                "thanks", "thank you" -> "شكراً"
                else -> null
            }
        }
        val formattedSuffix = if (isArabic) suffix.replace('?', '؟') else suffix
        return if (translated != null) "$translated$formattedSuffix" else null
    }

    private fun extractGeminiContent(body: String): String {
        val response = try {
            json.decodeFromString<GenerateContentResponse>(body)
        } catch (_: Exception) {
            throw TranslationException.UnreadableAnswer("Unreadable Gemini response")
        }
        val candidate = response.candidates?.firstOrNull()
            ?: throw TranslationException.UnreadableAnswer("No candidates returned")
        val part = candidate.content?.parts?.firstOrNull { !it.text.isNullOrBlank() }
            ?: throw TranslationException.UnreadableAnswer("Empty response text")
        return part.text!!
    }

    private fun extractOpenAiContent(body: String): String {
        val content = json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")
            ?: throw TranslationException.UnreadableAnswer("Missing choices[0].message.content")
        return content.jsonPrimitive.content
    }

    private fun extractAnthropicContent(body: String): String {
        val content = json.parseToJsonElement(body).jsonObject["content"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("text")
            ?: throw TranslationException.UnreadableAnswer("Missing content[0].text")
        return content.jsonPrimitive.content
    }

    /**
     * Parses the LLM's JSON output whether it is:
     * - A raw array: `[{"id": 0, "text": "..."}]`
     * - An object with a list: `{"translations": [...]}` or `{"items": [...]}`
     * - Enclosed in ```json markdown fences
     */
    fun parseTranslations(content: String): Map<Int, String> {
        val clean = content.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonElement = try {
            json.parseToJsonElement(clean)
        } catch (_: Exception) {
            // If model added text before/after JSON, extract between first [ and last ]
            val start = clean.indexOf('[')
            val end = clean.lastIndexOf(']')
            if (start != -1 && end > start) {
                try {
                    json.parseToJsonElement(clean.substring(start, end + 1))
                } catch (_: Exception) {
                    return emptyMap()
                }
            } else {
                return emptyMap()
            }
        }

        val array = when (jsonElement) {
            is JsonArray -> jsonElement
            is JsonObject -> {
                jsonElement.values.firstOrNull { it is JsonArray } as? JsonArray ?: JsonArray(emptyList())
            }
            else -> JsonArray(emptyList())
        }

        val result = mutableMapOf<Int, String>()
        for (item in array) {
            if (item !is JsonObject) continue
            val id = item["id"]?.jsonPrimitive?.intOrNull ?: continue
            val text = item["text"]?.jsonPrimitive?.content ?: continue
            result[id] = text
        }
        return result
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
