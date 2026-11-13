package tachiyomi.translation.provider

data class ProviderCapabilities(
    val supportsBatch: Boolean,
    val supportsContext: Boolean,
    val maxBatch: Int,
)

data class ProviderConfig(
    val apiKey: String? = null,
    val region: String? = null,
    val model: String? = null,
    val baseUrl: String? = null,
)

data class TranslationRequest(
    val texts: List<String>,
    /** null means let the provider auto-detect. */
    val from: String? = null,
    val to: String = DEFAULT_TARGET,
    /** Previous entries of the page, used by LLM providers to keep pronouns consistent. */
    val context: List<String> = emptyList(),
) {
    companion object {
        const val DEFAULT_TARGET = "zh-CN"
    }
}

sealed interface TranslationOutcome {
    data class Success(
        val texts: List<String>,
        val elapsedMillis: Long,
    ) : TranslationOutcome

    data class Failure(
        val kind: ErrorKind,
        val message: String? = null,
        val httpCode: Int? = null,
    ) : TranslationOutcome
}

enum class ErrorKind {
    NETWORK,
    AUTH,
    RATE_LIMIT,
    BAD_RESPONSE,
    UNSUPPORTED,
    CANCELLED,
}

interface TranslationProvider {
    val id: String
    val displayName: String
    val requiresKey: Boolean
    val capabilities: ProviderCapabilities

    suspend fun translate(request: TranslationRequest): TranslationOutcome
}

/** Metadata for the settings screen; kept separate so it can be listed without building clients. */
data class ProviderDescriptor(
    val id: String,
    val displayName: String,
    val requiresKey: Boolean,
    val needsRegion: Boolean = false,
    val needsModel: Boolean = false,
    val note: String? = null,
)
