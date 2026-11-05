package eu.kanade.tachiyomi.data.translation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.translation.AiProvider
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64

/**
 * Client for Google's Gemini image models ("Nano Banana") using the `generateContent` REST endpoint.
 *
 * Security notes:
 * - The key is only sent in the `x-goog-api-key` header, never in the URL.
 * - Neither the key nor image data is ever logged by this class.
 * - The shared Mihon client logs request headers when "verbose logging" is on, so the derived client used here
 *   drops that logging interceptor.
 */
@Inject
@SingleIn(AppScope::class)
class NanoBananaClient(
    private val networkHelper: NetworkHelper,
    private val json: Json,
) {

    private val client: OkHttpClient by lazy { buildGeminiHttpClient(networkHelper) }

    /**
     * Sends one page for translation and returns the translated page.
     *
     * Retries rate limits, 5xx errors and network errors up to 3 times with exponential backoff
     * (honouring Google's retry hint, capped at 60 s). Other errors are thrown immediately.
     *
     * @param image the page, already downscaled if needed.
     * @throws TranslationException for all expected failures.
     */
    suspend fun translatePage(
        image: ByteArray,
        mimeType: String,
        apiKey: String,
        model: String,
        language: String,
        promptContext: String = "",
        provider: AiProvider = AiProvider.GEMINI,
        baseUrl: String = "",
        arabicStyle: String = eu.kanade.domain.translation.TranslationOptions.ARABIC_STYLE_BALADI,
    ): TranslatedImage {
        if (provider != AiProvider.GEMINI) {
            throw TranslationException.UnsupportedCapability("redraw mode currently requires a Gemini image model")
        }
        if (!isValidApiKey(apiKey)) {
            throw TranslationException.InvalidApiKey("key is empty or contains invalid characters")
        }

        val requestBody = json.encodeToString(buildRequest(image, mimeType, language, promptContext, arabicStyle))
            .toRequestBody(JSON_MEDIA_TYPE)

        var attempt = 0
        while (true) {
            try {
                return executeOnce(requestBody, apiKey, model)
            } catch (e: TranslationException) {
                if (attempt >= e.maxRetries) throw e
                attempt++
                currentCoroutineContext().ensureActive()
                delay(backoffMillis(attempt, e.retryAfterMillis))
            }
        }
    }

    private suspend fun executeOnce(
        body: okhttp3.RequestBody,
        apiKey: String,
        model: String,
    ): TranslatedImage {
        val request = POST(
            url = "$GEMINI_BASE_URL/$model:generateContent",
            headers = Headers.headersOf("x-goog-api-key", apiKey.trim()),
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
                throw NanoBananaResponseParser.parseError(it.code, text, json, it.header("Retry-After"))
            }
            return NanoBananaResponseParser.parseImage(text, json)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private const val BASE_BACKOFF_MILLIS = 2_000L
        private const val MAX_BACKOFF_MILLIS = 60_000L

        /** Prompt sent with every page. Tweak here. `{language}` is replaced with the target language name. */
        const val PROMPT_TEMPLATE =
            "Translate all text in this manga page into {language}. Keep the artwork, panel layout, " +
                "speech bubble shapes and style exactly the same. Replace the original text with the translated " +
                "text, matching the original lettering style and fitting it inside the bubbles. Do not add, " +
                "remove, crop or redraw any other part of the image. Return the full page as an image."

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
            generationConfig = GenerationConfig(responseModalities = listOf("TEXT", "IMAGE")),
        )

        internal fun backoffMillis(attempt: Int, retryAfterMillis: Long?): Long {
            val exponential = BASE_BACKOFF_MILLIS shl (attempt - 1).coerceAtMost(5)
            return (retryAfterMillis ?: exponential).coerceIn(BASE_BACKOFF_MILLIS, MAX_BACKOFF_MILLIS)
        }
    }
}
