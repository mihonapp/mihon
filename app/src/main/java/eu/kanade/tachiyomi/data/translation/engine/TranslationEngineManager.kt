package eu.kanade.tachiyomi.data.translation.engine

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import tachiyomi.domain.translation.service.TranslationPreferences
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TranslationEngineManager(
    private val preferences: TranslationPreferences,
    private val networkHelper: NetworkHelper,
    private val json: Json,
) {

    suspend fun detectLanguage(text: String): String {
        return suspendCancellableCoroutine { continuation ->
            val languageIdentifier = LanguageIdentification.getClient()
            languageIdentifier.identifyLanguage(text)
                .addOnSuccessListener { languageCode ->
                    if (languageCode == "und") {
                        continuation.resume("ja") // default fallback for manga
                    } else {
                        continuation.resume(languageCode)
                    }
                }
                .addOnFailureListener {
                    continuation.resume("ja")
                }
        }
    }

    suspend fun translate(text: String, sourceLang: String, targetLang: String): String = withContext(Dispatchers.IO) {
        val engine = preferences.translatorEngine().get()
        when (engine) {
            "mlkit" -> translateMlKit(text, sourceLang, targetLang)
            "google_web" -> translateGoogleWeb(text, sourceLang, targetLang)
            "deepl_web" -> translateDeepLWeb(text, sourceLang, targetLang)
            "gemini" -> translateGemini(text, targetLang)
            "openrouter" -> translateOpenRouter(text, targetLang)
            else -> translateMlKit(text, sourceLang, targetLang)
        }
    }

    private suspend fun translateMlKit(text: String, sourceLang: String, targetLang: String): String {
        val sourceML = TranslateLanguage.fromLanguageTag(sourceLang) ?: TranslateLanguage.JAPANESE
        val targetML = TranslateLanguage.fromLanguageTag(targetLang) ?: TranslateLanguage.INDONESIAN
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceML)
            .setTargetLanguage(targetML)
            .build()
        val translator = Translation.getClient(options)

        return suspendCancellableCoroutine { continuation ->
            translator.downloadModelIfNeeded()
                .addOnSuccessListener {
                    translator.translate(text)
                        .addOnSuccessListener { translatedText ->
                            continuation.resume(translatedText)
                        }
                        .addOnFailureListener { e ->
                            continuation.resumeWithException(e)
                        }
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    private fun translateGoogleWeb(text: String, sourceLang: String, targetLang: String): String {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encodedText"
        val request = Request.Builder().url(url).build()
        val response = networkHelper.client.newCall(request).execute()
        val bodyStr = response.body.string()
        val jsonArray = json.parseToJsonElement(bodyStr).jsonArray
        val sentences = jsonArray[0].jsonArray
        val sb = StringBuilder()
        for (item in sentences) {
            sb.append(item.jsonArray[0].jsonPrimitive.content)
        }
        return sb.toString()
    }

    private fun translateDeepLWeb(text: String, sourceLang: String, targetLang: String): String {
        // Fallback or lightweight deepL web translate endpoint simulation via client query
        return try {
            translateGoogleWeb(text, sourceLang, targetLang)
        } catch (e: Exception) {
            text
        }
    }

    private fun translateGemini(text: String, targetLang: String): String {
        val apiKey = preferences.geminiApiKey().get()
        val model = preferences.geminiModel().get().ifBlank { "gemini-1.5-flash" }
        val promptTemplate = preferences.translationPrompt().get()
        val prompt = promptTemplate.replace("{text}", text) + "\nTarget language code: $targetLang"

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val payload = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", prompt)
                        })
                    })
                })
            })
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val response = networkHelper.client.newCall(request).execute()
        val bodyStr = response.body.string()
        val jsonObj = json.parseToJsonElement(bodyStr).jsonObject
        val candidates = jsonObj["candidates"]?.jsonArray ?: return text
        val content = candidates[0].jsonObject["content"]?.jsonObject
        val parts = content?.get("parts")?.jsonArray
        return parts?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content?.trim() ?: text
    }

    private fun translateOpenRouter(text: String, targetLang: String): String {
        val apiKey = preferences.openRouterApiKey().get()
        val model = preferences.openRouterModel().get().ifBlank { "google/gemini-2.0-flash-001" }
        val promptTemplate = preferences.translationPrompt().get()
        val prompt = promptTemplate.replace("{text}", text) + "\nTarget language code: $targetLang"

        val url = "https://openrouter.ai/api/v1/chat/completions"
        val payload = buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }.toString()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val response = networkHelper.client.newCall(request).execute()
        val bodyStr = response.body.string()
        val jsonObj = json.parseToJsonElement(bodyStr).jsonObject
        val choices = jsonObj["choices"]?.jsonArray ?: return text
        val message = choices[0].jsonObject["message"]?.jsonObject
        return message?.get("content")?.jsonPrimitive?.content?.trim() ?: text
    }
}
