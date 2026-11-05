package eu.kanade.tachiyomi.data.translation.overlay

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.translation.AiProvider
import eu.kanade.tachiyomi.data.translation.GEMINI_BASE_URL
import eu.kanade.tachiyomi.data.translation.GenerateContentRequest
import eu.kanade.tachiyomi.data.translation.GenerationConfig
import eu.kanade.tachiyomi.data.translation.NanoBananaClient
import eu.kanade.tachiyomi.data.translation.NanoBananaResponseParser
import eu.kanade.tachiyomi.data.translation.RequestContent
import eu.kanade.tachiyomi.data.translation.RequestInlineData
import eu.kanade.tachiyomi.data.translation.RequestPart
import eu.kanade.tachiyomi.data.translation.TranslationException
import eu.kanade.tachiyomi.data.translation.TranslationLog
import eu.kanade.tachiyomi.data.translation.buildGeminiHttpClient
import eu.kanade.tachiyomi.data.translation.isValidApiKey
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
 * Free tier friendly translation mode: asks a Gemini text model for the position and translation of every text box
 * on a page as JSON. The app draws the translation itself, see [PageOverlayRenderer].
 *
 * Requests are spaced out by a process wide [RequestPacer]. Neither the key nor image data is logged.
 */
@Inject
@SingleIn(AppScope::class)
class TextOverlayClient(
    private val networkHelper: NetworkHelper,
    private val json: Json,
) {

    private val client: OkHttpClient by lazy { buildGeminiHttpClient(networkHelper) }
    private val pacer = RequestPacer()

    /**
     * @param baseIntervalMillis minimum time between two requests (0 for paid keys).
     * @param label names the request in the translation log, for example `page 3 crop 1/2`.
     * @return all text boxes found on the page; empty if the page has no text.
     * @throws TranslationException for all expected failures.
     */
    suspend fun detectAndTranslate(
        image: ByteArray,
        mimeType: String,
        apiKey: String,
        model: String,
        language: String,
        baseIntervalMillis: Long,
        promptContext: String = "",
        provider: AiProvider = AiProvider.GEMINI,
        baseUrl: String = "",
        label: String = "request",
        arabicStyle: String = eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI,
    ): List<TextBox> {
        if (!isValidApiKey(apiKey)) {
            throw TranslationException.InvalidApiKey("key is empty or contains invalid characters")
        }
        // An answer that fails to parse usually fails again when the request is identical, so every retry asks with a
        // little more randomness.
        fun bodyFor(attempt: Int) = (
            if (provider == AiProvider.GEMINI) {
                json.encodeToString(buildRequest(image, mimeType, language, promptContext, temperatureFor(attempt), arabicStyle))
            } else if (provider == AiProvider.ANTHROPIC) {
                buildAnthropicRequest(image, mimeType, language, promptContext, temperatureFor(attempt), arabicStyle).toString()
            } else {
                buildOpenAiRequest(model, image, mimeType, language, promptContext, temperatureFor(attempt), arabicStyle).toString()
            }
            ).toRequestBody(JSON_MEDIA_TYPE)

        TranslationLog.step {
            "$label: sending ${image.size / BYTES_PER_KB} KB $mimeType to $model, " +
                "${promptContext.length} characters of extra prompt context"
        }

        var attempt = 0
        while (true) {
            val queuedAt = System.nanoTime()
            pacer.awaitTurn(baseIntervalMillis)
            val sentAt = System.nanoTime()
            try {
                val boxes = executeOnce(bodyFor(attempt), apiKey, model, provider, baseUrl, label)
                pacer.onSuccess()
                TranslationLog.step {
                    "$label: answer after ${millisBetween(sentAt, System.nanoTime())} ms " +
                        "(${millisBetween(queuedAt, sentAt)} ms waiting for the request pacer), " +
                        "${boxes.size} box(es)"
                }
                boxes.forEachIndexed { index, box ->
                    TranslationLog.step { "$label:   #${index + 1} ${TranslationLog.describe(box)}" }
                }
                return boxes
            } catch (e: TranslationException) {
                TranslationLog.step {
                    "$label: attempt ${attempt + 1} failed after ${millisBetween(sentAt, System.nanoTime())} ms: " +
                        TranslationLog.describe(e, maxFrames = 0)
                }
                if (e is TranslationException.RateLimited) pacer.onRateLimited(e.retryAfterMillis)
                if (attempt >= e.maxRetries) {
                    TranslationLog.step { "$label: giving up after ${attempt + 1} attempt(s)" }
                    throw e
                }
                attempt++
                currentCoroutineContext().ensureActive()
                // Rate limits are already handled by the pacer. Everything else gets a plain backoff.
                if (e !is TranslationException.RateLimited) {
                    val backoff = NanoBananaClient.backoffMillis(attempt, e.retryAfterMillis)
                    TranslationLog.step { "$label: trying again in $backoff ms" }
                    delay(backoff)
                } else {
                    TranslationLog.step { "$label: rate limited, trying again when the request pacer allows it" }
                }
            }
        }
    }

    private suspend fun executeOnce(
        body: RequestBody,
        apiKey: String,
        model: String,
        provider: AiProvider,
        baseUrl: String,
        label: String,
    ): List<TextBox> {
        val endpoint = if (provider == AiProvider.GEMINI) {
            "$GEMINI_BASE_URL/$model:generateContent"
        } else if (provider == AiProvider.ANTHROPIC) {
            "${provider.defaultBaseUrl}/messages"
        } else {
            "${(
                baseUrl.takeIf { provider == AiProvider.CUSTOM && it.isNotBlank() }
                    ?: provider.defaultBaseUrl
                ).orEmpty().trimEnd('/')}/chat/completions"
        }
        val request = POST(
            url = endpoint,
            headers = if (provider == AiProvider.GEMINI) {
                Headers.headersOf("x-goog-api-key", apiKey.trim())
            } else if (provider == AiProvider.ANTHROPIC) {
                Headers.headersOf(
                    "x-api-key",
                    apiKey.trim(),
                    "anthropic-version",
                    ANTHROPIC_VERSION,
                )
            } else {
                Headers.headersOf("Authorization", "Bearer ${apiKey.trim()}")
            },
            body = body,
        )

        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            throw TranslationException.NetworkError(e)
        }

        response.use {
            val text = try {
                it.body.string()
            } catch (e: IOException) {
                throw TranslationException.NetworkError(e)
            }
            if (!it.isSuccessful) {
                TranslationLog.step { "$label: HTTP ${it.code}, ${text.length} characters of answer" }
                throw if (provider == AiProvider.GEMINI) {
                    NanoBananaResponseParser.parseError(it.code, text, json, it.header("Retry-After"))
                } else if (provider == AiProvider.ANTHROPIC) {
                    when (it.code) {
                        401, 403 -> TranslationException.InvalidApiKey("provider rejected the key")
                        404 -> TranslationException.ModelNotFound(model)
                        429 -> TranslationException.RateLimited(null)
                        in 500..599 -> TranslationException.ServerError(it.code)
                        else -> TranslationException.RequestRejected(it.code, "provider request failed")
                    }
                } else {
                    when (it.code) {
                        401, 403 -> TranslationException.InvalidApiKey("provider rejected the key")
                        404 -> TranslationException.ModelNotFound(model)
                        429 -> TranslationException.RateLimited(null)
                        in 500..599 -> TranslationException.ServerError(it.code)
                        else -> TranslationException.RequestRejected(it.code, "provider request failed")
                    }
                }
            }
            val answer = when (provider) {
                AiProvider.GEMINI -> text
                AiProvider.ANTHROPIC -> extractAnthropicContent(text)
                else -> extractOpenAiContent(text)
            }
            return OverlayResponseParser.parse(answer, json) { note ->
                TranslationLog.step { "$label: $note" }
            }
        }
    }

    private fun extractOpenAiContent(body: String): String {
        val content = json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")
            ?: throw TranslationException.UnreadableAnswer("missing choices[0].message.content")
        return content.jsonPrimitive.contentOrNull ?: content.toString()
    }

    private fun extractAnthropicContent(body: String): String {
        return json.parseToJsonElement(body).jsonObject["content"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            ?: throw TranslationException.UnreadableAnswer("missing content[0].text")
    }

    private fun millisBetween(from: Long, to: Long) = (to - from) / NANOS_PER_MILLI

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val BYTES_PER_KB = 1024
        private const val DEFAULT_TEMPERATURE = 0.1
        private const val RETRY_TEMPERATURE_STEP = 0.25
        private const val MAX_TEMPERATURE = 0.7
        private const val ANTHROPIC_VERSION = "2023-06-01"

        /** 0.1 for the first request, then 0.35, 0.6 and so on up to 0.7. */
        internal fun temperatureFor(attempt: Int): Double =
            (DEFAULT_TEMPERATURE + attempt * RETRY_TEMPERATURE_STEP).coerceAtMost(MAX_TEMPERATURE)
        private const val NANOS_PER_MILLI = 1_000_000L

        /** Prompt sent with every page. Tweak here. `{language}` is replaced with the target language name. */
        const val PROMPT_TEMPLATE =
            "You are translating a manga or comic page. Find every piece of text on the page: speech bubbles, " +
                "thought bubbles, narration or caption boxes and sound effects. Translate each into {language}.\n" +
                "Images may be crops from tall pages. Scan each crop fully, top to bottom, including text near or " +
                "cut off by crop edges. Translate only readable words; never invent text outside the crop.\n" +
                "Rules:\n" +
                "- Return one entry per bubble or text box, not one per line. Put all the original text of a " +
                "bubble into one box.\n" +
                "- First transcribe the exact visible source lettering, then translate it. Every readable phrase " +
                "must have one entry; never omit a bubble, thought, caption, label, " +
                "screen text, or stylised lettering.\n" +
                "- \"box_2d\" is [ymin, xmin, ymax, xmax] on a 0-1000 grid. Enclose all visible lettering, including " +
                "its outline or shadow, with little padding. Do NOT box the whole bubble, panel, sign, or empty " +
                "space. Never include a face, hair, body, or artwork in a text box. Check that each box actually " +
                "covers the source lettering and nothing else before replying.\n" +
                "- \"kind\" is \"bubble\" for speech or thought bubbles, \"caption\" for narration boxes, " +
                "game or system windows, signs, labels and any other readable text. Use \"sfx\" ONLY for large " +
                "stylised onomatopoeia drawn as part of the artwork (impact or sound effects), never for sentences.\n" +
                "- \"source_text\" is an exact transcription in the original language. \"text\" is its natural, " +
                "grammatically correct translation into {language}; no notes or invented words.\n" +
                "- Maintain strict pronoun, gender, and adjective agreement across all dialogue boxes on the page.\n" +
                "- Proofread every translation before replying: correct spelling and standard grammar, no typos " +
                "and no invented or misspelled words.\n" +
                "- Include ALL text, also text outside bubbles on plain backgrounds, stylised, outlined, bold or " +
                "coloured lettering, credits, website addresses, closing messages and chapter titles. Do not " +
                "skip any line, even if it looks like a title or a decoration.\n" +
                "- Before replying, do a second top-to-bottom scan and verify every visible readable text region " +
                "appears exactly once. A readable page or crop must not return an empty boxes list.\n" +
                "- Ignore watermarks and logos of reading apps or websites, page numbers and decorations that are " +
                "not part of the story.\n" +
                "- List the entries in reading order.\n" +
                "Reply with JSON only, in this format: " +
                "{\"boxes\":[{\"box_2d\":[ymin,xmin,ymax,xmax]," +
                "\"kind\":\"bubble\",\"source_text\":\"...\",\"text\":\"...\"}]}\n" +
                "If the page has no text reply {\"boxes\":[]}."

        /**
         * Schema sent as `responseSchema`. With it Gemini cannot leave a quote unescaped or cut a string in half,
         * which was the cause of the "Invalid JSON" failures that repeated on every retry.
         */
        internal val RESPONSE_SCHEMA: JsonElement = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("boxes") {
                    put("type", "ARRAY")
                    putJsonObject("items") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("box_2d") {
                                put("type", "ARRAY")
                                putJsonObject("items") { put("type", "INTEGER") }
                            }
                            putJsonObject("kind") { put("type", "STRING") }
                            putJsonObject("source_text") { put("type", "STRING") }
                            putJsonObject("text") { put("type", "STRING") }
                        }
                        putJsonArray("required") {
                            add(kotlinx.serialization.json.JsonPrimitive("box_2d"))
                            add(kotlinx.serialization.json.JsonPrimitive("kind"))
                            add(kotlinx.serialization.json.JsonPrimitive("source_text"))
                            add(kotlinx.serialization.json.JsonPrimitive("text"))
                        }
                    }
                }
            }
            putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("boxes")) }
        }

        private val OPENAI_RESPONSE_SCHEMA: JsonElement = buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
            putJsonObject("properties") {
                putJsonObject("boxes") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        put("additionalProperties", false)
                        putJsonObject("properties") {
                            putJsonObject("box_2d") {
                                put("type", "array")
                                putJsonObject("items") { put("type", "integer") }
                            }
                            putJsonObject("kind") { put("type", "string") }
                            putJsonObject("source_text") { put("type", "string") }
                            putJsonObject("text") { put("type", "string") }
                        }
                        putJsonArray("required") {
                            add(kotlinx.serialization.json.JsonPrimitive("box_2d"))
                            add(kotlinx.serialization.json.JsonPrimitive("kind"))
                            add(kotlinx.serialization.json.JsonPrimitive("source_text"))
                            add(kotlinx.serialization.json.JsonPrimitive("text"))
                        }
                    }
                }
            }
            putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("boxes")) }
        }

        internal fun buildAnthropicRequest(
            image: ByteArray,
            mimeType: String,
            language: String,
            extra: String = "",
            temperature: Double = DEFAULT_TEMPERATURE,
            arabicStyle: String = eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI,
        ): JsonElement = buildJsonObject {
            put("max_tokens", 4096)
            put("temperature", temperature)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", buildPrompt(language, extra, arabicStyle))
                                },
                            )
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
                    },
                )
            }
        }

        internal fun buildOpenAiRequest(
            model: String,
            image: ByteArray,
            mimeType: String,
            language: String,
            extra: String = "",
            temperature: Double = DEFAULT_TEMPERATURE,
            arabicStyle: String = eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI,
        ): JsonElement = buildJsonObject {
            put("model", model)
            put("temperature", temperature)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", buildPrompt(language, extra, arabicStyle))
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") {
                                        put("url", "data:$mimeType;base64,${Base64.getEncoder().encodeToString(image)}")
                                    }
                                },
                            )
                        }
                    },
                )
            }
            putJsonObject("response_format") {
                put("type", "json_schema")
                putJsonObject("json_schema") {
                    put("name", "translation_boxes")
                    put("strict", true)
                    put("schema", OPENAI_RESPONSE_SCHEMA)
                }
            }
        }

        fun buildPrompt(
            language: String,
            extra: String = "",
            arabicStyle: String = eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI,
        ): String {
            val base = PROMPT_TEMPLATE.replace("{language}", language)
            val arabicPart = if (eu.kanade.domain.translation.TranslationOptions.isArabic(language)) {
                "\n\n" + eu.kanade.domain.translation.TranslationOptions.getArabicPromptGuidelines(arabicStyle)
            } else {
                ""
            }
            val prompt = "$base$arabicPart"
            return if (extra.isBlank()) prompt else "$prompt\n\n$extra"
        }

        fun buildRequest(
            image: ByteArray,
            mimeType: String,
            language: String,
            extra: String = "",
            temperature: Double = DEFAULT_TEMPERATURE,
            arabicStyle: String = eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI,
        ) = GenerateContentRequest(
            contents = listOf(
                RequestContent(
                    parts = listOf(
                        RequestPart(text = buildPrompt(language, extra, arabicStyle)),
                        RequestPart(
                            inlineData = RequestInlineData(
                                mimeType = mimeType,
                                data = Base64.getEncoder().encodeToString(image),
                            ),
                        ),
                    ),
                ),
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = temperature,
                responseSchema = RESPONSE_SCHEMA,
            ),
        )
    }
}
