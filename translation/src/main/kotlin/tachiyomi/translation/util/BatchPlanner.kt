package tachiyomi.translation.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure helpers for turning a page's entries into provider requests and back.
 *
 * LLM providers are the only ones that need the separator/JSON conventions; native batch APIs
 * (Edge, Azure, Google Cloud) take a plain list.
 */
object BatchPlanner {
    const val SEPARATOR = "<sep>"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Splits `0 until count` into chunks of at most [maxBatch]. */
    fun batches(count: Int, maxBatch: Int): List<IntRange> {
        if (count <= 0) return emptyList()
        val size = maxBatch.coerceAtLeast(1)
        return (0 until count step size).map { start ->
            start until minOf(start + size, count)
        }
    }

    fun joinSeparated(texts: List<String>): String = texts.joinToString(SEPARATOR)

    /**
     * Reverses [joinSeparated]. Returns null when the provider dropped or merged items, which the
     * caller should treat as a failed batch and retry one by one.
     */
    fun splitSeparated(raw: String?, expected: Int): List<String>? {
        if (raw == null) return null
        val parts = raw.split(SEPARATOR).map { it.trim() }
        return parts.takeIf { it.size == expected }
    }

    fun buildLlmUserPrompt(
        texts: List<String>,
        context: List<String>,
        target: String,
    ): String = buildString {
        appendLine("目标语言：$target")
        if (context.isNotEmpty()) {
            appendLine("前文（仅供理解指代，不要翻译、不要输出）：")
            context.forEach { appendLine("- $it") }
        }
        appendLine("待翻译 ${texts.size} 条，输出 JSON 字符串数组，长度必须等于 ${texts.size}：")
        texts.forEachIndexed { index, text -> appendLine("${index + 1}. $text") }
    }

    /**
     * Extracts the first JSON string array from a model reply, tolerating code fences and prose
     * before/after it. Also accepts `[{ "text": ... }]` shapes.
     */
    fun extractStringArray(raw: String?): List<String>? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return runCatching {
            json.parseToJsonElement(text.substring(start, end + 1)).jsonArray.map { item ->
                when (item) {
                    is JsonPrimitive -> item.contentOrNull.orEmpty()
                    is JsonObject -> item["text"]?.jsonPrimitive?.contentOrNull
                        ?: item["translation"]?.jsonPrimitive?.contentOrNull
                        ?: ""
                    else -> ""
                }
            }
        }.getOrNull()
    }
}
