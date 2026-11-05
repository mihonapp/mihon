package eu.kanade.domain.translation

import kotlinx.serialization.Serializable

/** Supported AI backends for chapter translation. */
enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String?,
    val supportsImageGeneration: Boolean,
) {
    GEMINI("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta", true),
    OPENAI("openai", "OpenAI", "https://api.openai.com/v1", false),
    OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", false),
    GROQ("groq", "Groq", "https://api.groq.com/openai/v1", false),
    TOGETHER("together", "Together AI", "https://api.together.xyz/v1", false),
    MISTRAL("mistral", "Mistral AI", "https://api.mistral.ai/v1", false),
    XAI("xai", "xAI", "https://api.x.ai/v1", false),
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com/v1", false),
    ANTHROPIC("anthropic", "Anthropic Claude", "https://api.anthropic.com/v1", false),
    CUSTOM("custom", "Custom OpenAI-compatible endpoint", null, false),
    ;

    companion object {
        fun fromId(id: String): AiProvider = entries.firstOrNull { it.id == id } ?: GEMINI
    }
}

enum class AiCapability {
    TEXT_GENERATION,
    IMAGE_INPUT,
    JSON_SCHEMA,
    IMAGE_GENERATION,
}

@Serializable
data class AiModel(
    val id: String,
    val name: String = id,
    val capabilities: Set<String> = emptySet(),
) {
    fun supports(capability: AiCapability): Boolean = capability.name in capabilities
}

object AiCapabilitySet {
    fun of(vararg capabilities: AiCapability): Set<String> = capabilities.map { it.name }.toSet()
}
