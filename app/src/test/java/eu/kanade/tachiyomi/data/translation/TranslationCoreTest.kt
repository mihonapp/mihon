package eu.kanade.tachiyomi.data.translation

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test

class TranslationCoreTest {

    @Test
    fun `model list keeps only generateContent models`() {
        val models = listOf(
            GeminiModel(
                name = "models/gemini-3-flash-preview",
                baseModelId = "gemini-3-flash-preview",
                displayName = "Gemini 3 Flash Preview",
                supportedGenerationMethods = listOf("generateContent"),
            ),
            GeminiModel(
                name = "models/text-embedding",
                baseModelId = "text-embedding",
                displayName = "Embedding",
                supportedGenerationMethods = listOf("embedContent"),
            ),
        )

        models.generateContentModels().map { it.id } shouldContainExactly listOf("gemini-3-flash-preview")
    }

    @Test
    fun `redaction removes api key and image payloads but keeps text`() {
        val raw = """
            {
              "url": "https://generativelanguage.googleapis.com/v1beta/models/gemini:generateContent?key=secret-key",
              "inlineData": {"mimeType": "image/png", "data": "base64-image"},
              "text": "translated line"
            }
        """.trimIndent()

        TranslationLogRedactor.redact(raw) shouldBe """
            {
              "url": "https://generativelanguage.googleapis.com/v1beta/models/gemini:generateContent?key=<redacted>",
              "inlineData": {"mimeType": "image/png", "data": "<redacted-image>"},
              "text": "translated line"
            }
        """.trimIndent()
    }

    @Test
    fun `generation config applies raw json override last`() {
        val prefs = TranslationGenerationConfig(
            temperature = 0.2f,
            topP = 0.9f,
            topK = 40,
            maxOutputTokens = 4096,
            thinkingBudget = 1024,
            rawJsonOverride = """{"temperature":0.7,"candidateCount":1}""",
        )

        val obj = prefs.toGeminiJson(Json).jsonObject

        obj["temperature"].toString() shouldBe "0.7"
        obj["topP"].toString() shouldBe "0.9"
        obj["topK"].toString() shouldBe "40"
        obj["maxOutputTokens"].toString() shouldBe "4096"
        obj["candidateCount"].toString() shouldBe "1"
        obj["thinkingConfig"]!!.jsonObject shouldContain ("thinkingBudget" to Json.parseToJsonElement("1024"))
    }

    @Test
    fun `enqueue plan skips existing overlays unless overwrite requested`() {
        val pages = listOf(
            TranslationPageCandidate(chapterId = 1, pageIndex = 0, hasOverlay = true),
            TranslationPageCandidate(chapterId = 1, pageIndex = 1, hasOverlay = false),
        )

        TranslationEnqueuePlanner.pagesToQueue(pages, overwrite = false) shouldContainExactly listOf(pages[1])
        TranslationEnqueuePlanner.pagesToQueue(pages, overwrite = true) shouldContainExactly pages
    }
}
