package eu.kanade.tachiyomi.data.translation

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.math.min
import java.io.IOException
import java.util.LinkedHashMap
import java.util.Locale
import tachiyomi.data.Translation_jobs
import tachiyomi.domain.translation.service.DEFAULT_TRANSLATION_SYSTEM_PROMPT
import tachiyomi.domain.translation.service.TranslationPreferences

const val DEFAULT_GEMINI_TRANSLATION_MODEL = "gemini-3-flash-preview"
const val DEFAULT_GEMINI_MAX_OUTPUT_TOKENS = 65_536
const val DEFAULT_TRANSLATION_MAX_IMAGES_PER_BATCH = 38
const val TRANSLATION_BATCH_ALL = 0

object TranslationLanguages {
    const val SOURCE_AUTO = "auto"
    const val SOURCE_JAPANESE = "ja"
    const val SOURCE_KOREAN = "ko"
    const val SOURCE_CHINESE = "zh"

    fun defaultTargetLanguage(): String {
        return Locale.getDefault().displayLanguage.ifBlank { "English" }
    }

    fun sourcePromptLabel(value: String?): String? {
        return when (value?.trim().orEmpty().lowercase(Locale.ROOT)) {
            "", SOURCE_AUTO -> null
            SOURCE_JAPANESE -> "Japanese"
            SOURCE_KOREAN -> "Korean"
            SOURCE_CHINESE -> "Chinese"
            else -> value?.trim()
        }
    }

    fun sourceDisplayLabel(value: String?): String {
        return sourcePromptLabel(value) ?: "Auto"
    }
}

fun TranslationPreferences.resolvedTargetLanguage(): String {
    return targetLanguage.get().ifBlank { TranslationLanguages.defaultTargetLanguage() }
}

fun TranslationPreferences.resolvedSourceLanguageOrNull(): String? {
    return TranslationLanguages.sourcePromptLabel(sourceLanguage.get())
}

fun TranslationPreferences.normalizedMaxImagesPerBatch(): Int {
    val value = maxImagesPerBatch.get()
    return if (value == TRANSLATION_BATCH_ALL) TRANSLATION_BATCH_ALL else value.coerceAtLeast(1)
}

fun TranslationPreferences.normalizedParallelRetryLanes(pendingManualRetryJobs: Int): Int {
    val parsed = parallelRetryLanes.get().trim().toIntOrNull()
    val configured = if (parsed != null && parsed >= 0) parsed else 1
    return TranslationRetryLanePlanner.workerCount(
        pendingManualRetryJobs = pendingManualRetryJobs,
        configuredLanes = configured,
    )
}

object TranslationRetryLanePlanner {
    fun workerCount(pendingManualRetryJobs: Int, configuredLanes: Int): Int {
        val pending = pendingManualRetryJobs.coerceAtLeast(0)
        if (pending == 0) return 0
        val lanes = configuredLanes.coerceAtLeast(0)
        return if (lanes == 0) pending else min(lanes, pending)
    }
}

@Serializable
data class GeminiModel(
    val name: String,
    val baseModelId: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val inputTokenLimit: Int? = null,
    val outputTokenLimit: Int? = null,
    val supportedGenerationMethods: List<String> = emptyList(),
    val version: String? = null,
    val thinking: Boolean? = null,
    val temperature: Float? = null,
    val maxTemperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
) {
    val id: String
        get() = name.removePrefix("models/")
}

fun List<GeminiModel>.generateContentModels(): List<GeminiModel> {
    return filter { model ->
        model.supportedGenerationMethods.any { it.equals("generateContent", ignoreCase = true) }
    }
}

object TranslationModelLimits {
    fun outputTokenLimitFor(selectedModel: String, cachedModels: List<GeminiModel>): Int? {
        return cachedModels
            .firstOrNull { it.matches(selectedModel) }
            ?.outputTokenLimit
            ?.takeIf { it > 0 }
    }

    fun maxOutputTokensFor(
        requested: Int,
        selectedModel: String,
        cachedModels: List<GeminiModel>,
    ): Int {
        val normalized = requested.coerceAtLeast(1)
        val modelLimit = outputTokenLimitFor(selectedModel, cachedModels) ?: return normalized
        return min(normalized, modelLimit)
    }

    fun encodeModels(models: List<GeminiModel>, json: Json): String {
        return json.encodeToString(models.generateContentModels())
    }

    fun decodeModels(value: String, json: Json): List<GeminiModel> {
        return runCatching {
            json.decodeFromString<List<GeminiModel>>(value)
        }.getOrDefault(emptyList())
            .generateContentModels()
    }

    private fun GeminiModel.matches(selectedModel: String): Boolean {
        val selected = selectedModel.removePrefix("models/")
        return id == selected || name == selectedModel || baseModelId == selected
    }
}

object TranslationSetupPingPolicy {
    const val MAX_OUTPUT_TOKENS = 128
    const val THINKING_LEVEL = "low"
    const val REQUEST_SUMMARY = "prompt=Reply with OK.\nconfig=temperature=0,maxOutputTokens=128,thinkingLevel=low"

    fun generationConfig(): JsonElement {
        return buildJsonObject {
            put("temperature", 0)
            put("maxOutputTokens", MAX_OUTPUT_TOKENS)
            put(
                "thinkingConfig",
                buildJsonObject {
                    put("thinkingLevel", THINKING_LEVEL)
                },
            )
        }
    }
}

object TranslationLogRedactor {
    private val apiKeyRegex = Regex("""([?&]key=)[^"&\s]+""", RegexOption.IGNORE_CASE)
    private val sensitiveHeaderJsonRegex = Regex(
        """"(x-goog-api-key|authorization)"\s*:\s*"[^"]*"""",
        RegexOption.IGNORE_CASE,
    )
    private val sensitiveHeaderTextRegex = Regex(
        """(?i)\b(x-goog-api-key|authorization)\s*[:=]\s*[^\s,]+""",
    )
    private val inlineDataRegex = Regex(
        """"(inlineData|inline_data)"\s*:\s*\{\s*"(mimeType|mime_type)"\s*:\s*"([^"]+)"\s*,\s*"data"\s*:\s*"[^"]*"\s*\}""",
    )
    private val longDataFieldRegex = Regex(
        """"data"\s*:\s*"[A-Za-z0-9+/=_-]{128,}"""",
    )

