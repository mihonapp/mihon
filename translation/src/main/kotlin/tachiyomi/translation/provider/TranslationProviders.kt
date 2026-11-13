package tachiyomi.translation.provider

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import tachiyomi.translation.util.BatchPlanner
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal val translationJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

internal object HttpSupport {
    suspend fun run(
        callFactory: Call.Factory,
        request: Request,
        parse: (String) -> List<String>?,
    ): TranslationOutcome {
        val start = System.nanoTime()
        val response = try {
            callFactory.await(request)
        } catch (e: IOException) {
            return TranslationOutcome.Failure(ErrorKind.NETWORK, e.message)
        }

        response.use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                return TranslationOutcome.Failure(res.code.toErrorKind(), raw.take(300), res.code)
            }
            val parsed = parse(raw)
                ?: return TranslationOutcome.Failure(ErrorKind.BAD_RESPONSE, raw.take(300), res.code)
            return TranslationOutcome.Success(parsed, (System.nanoTime() - start) / 1_000_000)
        }
    }

    private fun Int.toErrorKind(): ErrorKind = when (this) {
        401, 403 -> ErrorKind.AUTH
        429 -> ErrorKind.RATE_LIMIT
        else -> ErrorKind.BAD_RESPONSE
    }
}

private suspend fun Call.Factory.await(request: Request): Response = suspendCancellableCoroutine { cont ->
    val call = newCall(request)
    cont.invokeOnCancellation { call.cancel() }
    call.enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        },
    )
}

/** Microsoft's Edge endpoint: no auth, body is a plain string array. */
internal class EdgeProvider(private val callFactory: Call.Factory) : TranslationProvider {
    override val id: String = ProviderRegistry.EDGE
    override val displayName: String = "Microsoft Edge"
    override val requiresKey: Boolean = false
    override val capabilities = ProviderCapabilities(
        supportsBatch = true,
        supportsContext = false,
        maxBatch = 25,
    )

    override suspend fun translate(request: TranslationRequest): TranslationOutcome {
        val url = "https://edge.microsoft.com/translate/translatetext".toHttpUrl().newBuilder()
            .addQueryParameter("from", request.from.orEmpty())
            .addQueryParameter("to", request.to)
            .addQueryParameter("isEnterpriseClient", "false")
            .build()
        val body = translationJson
            .encodeToString(ListSerializer(String.serializer()), request.texts)
            .toRequestBody(JSON_MEDIA)
        val httpRequest = Request.Builder().url(url).post(body).build()
        return HttpSupport.run(callFactory, httpRequest, ::parseTranslationPairs)
    }
}

/** Official Azure Translator: key + region, body is `[{ "Text": "..." }]`. */
internal class AzureProvider(
    private val callFactory: Call.Factory,
    private val config: ProviderConfig,
) : TranslationProvider {
    override val id: String = ProviderRegistry.AZURE
    override val displayName: String = "Azure Translator"
    override val requiresKey: Boolean = true
    override val capabilities = ProviderCapabilities(
        supportsBatch = true,
        supportsContext = false,
        maxBatch = 25,
    )

    override suspend fun translate(request: TranslationRequest): TranslationOutcome {
        val key = config.apiKey?.takeIf { it.isNotBlank() }
            ?: return TranslationOutcome.Failure(ErrorKind.AUTH, "Missing API key")

        val url = "https://api.cognitive.microsofttranslator.com/translate".toHttpUrl().newBuilder()
            .addQueryParameter("api-version", "3.0")
            .addQueryParameter("to", request.to)
            .apply { request.from?.let { addQueryParameter("from", it) } }
            .build()

        val payload = buildAzurePayload(request.texts)

        val httpRequest = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON_MEDIA))
            .header("Ocp-Apim-Subscription-Key", key)
            .apply { config.region?.takeIf { it.isNotBlank() }?.let { header("Ocp-Apim-Subscription-Region", it) } }
            .build()
        return HttpSupport.run(callFactory, httpRequest, ::parseTranslationPairs)
    }
}

/** Google's free gtx endpoint. Single text per call, no auth, frequently unreachable from mainland. */
internal class GoogleWebProvider(private val callFactory: Call.Factory) : TranslationProvider {
    override val id: String = ProviderRegistry.GOOGLE
    override val displayName: String = "Google (free)"
    override val requiresKey: Boolean = false
    override val capabilities = ProviderCapabilities(
        supportsBatch = false,
        supportsContext = false,
        maxBatch = 1,
    )

    override suspend fun translate(request: TranslationRequest): TranslationOutcome {
        val text = request.texts.firstOrNull()
            ?: return TranslationOutcome.Success(emptyList(), 0L)

        val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("dj", "1")
            .addQueryParameter("dt", "t")
            .addQueryParameter("ie", "UTF-8")
            .addQueryParameter("q", text)
            .addQueryParameter("sl", request.from ?: "auto")
            .addQueryParameter("tl", request.to)
            .build()

        val httpRequest = Request.Builder().url(url).get().build()
        return HttpSupport.run(callFactory, httpRequest) { raw ->
            runCatching {
                val sentences = translationJson.parseToJsonElement(raw).jsonObject["sentences"]?.jsonArray
                    ?: return@runCatching null
                listOf(sentences.joinToString("") { it.jsonObject["trans"]?.jsonPrimitive?.contentOrNull.orEmpty() })
            }.getOrNull()
        }
    }
}

