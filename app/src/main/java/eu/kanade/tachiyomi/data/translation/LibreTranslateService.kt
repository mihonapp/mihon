package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.domain.translation.model.TranslationLanguage
import tachiyomi.domain.translation.service.TranslationPreferences

class LibreTranslateService(
    private val networkHelper: NetworkHelper,
    private val preferences: TranslationPreferences,
) : TranslationService {

    override suspend fun translate(
        texts: List<String>,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): List<String> {
        val baseUrl = preferences.libreTranslateUrl.get().trim().trimEnd('/').ifBlank {
            TranslationPreferences.DEFAULT_LIBRE_TRANSLATE_URL
        }
        val apiKey = preferences.libreTranslateApiKey.get().trim()

        val payload = buildJsonObject {
            put("q", JsonArray(texts.map { JsonPrimitive(it) }))
            put("source", if (source == TranslationLanguage.AUTO) "auto" else source.libreCode())
            put("target", target.libreCode())
            put("format", "text")
            if (apiKey.isNotEmpty()) put("api_key", apiKey)
        }

        val request = Request.Builder()
            .url("$baseUrl/translate")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val body = networkHelper.client.executeForBody(request)
        val translated = runCatching {
            Json.Default.parseToJsonElement(body).jsonObject.getValue("translatedText")
        }.getOrNull() ?: throw TranslationException(
            TranslationError.PROVIDER_ERROR,
            "Unexpected response from LibreTranslate",
        )

        val translations = when (translated) {
            is JsonArray -> translated.map { it.jsonPrimitive.content }
            else -> listOf(translated.jsonPrimitive.content)
        }

        if (translations.size != texts.size) {
            throw TranslationException(
                TranslationError.PROVIDER_ERROR,
                "LibreTranslate returned ${translations.size} translations for ${texts.size} texts",
            )
        }
        return translations
    }

    private fun TranslationLanguage.libreCode(): String = when (this) {
        TranslationLanguage.CHINESE_SIMPLIFIED -> "zh"
        TranslationLanguage.CHINESE_TRADITIONAL -> "zt"
        else -> code.substringBefore('-')
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