    fun redact(value: String): String {
        return value
            .replace(apiKeyRegex) { match -> match.groupValues[1] + "<redacted>" }
            .replace(sensitiveHeaderJsonRegex) { match ->
                """"${match.groupValues[1]}": "<redacted>""""
            }
            .replace(sensitiveHeaderTextRegex) { match ->
                "${match.groupValues[1]}=<redacted>"
            }
            .replace(inlineDataRegex) { match ->
                """"${match.groupValues[1]}": {"${match.groupValues[2]}": "${match.groupValues[3]}", "data": "<redacted-image>"}"""
            }
            .replace(longDataFieldRegex) {
                """"data": "<redacted-image>""""
            }
    }
}

enum class TranslationWorkStartPolicy {
    Keep,
    Replace,
}

enum class TranslationWorkKind(val value: String) {
    Normal("normal"),
    ManualRetry("manual_retry"),
    ;

    companion object {
        fun from(value: String?): TranslationWorkKind {
            return entries.firstOrNull { it.value == value } ?: Normal
        }
    }
}

object TranslationClaimToken {
    fun isLikelyClaimToken(value: String?): Boolean {
        val token = value.orEmpty()
        return TranslationWorkKind.entries.any { token.startsWith("${it.value}:") }
    }

    fun publicErrorMessage(value: String?): String? {
        return value.takeUnless(::isLikelyClaimToken)
    }

    fun publicLogFields(value: String?): Map<String, Any?> {
        val parts = value.orEmpty().split(":")
        if (parts.size < 5 || !isLikelyClaimToken(value)) {
            return emptyMap()
        }
        return mapOf(
            "claim_worker_kind" to parts.getOrNull(0),
            "claim_lane_id" to parts.getOrNull(1)?.toIntOrNull(),
            "claim_chunk_index" to parts.getOrNull(3)?.toIntOrNull(),
            "claim_group_index" to parts.getOrNull(4)?.toIntOrNull(),
        )
    }
}

object TranslationLogDetailsFormatter {
    fun queueState(
        action: String,
        jobId: Long?,
        previousStatus: String?,
        nextStatus: String?,
        reason: String? = null,
        extra: Map<String, Any?> = emptyMap(),
    ): String {
        return buildString {
            appendLine("action=$action")
            appendLine("job_id=${jobId ?: "-"}")
            appendLine("previous_status=${previousStatus ?: "-"}")
            appendLine("next_status=${nextStatus ?: "-"}")
            reason?.takeIf { it.isNotBlank() }?.let { appendLine("reason=$it") }
            extra.forEach { (key, value) ->
                appendLine("$key=${value ?: "-"}")
            }
        }.trimEnd()
    }

    fun apiCall(
        operation: String,
        method: String,
        endpoint: String,
        model: String?,
        statusCode: Int?,
        elapsedMs: Long,
        requestSummary: String,
        responseSummary: String? = null,
        errorBody: String? = null,
        rawRequestJson: String? = null,
        rawResponseJson: String? = null,
    ): String {
        return TranslationLogRedactor.redact(
            buildString {
                appendLine("operation=$operation")
                appendLine("method=$method")
                appendLine("endpoint=$endpoint")
                appendLine("model=${model ?: "-"}")
                appendLine("status_code=${statusCode ?: "-"}")
                appendLine("elapsed_ms=$elapsedMs")
                appendLine("request:")
                appendLine(requestSummary)
                responseSummary?.takeIf { it.isNotBlank() }?.let {
                    appendLine("response:")
                    appendLine(it)
                }
                errorBody?.takeIf { it.isNotBlank() }?.let {
                    appendLine("error_body:")
                    appendLine(it)
                }
                rawRequestJson?.takeIf { it.isNotBlank() }?.let {
                    appendLine("raw_request_json:")
                    appendLine(it)
                }
                rawResponseJson?.takeIf { it.isNotBlank() }?.let {
                    appendLine("raw_response_json:")
                    appendLine(it)
                }
            }.trimEnd(),
        )
    }
}

data class TranslationGenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxOutputTokens: Int? = null,
    val thinkingLevel: String? = null,
    val rawJsonOverride: String = "",
) {
    fun toGeminiJson(json: Json): JsonElement {
        val base = buildJsonObject {
            temperature?.let { put("temperature", it) }
            topP?.let { put("topP", it) }
            topK?.let { put("topK", it) }
            maxOutputTokens?.let { put("maxOutputTokens", it) }
            thinkingLevel?.takeIf { it.isNotBlank() }?.let {
                put(
                    "thinkingConfig",
                    buildJsonObject {
                        put("thinkingLevel", it)
                    },
                )
            }
        }
        val override = rawJsonOverride.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { json.parseToJsonElement(it).jsonObject }
            ?: JsonObject(emptyMap())
        return JsonObject(base + override)
    }
}

enum class TranslationThinkingLevel(val value: String) {
    High("high"),
    Medium("medium"),
    Low("low"),
}

data class TranslationPageCandidate(
    val chapterId: Long,
    val pageIndex: Int,
    val hasOverlay: Boolean,
    val hasActiveJob: Boolean = false,
)

data class TranslationBoxEdit(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val originalText: String,
    val translatedText: String,
    val textType: String,
    val confidence: Double? = null,
    val styleJson: String? = null,
)

