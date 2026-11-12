package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request
import tachiyomi.domain.translation.model.TranslationLanguage
import tachiyomi.domain.translation.service.TranslationPreferences

class DeepLTranslationService(
    private val networkHelper: NetworkHelper,
    private val preferences: TranslationPreferences,
) : TranslationService {

    override suspend fun translate(
        texts: List<String>,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): List<String> {
        val apiKey = preferences.deeplApiKey.get().trim()
        if (apiKey.isEmpty()) {
            throw TranslationException(TranslationError.MISSING_API_KEY)
        }

        val form = FormBody.Builder()
            .apply {
                texts.forEach { add("text", it) }
                if (source != TranslationLanguage.AUTO) add("source_lang", source.deepLSourceCode())
                add("target_lang", target.deepLTargetCode())
            }
            .build()

        val request = Request.Builder()
            .url(if (apiKey.endsWith(FREE_KEY_SUFFIX)) FREE_ENDPOINT else ENDPOINT)
            .header("Authorization", "DeepL-Auth-Key $apiKey")
            .post(form)
            .build()

        val body = networkHelper.client.executeForBody(request)
        val translations = runCatching {
            Json.Default.parseToJsonElement(body).jsonObject.getValue("translations").jsonArray
        }.getOrNull() ?: throw TranslationException(TranslationError.PROVIDER_ERROR, "Unexpected response from DeepL")

        return translations.map { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
    }

    private fun TranslationLanguage.deepLSourceCode(): String = when (this) {
        TranslationLanguage.CHINESE_SIMPLIFIED, TranslationLanguage.CHINESE_TRADITIONAL -> "ZH"
        else -> code.substringBefore('-').uppercase()
    }

    private fun TranslationLanguage.deepLTargetCode(): String = when (this) {
        TranslationLanguage.ENGLISH -> "EN-US"
        TranslationLanguage.PORTUGUESE -> "PT-PT"
        TranslationLanguage.CHINESE_SIMPLIFIED -> "ZH-HANS"
        TranslationLanguage.CHINESE_TRADITIONAL -> "ZH-HANT"
        else -> code.substringBefore('-').uppercase()
    }

    companion object {
        private const val ENDPOINT = "https://api.deepl.com/v2/translate"
        private const val FREE_ENDPOINT = "https://api-free.deepl.com/v2/translate"
        private const val FREE_KEY_SUFFIX = ":fx"
    }
}
