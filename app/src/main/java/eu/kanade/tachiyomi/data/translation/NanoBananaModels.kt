package eu.kanade.tachiyomi.data.translation

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames

// region Request

@Serializable
data class GenerateContentRequest(
    val contents: List<RequestContent>,
    val generationConfig: GenerationConfig,
)

@Serializable
data class RequestContent(
    val parts: List<RequestPart>,
)

@Serializable
data class RequestPart(
    val text: String? = null,
    @SerialName("inline_data") val inlineData: RequestInlineData? = null,
)

@Serializable
data class RequestInlineData(
    @SerialName("mime_type") val mimeType: String,
    val data: String,
)

@Serializable
data class GenerationConfig(
    val responseModalities: List<String>? = null,
    val responseMimeType: String? = null,
    val temperature: Double? = null,
    /** Constrains the answer to this JSON schema (OpenAPI subset), so the model cannot emit broken JSON. */
    val responseSchema: JsonElement? = null,
)

// endregion

// region Response

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
    val promptFeedback: PromptFeedback? = null,
)

@Serializable
data class Candidate(
    val content: ResponseContent? = null,
    val finishReason: String? = null,
)

@Serializable
data class ResponseContent(
    val parts: List<ResponsePart>? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ResponsePart(
    val text: String? = null,
    @JsonNames("inline_data") val inlineData: ResponseInlineData? = null,
    val thought: Boolean? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ResponseInlineData(
    @JsonNames("mime_type") val mimeType: String? = null,
    val data: String? = null,
)

@Serializable
data class PromptFeedback(
    val blockReason: String? = null,
)

// endregion

// region Error body

@Serializable
data class ApiErrorResponse(
    val error: ApiError? = null,
)

@Serializable
data class ApiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
)

// endregion

/** A translated page returned by the model. */
class TranslatedImage(
    val bytes: ByteArray,
    val mimeType: String,
) {
    /** File extension matching [mimeType]. */
    val extension: String
        get() = when (mimeType.lowercase().substringBefore(';').trim()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            else -> "png"
        }
}
