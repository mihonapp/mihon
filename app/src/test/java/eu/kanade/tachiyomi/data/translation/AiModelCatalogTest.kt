package eu.kanade.tachiyomi.data.translation

import eu.kanade.domain.translation.AiCapability
import eu.kanade.domain.translation.AiCapabilitySet
import eu.kanade.domain.translation.AiModel
import eu.kanade.domain.translation.AiModelCatalog
import eu.kanade.domain.translation.AiProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiModelCatalogTest {
    @Test
    fun `catalog preserves model capabilities`() {
        val models = listOf(
            AiModel(
                id = "gpt-4o",
                name = "GPT-4o",
                capabilities = AiCapabilitySet.of(AiCapability.TEXT_GENERATION, AiCapability.IMAGE_INPUT),
            ),
        )

        val decoded = AiModelCatalog.decode(AiModelCatalog.encode(models))

        assertEquals(models, decoded)
        assertTrue(decoded.single().supports(AiCapability.IMAGE_INPUT))
    }

    @Test
    fun `unknown provider falls back to Gemini for legacy preferences`() {
        assertEquals(AiProvider.GEMINI, AiProvider.fromId("removed-provider"))
    }

    @Test
    fun `next provider batch is registered`() {
        assertEquals("https://api.groq.com/openai/v1", AiProvider.GROQ.defaultBaseUrl)
        assertEquals("https://api.together.xyz/v1", AiProvider.TOGETHER.defaultBaseUrl)
        assertEquals("https://api.mistral.ai/v1", AiProvider.MISTRAL.defaultBaseUrl)
        assertEquals("https://api.x.ai/v1", AiProvider.XAI.defaultBaseUrl)
        assertEquals("https://api.deepseek.com/v1", AiProvider.DEEPSEEK.defaultBaseUrl)
        assertEquals("https://api.anthropic.com/v1", AiProvider.ANTHROPIC.defaultBaseUrl)
    }
}
