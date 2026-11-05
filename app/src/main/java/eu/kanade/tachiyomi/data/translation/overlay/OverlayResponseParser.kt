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
    private const val TAIL_LENGTH = 80
    private const val UNICODE_ESCAPE_LENGTH = 5
    private const val HEX_RADIX = 16

    private val BOX_START = Regex("\\{\\s*\"box_2d\"\\s*:")
    private const val NUMBER = "(-?\\d+(?:\\.\\d+)?)"
    private val BOX_NUMBERS = Regex(
        "\"box_2d\"\\s*:\\s*\\[\\s*(?:\\[\\s*)?$NUMBER\\s*,\\s*$NUMBER\\s*,\\s*$NUMBER\\s*,\\s*$NUMBER",
    )
    private val KIND_FIELD = Regex("\"kind\"\\s*:\\s*\"([^\"]*)\"")
    private val SOURCE_FIELD = Regex(
        "\"source_text\"\\s*:\\s*\"(.*?)\"\\s*,\\s*\"text\"\\s*:",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val TEXT_FIELD = Regex("\"text\"\\s*:\\s*\"(.*)\"[\\s,\\]}]*$", RegexOption.DOT_MATCHES_ALL)
    private val ESCAPE = Regex("\\\\(u[0-9a-fA-F]{4}|.)", RegexOption.DOT_MATCHES_ALL)

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
        } catch (e: IllegalArgumentException) {
            // Small models sometimes leave a quote or a control character unescaped inside a translation. One bad
            // character must not cost the whole page, so read the boxes field by field instead.
            val repaired = repairTruncated(answer.substring(start))?.let {
                try {
                    json.parseToJsonElement(it)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            if (repaired != null) {
                onDropped("the answer was cut off, closed it and kept the complete boxes")
                return boxesOf(repaired, json, onDropped)
            }
            val salvaged = salvageBoxes(answer, onDropped)
            if (salvaged.isNotEmpty()) {
                onDropped(
                    "the answer was not valid JSON (${e.message.orEmpty().take(MAX_DETAIL_LENGTH)}), " +
                        "recovered ${salvaged.size} box(es) by reading the fields directly",
                )
                return salvaged
            }
            throw TranslationException.UnreadableAnswer(
                "Invalid JSON: ${e.message.orEmpty().take(MAX_DETAIL_LENGTH)} " +
                    "(answer of ${answer.length} characters, starts \"${answer.take(TAIL_LENGTH)}\", " +
                    "ends \"${answer.takeLast(TAIL_LENGTH)}\")",
            )
        }
        return boxesOf(element, json, onDropped)
    }

    private fun boxesOf(element: JsonElement, json: Json, onDropped: (String) -> Unit): List<TextBox> {
        val array: JsonArray = when (element) {
            is JsonArray -> element
            is JsonObject -> element["boxes"] as? JsonArray
                ?: throw TranslationException.UnreadableAnswer("Missing boxes field")
            else -> throw TranslationException.UnreadableAnswer("Unexpected JSON")
        }
        return array.mapNotNull { toTextBox(it, json, onDropped) }
    }

    /**
     * Closes an answer that stops in the middle of a box: everything after the last complete box object is cut away
     * and the open array and object are closed. Returns null when there is no complete box to keep.
     */
    internal fun repairTruncated(answer: String): String? {
        var depth = 0
        var inString = false
        var escaped = false
        var lastBoxEnd = -1
        for ((i, c) in answer.withIndex()) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    // Depth 2 after a closing brace is a finished box inside {"boxes":[ ... ]}. Depth 1 is a
                    // finished box inside a bare array.
                    if (c == '}' && (depth == 2 || (depth == 1 && answer.trimStart().startsWith("[")))) lastBoxEnd = i
                }
            }
        }
        if (lastBoxEnd < 0) return null
        val head = answer.substring(0, lastBoxEnd + 1)
        return if (head.trimStart().startsWith("[")) "$head]" else "$head]}"
    }

    /** Some answers wrap the four numbers once more: `"box_2d":[[ymin,xmin,ymax,xmax]]`. */
    private fun flattenBox(element: JsonElement): JsonElement {
        val obj = element as? JsonObject ?: return element
        val box = obj["box_2d"] as? JsonArray ?: return element
        val inner = box.singleOrNull() as? JsonArray ?: return element
        return JsonObject(obj + ("box_2d" to inner))
    }

    private fun toTextBox(element: JsonElement, json: Json, onDropped: (String) -> Unit): TextBox? {
        val raw = try {
            json.decodeFromJsonElement<RawBox>(flattenBox(element))
        } catch (_: IllegalArgumentException) {
            onDropped("left out a box: not a box object: ${element.toString().take(MAX_DETAIL_LENGTH)}")
            return null
        }

        return makeBox(raw.box, raw.kind, raw.text?.trim().orEmpty(), raw.sourceText?.trim().orEmpty(), onDropped)
    }

    /** Checks the numbers and text of one box and builds it, or reports why it is left out. */
    private fun makeBox(
        coordinates: List<Double>?,
        kind: String?,
        text: String,
        sourceText: String,
        onDropped: (String) -> Unit,
    ): TextBox? {
        if (text.isEmpty()) {
            onDropped("left out a box: no translated text, source \"${sourceText.take(MAX_DETAIL_LENGTH)}\"")
            return null
        }
        if (coordinates == null || coordinates.size != 4 || coordinates.any { !it.isFinite() }) {
            onDropped("left out a box: box_2d is not four numbers, text \"${text.take(MAX_DETAIL_LENGTH)}\"")
            return null
        }

        val values = coordinates.map { it.coerceIn(0.0, TextBox.GRID.toDouble()).toInt() }
        val yMin = minOf(values[0], values[2])
        val yMax = maxOf(values[0], values[2])
        val xMin = minOf(values[1], values[3])
        val xMax = maxOf(values[1], values[3])
        if (yMax - yMin < 1 || xMax - xMin < 1) {
            onDropped("left out a box: box has no area $values, text \"${text.take(MAX_DETAIL_LENGTH)}\"")
            return null
        }

        return TextBox(yMin, xMin, yMax, xMax, parseKind(kind), text, sourceText)
    }

    /**
     * Reads boxes out of an answer that is not valid JSON. Every box starts with `{"box_2d"`, so the answer is cut
     * there and the fields of each piece are read with patterns that do not care about stray quotes in the text.
     * A piece that is cut off (no closing quote) is skipped.
     */
    internal fun salvageBoxes(answer: String, onDropped: (String) -> Unit = {}): List<TextBox> {
        val starts = BOX_START.findAll(answer).map { it.range.first }.toList()
        if (starts.isEmpty()) return emptyList()
        return starts.mapIndexedNotNull { index, from ->
            val chunk = answer.substring(from, starts.getOrNull(index + 1) ?: answer.length)
            val numbers = BOX_NUMBERS.find(chunk)?.groupValues?.drop(1)?.map { it.toDouble() }
            val kind = KIND_FIELD.find(chunk)?.groupValues?.get(1)
            val source = SOURCE_FIELD.find(chunk)?.groupValues?.get(1)?.let(::unescape).orEmpty().trim()
            val text = TEXT_FIELD.find(chunk)?.groupValues?.get(1)?.let(::unescape).orEmpty().trim()
            if (text.isEmpty() && numbers != null) {
                onDropped(
                    "left out a box: could not read its text (cut off?), source \"${source.take(MAX_DETAIL_LENGTH)}\"",
                )
            }
            makeBox(numbers, kind, text, source, onDropped)
        }
    }

    /** Turns the JSON escapes of a string that was cut out by hand back into characters. */
    private fun unescape(raw: String): String = ESCAPE.replace(raw) { match ->
        val code = match.groupValues[1]
        when {
            code.length == UNICODE_ESCAPE_LENGTH && code.startsWith("u") ->
                code.substring(1).toInt(HEX_RADIX).toChar().toString()
            code == "n" || code == "t" || code == "r" -> " "
            else -> code
        }
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
