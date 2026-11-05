package eu.kanade.tachiyomi.data.translation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.translation.AiCapability
import eu.kanade.domain.translation.AiModel
import eu.kanade.domain.translation.AiProvider
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.io.IOException

/** Retrieves and normalizes the model catalog for the selected provider. */
@Inject
@SingleIn(AppScope::class)
class AiModelDiscovery(
    private val networkHelper: NetworkHelper,
    private val json: Json,
) {
    private val client: OkHttpClient by lazy { buildGeminiHttpClient(networkHelper) }

    suspend fun listModels(provider: AiProvider, apiKey: String, baseUrl: String? = null): List<AiModel> {
        if (!isValidApiKey(
                apiKey,
            )
        ) {
            throw TranslationException.InvalidApiKey("key is empty or contains invalid characters")
        }
        if (provider == AiProvider.CUSTOM && baseUrl.isNullOrBlank()) {
            throw TranslationException.RequestRejected(400, "custom endpoint is not configured")
        }
        val endpoint = when (provider) {
            AiProvider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models"
            AiProvider.OPENROUTER -> "https://openrouter.ai/api/v1/models"
            AiProvider.ANTHROPIC -> "https://api.anthropic.com/v1/models"
            AiProvider.OPENAI,
            AiProvider.GROQ,
            AiProvider.TOGETHER,
            AiProvider.MISTRAL,
            AiProvider.XAI,
            AiProvider.DEEPSEEK,
            AiProvider.CUSTOM,
            -> "${(baseUrl ?: provider.defaultBaseUrl).orEmpty().trimEnd('/')}/models"
        }
        val headers = when (provider) {
            AiProvider.GEMINI -> Headers.headersOf("x-goog-api-key", apiKey.trim())
            AiProvider.ANTHROPIC -> Headers.headersOf(
                "x-api-key",
                apiKey.trim(),
                "anthropic-version",
                ANTHROPIC_VERSION,
            )
            else -> Headers.headersOf("Authorization", "Bearer ${apiKey.trim()}")
        }
        val response = try {
            client.newCall(GET(endpoint, headers = headers)).await()
        } catch (e: IOException) {
            throw TranslationException.NetworkError(e)
        }
        response.use {
            val body = it.body.string()
            if (!it.isSuccessful) throw parseDiscoveryError(it.code, body)
            return parseModels(provider, body)
        }
    }

    private fun parseModels(provider: AiProvider, body: String): List<AiModel> {
        val root = json.parseToJsonElement(body).jsonObject
        val models = root["models"]?.jsonArray ?: root["data"]?.jsonArray ?: return emptyList()
        return models.mapNotNull { element ->
            val item = element.jsonObject
            val id = item["name"]?.jsonPrimitive?.contentOrNull
                ?.removePrefix("models/")
                ?: item["id"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            val methods = item["supportedGenerationMethods"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.contentOrNull
            }.orEmpty()
            val name = item["displayName"]?.jsonPrimitive?.contentOrNull
                ?: item["display_name"]?.jsonPrimitive?.contentOrNull
                ?: id
            AiModel(id = id, name = name, capabilities = capabilities(provider, id, methods, item))
        }.distinctBy { it.id }
    }

    private fun capabilities(
        provider: AiProvider,
        id: String,
        methods: List<String>,
        metadata: kotlinx.serialization.json.JsonObject,
    ): Set<String> {
        val lower = id.lowercase()
        val inputModalities = metadata["architecture"]?.jsonObject?.get("input_modalities")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        val outputModalities = metadata["architecture"]?.jsonObject?.get("output_modalities")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        val imageInput = provider == AiProvider.GEMINI ||
            inputModalities.any { it.equals("image", ignoreCase = true) } ||
            provider == AiProvider.ANTHROPIC || lower.contains("vision") || lower.contains("4o") ||
            lower.contains("gemini") ||
            lower.contains("vl") || lower.contains("pixtral") || lower.contains("grok") || lower.contains("llama-4")
        val imageOutput = provider.supportsImageGeneration &&
            (
                outputModalities.any { it.equals("image", ignoreCase = true) } ||
                    (methods.any { it.contains("generateContent") } && lower.contains("image"))
                )
        return buildSet {
            add(AiCapability.TEXT_GENERATION.name)
            if (imageInput) add(AiCapability.IMAGE_INPUT.name)
            if (provider != AiProvider.GEMINI) {
                add(AiCapability.JSON_SCHEMA.name)
            }
            if (imageOutput) add(AiCapability.IMAGE_GENERATION.name)
        }
    }

    private fun parseDiscoveryError(code: Int, body: String): TranslationException = when (code) {
        401, 403 -> TranslationException.InvalidApiKey("model listing rejected")
        404 -> TranslationException.ModelNotFound("model listing endpoint not found")
        429 -> TranslationException.RateLimited(null)
        in 500..599 -> TranslationException.ServerError(code)
        else -> TranslationException.RequestRejected(code, "model listing failed (${body.length} bytes)")
    }

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
