package eu.kanade.tachiyomi.data.translation.overlay

import eu.kanade.tachiyomi.data.translation.GenerateContentResponse
import eu.kanade.tachiyomi.data.translation.NanoBananaResponseParser
import eu.kanade.tachiyomi.data.translation.TranslationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Pure parsing of the text model's answer for overlay mode. No Android dependencies, so it is unit tested on the
 * JVM. Boxes that are malformed are dropped instead of failing the whole page.
 */
object OverlayResponseParser {

    @Serializable
    private class RawBox(
        @SerialName("box_2d") val box: List<Double>? = null,
        val kind: String? = null,
        @SerialName("source_text") val sourceText: String? = null,
        val text: String? = null,
    )

    private const val MAX_DETAIL_LENGTH = 200

    /**
     * @return the text boxes of the page, possibly empty when the page has no text.
     * @throws TranslationException.ContentBlocked when safety filters blocked the request.
     * @throws TranslationException.UnreadableAnswer when the answer is not the expected JSON.
     * @throws TranslationException.OutputTruncated when the model stopped at its output token limit.
     */
    fun parse(body: String, json: Json, onDropped: (String) -> Unit = {}): List<TextBox> {
        val response = try {
            json.decodeFromString<GenerateContentResponse>(body)
        } catch (_: IllegalArgumentException) {
            throw TranslationException.UnreadableAnswer("Unreadable response")
        }

        response.promptFeedback?.blockReason?.takeIf { it.isNotBlank() }?.let {
            throw TranslationException.ContentBlocked(it)
        }

        val candidate = response.candidates?.firstOrNull()
            ?: throw TranslationException.UnreadableAnswer("No candidates")
        if (candidate.finishReason.equals("MAX_TOKENS", ignoreCase = true)) {
            throw TranslationException.OutputTruncated()
        }
        val answer = candidate.content?.parts.orEmpty()
            .filter { it.thought != true }
            .mapNotNull { it.text }
            .joinToString("")
            .trim()

        if (answer.isEmpty()) {
            val finishReason = candidate.finishReason
            if (finishReason != null && finishReason in NanoBananaResponseParser.SAFETY_FINISH_REASONS) {
                throw TranslationException.ContentBlocked(finishReason)
            }
            throw TranslationException.UnreadableAnswer(finishReason ?: "Empty answer")
        }

        return parseAnswer(answer, json, onDropped)
    }

    /** @param onDropped is told why a box of the answer was left out, for the translation log. */
    internal fun parseAnswer(answer: String, json: Json, onDropped: (String) -> Unit = {}): List<TextBox> {
        // Models sometimes wrap JSON in ``` fences or add a sentence around it.
        val start = answer.indexOfFirst { it == '{' || it == '[' }
        val end = answer.indexOfLast { it == '}' || it == ']' }
        if (start < 0 || end < start) {
            throw TranslationException.UnreadableAnswer("No JSON found: ${answer.take(MAX_DETAIL_LENGTH)}")
        }

        val element = try {
            json.parseToJsonElement(answer.substring(start, end + 1))
        } catch (_: IllegalArgumentException) {
            throw TranslationException.UnreadableAnswer("Invalid JSON")
        }

        val array: JsonArray = when (element) {
            is JsonArray -> element
            is JsonObject -> element["boxes"] as? JsonArray
                ?: throw TranslationException.UnreadableAnswer("Missing boxes field")
            else -> throw TranslationException.UnreadableAnswer("Unexpected JSON")
        }
        return array.mapNotNull { toTextBox(it, json, onDropped) }
    }

    private fun toTextBox(element: JsonElement, json: Json, onDropped: (String) -> Unit): TextBox? {
        val raw = try {
            json.decodeFromJsonElement<RawBox>(element)
        } catch (_: IllegalArgumentException) {
            onDropped("not a box object: ${element.toString().take(MAX_DETAIL_LENGTH)}")
            return null
        }

        val text = raw.text?.trim().orEmpty()
        val coordinates = raw.box
        if (text.isEmpty()) {
            onDropped("no translated text, source \"${raw.sourceText.orEmpty().take(MAX_DETAIL_LENGTH)}\"")
            return null
        }
        if (coordinates == null || coordinates.size != 4 || coordinates.any { !it.isFinite() }) {
            onDropped("box_2d is not four numbers, text \"${text.take(MAX_DETAIL_LENGTH)}\"")
            return null
        }

        val values = coordinates.map { it.coerceIn(0.0, TextBox.GRID.toDouble()).toInt() }
        val yMin = minOf(values[0], values[2])
        val yMax = maxOf(values[0], values[2])
        val xMin = minOf(values[1], values[3])
        val xMax = maxOf(values[1], values[3])
        if (yMax - yMin < 1 || xMax - xMin < 1) {
            onDropped("box has no area $values, text \"${text.take(MAX_DETAIL_LENGTH)}\"")
            return null
        }

        return TextBox(yMin, xMin, yMax, xMax, parseKind(raw.kind), text, raw.sourceText?.trim().orEmpty())
    }

    private fun parseKind(kind: String?): BoxKind {
        val lower = kind?.lowercase().orEmpty()
        return when {
            "sfx" in lower || "sound" in lower -> BoxKind.SFX
            "caption" in lower || "narrat" in lower -> BoxKind.CAPTION
            else -> BoxKind.BUBBLE
        }
    }
}
