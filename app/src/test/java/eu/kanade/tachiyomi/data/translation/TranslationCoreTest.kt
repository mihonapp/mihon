package eu.kanade.tachiyomi.data.translation

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.data.Translation_boxes
import tachiyomi.data.Translation_jobs
import tachiyomi.domain.translation.service.DEFAULT_TRANSLATION_SYSTEM_PROMPT
import tachiyomi.domain.translation.service.TranslationPreferences
import java.io.IOException
import kotlin.math.abs

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
              "thoughtSignature": "${"A".repeat(160)}",
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
              "thoughtSignature": "<redacted>",
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
            rawResponseJson = """{"thoughtSignature":"${"b".repeat(160)}","error":{"message":"quota exceeded"}}""",
        )

        details shouldContain "operation=translatePageImage"
        details shouldContain "status_code=429"
        details shouldContain "Translate hello"
        details shouldContain "quota exceeded"
        details shouldContain "<redacted-image>"
        details shouldContain "thoughtSignature"
        details shouldContain "x-goog-api-key=<redacted>"
        details shouldNotContain "secret-header"
        details shouldNotContain "aaaaaaaaaaaaaaaa"
        details shouldNotContain "bbbbbbbbbbbbbbbb"
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
        preferences.parallelRetryLanes.get() shouldBe "1"
        preferences.sourceLanguage.get() shouldBe TranslationLanguages.SOURCE_AUTO
        preferences.overlayTextSizeMode.get() shouldBe "dynamic"
        preferences.overlayFontFamily.get() shouldBe "system"
        preferences.overlayBoxPaddingDp.get() shouldBe 0
        preferences.concurrency.get() shouldBe 1
        preferences.globalInstructions.get() shouldBe DEFAULT_TRANSLATION_SYSTEM_PROMPT
    }

    @Test
    fun `setup ping config leaves room for Gemini 3 thinking tokens`() {
        val obj = TranslationSetupPingPolicy.generationConfig().jsonObject

        obj["temperature"].toString() shouldBe "0"
        obj["maxOutputTokens"].toString() shouldBe "128"
        obj["thinkingConfig"]!!.jsonObject shouldContain (
            "thinkingLevel" to Json.parseToJsonElement("\"low\"")
            )
        TranslationSetupPingPolicy.REQUEST_SUMMARY shouldContain "maxOutputTokens=128"
        TranslationSetupPingPolicy.REQUEST_SUMMARY shouldContain "thinkingLevel=low"
    }

    @Test
    fun `overlay editor geometry clamps page edge boxes without empty ranges`() {
        val geometry = TranslationBoxGeometryNormalizer.normalize(
            x = 0.99f,
            y = 0.99f,
            width = 0.5f,
            height = Float.NaN,
        )

        geometry.x shouldBe 0.99f
        geometry.y shouldBe 0.99f
        geometry.width shouldBe TranslationBoxGeometryNormalizer.MIN_BOX_SIZE
        geometry.height shouldBe TranslationBoxGeometryNormalizer.MIN_BOX_SIZE

        val range = TranslationBoxGeometryNormalizer.safeSliderRange(0.01f, 0.00999999f)
        range.start shouldBe 0f
        range.endInclusive shouldBe 1f
        TranslationBoxGeometryNormalizer.safeSliderValue(Float.NaN, range) shouldBe 0f
    }

    @Test
    fun `whole number Gemini coordinates normalize from image pixels`() {
        val overlay = Json.decodeFromString<TranslationOverlayResult>(
            """
            {
              "sourceLanguage": "ja",
              "targetLanguage": "en",
              "boxes": [
                {
                  "x": 811,
                  "y": 38,
                  "width": 106,
                  "height": 155,
                  "confidence": 0.99,
                  "originalText": "恭子さん。",
                  "translatedText": "Kyoko-san.",
                  "textType": "speech"
                },
                {
                  "x": 80,
                  "y": 820,
                  "width": 65,
                  "height": 125,
                  "confidence": 0.99,
                  "originalText": "だめよ。",
                  "translatedText": "No.",
                  "textType": "speech"
                }
              ]
            }
            """.trimIndent(),
        )

        val result = TranslationOverlayCoordinateNormalizer.normalize(
            overlay = overlay,
            imageWidth = 1000,
            imageHeight = 1000,
        )

        result.report.convertedPixelBoxes shouldBe 2
        result.report.droppedBoxes shouldBe 0
        result.overlay.boxes.map { it.x } shouldContainExactly listOf(0.811f, 0.08f)
        result.overlay.boxes.map { it.y } shouldContainExactly listOf(0.038f, 0.82f)
        result.overlay.boxes.map { it.width } shouldContainExactly listOf(0.106f, 0.065f)
        result.overlay.boxes.map { it.height } shouldContainExactly listOf(0.155f, 0.125f)
    }

    @Test
    fun `Gemini overlay parser recovers fenced prose json and numeric string coordinates`() {
        val parsed = TranslationOverlayJsonParser.parseOverlay(
            """
            Sure, here is the overlay for pages [162]:
            ```json
            {
              "sourceLanguage": "ja",
              "targetLanguage": "en",
              "boxes": [
                {
                  "x": "811",
                  "y": "38",
                  "width": "106",
                  "height": "155",
                  "confidence": "0.99",
                  "originalText": "恭子さん。",
                  "translatedText": "Kyoko-san.",
                  "textType": "speech"
                }
              ]
            }
            ```
            """.trimIndent(),
        )

        parsed.recovered shouldBe true
        parsed.value.sourceLanguage shouldBe "ja"
        parsed.value.boxes.single().x shouldBe 811f
        parsed.value.boxes.single().confidence shouldBe 0.99f
    }

    @Test
    fun `Gemini batch parser accepts page aliases and numeric string geometry`() {
        val parsed = TranslationOverlayJsonParser.parseBatch(
            """
            ```json
            {
              "targetLanguage": "en",
              "pages": [
                {
                  "pageNumber": "162",
                  "boxes": [
                    {
                      "x": "0.1",
                      "y": "0.2",
                      "width": "0.3",
                      "height": "0.4",
                      "originalText": "入口",
                      "translatedText": "Entrance"
                    }
                  ]
                }
              ]
            }
            ```
            """.trimIndent(),
        )

        parsed.recovered shouldBe true
        parsed.value.single().pageIndex shouldBe 162
        parsed.value.single().overlay.targetLanguage shouldBe "en"
        parsed.value.single().overlay.boxes.single().width shouldBe 0.3f
    }

    @Test
    fun `Gemini batch parser remaps one based pageNumber aliases when expected indexes are zero based`() {
        val parsed = TranslationOverlayJsonParser.parseBatch(
            """
            {
              "targetLanguage": "en",
              "pages": [
                {
                  "pageNumber": 1,
                  "boxes": [
                    {
                      "x": 0.1,
                      "y": 0.2,
                      "width": 0.3,
                      "height": 0.4,
                      "originalText": "恭子さん。",
                      "translatedText": "Kyoko-san."
                    }
                  ]
                },
                {
                  "pageNumber": 2,
                  "boxes": [
                    {
                      "x": 0.5,
                      "y": 0.6,
                      "width": 0.2,
                      "height": 0.1,
                      "originalText": "だめよ。",
                      "translatedText": "No."
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
            expectedPageIndexes = listOf(0, 1),
        )

        parsed.value.map { it.pageIndex } shouldContainExactly listOf(0, 1)
        parsed.remappedPages shouldBe 2
    }

    @Test
    fun `Gemini batch parser remaps one based pageNumber aliases within later chunks`() {
        val parsed = TranslationOverlayJsonParser.parseBatch(
            """
            {
              "targetLanguage": "en",
              "pages": [
                {
                  "pageNumber": 1,
                  "boxes": [
                    {
                      "x": 0.1,
                      "y": 0.2,
                      "width": 0.3,
                      "height": 0.4,
                      "originalText": "ここからは…",
                      "translatedText": "From here on..."
                    }
                  ]
                },
                {
                  "pageNumber": 2,
                  "boxes": [
                    {
                      "x": 0.5,
                      "y": 0.6,
                      "width": 0.2,
                      "height": 0.1,
                      "originalText": "仰せのままに…",
                      "translatedText": "As you wish..."
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
            expectedPageIndexes = listOf(38, 39),
        )

        parsed.value.map { it.pageIndex } shouldContainExactly listOf(38, 39)
        parsed.remappedPages shouldBe 2
    }

    @Test
    fun `Gemini batch parser remaps absolute one based pageNumber aliases when expected index is present`() {
        val parsed = TranslationOverlayJsonParser.parseBatch(
            """
            {
              "targetLanguage": "en",
              "pages": [
                {
                  "pageNumber": 163,
                  "boxes": [
                    {
                      "x": 0.1,
                      "y": 0.2,
                      "width": 0.3,
                      "height": 0.4,
                      "originalText": "恭子さん。",
                      "translatedText": "Kyoko-san."
                    }
                  ]
                },
                {
                  "pageNumber": 168,
                  "boxes": [
                    {
                      "x": 0.5,
                      "y": 0.6,
                      "width": 0.2,
                      "height": 0.1,
                      "originalText": "仰せのままに…",
                      "translatedText": "As you wish..."
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
            expectedPageIndexes = listOf(162, 167),
        )

        parsed.value.map { it.pageIndex } shouldContainExactly listOf(162, 167)
        parsed.remappedPages shouldBe 2
    }

    @Test
    fun `Gemini batch parser remaps page and index aliases when they are local chunk positions`() {
        val parsed = TranslationOverlayJsonParser.parseBatch(
            """
            {
              "targetLanguage": "en",
              "pages": [
                {
                  "page": 1,
                  "boxes": [
                    {
                      "x": 0.1,
                      "y": 0.2,
                      "width": 0.3,
                      "height": 0.4,
                      "originalText": "ここからは…",
                      "translatedText": "From here on..."
                    }
                  ]
                },
                {
                  "index": 1,
                  "boxes": [
                    {
                      "x": 0.5,
                      "y": 0.6,
                      "width": 0.2,
                      "height": 0.1,
                      "originalText": "仰せのままに…",
                      "translatedText": "As you wish..."
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
            expectedPageIndexes = listOf(38, 39),
        )

        parsed.value.map { it.pageIndex } shouldContainExactly listOf(38, 39)
        parsed.remappedPages shouldBe 2
    }

    @Test
    fun `Gemini text extractor joins split text parts before json parse`() {
        val text = TranslationGeminiTextParts.join(
            listOf(
                """{"sourceLanguage":"ja","targetLanguage":"en","boxes":[""",
                """{"x":0.1,"y":0.2,"width":0.3,"height":0.4,"originalText":"入口","translatedText":"Entrance"}]}""",
            ),
        )

        val parsed = TranslationOverlayJsonParser.parseOverlay(text)

        parsed.value.boxes.single().translatedText shouldBe "Entrance"
    }

    @Test
    fun `Gemini text extractor skips blank candidates before parse`() {
        val text = TranslationGeminiTextParts.firstNonBlankCandidate(
            listOf(
                listOf(null, ""),
                listOf("""{"sourceLanguage":"ja","targetLanguage":"en","boxes":[]}"""),
            ),
        )

        text shouldBe """{"sourceLanguage":"ja","targetLanguage":"en","boxes":[]}"""
        TranslationOverlayJsonParser.parseOverlay(text.orEmpty()).value.boxes shouldBe emptyList()
    }

    @Test
    fun `Gemini overlay parser fails clearly when no json payload exists`() {
        val error = runCatching {
            TranslationOverlayJsonParser.parseOverlay("No usable overlay today.")
        }.exceptionOrNull()

        error?.message.orEmpty() shouldContain "did not contain JSON"
    }

    @Test
    fun `normalized Gemini coordinates remain stable`() {
        val overlay = TranslationOverlayResult(
            boxes = listOf(
                TranslationOverlayBox(
                    x = 0.738f,
                    y = 0.117f,
                    width = 0.123f,
                    height = 0.267f,
                    confidence = 0.99f,
                    originalText = "掟があるとはいえ…",
                    translatedText = "Even if there are rules...",
                    textType = "thought",
                ),
            ),
        )

        val result = TranslationOverlayCoordinateNormalizer.normalize(
            overlay = overlay,
            imageWidth = 1000,
            imageHeight = 1000,
        )

        result.report.convertedPixelBoxes shouldBe 0
        result.report.droppedBoxes shouldBe 0
        result.overlay.boxes.single() shouldBe overlay.boxes.single()
    }

    @Test
    fun `mixed normalized and pixel coordinates convert only pixel fields`() {
        val overlay = TranslationOverlayResult(
            boxes = listOf(
                TranslationOverlayBox(
                    x = 0.7562f,
                    y = 639f,
                    width = 0.1157f,
                    height = 0.1009f,
                    originalText = "混在",
                    translatedText = "Mixed",
                    textType = "speech",
                ),
            ),
        )

        val result = TranslationOverlayCoordinateNormalizer.normalize(
            overlay = overlay,
            imageWidth = 844,
            imageHeight = 1200,
        )

        val box = result.overlay.boxes.single()
        result.report.convertedPixelBoxes shouldBe 1
        result.report.droppedBoxes shouldBe 0
        box.x shouldBe 0.7562f
        box.y shouldBe 0.5325f
        box.width shouldBe 0.1157f
        box.height shouldBe 0.1009f
    }

    @Test
    fun `thousandth style edge coordinates do not collapse to one percent slivers`() {
        val overlay = TranslationOverlayResult(
            boxes = listOf(
                TranslationOverlayBox(
                    x = 840f,
                    y = 349f,
                    width = 24f,
                    height = 125f,
                    originalText = "完全会員制で",
                    translatedText = "Completely membership-based,",
                    textType = "caption",
                ),
                TranslationOverlayBox(
                    x = 849f,
                    y = 93f,
                    width = 23f,
                    height = 155f,
                    originalText = "リッチマンズホテル",
                    translatedText = "Rich Man's Hotel",
                    textType = "caption",
                ),
            ),
        )

        val result = TranslationOverlayCoordinateNormalizer.normalize(
            overlay = overlay,
            imageWidth = 844,
            imageHeight = 1200,
        )

        result.report.convertedPixelBoxes shouldBe 2
        result.report.droppedBoxes shouldBe 0
        result.overlay.boxes.map { it.x } shouldContainExactly listOf(0.84f, 0.849f)
        result.overlay.boxes.map { it.width } shouldContainExactly listOf(0.024f, 0.023f)
        result.overlay.boxes.map { it.y } shouldContainExactly listOf(349f / 1200f, 93f / 1200f)
        result.overlay.boxes.map { it.height } shouldContainExactly listOf(125f / 1200f, 155f / 1200f)
        result.report.entries.map { it.reason } shouldContainExactly listOf(
            "x_thousandth_y_pixel_coordinates_converted",
            "x_thousandth_y_pixel_coordinates_converted",
        )
    }

    @Test
    fun `pixel coordinates without image dimensions are dropped with report`() {
        val overlay = TranslationOverlayResult(
            boxes = listOf(
                TranslationOverlayBox(
                    x = 762f,
                    y = 16f,
                    width = 164f,
                    height = 221f,
                    confidence = 0.99f,
                    originalText = "私が…",
                    translatedText = "I...",
                    textType = "speech",
                ),
            ),
        )

        val result = TranslationOverlayCoordinateNormalizer.normalize(
            overlay = overlay,
            imageWidth = null,
            imageHeight = null,
        )

        result.overlay.boxes shouldBe emptyList()
        result.report.convertedPixelBoxes shouldBe 0
        result.report.droppedBoxes shouldBe 1
        result.report.entries.single().reason shouldBe "pixel_coordinates_without_image_size"
    }

    @Test
    fun `invalid coordinate geometry is dropped before saving`() {
        val overlay = TranslationOverlayResult(
            boxes = listOf(
                TranslationOverlayBox(
                    x = Float.NaN,
                    y = 0.1f,
                    width = 0.2f,
                    height = 0.2f,
                    originalText = "bad",
                    translatedText = "bad",
                ),
                TranslationOverlayBox(
                    x = 0.2f,
                    y = 0.2f,
                    width = 0f,
                    height = 0.2f,
                    originalText = "zero",
                    translatedText = "zero",
                ),
            ),
        )

        val result = TranslationOverlayCoordinateNormalizer.normalize(
            overlay = overlay,
            imageWidth = 1000,
            imageHeight = 1000,
        )

        result.overlay.boxes shouldBe emptyList()
        result.report.droppedBoxes shouldBe 2
    }

    @Test
    fun `overlay persistence guard rejects invalid geometry instead of coercing it visible`() {
        TranslationOverlayPersistenceGuard.normalizedGeometryOrNull(
            TranslationOverlayBox(
                x = 2f,
                y = 0.1f,
                width = 0.2f,
                height = 0.2f,
                originalText = "bad",
                translatedText = "bad",
            ),
        ) shouldBe null

        TranslationOverlayPersistenceGuard.normalizedGeometryOrNull(
            TranslationOverlayBox(
                x = 0.2f,
                y = 0.1f,
                width = 0.2f,
                height = 0.2f,
                originalText = "ok",
                translatedText = "ok",
            ),
        ) shouldBe TranslationBoxGeometry(0.2f, 0.1f, 0.2f, 0.2f)
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

        TranslationJobStatus.ManualRetry.isActiveForDedupe() shouldBe true
    }

    @Test
    fun `retry planner blocks until setup ready and uses manual retry lane for manual starts`() {
        TranslationRetryPlanner.manualRetry(setupReady = false) shouldBe TranslationRetryDecision(
            allowed = false,
            nextStatus = null,
            startPolicy = null,
            forceOverwrite = false,
        )

        TranslationRetryPlanner.manualRetry(setupReady = true) shouldBe TranslationRetryDecision(
            allowed = true,
            nextStatus = TranslationJobStatus.ManualRetry,
            startPolicy = TranslationWorkStartPolicy.Keep,
            forceOverwrite = true,
        )

        TranslationRetryPlanner.autoRequeueAfterSetup(
            status = TranslationJobStatus.PausedAuth,
            setupReady = true,
        ) shouldBe TranslationRetryDecision(
            allowed = true,
            nextStatus = TranslationJobStatus.Queued,
            startPolicy = TranslationWorkStartPolicy.Replace,
            forceOverwrite = false,
        )

        TranslationRetryPlanner.autoRequeueAfterSetup(
            status = TranslationJobStatus.PausedQuota,
            setupReady = true,
        ).allowed shouldBe false
    }

    @Test
    fun `manual retry lanes default to one and zero means unlimited`() {
        TranslationRetryLanePlanner.workerCount(pendingManualRetryJobs = 0, configuredLanes = 1) shouldBe 0
        TranslationRetryLanePlanner.workerCount(pendingManualRetryJobs = 5, configuredLanes = 1) shouldBe 1
        TranslationRetryLanePlanner.workerCount(pendingManualRetryJobs = 5, configuredLanes = 2) shouldBe 2
        TranslationRetryLanePlanner.workerCount(pendingManualRetryJobs = 5, configuredLanes = 0) shouldBe 5

        val preferences = TranslationPreferences(InMemoryPreferenceStore())
        preferences.parallelRetryLanes.set("-1")
        preferences.normalizedParallelRetryLanes(pendingManualRetryJobs = 5) shouldBe 1
        preferences.parallelRetryLanes.set("invalid")
        preferences.normalizedParallelRetryLanes(pendingManualRetryJobs = 5) shouldBe 1
        preferences.parallelRetryLanes.set("0")
        preferences.normalizedParallelRetryLanes(pendingManualRetryJobs = 5) shouldBe 5
    }

    @Test
    fun `translation concurrency default one accepts eight and zero means unlimited`() {
        TranslationConcurrencyPlanner.workerCount(
            configuredConcurrency = 1,
            pendingGroups = 8,
        ) shouldBe 1
        TranslationConcurrencyPlanner.workerCount(
            configuredConcurrency = 8,
            pendingGroups = 8,
        ) shouldBe 8
        TranslationConcurrencyPlanner.workerCount(
            configuredConcurrency = 0,
            pendingGroups = 8,
        ) shouldBe 8
        TranslationConcurrencyPlanner.workerCount(
            configuredConcurrency = -1,
            pendingGroups = 8,
        ) shouldBe 1
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
    fun `batch enqueue planner queues all eligible pages because worker chunks batches`() {
        val pages = (0 until 50).map { page ->
            TranslationPageCandidate(chapterId = 1, pageIndex = page, hasOverlay = false)
        }

        TranslationBatchPlanner.pagesToQueue(
            pages = pages,
            overwrite = false,
            maxImagesPerBatch = 38,
        ).map { it.pageIndex } shouldContainExactly (0 until 50).toList()

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
        ).map { it.pageIndex } shouldContainExactly listOf(2, 3)
    }

    @Test
    fun `pending job batcher chunks one hundred images by max images per batch`() {
        val jobs = (0 until 100).map { index ->
            translationJob(
                id = index + 1L,
                chapterId = 10,
                pageIndex = index.toLong(),
            )
        }

        val groups = TranslationPendingJobBatcher.groupPendingJobs(jobs, maxImagesPerBatch = 38)

        groups.map { it.jobs.size } shouldContainExactly listOf(38, 38, 24)
    }

    @Test
    fun `pending job batcher caps all mode worker groups to avoid overlong workers`() {
        val jobs = (0 until 100).map { index ->
            translationJob(
                id = index + 1L,
                chapterId = 10,
                pageIndex = index.toLong(),
            )
        }

        val groups = TranslationPendingJobBatcher.groupPendingJobs(jobs, maxImagesPerBatch = TRANSLATION_BATCH_ALL)

        groups.map { it.jobs.size } shouldContainExactly listOf(38, 38, 24)
        groups.flatMap { it.jobs }.map { it.page_index?.toInt() } shouldContainExactly (0 until 100).toList()
    }

    @Test
    fun `batch fallback planner splits malformed all mode batches before single page fallback`() {
        TranslationBatchFallbackPlanner.splitIndexes(169) shouldBe ((0 until 84) to (84 until 169))
        TranslationBatchFallbackPlanner.splitIndexes(2) shouldBe ((0 until 1) to (1 until 2))
        TranslationBatchFallbackPlanner.splitIndexes(1) shouldBe null
    }

    @Test
    fun `vision payload policy splits large inline image batches before request construction`() {
        val twoMiB = 2L * 1024L * 1024L
        val ranges = TranslationVisionBatchPayloadPolicy.splitByPayload(
            imageByteSizes = List(38) { twoMiB },
            maxBytes = 10L * 1024L * 1024L,
        )

        ranges.map { it.count() } shouldContainExactly listOf(3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 2)
        ranges.flatMap { it.toList() } shouldContainExactly (0 until 38).toList()
    }

    @Test
    fun `vision payload policy bounds prepared image batches before loading all pages`() {
        val ranges = TranslationVisionBatchPayloadPolicy.splitByPreparedPageCount(169)

        ranges.map { it.count() } shouldContainExactly listOf(38, 38, 38, 38, 17)
        ranges.flatMap { it.toList() } shouldContainExactly (0 until 169).toList()
    }

    @Test
    fun `vision payload policy keeps a single oversized page as one request`() {
        val ranges = TranslationVisionBatchPayloadPolicy.splitByPayload(
            imageByteSizes = listOf(20L * 1024L * 1024L),
            maxBytes = 10L * 1024L * 1024L,
        )

        ranges shouldContainExactly listOf(0..0)
    }

    @Test
    fun `batch fallback classifier only allows parser shaped failures`() {
        TranslationBatchFailureClassifier.shouldUseBatchFallback(
            IllegalArgumentException("Gemini response did not contain usable overlay JSON"),
        ) shouldBe true
        TranslationBatchFailureClassifier.shouldUseBatchFallback(
            IllegalStateException("Gemini batch response did not include text"),
        ) shouldBe true
        TranslationBatchFailureClassifier.shouldUseBatchFallback(
            SerializationException("Unexpected JSON token"),
        ) shouldBe true

        TranslationBatchFailureClassifier.shouldUseBatchFallback(
            IOException("Unable to resolve host generativelanguage.googleapis.com"),
        ) shouldBe false
        TranslationBatchFailureClassifier.shouldUseBatchFallback(
            GeminiApiException(503, """{"error":{"message":"unavailable"}}"""),
        ) shouldBe false
        TranslationBatchFailureClassifier.shouldUseBatchFallback(
            IllegalArgumentException("Wrapped transport failure", IOException("dns failed")),
        ) shouldBe false
    }

    @Test
    fun `Gemini client uses long timeouts for large live translation batches`() {
        TranslationGeminiNetworkPolicy.READ_TIMEOUT_MINUTES shouldBe 10L
        TranslationGeminiNetworkPolicy.WRITE_TIMEOUT_MINUTES shouldBe 10L
        TranslationGeminiNetworkPolicy.CALL_TIMEOUT_MINUTES shouldBe 10L
    }

    @Test
    fun `translation workers avoid foreground service time limit cancellation`() {
        TranslationWorkerPolicy.USE_FOREGROUND_SERVICE shouldBe false
        TranslationWorkerPolicy.BATCH_EXECUTION_TIMEOUT_MS shouldBe 30L * 60L * 1000L
        TranslationRunningJobPolicy.HEARTBEAT_MS shouldBe 30_000L
        TranslationRunningJobPolicy.STALE_RUNNING_MS shouldBe
            TranslationRunningJobPolicy.HEARTBEAT_MS * 3
    }

    @Test
    fun `worker continuation policy yields after a processed group when pending work remains`() {
        TranslationWorkerContinuationPolicy.shouldYieldAfterGroups(
            processedGroupCount = 0,
            hasPendingJobs = true,
        ) shouldBe false
        TranslationWorkerContinuationPolicy.shouldYieldAfterGroups(
            processedGroupCount = 1,
            hasPendingJobs = false,
        ) shouldBe false
        TranslationWorkerContinuationPolicy.shouldYieldAfterGroups(
            processedGroupCount = 1,
            hasPendingJobs = true,
        ) shouldBe true
    }

    @Test
    fun `running job policy keeps fresh normal jobs blocking new claims`() {
        val now = 20_000L + TranslationRunningJobPolicy.STALE_RUNNING_MS
        val fresh = translationJob(
            id = 1,
            chapterId = 10,
            pageIndex = 0,
            status = TranslationJobStatus.Running.value,
            updatedAt = now - TranslationRunningJobPolicy.STALE_RUNNING_MS + 1,
            errorMessage = "normal:0:123:0:0",
        )
        val stale = fresh.copy(
            _id = 2,
            updated_at = now - TranslationRunningJobPolicy.STALE_RUNNING_MS,
        )

        TranslationRunningJobPolicy.matchesKind(fresh, TranslationWorkKind.Normal) shouldBe true
        TranslationRunningJobPolicy.matchesKind(fresh, TranslationWorkKind.ManualRetry) shouldBe false
        TranslationRunningJobPolicy.isStale(fresh, now) shouldBe false
        TranslationRunningJobPolicy.isStale(stale, now) shouldBe true
        TranslationRunningJobPolicy.waitMsUntilStale(fresh, now) shouldBe 1_001L
        TranslationRunningJobPolicy.waitMsUntilStale(
            fresh.copy(updated_at = now - 1_000L),
            now,
        ) shouldBe TranslationRunningJobPolicy.HEARTBEAT_MS
        TranslationRunningJobPolicy.requeueStatus(TranslationWorkKind.Normal) shouldBe TranslationJobStatus.Retrying
    }

    @Test
    fun `running job policy does not recover stale jobs owned by active worker`() {
        val now = 20_000L + TranslationRunningJobPolicy.STALE_RUNNING_MS
        val stale = translationJob(
            id = 1,
            chapterId = 10,
            pageIndex = 0,
            status = TranslationJobStatus.Running.value,
            updatedAt = now - TranslationRunningJobPolicy.STALE_RUNNING_MS,
            errorMessage = "normal:0:123:0:0",
        )

        TranslationRunningJobPolicy.isRecoverableStale(
            job = stale,
            now = now,
            activeJobIds = setOf(stale._id),
        ) shouldBe false
        TranslationRunningJobPolicy.blocksNewClaims(
            job = stale,
            now = now,
            activeJobIds = setOf(stale._id),
        ) shouldBe true
        TranslationRunningJobPolicy.isRecoverableStale(
            job = stale,
            now = now,
            activeJobIds = emptySet(),
        ) shouldBe true
    }

    @Test
    fun `running job policy separates manual retry leases`() {
        val manual = translationJob(
            id = 1,
            chapterId = 10,
            pageIndex = 0,
            status = TranslationJobStatus.Running.value,
            errorMessage = "manual_retry:1:123:0:0",
        )
        val legacyNormal = manual.copy(_id = 2, error_message = null)

        TranslationRunningJobPolicy.matchesKind(manual, TranslationWorkKind.ManualRetry) shouldBe true
        TranslationRunningJobPolicy.matchesKind(manual, TranslationWorkKind.Normal) shouldBe false
        TranslationRunningJobPolicy.matchesKind(legacyNormal, TranslationWorkKind.Normal) shouldBe true
        TranslationRunningJobPolicy.requeueStatus(TranslationWorkKind.ManualRetry) shouldBe TranslationJobStatus.ManualRetry
    }

    @Test
    fun `stopped worker recovery keeps normal and manual retry jobs retryable`() {
        val normal = translationJob(
            id = 1,
            chapterId = 10,
            pageIndex = 0,
            status = TranslationJobStatus.Running.value,
            errorMessage = "normal:0:123:0:0",
        )
        val manual = normal.copy(
            _id = 2,
            error_message = "manual_retry:1:123:0:0",
        )
        val legacyNormal = normal.copy(
            _id = 3,
            error_message = null,
        )

        TranslationRunningJobPolicy.kindForClaimToken(normal) shouldBe TranslationWorkKind.Normal
        TranslationRunningJobPolicy.requeueStatusForStoppedWorker(normal) shouldBe TranslationJobStatus.Retrying
        TranslationRunningJobPolicy.kindForClaimToken(manual) shouldBe TranslationWorkKind.ManualRetry
        TranslationRunningJobPolicy.requeueStatusForStoppedWorker(manual) shouldBe TranslationJobStatus.ManualRetry
        TranslationRunningJobPolicy.kindForClaimToken(legacyNormal) shouldBe TranslationWorkKind.Normal
        TranslationRunningJobPolicy.requeueStatusForStoppedWorker(legacyNormal) shouldBe TranslationJobStatus.Retrying
        TranslationClaimToken.publicErrorMessage(manual.error_message) shouldBe null
    }

    @Test
    fun `pending job batcher groups compatible image jobs up to cap`() {
        val jobs = (0 until 5).map { index ->
            translationJob(
                id = index + 1L,
                chapterId = 10,
                pageIndex = index.toLong(),
            )
        } + translationJob(
            id = 20,
            chapterId = 11,
            pageIndex = 0,
        )

        val groups = TranslationPendingJobBatcher.groupPendingJobs(jobs, maxImagesPerBatch = 3)

        groups.map { it.jobs.map(Translation_jobs::_id) } shouldContainExactly listOf(
            listOf(1L, 2L, 3L),
            listOf(4L, 5L),
            listOf(20L),
        )
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
        val defaultSystemPrompt = TranslationPromptPolicy.systemPrompt(DEFAULT_TRANSLATION_SYSTEM_PROMPT)
        val pagePrompt = TranslationPromptPolicy.pagePrompt("English", "ja")
        val batchPrompt = TranslationPromptPolicy.batchPagePrompt(listOf(1, 2), "English", "ja")

        systemPrompt shouldContain "Ignore sound effects"
        systemPrompt shouldContain "Keep honorifics."
        defaultSystemPrompt shouldNotContain "Additional user system prompt"
        pagePrompt shouldContain "Source language: Japanese"
        pagePrompt shouldContain "Do not return pixel coordinates"
        batchPrompt shouldContain "Do not return pixel coordinates"
        batchPrompt shouldContain "Use only these exact pageIndex values"
        batchPrompt shouldContain "Do not return pageNumber, page, or local batch positions"
        pagePrompt shouldNotContain "Include dialogue, captions, signs, and sound effects"
    }

    @Test
    fun `overlay style json round trips overrides`() {
        val style = TranslationOverlayBoxStyle(
            fontFamily = "serif",
            textColor = "#FFFFFFFF",
            fillColor = "#80000000",
            strokeColor = "#FFFF0000",
            paddingDp = 6f,
            textAlign = "start",
        )

        TranslationOverlayBoxStyle.fromJson(style.toJsonOrNull()) shouldBe style
        TranslationOverlayBoxStyle().toJsonOrNull() shouldBe null
    }

    @Test
    fun `overlay text fit policy grows short text and shrinks long text within readable bounds`() {
        val shortText = TranslationOverlayTextFitPolicy.sizeRangePx(
            mode = "dynamic",
            customSp = 16,
            boxHeightPx = 160f,
            density = 1f,
            scaledDensity = 1f,
            paddingPx = 0f,
        )
        val longText = TranslationOverlayTextFitPolicy.sizeRangePx(
            mode = "dynamic",
            customSp = 16,
            boxHeightPx = 30f,
            density = 1f,
            scaledDensity = 1f,
            paddingPx = 0f,
        )
        val custom = TranslationOverlayTextFitPolicy.sizeRangePx(
            mode = "custom",
            customSp = 32,
            boxHeightPx = 160f,
            density = 1f,
            scaledDensity = 1f,
            paddingPx = 0f,
        )

        shortText.minPx shouldBe 8f
        shortText.preferredMaxPx shouldBe 24f
        longText.preferredMaxPx shouldBe 9.375f
        custom.preferredMaxPx shouldBe 32f
        TranslationOverlayTextFitPolicy.shouldLogTruncation(ellipsisCount = 1) shouldBe true
    }

    @Test
    fun `overlay text orientation policy keeps tall narrow boxes horizontal`() {
        TranslationOverlayTextOrientationPolicy.shouldRotateTranslatedText(
            boxWidthPx = 24f,
            boxHeightPx = 220f,
            textLength = 48,
        ) shouldBe false

        TranslationOverlayTextOrientationPolicy.shouldRotateTranslatedText(
            boxWidthPx = 180f,
            boxHeightPx = 80f,
            textLength = 8,
        ) shouldBe false
    }

    @Test
    fun `overlay display transform preserves identity boxes and text on single fragment`() {
        val source = overlaySourceBox(
            x = 0.1f,
            y = 0.2f,
            width = 0.3f,
            height = 0.4f,
            translatedText = "Hello there",
        )

        val display = TranslationOverlayDisplayTransformer.transform(
            boxes = listOf(source),
            transform = TranslationOverlayDisplayTransform.Identity,
        )

        source.x shouldBe 0.1f
        source.y shouldBe 0.2f
        display.single().displayX shouldBeCloseTo 0.1f
        display.single().displayY shouldBeCloseTo 0.2f
        display.single().displayWidth shouldBeCloseTo 0.3f
        display.single().displayHeight shouldBeCloseTo 0.4f
        display.single().drawTranslatedText shouldBe true
    }

    @Test
    fun `display transformation never mutates persisted source boxes`() {
        val persisted = translationBox(
            x = 0.4,
            y = 0.2,
            width = 0.3,
            height = 0.2,
        )
        val original = persisted.copy()

        val display = TranslationOverlayDisplayTransformer.fromPersisted(
            boxes = listOf(persisted),
            transform = TranslationOverlayDisplayTransform.reflowed(TranslationOverlaySourceHalf.Right),
        )

        persisted shouldBe original
        display.map { it.sourceX } shouldContainExactly listOf(persisted.x.toFloat(), persisted.x.toFloat())
        display.map { it.displayY } shouldContainExactlyCloseTo listOf(0.6f, 0.1f)
    }

    @Test
    fun `overlay display transform rotates boxes clockwise and counter clockwise`() {
        val source = overlaySourceBox(
            x = 0.1f,
            y = 0.2f,
            width = 0.3f,
            height = 0.4f,
        )

        val clockwise = TranslationOverlayDisplayTransformer.transform(
            boxes = listOf(source),
            transform = TranslationOverlayDisplayTransform.Clockwise90,
        ).single()
        val counterClockwise = TranslationOverlayDisplayTransformer.transform(
            boxes = listOf(source),
            transform = TranslationOverlayDisplayTransform.CounterClockwise90,
        ).single()

        clockwise.displayX shouldBeCloseTo 0.4f
        clockwise.displayY shouldBeCloseTo 0.1f
        clockwise.displayWidth shouldBeCloseTo 0.4f
        clockwise.displayHeight shouldBeCloseTo 0.3f
        counterClockwise.displayX shouldBeCloseTo 0.2f
        counterClockwise.displayY shouldBeCloseTo 0.6f
        counterClockwise.displayWidth shouldBeCloseTo 0.4f
        counterClockwise.displayHeight shouldBeCloseTo 0.3f
    }

    @Test
    fun `overlay display transform reflows both seam orders and splits seam crossing boxes`() {
        val source = overlaySourceBox(
            x = 0.4f,
            y = 0.2f,
            width = 0.3f,
            height = 0.2f,
            translatedText = "Across seam",
        )

        val leftTop = TranslationOverlayDisplayTransformer.transform(
            boxes = listOf(source),
            transform = TranslationOverlayDisplayTransform.reflowed(topSourceHalf = TranslationOverlaySourceHalf.Left),
        )
        val rightTop = TranslationOverlayDisplayTransformer.transform(
            boxes = listOf(source),
            transform = TranslationOverlayDisplayTransform.reflowed(topSourceHalf = TranslationOverlaySourceHalf.Right),
        )

        leftTop.map { it.displayX } shouldContainExactlyCloseTo listOf(0.8f, 0f)
        leftTop.map { it.displayY } shouldContainExactlyCloseTo listOf(0.1f, 0.6f)
        leftTop.map { it.displayWidth } shouldContainExactlyCloseTo listOf(0.2f, 0.4f)
        leftTop.map { it.displayHeight } shouldContainExactlyCloseTo listOf(0.1f, 0.1f)
        leftTop.map { it.drawTranslatedText } shouldContainExactly listOf(false, true)

        rightTop.map { it.displayX } shouldContainExactlyCloseTo listOf(0.8f, 0f)
        rightTop.map { it.displayY } shouldContainExactlyCloseTo listOf(0.6f, 0.1f)
        rightTop.map { it.displayWidth } shouldContainExactlyCloseTo listOf(0.2f, 0.4f)
        rightTop.map { it.displayHeight } shouldContainExactlyCloseTo listOf(0.1f, 0.1f)
        rightTop.map { it.drawTranslatedText } shouldContainExactly listOf(false, true)
    }

    @Test
    fun `overlay display transform clamps rotated fragments into display bounds`() {
        val source = overlaySourceBox(
            x = 0.9f,
            y = 0.8f,
            width = 0.3f,
            height = 0.4f,
        )

        val display = TranslationOverlayDisplayTransformer.transform(
            boxes = listOf(source),
            transform = TranslationOverlayDisplayTransform.Clockwise90,
        ).single()

        display.displayX shouldBeCloseTo 0f
        display.displayY shouldBeCloseTo 0.9f
        display.displayWidth shouldBeCloseTo 0.2f
        display.displayHeight shouldBeCloseTo 0.1f
    }

    @Test
    fun `identity transform clamps partial boxes and drops fully off page boxes`() {
        val partial = TranslationOverlayDisplayTransformer.transform(
            boxes = listOf(overlaySourceBox(x = -0.1f, y = 0.9f, width = 0.3f, height = 0.3f)),
            transform = TranslationOverlayDisplayTransform.Identity,
        ).single()
        val offPage = TranslationOverlayDisplayTransformer.transform(
            boxes = listOf(overlaySourceBox(x = 1.1f, y = 0.2f, width = 0.2f, height = 0.2f)),
            transform = TranslationOverlayDisplayTransform.Identity,
        )

        partial.displayX shouldBeCloseTo 0f
        partial.displayY shouldBeCloseTo 0.9f
        partial.displayWidth shouldBeCloseTo 0.2f
        partial.displayHeight shouldBeCloseTo 0.1f
        offPage shouldBe emptyList()
    }

    @Test
    fun `webtoon display transform follows wide page rotate and reflow processing`() {
        TranslationOverlayDisplayTransformResolver.forWebtoon(
            isWideImage = false,
            rotateToFit = true,
            rotateToFitInverted = false,
            splitDoublePage = true,
            splitDoublePageInverted = false,
        ) shouldBe TranslationOverlayDisplayTransform.Identity

        TranslationOverlayDisplayTransformResolver.forWebtoon(
            isWideImage = true,
            rotateToFit = true,
            rotateToFitInverted = false,
            splitDoublePage = true,
            splitDoublePageInverted = true,
        ) shouldBe TranslationOverlayDisplayTransform.Clockwise90

        TranslationOverlayDisplayTransformResolver.forWebtoon(
            isWideImage = true,
            rotateToFit = true,
            rotateToFitInverted = true,
            splitDoublePage = false,
            splitDoublePageInverted = false,
        ) shouldBe TranslationOverlayDisplayTransform.CounterClockwise90

        TranslationOverlayDisplayTransformResolver.forWebtoon(
            isWideImage = true,
            rotateToFit = false,
            rotateToFitInverted = false,
            splitDoublePage = true,
            splitDoublePageInverted = false,
        ) shouldBe TranslationOverlayDisplayTransform.reflowed(TranslationOverlaySourceHalf.Right)

        TranslationOverlayDisplayTransformResolver.forWebtoon(
            isWideImage = true,
            rotateToFit = false,
            rotateToFitInverted = false,
            splitDoublePage = true,
            splitDoublePageInverted = true,
        ) shouldBe TranslationOverlayDisplayTransform.reflowed(TranslationOverlaySourceHalf.Left)
    }

    @Test
    fun `overlay rect mapper maps normalized boxes into rendered page bounds`() {
        val mapped = TranslationOverlayRectMapper.map(
            x = 0.1f,
            y = 0.2f,
            width = 0.3f,
            height = 0.4f,
            sourceWidth = 1000,
            sourceHeight = 2000,
            imageLeft = 10f,
            imageTop = 20f,
            imageWidth = 1000f,
            imageHeight = 2000f,
            viewWidth = 1080,
            viewHeight = 2200,
        )
        val offPage = TranslationOverlayRectMapper.map(
            x = 1.1f,
            y = 0.2f,
            width = 0.3f,
            height = 0.4f,
            sourceWidth = 1000,
            sourceHeight = 2000,
            imageLeft = 10f,
            imageTop = 20f,
            imageWidth = 1000f,
            imageHeight = 2000f,
            viewWidth = 1080,
            viewHeight = 2200,
        )

        mapped.skipReason shouldBe null
        mapped.left shouldBe 110f
        mapped.top shouldBe 420f
        mapped.right shouldBe 410f
        mapped.bottom shouldBe 1220f
        offPage.skipReason shouldBe "mapped_rect_off_page"
    }

    @Test
    fun `overlay rect mapper handles portrait and landscape rendered strips with offsets`() {
        val portrait = TranslationOverlayRectMapper.map(
            x = 0.2f,
            y = 0.25f,
            width = 0.1f,
            height = 0.15f,
            sourceWidth = 1000,
            sourceHeight = 2000,
            imageLeft = 0f,
            imageTop = 120f,
            imageWidth = 1080f,
            imageHeight = 2160f,
            viewWidth = 1080,
            viewHeight = 2400,
        )
        val landscape = TranslationOverlayRectMapper.map(
            x = 0.2f,
            y = 0.25f,
            width = 0.1f,
            height = 0.15f,
            sourceWidth = 1000,
            sourceHeight = 2000,
            imageLeft = 420f,
            imageTop = 0f,
            imageWidth = 420f,
            imageHeight = 840f,
            viewWidth = 2400,
            viewHeight = 1080,
        )

        portrait.skipReason shouldBe null
        portrait.left shouldBe 216f
        portrait.top shouldBe 660f
        portrait.right shouldBe 324f
        portrait.bottom shouldBe 984f
        landscape.skipReason shouldBe null
        landscape.left shouldBe 504f
        landscape.top shouldBe 210f
        landscape.right shouldBe 546f
        landscape.bottom shouldBe 336f
    }

    @Test
    fun `overlay rect mapper follows zoomed and panned image bounds`() {
        val mapped = TranslationOverlayRectMapper.map(
            x = 0.2f,
            y = 0.25f,
            width = 0.1f,
            height = 0.15f,
            sourceWidth = 1000,
            sourceHeight = 2000,
            imageLeft = -100f,
            imageTop = -250f,
            imageWidth = 1500f,
            imageHeight = 3000f,
            viewWidth = 1080,
            viewHeight = 2200,
        )

        mapped.skipReason shouldBe null
        mapped.left shouldBeCloseTo 200f
        mapped.top shouldBeCloseTo 500f
        mapped.right shouldBeCloseTo 350f
        mapped.bottom shouldBeCloseTo 950f
    }

    @Test
    fun `overlay render cache key invalidates on readiness resize rotation pan zoom and box changes`() {
        val boxes = listOf(
            overlayDisplayBox(
                displayX = 0.1f,
                displayY = 0.2f,
                displayWidth = 0.3f,
                displayHeight = 0.4f,
            ),
        )
        val base = TranslationOverlayRenderCacheKey.create(
            displayBoxes = boxes,
            displayTransform = TranslationOverlayDisplayTransform.Identity,
            pageViewReady = false,
            sourceWidth = 1000,
            sourceHeight = 2000,
            imageLeft = 10f,
            imageTop = 20f,
            imageWidth = 1000f,
            imageHeight = 2000f,
            viewWidth = 1080,
            viewHeight = 2200,
        )

        base shouldBe TranslationOverlayRenderCacheKey.create(
            displayBoxes = boxes,
            displayTransform = TranslationOverlayDisplayTransform.Identity,
            pageViewReady = false,
            sourceWidth = 1000,
            sourceHeight = 2000,
            imageLeft = 10f,
            imageTop = 20f,
            imageWidth = 1000f,
            imageHeight = 2000f,
            viewWidth = 1080,
            viewHeight = 2200,
        )
        TranslationOverlayRenderCacheKey.create(
            displayBoxes = boxes,
            displayTransform = TranslationOverlayDisplayTransform.Identity,
            pageViewReady = true,
            sourceWidth = 1000,
            sourceHeight = 2000,
            imageLeft = 10f,
            imageTop = 20f,
            imageWidth = 1000f,
            imageHeight = 2000f,
            viewWidth = 1080,
            viewHeight = 2200,
        ) shouldBe base.copy(pageViewReady = true)
        TranslationOverlayRenderCacheKey.create(
            displayBoxes = boxes,
            displayTransform = TranslationOverlayDisplayTransform.Identity,
            pageViewReady = false,
            sourceWidth = 1000,
            sourceHeight = 2000,
            imageLeft = 10f,
            imageTop = 20f,
            imageWidth = 1000f,
            imageHeight = 2000f,
            viewWidth = 2200,
            viewHeight = 1080,
        ) shouldBe base.copy(viewWidth = 2200, viewHeight = 1080)

        TranslationOverlayRenderCacheKey.create(
            displayBoxes = boxes,
            displayTransform = TranslationOverlayDisplayTransform.Clockwise90,
            pageViewReady = false,
            sourceWidth = 1000,
            sourceHeight = 2000,
            imageLeft = 10f,
            imageTop = 20f,
            imageWidth = 1000f,
            imageHeight = 2000f,
            viewWidth = 1080,
            viewHeight = 2200,
        ) shouldBe base.copy(displayTransform = TranslationOverlayDisplayTransform.Clockwise90)
        TranslationOverlayRenderCacheKey.create(
            displayBoxes = boxes,
            displayTransform = TranslationOverlayDisplayTransform.Identity,
            pageViewReady = false,
            sourceWidth = 1000,
            sourceHeight = 2000,
            imageLeft = 30f,
            imageTop = 60f,
            imageWidth = 1500f,
            imageHeight = 3000f,
            viewWidth = 1080,
            viewHeight = 2200,
        ) shouldBe base.copy(
            imageLeftBits = 30f.toBits(),
            imageTopBits = 60f.toBits(),
            imageWidthBits = 1500f.toBits(),
            imageHeightBits = 3000f.toBits(),
        )
        TranslationOverlayRenderCacheKey.create(
            displayBoxes = boxes + overlayDisplayBox(sourceBoxId = 2),
            displayTransform = TranslationOverlayDisplayTransform.Identity,
            pageViewReady = false,
            sourceWidth = 1000,
            sourceHeight = 2000,
            imageLeft = 10f,
            imageTop = 20f,
            imageWidth = 1000f,
            imageHeight = 2000f,
            viewWidth = 1080,
            viewHeight = 2200,
        ).displayBoxesHash shouldBe (boxes + overlayDisplayBox(sourceBoxId = 2)).hashCode()
    }

    @Test
    fun `overlay render cache key distinguishes boxes whose hashes collide`() {
        val first = TranslationOverlayRenderCacheKey.create(
            displayBoxes = listOf(overlayDisplayBox(sourceBoxId = 1)),
            displayTransform = TranslationOverlayDisplayTransform.Identity,
            pageViewReady = true,
            sourceWidth = 1000,
            sourceHeight = 2000,
            imageLeft = 0f,
            imageTop = 0f,
            imageWidth = 1000f,
            imageHeight = 2000f,
            viewWidth = 1000,
            viewHeight = 2000,
        )
        val collidingId = 1L shl Int.SIZE_BITS
        val second = TranslationOverlayRenderCacheKey.create(
            displayBoxes = listOf(overlayDisplayBox(sourceBoxId = collidingId)),
            displayTransform = TranslationOverlayDisplayTransform.Identity,
            pageViewReady = true,
            sourceWidth = 1000,
            sourceHeight = 2000,
            imageLeft = 0f,
            imageTop = 0f,
            imageWidth = 1000f,
            imageHeight = 2000f,
            viewWidth = 1000,
            viewHeight = 2000,
        )

        first.displayBoxesHash shouldBe second.displayBoxesHash
        first shouldNotBe second
    }

    @Test
    fun `translation settings metadata includes descriptions defaults and bounded fields`() {
        TranslationSettingsMetadata.all.size shouldBe TranslationSettingsMetadata.all.map { it.key }.toSet().size

        TranslationSettingsMetadata.all.forEach { entry ->
            val subtitle = entry.subtitle()
            subtitle shouldContain "Description = "
            subtitle shouldContain "Default = "
            if (entry.numeric) {
                subtitle shouldContain "Min = "
                subtitle shouldContain "Max = "
                subtitle shouldNotContain "Options = "
                subtitle shouldNotContain "Format = "
            } else {
                val hasOptions = subtitle.contains("Options = ")
                val hasFormat = subtitle.contains("Format = ")
                (hasOptions || hasFormat) shouldBe true
            }
        }

        TranslationSettingsMetadata.overlayTextSizeSp.subtitle(currentValue = "18 sp") shouldContain "Current = 18 sp"
        TranslationSettingsMetadata.sourceLanguage.subtitle(currentValue = "Auto") shouldContain "Options = Auto, Japanese, Korean, Chinese"
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
    fun `reader missing overlay diagnostics are deduped by page and refresh source`() {
        TranslationReaderOverlayMissingLogDeduper.resetForTest()

        TranslationReaderOverlayMissingLogDeduper.shouldLogMissing(
            chapterId = 1,
            pageIndex = 10,
            targetLanguage = "English",
            refreshSource = "pager_visible_page",
        ) shouldBe true
        TranslationReaderOverlayMissingLogDeduper.shouldLogMissing(
            chapterId = 1,
            pageIndex = 10,
            targetLanguage = "English",
            refreshSource = "pager_visible_page",
        ) shouldBe false
        TranslationReaderOverlayMissingLogDeduper.shouldLogMissing(
            chapterId = 1,
            pageIndex = 10,
            targetLanguage = "English",
            refreshSource = "webtoon_visible_page",
        ) shouldBe true
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
    fun `queue group exposes status counts for sticky headers`() {
        val group = TranslationQueueUiModel.filterAndGroup(
            items = listOf(
                queueItem(id = 1, status = TranslationJobStatus.Queued.value),
                queueItem(id = 2, status = TranslationJobStatus.Running.value),
                queueItem(id = 3, status = TranslationJobStatus.PausedAuth.value),
                queueItem(id = 4, status = TranslationJobStatus.PausedQuota.value),
            ),
            filters = emptySet(),
        ).single()

        group.statusCounts shouldBe mapOf(
            TranslationJobStatus.Queued.value to 1,
            TranslationJobStatus.Running.value to 1,
            TranslationJobStatus.PausedAuth.value to 1,
            TranslationJobStatus.PausedQuota.value to 1,
        )
    }

    @Test
    fun `queue derived ui state precomputes counts groups and job scoped logs`() {
        val state = TranslationQueueUiModel.derive(
            items = listOf(
                queueItem(id = 1, status = TranslationJobStatus.Queued.value),
                queueItem(id = 2, status = TranslationJobStatus.Completed.value),
            ),
            logs = listOf(
                logItem(id = 3, jobId = 1, message = "Retry blocked", details = "same"),
                logItem(id = 2, jobId = 1, message = "Retry blocked", details = "same"),
                logItem(id = 1, jobId = 2, message = "Queued", details = "other"),
            ),
            filters = emptySet(),
        )

        state.activeJobCount shouldBe 1
        state.queueGroups.single().items.map { it.id } shouldContainExactly listOf(1L, 2L)
        state.groupedLogsByJob[1]?.single()?.count shouldBe 2
        state.groupedLogsByJob[2]?.single()?.count shouldBe 1
    }

    @Test
    fun `queue type filters are multi select and empty means all`() {
        val items = listOf(
            queueItem(id = 1, status = TranslationJobStatus.Queued.value),
            queueItem(id = 2, status = TranslationJobStatus.Retrying.value),
            queueItem(id = 3, status = TranslationJobStatus.Running.value),
            queueItem(id = 4, status = TranslationJobStatus.PausedAuth.value),
            queueItem(id = 5, status = TranslationJobStatus.Completed.value),
            queueItem(id = 6, status = TranslationJobStatus.ManualRetry.value),
        )

        TranslationQueueUiModel.filterAndGroup(items, filters = emptySet())
            .flatMap { it.items }
            .map { it.id } shouldContainExactly listOf(1L, 2L, 3L, 4L, 5L, 6L)

        TranslationQueueUiModel.filterAndGroup(
            items,
            filters = setOf(TranslationQueueTypeFilter.Waiting, TranslationQueueTypeFilter.Paused),
        )
            .flatMap { it.items }
            .map { it.id } shouldContainExactly listOf(1L, 2L, 4L, 6L)
    }

    @Test
    fun `overlay edit planner deletes saved page only when user saves zero boxes`() {
        TranslationOverlayEditPlanner.actionFor(boxCount = 0) shouldBe TranslationOverlayEditAction.DeletePage
        TranslationOverlayEditPlanner.actionFor(boxCount = 1) shouldBe TranslationOverlayEditAction.ReplaceBoxes
    }

    @Test
    fun `overlay save verification treats editor delete as verified row absence`() {
        val verified = TranslationOverlaySaveVerificationPolicy.verifyDelete(readBackPageExists = false)
        val failed = TranslationOverlaySaveVerificationPolicy.verifyDelete(readBackPageExists = true)

        verified.success shouldBe true
        verified.expectedState shouldBe "absent"
        verified.readBackState shouldBe "absent"
        failed.success shouldBe false
        failed.failureReason shouldContain "still_exists"
    }

    @Test
    fun `overlay save verification preserves generated zero box rows but detects replace mismatch`() {
        val generatedEmpty = TranslationOverlaySaveVerificationPolicy.verifyReplace(
            expectedBoxCount = 0,
            readBackPageExists = true,
            readBackBoxCount = 0,
        )
        val mismatch = TranslationOverlaySaveVerificationPolicy.verifyReplace(
            expectedBoxCount = 2,
            readBackPageExists = true,
            readBackBoxCount = 1,
        )

        generatedEmpty.success shouldBe true
        generatedEmpty.expectedState shouldBe "present"
        generatedEmpty.readBackState shouldBe "present:0"
        mismatch.success shouldBe false
        mismatch.failureReason shouldContain "box_count_mismatch"
    }

    @Test
    fun `failed overlay save verification requires removing unverified saved page before retry`() {
        val success = TranslationOverlaySaveVerificationPolicy.verifyReplace(
            expectedBoxCount = 1,
            readBackPageExists = true,
            readBackBoxCount = 1,
        )
        val mismatch = TranslationOverlaySaveVerificationPolicy.verifyReplace(
            expectedBoxCount = 2,
            readBackPageExists = true,
            readBackBoxCount = 1,
        )
        val missing = TranslationOverlaySaveVerificationPolicy.verifyReplace(
            expectedBoxCount = 1,
            readBackPageExists = false,
            readBackBoxCount = null,
        )

        TranslationOverlaySaveVerificationPolicy.shouldRemoveUnverifiedSavedPage(success) shouldBe false
        TranslationOverlaySaveVerificationPolicy.shouldRemoveUnverifiedSavedPage(mismatch) shouldBe true
        TranslationOverlaySaveVerificationPolicy.shouldRemoveUnverifiedSavedPage(missing) shouldBe true
    }

    @Test
    fun `saved overlay skip policy ignores missing deleted rows and overwrite retry`() {
        TranslationSavedOverlayPolicy.shouldSkipExistingOverlay(
            hasSavedPageRow = true,
            overwrite = false,
        ) shouldBe true
        TranslationSavedOverlayPolicy.shouldSkipExistingOverlay(
            hasSavedPageRow = false,
            overwrite = false,
        ) shouldBe false
        TranslationSavedOverlayPolicy.shouldSkipExistingOverlay(
            hasSavedPageRow = true,
            overwrite = true,
        ) shouldBe false
    }

    @Test
    fun `reader overlay load policy gives identical clear and show decisions for pager and webtoon`() {
        val pagerMissing = TranslationReaderOverlayLoadPolicy.decision(
            overlayVisible = true,
            chapterId = 10,
            pageIndex = 4,
            targetLanguage = "English",
            refreshSource = "pager_visible_page",
            savedPageExists = false,
            savedBoxCount = 0,
        )
        val webtoonMissing = TranslationReaderOverlayLoadPolicy.decision(
            overlayVisible = true,
            chapterId = 10,
            pageIndex = 4,
            targetLanguage = "English",
            refreshSource = "webtoon_visible_page",
            savedPageExists = false,
            savedBoxCount = 0,
        )
        val generatedEmpty = TranslationReaderOverlayLoadPolicy.decision(
            overlayVisible = true,
            chapterId = 10,
            pageIndex = 5,
            targetLanguage = "English",
            refreshSource = "pager_visible_page",
            savedPageExists = true,
            savedBoxCount = 0,
        )

        pagerMissing.action shouldBe TranslationReaderOverlayLoadAction.Clear
        pagerMissing.clearReason shouldBe "no_saved_overlay"
        pagerMissing.shouldLogMissing shouldBe true
        webtoonMissing.action shouldBe pagerMissing.action
        webtoonMissing.clearReason shouldBe pagerMissing.clearReason
        generatedEmpty.action shouldBe TranslationReaderOverlayLoadAction.Show
        generatedEmpty.clearReason shouldBe null
        generatedEmpty.shouldLogMissing shouldBe false
    }

    @Test
    fun `overlay render skip policy reports user safe reasons`() {
        TranslationOverlayRenderSkipPolicy.reason(
            hasPageView = false,
            pageViewReady = false,
            sourceWidth = 0,
            sourceHeight = 0,
            rectWidth = 0f,
            rectHeight = 0f,
        ) shouldBe "missing_page_view"
        TranslationOverlayRenderSkipPolicy.reason(
            hasPageView = true,
            pageViewReady = false,
            sourceWidth = 844,
            sourceHeight = 1200,
            rectWidth = 0f,
            rectHeight = 0f,
        ) shouldBe "page_view_not_ready"
        TranslationOverlayRenderSkipPolicy.reason(
            hasPageView = true,
            pageViewReady = true,
            sourceWidth = 844,
            sourceHeight = 1200,
            rectWidth = 0.5f,
            rectHeight = 4f,
        ) shouldBe "rect_too_small"
        TranslationOverlayRenderSkipPolicy.reason(
            hasPageView = true,
            pageViewReady = true,
            sourceWidth = 844,
            sourceHeight = 1200,
            rectWidth = 12f,
            rectHeight = 12f,
        ) shouldBe null
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

    @Test
    fun `log details preview is bounded but preserves full details`() {
        val details = "a".repeat(700)
        val item = logItem(id = 1, message = "Verbose log", details = details)

        item.details shouldBe details
        item.detailsPreview?.length shouldBe 604
        item.detailsPreview?.endsWith("\n...") shouldBe true
    }

    @Test
    fun `claim tokens are hidden from user facing queue errors`() {
        TranslationClaimToken.publicErrorMessage("normal:0:123:0:0") shouldBe null
        TranslationClaimToken.publicErrorMessage("manual_retry:1:123:0:0") shouldBe null
        TranslationClaimToken.publicErrorMessage("quota exceeded") shouldBe "quota exceeded"
        TranslationClaimToken.laneId("normal:7:123:0:0") shouldBe 7
        TranslationClaimToken.laneId("quota exceeded") shouldBe null
    }

    @Test
    fun `claim tokens are summarized in logs without exposing raw token`() {
        val fields = TranslationClaimToken.publicLogFields("manual_retry:2:123456:4:5")

        fields shouldContain ("claim_worker_kind" to "manual_retry")
        fields shouldContain ("claim_lane_id" to 2)
        fields shouldContain ("claim_chunk_index" to 4)
        fields shouldContain ("claim_group_index" to 5)
        fields.values.joinToString() shouldNotContain "123456"
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
        jobId: Long? = 1,
        createdAt: Long = id,
        message: String,
        details: String?,
    ): TranslationLogUiItem {
        return TranslationLogUiItem(
            id = id,
            jobId = jobId,
            pageId = null,
            createdAt = createdAt,
            level = TranslationLogLevel.Warning.value,
            tag = "queue",
            message = message,
            details = details,
        )
    }

    private fun translationJob(
        id: Long,
        chapterId: Long,
        pageIndex: Long?,
        status: String = TranslationJobStatus.Queued.value,
        updatedAt: Long = id,
        errorMessage: String? = null,
    ): Translation_jobs {
        return Translation_jobs(
            _id = id,
            manga_id = 1,
            chapter_id = chapterId,
            page_index = pageIndex,
            scope = TranslationScope.Image.value,
            pipeline = "gemini_vision",
            mode = TranslationMode.Overlay.value,
            model = "gemini-3-flash-preview",
            target_language = "English",
            source_language = null,
            overwrite = false,
            status = status,
            progress_current = 0,
            progress_total = 1,
            attempts = 0,
            created_at = id,
            updated_at = updatedAt,
            error_message = errorMessage,
        )
    }

    private fun overlaySourceBox(
        sourceBoxId: Long = 1,
        sourcePageId: Long = 10,
        x: Float = 0.1f,
        y: Float = 0.2f,
        width: Float = 0.3f,
        height: Float = 0.4f,
        translatedText: String = "Translated",
    ): TranslationOverlaySourceBox {
        return TranslationOverlaySourceBox(
            sourceBoxId = sourceBoxId,
            sourcePageId = sourcePageId,
            x = x,
            y = y,
            width = width,
            height = height,
            originalText = "Original",
            translatedText = translatedText,
            textType = "dialogue",
            confidence = 0.99,
            styleJson = null,
        )
    }

    private fun translationBox(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
    ): Translation_boxes {
        return Translation_boxes(
            _id = 1,
            page_id = 10,
            x = x,
            y = y,
            width = width,
            height = height,
            original_text = "Original",
            translated_text = "Translated",
            text_type = "dialogue",
            confidence = 0.99,
            style_json = null,
            sort_order = 0,
        )
    }

    private fun overlayDisplayBox(
        sourceBoxId: Long = 1,
        sourcePageId: Long = 10,
        displayX: Float = 0.1f,
        displayY: Float = 0.2f,
        displayWidth: Float = 0.3f,
        displayHeight: Float = 0.4f,
    ): TranslationOverlayDisplayBox {
        return TranslationOverlayDisplayBox(
            sourceBoxId = sourceBoxId,
            sourcePageId = sourcePageId,
            sourceX = displayX,
            sourceY = displayY,
            sourceWidth = displayWidth,
            sourceHeight = displayHeight,
            displayX = displayX,
            displayY = displayY,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            originalText = "Original",
            translatedText = "Translated",
            textType = "dialogue",
            confidence = 0.99,
            styleJson = null,
        )
    }

    private infix fun Float.shouldBeCloseTo(expected: Float) {
        (abs(this - expected) <= 0.0001f) shouldBe true
    }

    private infix fun List<Float>.shouldContainExactlyCloseTo(expected: List<Float>) {
        size shouldBe expected.size
        zip(expected).forEach { (actual, expectedValue) ->
            actual shouldBeCloseTo expectedValue
        }
    }
}
