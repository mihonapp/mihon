package eu.kanade.tachiyomi.data.translation

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.translation.service.TranslationPreferences

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
              "x-goog-api-key": "secret-header",
              "authorization": "Bearer secret-token",
              "inlineData": {"mimeType": "image/png", "data": "base64-image"},
              "inline_data": {"mime_type": "image/jpeg", "data": "base64-image"},
              "text": "translated line"
            }
        """.trimIndent()

        TranslationLogRedactor.redact(raw) shouldBe """
            {
              "url": "https://generativelanguage.googleapis.com/v1beta/models/gemini:generateContent?key=<redacted>",
              "x-goog-api-key": "<redacted>",
              "authorization": "<redacted>",
              "inlineData": {"mimeType": "image/png", "data": "<redacted-image>"},
              "inline_data": {"mime_type": "image/jpeg", "data": "<redacted-image>"},
              "text": "translated line"
            }
        """.trimIndent()
    }

    @Test
    fun `api log formatter keeps full state but redacts secrets and image data`() {
        val details = TranslationLogDetailsFormatter.apiCall(
            operation = "translatePageImage",
            method = "POST",
            endpoint = "/v1beta/models/gemini-3-flash-preview:generateContent",
            model = "gemini-3-flash-preview",
            statusCode = 429,
            elapsedMs = 1234,
            requestSummary = """
                prompt:
                Translate hello
                x-goog-api-key: secret-header
            """.trimIndent(),
            errorBody = """{"error":{"code":429,"message":"quota exceeded"}}""",
            rawRequestJson = """
                {
                  "inline_data": {"mime_type": "image/png", "data": "${"a".repeat(160)}"},
                  "text": "Translate hello"
                }
            """.trimIndent(),
            rawResponseJson = """{"error":{"message":"quota exceeded"}}""",
        )

        details shouldContain "operation=translatePageImage"
        details shouldContain "status_code=429"
        details shouldContain "Translate hello"
        details shouldContain "quota exceeded"
        details shouldContain "<redacted-image>"
        details shouldContain "x-goog-api-key=<redacted>"
        details shouldNotContain "secret-header"
        details shouldNotContain "aaaaaaaaaaaaaaaa"
    }

    @Test
    fun `generation config applies raw json override last`() {
        val prefs = TranslationGenerationConfig(
            temperature = 0.2f,
            topP = 0.9f,
            topK = 40,
            maxOutputTokens = 4096,
            thinkingLevel = TranslationThinkingLevel.Low.value,
            rawJsonOverride = """{"temperature":0.7,"candidateCount":1}""",
        )

        val obj = prefs.toGeminiJson(Json).jsonObject

        obj["temperature"].toString() shouldBe "0.7"
        obj["topP"].toString() shouldBe "0.9"
        obj["topK"].toString() shouldBe "40"
        obj["maxOutputTokens"].toString() shouldBe "4096"
        obj["candidateCount"].toString() shouldBe "1"
        obj["thinkingConfig"]!!.jsonObject shouldContain (
            "thinkingLevel" to Json.parseToJsonElement("\"low\"")
            )
        obj["thinkingConfig"]!!.jsonObject shouldNotContainKey "thinkingBudget"
    }

    @Test
    fun `generation config defaults match Gemini 3 translation defaults`() {
        val preferences = TranslationPreferences(InMemoryPreferenceStore())

        preferences.temperature.get() shouldBe 1f
        preferences.topP.get() shouldBe 0.95f
        preferences.topK.get() shouldBe 64
        preferences.maxOutputTokens.get() shouldBe 65_536
        preferences.thinkingLevel.get() shouldBe TranslationThinkingLevel.High.value
    }

    @Test
    fun `model output cap uses fetched selected model limit`() {
        val models = listOf(
            GeminiModel(name = "models/gemini-3-flash-preview", outputTokenLimit = 65_536),
            GeminiModel(name = "models/custom-low-output", outputTokenLimit = 8_192),
        )

        TranslationModelLimits.maxOutputTokensFor(
            requested = 65_536,
            selectedModel = "custom-low-output",
            cachedModels = models,
        ) shouldBe 8_192
    }

    @Test
    fun `unknown model output cap preserves Gemini 3 default`() {
        TranslationModelLimits.maxOutputTokensFor(
            requested = 65_536,
            selectedModel = "unfetched-custom-model",
            cachedModels = emptyList(),
        ) shouldBe 65_536
    }

    @Test
    fun `paused auth is recoverable but paused quota needs explicit retry`() {
        TranslationJobStatus.PausedAuth.isActiveForDedupe() shouldBe true
        TranslationJobStatus.PausedAuth.isRetryableFromQueue() shouldBe true
        TranslationJobStatus.PausedAuth.canAutoRequeueAfterSetup() shouldBe true

        TranslationJobStatus.PausedQuota.isActiveForDedupe() shouldBe true
        TranslationJobStatus.PausedQuota.isRetryableFromQueue() shouldBe true
        TranslationJobStatus.PausedQuota.canAutoRequeueAfterSetup() shouldBe false
    }

    @Test
    fun `retry planner blocks until setup ready and uses replace for manual starts`() {
        TranslationRetryPlanner.manualRetry(setupReady = false) shouldBe TranslationRetryDecision(
            allowed = false,
            nextStatus = null,
            startPolicy = null,
        )

        TranslationRetryPlanner.manualRetry(setupReady = true) shouldBe TranslationRetryDecision(
            allowed = true,
            nextStatus = TranslationJobStatus.Queued,
            startPolicy = TranslationWorkStartPolicy.Replace,
        )

        TranslationRetryPlanner.autoRequeueAfterSetup(
            status = TranslationJobStatus.PausedAuth,
            setupReady = true,
        ) shouldBe TranslationRetryDecision(
            allowed = true,
            nextStatus = TranslationJobStatus.Queued,
            startPolicy = TranslationWorkStartPolicy.Replace,
        )

        TranslationRetryPlanner.autoRequeueAfterSetup(
            status = TranslationJobStatus.PausedQuota,
            setupReady = true,
        ).allowed shouldBe false
    }

    @Test
    fun `active duplicate matching is exact`() {
        val queued = TranslationJobSignature(
            mangaId = 1,
            chapterId = 2,
            pageIndex = null,
            scope = TranslationScope.Chapter,
            pipeline = "gemini_vision",
            mode = TranslationMode.Overlay,
            targetLanguage = "English",
            status = TranslationJobStatus.Queued,
        )

        val same = queued.copy(status = TranslationJobStatus.PausedAuth)
        val differentPage = queued.copy(scope = TranslationScope.Image, pageIndex = 0)
        val finished = queued.copy(status = TranslationJobStatus.Completed)

        TranslationJobDedupe.findActiveDuplicate(listOf(queued), same) shouldBe queued
        TranslationJobDedupe.findActiveDuplicate(listOf(queued), differentPage) shouldBe null
        TranslationJobDedupe.findActiveDuplicate(listOf(finished), same) shouldBe null
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

    @Test
    fun `setup validator blocks missing api key`() = runTest {
        val preferences = TranslationPreferences(InMemoryPreferenceStore())
        val validator = TranslationSetupValidator(
            preferences = preferences,
            listModels = { error("model list should not run") },
            testGenerateContent = { _, _ -> error("model test should not run") },
        )

        val result = validator.testSetup()

        result.ready shouldBe false
        validator.readiness().ready shouldBe false
    }

    @Test
    fun `setup validator cache stays ready until model changes`() = runTest {
        val preferences = TranslationPreferences(InMemoryPreferenceStore())
        preferences.geminiApiKey.set("secret-key")
        val validator = TranslationSetupValidator(
            preferences = preferences,
            listModels = {
                listOf(
                    GeminiModel(
                        name = "models/gemini-3-flash-preview",
                        supportedGenerationMethods = listOf("generateContent"),
                    ),
                )
            },
            testGenerateContent = { _, model -> model shouldBe "gemini-3-flash-preview" },
        )

        val result = validator.testSetup()

        result.ready shouldBe true
        validator.readiness().ready shouldBe true
        preferences.setupFingerprint.get() shouldNotContain "secret-key"

        preferences.geminiModel.set("gemini-4-flash-preview")

        validator.readiness().ready shouldBe false
    }

    @Test
    fun `setup validator requires inpaint model only when inpaint is enabled`() = runTest {
        val preferences = TranslationPreferences(InMemoryPreferenceStore())
        preferences.geminiApiKey.set("secret-key")
        preferences.geminiInpaintModel.set("gemini-image")
        val validator = TranslationSetupValidator(
            preferences = preferences,
            listModels = {
                listOf(
                    GeminiModel(
                        name = "models/gemini-3-flash-preview",
                        supportedGenerationMethods = listOf("generateContent"),
                    ),
                )
            },
            testGenerateContent = { _, _ -> },
        )

        validator.testSetup().ready shouldBe true

        preferences.enableInpaint.set(true)

        validator.testSetup().ready shouldBe false
        validator.readiness().ready shouldBe false
    }
}