data class TranslationBoxGeometry(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

object TranslationBoxGeometryNormalizer {
    const val MIN_BOX_SIZE = 0.01f

    fun normalize(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ): TranslationBoxGeometry {
        val safeX = x.finiteOr(0f).coerceIn(0f, 1f - MIN_BOX_SIZE)
        val safeY = y.finiteOr(0f).coerceIn(0f, 1f - MIN_BOX_SIZE)
        val safeWidth = width.finiteOr(MIN_BOX_SIZE)
            .coerceIn(MIN_BOX_SIZE, (1f - safeX).coerceAtLeast(MIN_BOX_SIZE))
        val safeHeight = height.finiteOr(MIN_BOX_SIZE)
            .coerceIn(MIN_BOX_SIZE, (1f - safeY).coerceAtLeast(MIN_BOX_SIZE))
        return TranslationBoxGeometry(
            x = safeX,
            y = safeY,
            width = safeWidth,
            height = safeHeight,
        )
    }

    fun safeSliderRange(
        start: Float,
        endInclusive: Float,
    ): ClosedFloatingPointRange<Float> {
        val safeStart = start.finiteOr(0f).coerceIn(0f, 1f)
        val safeEnd = endInclusive.finiteOr(1f).coerceIn(0f, 1f)
        return if (safeEnd > safeStart) {
            safeStart..safeEnd
        } else {
            0f..1f
        }
    }

    fun safeSliderValue(
        value: Float,
        range: ClosedFloatingPointRange<Float>,
    ): Float {
        return value.finiteOr(range.start).coerceIn(range.start, range.endInclusive)
    }

    private fun Float.finiteOr(defaultValue: Float): Float {
        return if (isFinite()) this else defaultValue
    }
}

data class TranslationOverlayCoordinateNormalizationResult(
    val overlay: TranslationOverlayResult,
    val report: TranslationOverlayCoordinateNormalizationReport,
)

data class TranslationOverlayJsonParseResult<T>(
    val value: T,
    val jsonPayload: String,
    val recovered: Boolean,
    val droppedBoxes: Int,
    val droppedPages: Int = 0,
    val remappedPages: Int = 0,
) {
    val hasWarnings: Boolean
        get() = recovered || droppedBoxes > 0 || droppedPages > 0 || remappedPages > 0
}

object TranslationGeminiTextParts {
    fun join(parts: List<String?>): String {
        return parts
            .filterNot { it.isNullOrBlank() }
            .joinToString(separator = "") { it.orEmpty() }
    }

    fun firstNonBlankCandidate(candidates: List<List<String?>>): String? {
        return candidates
            .asSequence()
            .map(::join)
            .firstOrNull { it.isNotBlank() }
    }
}

object TranslationOverlayJsonParser {
    fun parseOverlay(
        text: String,
        json: Json = Json,
    ): TranslationOverlayJsonParseResult<TranslationOverlayResult> {
        val (payload, parsed) = parseFirstUsablePayload(text, json) { element ->
            val obj = element as? JsonObject
                ?: throw IllegalArgumentException("Gemini overlay JSON must be an object")
            parseOverlayObject(obj)
        }
        return TranslationOverlayJsonParseResult(
            value = parsed.overlay,
            jsonPayload = payload,
            recovered = payload.trim() != text.trim(),
            droppedBoxes = parsed.droppedBoxes,
        )
    }

    fun parseBatch(
        text: String,
        json: Json = Json,
        expectedPageIndexes: List<Int> = emptyList(),
    ): TranslationOverlayJsonParseResult<List<TranslationBatchOverlayResult>> {
        val (payload, parsed) = parseFirstUsablePayload(text, json) { element ->
            parseBatchElement(element, expectedPageIndexes)
        }
        return TranslationOverlayJsonParseResult(
            value = parsed.pages,
            jsonPayload = payload,
            recovered = payload.trim() != text.trim(),
            droppedBoxes = parsed.droppedBoxes,
            droppedPages = parsed.droppedPages,
            remappedPages = parsed.remappedPages,
        )
    }

    private fun parseBatchElement(
        element: JsonElement,
        expectedPageIndexes: List<Int>,
    ): ParsedBatch {
        val topLevel = element as? JsonObject
        val pageElements = when (element) {
            is JsonArray -> element
            is JsonObject -> element["pages"] as? JsonArray
                ?: throw IllegalArgumentException("Gemini batch JSON must include a pages array")
            else -> throw IllegalArgumentException("Gemini batch JSON must be an object or array")
        }
        val sourceLanguage = topLevel?.stringValue("sourceLanguage")
        val targetLanguage = topLevel?.stringValue("targetLanguage")
        var droppedBoxes = 0
        var droppedPages = 0
        var remappedPages = 0
        val pages = pageElements.mapNotNull { pageElement ->
            val pageObject = pageElement as? JsonObject ?: run {
                droppedPages++
                return@mapNotNull null
            }
            val pageIndexResolution = pageObject.pageIndexValue(expectedPageIndexes)
            val pageIndex = pageIndexResolution?.pageIndex
            if (pageIndex == null) {
                droppedPages++
                return@mapNotNull null
            }
            if (pageIndexResolution.remapped) remappedPages++
            val parsed = parseOverlayObject(
                obj = pageObject,
                fallbackSourceLanguage = pageObject.stringValue("sourceLanguage") ?: sourceLanguage,
                fallbackTargetLanguage = pageObject.stringValue("targetLanguage") ?: targetLanguage,
            )
            droppedBoxes += parsed.droppedBoxes
            TranslationBatchOverlayResult(
                pageIndex = pageIndex,
                overlay = parsed.overlay,
            )
        }
        if (pages.isEmpty() && pageElements.isNotEmpty()) {
            throw IllegalArgumentException("Gemini batch JSON contained no valid page objects")
        }
        return ParsedBatch(
            pages = pages,
            droppedBoxes = droppedBoxes,
            droppedPages = droppedPages,
            remappedPages = remappedPages,
        )
    }

    private fun JsonObject.pageIndexValue(expectedPageIndexes: List<Int>): PageIndexResolution? {
        intValue("pageIndex")?.let { return PageIndexResolution(it) }
        intValue("page")?.let { return oneBasedPageAliasValue(it, expectedPageIndexes) }
        intValue("index")?.let { return zeroBasedIndexAliasValue(it, expectedPageIndexes) }
        intValue("pageNumber")?.let { return oneBasedPageAliasValue(it, expectedPageIndexes) }
        return null
    }

    private fun oneBasedPageAliasValue(value: Int, expectedPageIndexes: List<Int>): PageIndexResolution {
        if (expectedPageIndexes.isEmpty()) return PageIndexResolution(value)
        if (value in 1..expectedPageIndexes.size) {
            val localPageIndex = expectedPageIndexes[value - 1]
            if (localPageIndex != value) {
                return PageIndexResolution(
                    pageIndex = localPageIndex,
                    remapped = true,
                )
            }
        }
        if (value in expectedPageIndexes) return PageIndexResolution(value)
        val zeroBasedPageNumber = value - 1
        if (zeroBasedPageNumber in expectedPageIndexes) {
            return PageIndexResolution(
                pageIndex = zeroBasedPageNumber,
                remapped = true,
            )
        }
        return PageIndexResolution(value)
    }

    private fun zeroBasedIndexAliasValue(value: Int, expectedPageIndexes: List<Int>): PageIndexResolution {
        if (expectedPageIndexes.isEmpty()) return PageIndexResolution(value)
        if (value in expectedPageIndexes) return PageIndexResolution(value)
        if (value in expectedPageIndexes.indices) {
            return PageIndexResolution(
                pageIndex = expectedPageIndexes[value],
                remapped = true,
            )
        }
        val oneBasedPageNumber = value - 1
        if (oneBasedPageNumber in expectedPageIndexes) {
            return PageIndexResolution(
                pageIndex = oneBasedPageNumber,
                remapped = true,
            )
        }
        return PageIndexResolution(value)
    }

    private fun parseOverlayObject(
        obj: JsonObject,
        fallbackSourceLanguage: String? = null,
        fallbackTargetLanguage: String? = null,
    ): ParsedOverlay {
        var droppedBoxes = 0
        val boxes = (obj["boxes"] as? JsonArray)
            .orEmpty()
            .mapNotNull { element ->
                parseBox(element as? JsonObject).also { box ->
                    if (box == null) droppedBoxes++
                }
            }
        return ParsedOverlay(
            overlay = TranslationOverlayResult(
                sourceLanguage = obj.stringValue("sourceLanguage") ?: fallbackSourceLanguage,
                targetLanguage = obj.stringValue("targetLanguage") ?: fallbackTargetLanguage,
                boxes = boxes,
            ),
            droppedBoxes = droppedBoxes,
        )
    }

    private fun parseBox(obj: JsonObject?): TranslationOverlayBox? {
        obj ?: return null
        val x = obj.floatValue("x") ?: return null
        val y = obj.floatValue("y") ?: return null
        val width = obj.floatValue("width", "w") ?: return null
        val height = obj.floatValue("height", "h") ?: return null
        val translatedText = obj.stringValue("translatedText", "translation", "text")
            ?: return null
        return TranslationOverlayBox(
            x = x,
            y = y,
            width = width,
            height = height,
            originalText = obj.stringValue("originalText", "sourceText", "original") ?: "",
            translatedText = translatedText,
            textType = obj.stringValue("textType", "type", "kind") ?: "dialogue",
            confidence = obj.floatValue("confidence"),
        )
    }

    private fun <T> parseFirstUsablePayload(
        text: String,
        json: Json,
        parse: (JsonElement) -> T,
    ): Pair<String, T> {
        val payloads = extractJsonPayloads(text)
        var lastError: Throwable? = null
        payloads.forEach { payload ->
            try {
                return payload to parse(json.parseToJsonElement(payload))
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw IllegalArgumentException(
            "Gemini response did not contain usable overlay JSON",
            lastError,
        )
    }

    private fun extractJsonPayloads(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Gemini response did not contain JSON")
        }
        val payloads = trimmed.indices.mapNotNull { index ->
            if (trimmed[index] == '{' || trimmed[index] == '[') {
                balancedPayloadAt(trimmed, index)
            } else {
                null
            }
        }
        if (payloads.isEmpty()) {
            throw IllegalArgumentException("Gemini response did not contain JSON")
        }
        return payloads
    }

    private fun balancedPayloadAt(
        text: String,
        start: Int,
    ): String? {
        val opening = text[start]
        val closing = if (opening == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            when {
                escaped -> escaped = false
                inString && char == '\\' -> escaped = true
                char == '"' -> inString = !inString
                inString -> Unit
                char == opening -> depth++
                char == closing -> {
                    depth--
                    if (depth == 0) {
                        return text.substring(start, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun JsonObject.stringValue(vararg names: String): String? {
        return names.firstNotNullOfOrNull { name ->
            (this[name] as? JsonPrimitive)
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
        }
    }

    private fun JsonObject.floatValue(vararg names: String): Float? {
        return names.firstNotNullOfOrNull { name ->
            (this[name] as? JsonPrimitive)?.floatOrStringOrNull()
        }
    }

    private fun JsonObject.intValue(vararg names: String): Int? {
        return names.firstNotNullOfOrNull { name ->
            (this[name] as? JsonPrimitive)?.intOrStringOrNull()
        }
    }

    private fun JsonPrimitive.floatOrStringOrNull(): Float? {
        return floatOrNull ?: contentOrNull?.toFloatOrNull()
    }

    private fun JsonPrimitive.intOrStringOrNull(): Int? {
        return intOrNull ?: contentOrNull?.toIntOrNull()
    }

    private data class ParsedOverlay(
        val overlay: TranslationOverlayResult,
        val droppedBoxes: Int,
    )

    private data class ParsedBatch(
        val pages: List<TranslationBatchOverlayResult>,
        val droppedBoxes: Int,
        val droppedPages: Int,
        val remappedPages: Int,
    )

    private data class PageIndexResolution(
        val pageIndex: Int,
        val remapped: Boolean = false,
    )
}

object TranslationOverlayPersistenceGuard {
    fun normalizedGeometryOrNull(box: TranslationOverlayBox): TranslationBoxGeometry? {
        val coordinates = listOf(box.x, box.y, box.width, box.height)
        if (coordinates.any { !it.isFinite() }) return null
        if (box.x < 0f || box.y < 0f || box.x >= 1f || box.y >= 1f) return null
        if (box.width <= 0f || box.height <= 0f) return null
        if (box.width > 1f || box.height > 1f) return null
        return TranslationBoxGeometryNormalizer.normalize(
            x = box.x,
            y = box.y,
            width = box.width,
            height = box.height,
        )
    }
}

object TranslationReaderOverlayMissingLogDeduper {
    private const val MAX_KEYS = 512
    private val loggedKeys = LinkedHashMap<String, Unit>(MAX_KEYS, 0.75f, true)

    @Synchronized
    fun shouldLogMissing(
        chapterId: Long,
        pageIndex: Int,
        targetLanguage: String,
        refreshSource: String,
    ): Boolean {
        val key = "$chapterId:$pageIndex:${targetLanguage.lowercase(Locale.ROOT)}:$refreshSource"
        if (loggedKeys.containsKey(key)) return false
        loggedKeys[key] = Unit
        if (loggedKeys.size > MAX_KEYS) {
            val iterator = loggedKeys.entries.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        return true
    }

    @Synchronized
    fun resetForTest() {
        loggedKeys.clear()
    }
}

data class TranslationOverlayCoordinateNormalizationReport(
    val inputBoxes: Int,
    val outputBoxes: Int,
    val convertedPixelBoxes: Int,
    val droppedBoxes: Int,
    val clampedBoxes: Int,
    val entries: List<TranslationOverlayCoordinateNormalizationEntry>,
) {
    val hasChanges: Boolean
        get() = convertedPixelBoxes > 0 || droppedBoxes > 0 || clampedBoxes > 0
}

data class TranslationOverlayCoordinateNormalizationEntry(
    val index: Int,
    val action: String,
    val reason: String,
    val originalX: Float,
    val originalY: Float,
    val originalWidth: Float,
    val originalHeight: Float,
    val normalizedX: Float? = null,
    val normalizedY: Float? = null,
    val normalizedWidth: Float? = null,
    val normalizedHeight: Float? = null,
)

object TranslationOverlayCoordinateNormalizer {
    fun normalize(
        overlay: TranslationOverlayResult,
        imageWidth: Int?,
        imageHeight: Int?,
    ): TranslationOverlayCoordinateNormalizationResult {
        val entries = mutableListOf<TranslationOverlayCoordinateNormalizationEntry>()
        val boxes = overlay.boxes.mapIndexedNotNull { index, box ->
            normalizeBox(
                index = index,
                box = box,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                entries = entries,
            )
        }
        return TranslationOverlayCoordinateNormalizationResult(
            overlay = overlay.copy(boxes = boxes),
            report = TranslationOverlayCoordinateNormalizationReport(
                inputBoxes = overlay.boxes.size,
                outputBoxes = boxes.size,
                convertedPixelBoxes = entries.count { it.action == "converted_pixel" },
                droppedBoxes = entries.count { it.action == "dropped" },
                clampedBoxes = entries.count { it.action == "clamped" || it.reason.contains("clamped") },
                entries = entries,
            ),
        )
    }

    private fun normalizeBox(
        index: Int,
        box: TranslationOverlayBox,
        imageWidth: Int?,
        imageHeight: Int?,
        entries: MutableList<TranslationOverlayCoordinateNormalizationEntry>,
    ): TranslationOverlayBox? {
        val coordinates = listOf(box.x, box.y, box.width, box.height)
        if (coordinates.any { !it.isFinite() }) {
            entries += box.entry(index, action = "dropped", reason = "non_finite_coordinate")
            return null
        }
        if (box.width <= 0f || box.height <= 0f) {
            entries += box.entry(index, action = "dropped", reason = "non_positive_size")
            return null
        }
        if (box.x < 0f || box.y < 0f) {
            entries += box.entry(index, action = "dropped", reason = "negative_origin")
            return null
        }

        val pixelCoordinates = coordinates.any { it > 1f }
        val rawGeometry = if (pixelCoordinates) {
            val safeWidth = imageWidth?.takeIf { it > 0 }
            val safeHeight = imageHeight?.takeIf { it > 0 }
            if (safeWidth == null || safeHeight == null) {
                entries += box.entry(index, action = "dropped", reason = "pixel_coordinates_without_image_size")
                return null
            }
            if (box.x >= safeWidth || box.y >= safeHeight) {
                entries += box.entry(index, action = "dropped", reason = "pixel_origin_outside_image")
                return null
            }
            TranslationBoxGeometry(
                x = box.x / safeWidth.toFloat(),
                y = box.y / safeHeight.toFloat(),
                width = box.width / safeWidth.toFloat(),
                height = box.height / safeHeight.toFloat(),
            )
        } else {
            TranslationBoxGeometry(
                x = box.x,
                y = box.y,
                width = box.width,
                height = box.height,
            )
        }

        if (rawGeometry.width <= 0f || rawGeometry.height <= 0f) {
            entries += box.entry(index, action = "dropped", reason = "non_positive_normalized_size")
            return null
        }

        val geometry = TranslationBoxGeometryNormalizer.normalize(
            x = rawGeometry.x,
            y = rawGeometry.y,
            width = rawGeometry.width,
            height = rawGeometry.height,
        )
        val adjusted = geometry != rawGeometry
        val normalizedBox = box.copy(
            x = geometry.x,
            y = geometry.y,
            width = geometry.width,
            height = geometry.height,
        )
        when {
            pixelCoordinates -> entries += box.entry(
                index = index,
                action = "converted_pixel",
                reason = if (adjusted) "pixel_coordinates_converted_and_clamped" else "pixel_coordinates_converted",
                normalized = geometry,
            )
            adjusted -> entries += box.entry(
                index = index,
                action = "clamped",
                reason = "normalized_coordinates_clamped_to_page",
                normalized = geometry,
            )
        }
        return normalizedBox
    }

    private fun TranslationOverlayBox.entry(
        index: Int,
        action: String,
        reason: String,
        normalized: TranslationBoxGeometry? = null,
    ): TranslationOverlayCoordinateNormalizationEntry {
        return TranslationOverlayCoordinateNormalizationEntry(
            index = index,
            action = action,
            reason = reason,
            originalX = x,
            originalY = y,
            originalWidth = width,
            originalHeight = height,
            normalizedX = normalized?.x,
            normalizedY = normalized?.y,
            normalizedWidth = normalized?.width,
            normalizedHeight = normalized?.height,
        )
    }
}

@Serializable
data class TranslationOverlayBoxStyle(
    val fontFamily: String? = null,
    val textColor: String? = null,
    val fillColor: String? = null,
    val strokeColor: String? = null,
    val paddingDp: Float? = null,
    val textAlign: String? = null,
) {
    fun mergedWith(base: TranslationOverlayBoxStyle): TranslationOverlayBoxStyle {
        return TranslationOverlayBoxStyle(
            fontFamily = fontFamily ?: base.fontFamily,
            textColor = textColor ?: base.textColor,
            fillColor = fillColor ?: base.fillColor,
            strokeColor = strokeColor ?: base.strokeColor,
            paddingDp = paddingDp ?: base.paddingDp,
            textAlign = textAlign ?: base.textAlign,
        )
    }

    fun toJsonOrNull(json: Json = Json): String? {
        return takeIf {
            !it.fontFamily.isNullOrBlank() ||
                !it.textColor.isNullOrBlank() ||
                !it.fillColor.isNullOrBlank() ||
                !it.strokeColor.isNullOrBlank() ||
                it.paddingDp != null ||
                !it.textAlign.isNullOrBlank()
        }?.let { json.encodeToString(it) }
    }

    companion object {
        fun fromJson(value: String?, json: Json = Json): TranslationOverlayBoxStyle {
            return value
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { json.decodeFromString<TranslationOverlayBoxStyle>(it) }.getOrNull() }
                ?: TranslationOverlayBoxStyle()
        }

        fun fromPreferences(preferences: TranslationPreferences): TranslationOverlayBoxStyle {
            return TranslationOverlayBoxStyle(
                fontFamily = preferences.overlayFontFamily.get(),
                textColor = preferences.overlayTextColor.get(),
                fillColor = preferences.overlayBoxFillColor.get(),
                strokeColor = preferences.overlayBoxStrokeColor.get(),
                paddingDp = preferences.overlayBoxPaddingDp.get().coerceIn(0, 24).toFloat(),
                textAlign = preferences.overlayTextAlignment.get(),
            )
        }
    }
}

object TranslationEnqueuePlanner {
    fun pagesToQueue(
        pages: List<TranslationPageCandidate>,
        overwrite: Boolean,
    ): List<TranslationPageCandidate> {
        return TranslationBatchPlanner.pagesToQueue(
            pages = pages,
            overwrite = overwrite,
            maxImagesPerBatch = TRANSLATION_BATCH_ALL,
        )
    }
}

object TranslationBatchPlanner {
    fun pagesToQueue(
        pages: List<TranslationPageCandidate>,
        overwrite: Boolean,
        maxImagesPerBatch: Int,
    ): List<TranslationPageCandidate> {
        return pages.filter { page ->
            (overwrite || !page.hasOverlay) && !page.hasActiveJob
        }
    }
}

object TranslationBatchFallbackPlanner {
    fun splitIndexes(size: Int): Pair<IntRange, IntRange>? {
        if (size <= 1) return null
        val midpoint = size / 2
        return (0 until midpoint) to (midpoint until size)
    }
}

object TranslationVisionBatchPayloadPolicy {
    const val MAX_PREPARED_IMAGE_BATCH_PAGES = DEFAULT_TRANSLATION_MAX_IMAGES_PER_BATCH
    const val MAX_INLINE_IMAGE_BATCH_BYTES = 10L * 1024L * 1024L

    fun splitByPreparedPageCount(
        pageCount: Int,
        maxPages: Int = MAX_PREPARED_IMAGE_BATCH_PAGES,
    ): List<IntRange> {
        if (pageCount <= 0) return emptyList()
        val limit = maxPages.coerceAtLeast(1)
        return (0 until pageCount)
            .chunked(limit)
            .map { indexes -> indexes.first()..indexes.last() }
    }

    fun splitByPayload(
        imageByteSizes: List<Long>,
        maxBytes: Long = MAX_INLINE_IMAGE_BATCH_BYTES,
    ): List<IntRange> {
        if (imageByteSizes.isEmpty()) return emptyList()

        val safeMax = maxBytes.coerceAtLeast(1L)
        val ranges = mutableListOf<IntRange>()
        var start = 0
        var currentBytes = 0L

        imageByteSizes.forEachIndexed { index, rawSize ->
            val size = rawSize.coerceAtLeast(0L)
            if (index > start && currentBytes + size > safeMax) {
                ranges += start until index
                start = index
                currentBytes = 0L
            }
            currentBytes += size
        }
        ranges += start until imageByteSizes.size
        return ranges
    }
}

object TranslationBatchFailureClassifier {
    fun shouldUseBatchFallback(error: Throwable): Boolean {
        if (error.hasCause<CancellationException>()) return false
        if (error.hasCause<IOException>()) return false
        if (error.hasCause<GeminiApiException>()) return false
        return when (error) {
            is IllegalArgumentException -> true
            is IllegalStateException -> error.message
                ?.contains("Gemini batch response", ignoreCase = true) == true
            is SerializationException -> true
            else -> false
        }
    }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) return true
            current = current.cause
        }
        return false
    }
}

object TranslationGeminiNetworkPolicy {
    const val READ_TIMEOUT_MINUTES = 10L
    const val WRITE_TIMEOUT_MINUTES = 10L
    const val CALL_TIMEOUT_MINUTES = 10L
}

object TranslationWorkerPolicy {
    const val USE_FOREGROUND_SERVICE = false
    const val BATCH_EXECUTION_TIMEOUT_MS = 30L * 60L * 1000L
}

object TranslationRunningJobPolicy {
    const val HEARTBEAT_MS = 30_000L
    const val STALE_RUNNING_MS = HEARTBEAT_MS * 3

    fun kindForClaimToken(job: Translation_jobs): TranslationWorkKind {
        val token = job.error_message
        return when {
            token?.startsWith("${TranslationWorkKind.ManualRetry.value}:") == true -> TranslationWorkKind.ManualRetry
            else -> TranslationWorkKind.Normal
        }
    }

    fun matchesKind(job: Translation_jobs, kind: TranslationWorkKind): Boolean {
        if (job.status != TranslationJobStatus.Running.value) return false
        return when (kind) {
            TranslationWorkKind.Normal -> kindForClaimToken(job) == TranslationWorkKind.Normal
            TranslationWorkKind.ManualRetry -> kindForClaimToken(job) == TranslationWorkKind.ManualRetry
        }
    }

    fun isStale(job: Translation_jobs, now: Long): Boolean {
        return now - job.updated_at >= STALE_RUNNING_MS
    }

    fun waitMsUntilStale(job: Translation_jobs, now: Long): Long {
        val age = now - job.updated_at
        return (STALE_RUNNING_MS - age + 1_000L).coerceIn(1_000L, HEARTBEAT_MS)
    }

    fun requeueStatus(kind: TranslationWorkKind): TranslationJobStatus {
        return when (kind) {
            TranslationWorkKind.Normal -> TranslationJobStatus.Retrying
            TranslationWorkKind.ManualRetry -> TranslationJobStatus.ManualRetry
        }
    }

    fun requeueStatusForStoppedWorker(job: Translation_jobs): TranslationJobStatus {
        return requeueStatus(kindForClaimToken(job))
    }
}

data class TranslationBatchJobGroup(
    val jobs: List<Translation_jobs>,
) {
    val first: Translation_jobs
        get() = jobs.first()
}

object TranslationPendingJobBatcher {
    fun groupPendingJobs(
        jobs: List<Translation_jobs>,
        maxImagesPerBatch: Int,
    ): List<TranslationBatchJobGroup> {
        val remaining = jobs.toMutableList()
        val groups = mutableListOf<TranslationBatchJobGroup>()
        while (remaining.isNotEmpty()) {
            val first = remaining.removeAt(0)
            if (first.scope != TranslationScope.Image.value) {
                groups += TranslationBatchJobGroup(listOf(first))
                continue
            }
            val limit = if (maxImagesPerBatch == TRANSLATION_BATCH_ALL) Int.MAX_VALUE else maxImagesPerBatch.coerceAtLeast(1)
            val batch = mutableListOf(first)
            val iterator = remaining.iterator()
            while (iterator.hasNext() && batch.size < limit) {
                val candidate = iterator.next()
                if (first.isBatchCompatibleWith(candidate)) {
                    batch += candidate
                    iterator.remove()
                }
            }
            groups += TranslationBatchJobGroup(batch)
        }
        return groups
    }

    private fun Translation_jobs.isBatchCompatibleWith(other: Translation_jobs): Boolean {
        return scope == TranslationScope.Image.value &&
            other.scope == TranslationScope.Image.value &&
            manga_id == other.manga_id &&
            chapter_id == other.chapter_id &&
            pipeline == other.pipeline &&
            mode == other.mode &&
            model == other.model &&
            target_language == other.target_language &&
            source_language == other.source_language &&
            overwrite == other.overwrite
    }
}

data class TranslationBatchImageInput(
    val pageIndex: Int,
    val imageBytes: ByteArray,
    val mimeType: String,
    val width: Int?,
    val height: Int?,
) {
    override fun equals(other: Any?): Boolean {
        return other is TranslationBatchImageInput &&
            pageIndex == other.pageIndex &&
            imageBytes.contentEquals(other.imageBytes) &&
            mimeType == other.mimeType &&
            width == other.width &&
            height == other.height
    }

    override fun hashCode(): Int {
        var result = pageIndex
        result = 31 * result + imageBytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + (width ?: 0)
        result = 31 * result + (height ?: 0)
        return result
    }
}

data class TranslationBatchOcrInput(
    val pageIndex: Int,
    val blocks: List<OcrTextBlock>,
)

data class TranslationBatchOverlayResult(
    val pageIndex: Int,
    val overlay: TranslationOverlayResult,
)

object TranslationPromptPolicy {
    fun systemPrompt(userPrompt: String): String {
        val trimmedUserPrompt = userPrompt.trim()
        return buildString {
            appendLine("You are a manga, manhwa, and manhua translation assistant.")
            appendLine("Only translate relevant or critical information: speech bubbles, thought bubbles, signs, captions, narration, and author's notes.")
            appendLine("Ignore sound effects, decorative text, unrelated background text, watermark text, and punctuation-only symbols.")
            appendLine("Return no box for ignored text.")
            trimmedUserPrompt
                .takeIf { it.isNotBlank() && it != DEFAULT_TRANSLATION_SYSTEM_PROMPT }
                ?.let {
                    appendLine("Additional user system prompt:")
                    appendLine(it)
                }
        }.trimEnd()
    }

    fun pagePrompt(targetLanguage: String, sourceLanguage: String?): String {
        return buildString {
            appendLine("Translate the relevant manga text into ${targetLanguage.ifBlank { "the app language" }}.")
            appendLine("Source language: ${TranslationLanguages.sourcePromptLabel(sourceLanguage) ?: "auto-detect"}.")
            appendLine("Return only JSON matching the schema. Coordinates must be normalized 0.0 to 1.0.")
            appendLine("Do not return pixel coordinates like 811 or 38; return fractions of the original image width and height.")
            appendLine("Each box needs x, y, width, height, originalText, translatedText, textType, confidence.")
        }.trimEnd()
    }

    fun batchPagePrompt(
        pageIndexes: List<Int>,
        targetLanguage: String,
        sourceLanguage: String?,
    ): String {
        return buildString {
            appendLine("Translate these ${pageIndexes.size} manga pages into ${targetLanguage.ifBlank { "the app language" }}.")
            appendLine("Source language: ${TranslationLanguages.sourcePromptLabel(sourceLanguage) ?: "auto-detect"}.")
            appendLine("Return only JSON matching the schema: an object with a pages array.")
            appendLine("Each page object must include pageIndex and boxes.")
            appendLine("Required pageIndex values: ${pageIndexes.joinToString()}.")
            appendLine("Use only these exact pageIndex values; do not renumber pages.")
            appendLine("Do not return pageNumber, page, or local batch positions like 1, 2, 3.")
            appendLine("Coordinates must be normalized 0.0 to 1.0 for each original page.")
            appendLine("Do not return pixel coordinates like 811 or 38; return fractions of each original page width and height.")
            appendLine("Each box needs x, y, width, height, originalText, translatedText, textType, confidence.")
        }.trimEnd()
    }
}

object TranslationOverlaySanitizer {
    private val ignoredTextTypes = setOf(
        "sfx",
        "soundeffect",
        "soundeffects",
        "sound_effect",
        "sound_effects",
        "punctuation",
        "decorative",
        "unrelated",
        "irrelevant",
        "watermark",
    )

    fun sanitize(overlay: TranslationOverlayResult): TranslationOverlayResult {
        return overlay.copy(
            boxes = overlay.boxes.filter(::isRelevantBox),
        )
    }

    fun isRelevantText(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.isNotEmpty() && trimmed.any { it.isLetterOrDigit() }
    }

    private fun isRelevantBox(box: TranslationOverlayBox): Boolean {
        val normalizedType = box.textType
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() || it == '_' }
        if (normalizedType in ignoredTextTypes) return false
        return isRelevantText(box.originalText.ifBlank { box.translatedText })
    }
}

data class TranslationNotificationState(
    val title: String,
    val text: String,
    val bigText: String,
    val progressMax: Int,
    val progressCurrent: Int,
    val indeterminate: Boolean,
)

object TranslationNotificationFormatter {
    fun format(
        item: TranslationQueueItem?,
        job: Translation_jobs,
        current: Long,
        total: Long,
        status: TranslationJobStatus,
        message: String?,
        hideContent: Boolean,
    ): TranslationNotificationState {
        val safeTotal = total.takeIf { it > 0 } ?: job.progress_total.takeIf { it > 0 } ?: 0
        val safeCurrent = current.coerceAtLeast(0)
        val progressText = if (safeTotal > 0) "$safeCurrent/$safeTotal" else "preparing"
        val percentText = if (safeTotal > 0) {
            "${((safeCurrent.toDouble() / safeTotal.toDouble()) * 100).toInt().coerceIn(0, 100)}%"
        } else {
            "-"
        }
        val title = if (hideContent) {
            "Translation queue"
        } else {
            item?.mangaTitle ?: "Translation queue"
        }
        val chapter = item?.chapterName ?: job.chapter_id?.let { "Chapter $it" } ?: "Unknown chapter"
        val page = job.page_index?.let { "page ${it + 1}" } ?: "chapter"
        val text = if (hideContent) {
            "${status.value} · $progressText · $percentText"
        } else {
            "$chapter · $page · ${status.value} · $progressText · $percentText"
        }
        val bigText = buildString {
            appendLine(text)
            appendLine("progress=$progressText")
            appendLine("percent=$percentText")
            appendLine("attempt=${job.attempts}")
            appendLine("model=${job.model}")
            appendLine("pipeline=${job.pipeline}")
            appendLine("target=${job.target_language.ifBlank { "app language" }}")
            appendLine("source=${TranslationLanguages.sourceDisplayLabel(job.source_language)}")
            message?.takeIf { it.isNotBlank() }?.let {
                appendLine("message=$it")
            }
        }.trimEnd()
        return TranslationNotificationState(
            title = title,
            text = text,
            bigText = if (hideContent) text else bigText,
            progressMax = safeTotal.toInt().coerceAtLeast(0),
            progressCurrent = safeCurrent.toInt().coerceAtLeast(0),
            indeterminate = safeTotal <= 0,
        )
    }
}

data class TranslationJobSignature(
    val mangaId: Long,
    val chapterId: Long?,
    val pageIndex: Long?,
    val scope: TranslationScope,
    val pipeline: String,
    val mode: TranslationMode,
    val targetLanguage: String,
    val status: TranslationJobStatus,
)

object TranslationJobDedupe {
    fun findActiveDuplicate(
        jobs: List<TranslationJobSignature>,
        candidate: TranslationJobSignature,
    ): TranslationJobSignature? {
        return jobs.firstOrNull { job ->
            job.status.isActiveForDedupe() &&
                job.mangaId == candidate.mangaId &&
                job.chapterId == candidate.chapterId &&
                job.pageIndex == candidate.pageIndex &&
                job.scope == candidate.scope &&
                job.pipeline == candidate.pipeline &&
                job.mode == candidate.mode &&
                job.targetLanguage == candidate.targetLanguage
        }
    }
}

data class TranslationRetryDecision(
    val allowed: Boolean,
    val nextStatus: TranslationJobStatus?,
    val startPolicy: TranslationWorkStartPolicy?,
    val forceOverwrite: Boolean = false,
)

data class TranslationResumeAllResult(
    val requeued: Int,
    val skippedExisting: Int,
    val skippedRunning: Int,
)

object TranslationRetryPlanner {
    fun manualRetry(setupReady: Boolean): TranslationRetryDecision {
        return if (setupReady) {
            TranslationRetryDecision(
                allowed = true,
                nextStatus = TranslationJobStatus.ManualRetry,
                startPolicy = TranslationWorkStartPolicy.Keep,
                forceOverwrite = true,
            )
        } else {
            TranslationRetryDecision(
                allowed = false,
                nextStatus = null,
                startPolicy = null,
            )
        }
    }

    fun autoRequeueAfterSetup(
        status: TranslationJobStatus,
        setupReady: Boolean,
    ): TranslationRetryDecision {
        return if (setupReady && status.canAutoRequeueAfterSetup()) {
            TranslationRetryDecision(
                allowed = true,
                nextStatus = TranslationJobStatus.Queued,
                startPolicy = TranslationWorkStartPolicy.Replace,
            )
        } else {
            TranslationRetryDecision(
                allowed = false,
                nextStatus = null,
                startPolicy = null,
            )
        }
    }
}

enum class TranslationOverlayEditAction {
    ReplaceBoxes,
    DeletePage,
}

object TranslationOverlayEditPlanner {
    fun actionFor(boxCount: Int): TranslationOverlayEditAction {
        return if (boxCount <= 0) {
            TranslationOverlayEditAction.DeletePage
        } else {
            TranslationOverlayEditAction.ReplaceBoxes
        }
    }
}

fun TranslationJobStatus.isActiveForDedupe(): Boolean {
    return this in ACTIVE_TRANSLATION_JOB_STATUSES
}

fun TranslationJobStatus.isRetryableFromQueue(): Boolean {
    return this in RETRYABLE_TRANSLATION_JOB_STATUSES
}

fun TranslationJobStatus.canAutoRequeueAfterSetup(): Boolean {
    return this == TranslationJobStatus.PausedAuth
}

private val ACTIVE_TRANSLATION_JOB_STATUSES = setOf(
    TranslationJobStatus.Queued,
    TranslationJobStatus.Running,
    TranslationJobStatus.Retrying,
    TranslationJobStatus.ManualRetry,
    TranslationJobStatus.PausedAuth,
    TranslationJobStatus.PausedQuota,
)

private val RETRYABLE_TRANSLATION_JOB_STATUSES = setOf(
    TranslationJobStatus.Failed,
    TranslationJobStatus.PausedAuth,
    TranslationJobStatus.PausedQuota,
)