/** Google Cloud Translation v2, key based. */
internal class GoogleCloudProvider(
    private val callFactory: Call.Factory,
    private val config: ProviderConfig,
) : TranslationProvider {
    override val id: String = ProviderRegistry.GOOGLE_CLOUD
    override val displayName: String = "Google Cloud"
    override val requiresKey: Boolean = true
    override val capabilities = ProviderCapabilities(
        supportsBatch = true,
        supportsContext = false,
        maxBatch = 50,
    )

    override suspend fun translate(request: TranslationRequest): TranslationOutcome {
        val key = config.apiKey?.takeIf { it.isNotBlank() }
            ?: return TranslationOutcome.Failure(ErrorKind.AUTH, "Missing API key")

        val payload = buildJsonObject {
            put("q", JsonArray(request.texts.map { JsonPrimitive(it) }))
            put("target", JsonPrimitive(request.to))
            request.from?.let { put("source", JsonPrimitive(it)) }
            put("format", JsonPrimitive("text"))
        }.toString()

        val httpRequest = Request.Builder()
            .url("https://translation.googleapis.com/language/translate/v2")
            .post(payload.toRequestBody(JSON_MEDIA))
            .header("X-Goog-Api-Key", key)
            .build()

        return HttpSupport.run(callFactory, httpRequest) { raw ->
            runCatching {
                translationJson.parseToJsonElement(raw)
                    .jsonObject["data"]?.jsonObject
                    ?.get("translations")?.jsonArray
                    ?.map { it.jsonObject["translatedText"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            }.getOrNull()
        }
    }
}

/**
 * OpenAI-compatible chat completion. The model is asked for a JSON array of strings so a single
 * request can carry a whole page; parsing tolerates code fences and stray prose.
 */
internal class LlmProvider(
    private val callFactory: Call.Factory,
    private val config: ProviderConfig,
) : TranslationProvider {
    override val id: String = ProviderRegistry.LLM
    override val displayName: String = "LLM"
    override val requiresKey: Boolean = true
    override val capabilities = ProviderCapabilities(
        supportsBatch = true,
        supportsContext = true,
        maxBatch = 20,
    )

    override suspend fun translate(request: TranslationRequest): TranslationOutcome {
        val key = config.apiKey?.takeIf { it.isNotBlank() }
            ?: return TranslationOutcome.Failure(ErrorKind.AUTH, "Missing API key")

        val baseUrl = config.baseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
        val model = config.model?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
        val userContent = BatchPlanner.buildLlmUserPrompt(request.texts, request.context, request.to)

        val payload = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("temperature", JsonPrimitive(0))
            put(
                "messages",
                JsonArray(
                    listOf(
                        message("system", SYSTEM_PROMPT),
                        message("user", userContent),
                    ),
                ),
            )
        }.toString()

        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .post(payload.toRequestBody(JSON_MEDIA))
            .header("Authorization", "Bearer $key")
            .build()

        return HttpSupport.run(callFactory, httpRequest) { raw ->
            val content = runCatching {
                translationJson.parseToJsonElement(raw)
                    .jsonObject["choices"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            BatchPlanner.extractStringArray(content)
        }
    }

    private fun message(role: String, content: String) = buildJsonObject {
        put("role", JsonPrimitive(role))
        put("content", JsonPrimitive(content))
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"

        private const val SYSTEM_PROMPT =
            "你是漫画台词翻译器。把用户给出的每一条文本翻译成简体中文，保持条数与顺序一致，" +
                "只输出一个 JSON 字符串数组，不要输出解释、序号或任何其他文字。保留人名与语气，拟声词照常翻译。"
    }
}

private fun parseTranslationPairs(raw: String): List<String>? = runCatching {
    translationJson.parseToJsonElement(raw).jsonArray.map { element ->
        val obj = element.jsonObject
        obj["translations"]?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            ?: obj["text"]?.jsonPrimitive?.contentOrNull
            ?: ""
    }
}.getOrNull()

/** Azure wants `[{ "Text": "..." }]`; kept separate so the payload shape is unit-testable. */
internal fun buildAzurePayload(texts: List<String>): String =
    texts.joinToString(separator = ",", prefix = "[", postfix = "]") { text ->
        buildJsonObject { put("Text", JsonPrimitive(text)) }.toString()
    }

object ProviderRegistry {
    const val EDGE = "edge"
    const val AZURE = "azure"
    const val GOOGLE = "google"
    const val GOOGLE_CLOUD = "google_cloud"
    const val LLM = "llm"

    val descriptors: List<ProviderDescriptor> = listOf(
        ProviderDescriptor(EDGE, "Microsoft Edge", requiresKey = false, note = "免配置，稳定性无保证"),
        ProviderDescriptor(AZURE, "Azure Translator", requiresKey = true, needsRegion = true),
        ProviderDescriptor(GOOGLE, "Google (free)", requiresKey = false, note = "大陆通常不可直连"),
        ProviderDescriptor(GOOGLE_CLOUD, "Google Cloud", requiresKey = true),
        ProviderDescriptor(LLM, "LLM", requiresKey = true, needsModel = true),
    )

    fun descriptor(id: String): ProviderDescriptor? = descriptors.firstOrNull { it.id == id }

    fun create(id: String, config: ProviderConfig, callFactory: Call.Factory): TranslationProvider? = when (id) {
        EDGE -> EdgeProvider(callFactory)
        AZURE -> AzureProvider(callFactory, config)
        GOOGLE -> GoogleWebProvider(callFactory)
        GOOGLE_CLOUD -> GoogleCloudProvider(callFactory, config)
        LLM -> LlmProvider(callFactory, config)
        else -> null
    }
}
