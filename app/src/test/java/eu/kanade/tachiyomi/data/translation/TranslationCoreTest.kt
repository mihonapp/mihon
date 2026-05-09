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
        preferences.maxImagesPerBatch.get() shouldBe DEFAULT_TRANSLATION_MAX_IMAGES_PER_BATCH
        preferences.sourceLanguage.get() shouldBe TranslationLanguages.SOURCE_AUTO
        preferences.overlayTextSizeMode.get() shouldBe "dynamic"
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
    fun `batch planner caps images and all means uncapped`() {
        val pages = (0 until 50).map { page ->
            TranslationPageCandidate(chapterId = 1, pageIndex = page, hasOverlay = false)
        }

        TranslationBatchPlanner.pagesToQueue(
            pages = pages,
            overwrite = false,
            maxImagesPerBatch = 38,
        ).map { it.pageIndex } shouldContainExactly (0 until 38).toList()

        TranslationBatchPlanner.pagesToQueue(
            pages = pages,
            overwrite = false,
            maxImagesPerBatch = TRANSLATION_BATCH_ALL,
        ) shouldContainExactly pages
    }

    @Test
    fun `batch planner skips overlays and active jobs so later tap advances`() {
        val pages = listOf(
            TranslationPageCandidate(chapterId = 1, pageIndex = 0, hasOverlay = false, hasActiveJob = true),
            TranslationPageCandidate(chapterId = 1, pageIndex = 1, hasOverlay = true, hasActiveJob = false),
            TranslationPageCandidate(chapterId = 1, pageIndex = 2, hasOverlay = false, hasActiveJob = false),
            TranslationPageCandidate(chapterId = 1, pageIndex = 3, hasOverlay = false, hasActiveJob = false),
        )

        TranslationBatchPlanner.pagesToQueue(
            pages = pages,
            overwrite = false,
            maxImagesPerBatch = 1,
        ).map { it.pageIndex } shouldContainExactly listOf(2)
    }

    @Test
    fun `source language values map to prompt labels`() {
        TranslationLanguages.sourcePromptLabel("auto") shouldBe null
        TranslationLanguages.sourcePromptLabel("ja") shouldBe "Japanese"
        TranslationLanguages.sourcePromptLabel("ko") shouldBe "Korean"
        TranslationLanguages.sourcePromptLabel("zh") shouldBe "Chinese"
    }

    @Test
    fun `translation prompt uses system prompt for filtering rules`() {
        val systemPrompt = TranslationPromptPolicy.systemPrompt("Keep honorifics.")
        val pagePrompt = TranslationPromptPolicy.pagePrompt("English", "ja")

        systemPrompt shouldContain "Ignore sound effects"
        systemPrompt shouldContain "Keep honorifics."
        pagePrompt shouldContain "Source language: Japanese"
        pagePrompt shouldNotContain "Include dialogue, captions, signs, and sound effects"
    }

    @Test
    fun `overlay sanitizer removes punctuation sfx and unrelated boxes`() {
        val overlay = TranslationOverlayResult(
            boxes = listOf(
                TranslationOverlayBox(0f, 0f, 0.1f, 0.1f, originalText = "Hello", translatedText = "Hello", textType = "dialogue"),
                TranslationOverlayBox(0f, 0f, 0.1f, 0.1f, originalText = "!!!", translatedText = "!!!", textType = "dialogue"),
                TranslationOverlayBox(0f, 0f, 0.1f, 0.1f, originalText = "ドン", translatedText = "Boom", textType = "sfx"),
                TranslationOverlayBox(0f, 0f, 0.1f, 0.1f, originalText = "scan", translatedText = "scan", textType = "watermark"),
                TranslationOverlayBox(0f, 0f, 0.1f, 0.1f, originalText = "Exit", translatedText = "Exit", textType = "sign"),
            ),
        )

        TranslationOverlaySanitizer.sanitize(overlay).boxes.map { it.originalText } shouldContainExactly listOf("Hello", "Exit")
    }

    @Test
    fun `notification formatter includes full state unless content is hidden`() {
        val job = queueItem(id = 1).toJob()
        val item = queueItem(id = 1, mangaTitle = "Manga", chapterName = "Chapter 1")

        val visible = TranslationNotificationFormatter.format(
            item = item,
            job = job,
            current = 3,
            total = 10,
            status = TranslationJobStatus.Running,
            message = "working",
            hideContent = false,
        )

        visible.title shouldBe "Manga"
        visible.bigText shouldContain "Chapter 1"
        visible.bigText shouldContain "model=gemini-3-flash-preview"
        visible.bigText shouldContain "pipeline=gemini_vision"
        visible.bigText shouldContain "message=working"

        val hidden = TranslationNotificationFormatter.format(
            item = item,
            job = job,
            current = 3,
            total = 10,
            status = TranslationJobStatus.Running,
            message = "working",
            hideContent = true,
        )

        hidden.title shouldBe "Translation queue"
        hidden.bigText shouldNotContain "Manga"
        hidden.bigText shouldNotContain "Chapter 1"
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

    @Test
    fun `queue ui groups by manga title and sorts chapters ascending`() {
        val items = listOf(
            queueItem(id = 1, mangaId = 2, mangaTitle = "Beta", chapterNumber = 5.0, chapterName = "Chapter 5"),
            queueItem(id = 2, mangaId = 1, mangaTitle = "Alpha", chapterNumber = 2.0, chapterName = "Chapter 2"),
            queueItem(id = 3, mangaId = 1, mangaTitle = "Alpha", chapterNumber = 1.0, chapterName = "Chapter 1"),
        )

        val groups = TranslationQueueUiModel.filterAndGroup(items, filters = emptySet())

        groups.map { it.mangaTitle } shouldContainExactly listOf("Alpha", "Beta")
        groups.first().items.map { it.id } shouldContainExactly listOf(3L, 2L)
    }

    @Test
    fun `queue type filters are multi select and empty means all`() {
        val items = listOf(
            queueItem(id = 1, status = TranslationJobStatus.Queued.value),
            queueItem(id = 2, status = TranslationJobStatus.Retrying.value),
            queueItem(id = 3, status = TranslationJobStatus.Running.value),
            queueItem(id = 4, status = TranslationJobStatus.PausedAuth.value),
            queueItem(id = 5, status = TranslationJobStatus.Completed.value),
        )

        TranslationQueueUiModel.filterAndGroup(items, filters = emptySet())
            .flatMap { it.items }
            .map { it.id } shouldContainExactly listOf(1L, 2L, 3L, 4L, 5L)

        TranslationQueueUiModel.filterAndGroup(
            items,
            filters = setOf(TranslationQueueTypeFilter.Waiting, TranslationQueueTypeFilter.Paused),
        )
            .flatMap { it.items }
            .map { it.id } shouldContainExactly listOf(1L, 2L, 4L)
    }

    @Test
    fun `adjacent identical logs collapse with count and time range`() {
        val logs = listOf(
            logItem(id = 3, createdAt = 3000, message = "Retry blocked", details = "same"),
            logItem(id = 2, createdAt = 2000, message = "Retry blocked", details = "same"),
            logItem(id = 1, createdAt = 1000, message = "Queued", details = "other"),
        )

        val grouped = TranslationLogUiModel.groupAdjacent(logs)

        grouped.size shouldBe 2
        grouped.first().count shouldBe 2
        grouped.first().firstCreatedAt shouldBe 2000
        grouped.first().latestCreatedAt shouldBe 3000
        grouped.first().first.details shouldBe "same"
    }

    @Test
    fun `non adjacent or different log details do not collapse`() {
        val logs = listOf(
            logItem(id = 4, message = "Retry blocked", details = "same"),
            logItem(id = 3, message = "Queued", details = "other"),
            logItem(id = 2, message = "Retry blocked", details = "same"),
            logItem(id = 1, message = "Retry blocked", details = "different"),
        )

        TranslationLogUiModel.groupAdjacent(logs).map { it.count } shouldContainExactly listOf(1, 1, 1, 1)
    }

    private fun queueItem(
        id: Long,
        mangaId: Long = 1,
        mangaTitle: String = "Alpha",
        chapterNumber: Double? = 1.0,
        chapterName: String? = "Chapter 1",
        status: String = TranslationJobStatus.Queued.value,
    ): TranslationQueueItem {
        return TranslationQueueItem(
            id = id,
            mangaId = mangaId,
            mangaTitle = mangaTitle,
            chapterId = id,
            chapterName = chapterName,
            chapterNumber = chapterNumber,
            pageIndex = null,
            scope = TranslationScope.Chapter.value,
            pipeline = "gemini_vision",
            mode = TranslationMode.Overlay.value,
            model = "gemini-3-flash-preview",
            targetLanguage = "English",
            sourceLanguage = null,
            overwrite = false,
            status = status,
            progressCurrent = 0,
            progressTotal = 1,
            attempts = 0,
            createdAt = id,
            updatedAt = id,
            errorMessage = null,
        )
    }

    private fun logItem(
        id: Long,
        createdAt: Long = id,
        message: String,
        details: String?,
    ): TranslationLogUiItem {
        return TranslationLogUiItem(
            id = id,
            jobId = 1,
            pageId = null,
            createdAt = createdAt,
            level = TranslationLogLevel.Warning.value,
            tag = "queue",
            message = message,
            details = details,
        )
    }
}
