package eu.kanade.tachiyomi.data.ocr

import eu.kanade.tachiyomi.data.security.SecureOcrPreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslateTextUseCase(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val securePrefs: SecureOcrPreferences = Injekt.get(),
) {

    /**
     * Translates a list of OCR text blocks with context awareness.
     *
     * For Google Free: joins all bubbles into one paragraph (separated by \n\n)
     * so the translator sees the full dialogue context. Google preserves \n\n in
     * output so we can split back reliably.
     *
     * For DeepL: uses native batch API which is inherently context-aware.
     */
    suspend fun awaitBatch(
        texts: List<String>,
        sourceLang: String,
        targetLang: String,
        service: Int,
    ): List<String> {
        if (texts.isEmpty()) return emptyList()

        // Normalize: collapse in-block newlines into natural text.
        // Fix hyphenation: "word-\nword" → "wordword"
        // Fix OCR errors: 0 → O (zero looks like letter O)
        val normalized = texts.map { text ->
            // Fix hyphenation at line breaks
            val noHyphen = text.replace(Regex("""-\s*\n\s*"""), "")
            
            // Fix common OCR errors
            val fixedOcr = noHyphen
                .replace(Regex("""\b0racle\b""", RegexOption.IGNORE_CASE), "Oracle")
                .replace(Regex("""\b0RACLE\b"""), "ORACLE")
                .replace(Regex("""([A-Za-z])0([A-Za-z])"""), "$1O$2")  // middle of word
                .replace(Regex("""^0([A-Za-z])"""), "O$1")  // start of word
                .replace(Regex("""([A-Za-z])0$"""), "$1O")  // end of word
            
            // Add context hints for better translation (invisible markers)
            val withContext = fixedOcr
                .replace(Regex("""^Well\?\s*""", RegexOption.IGNORE_CASE), "So? ")
                .replace(Regex("""^Well""", RegexOption.IGNORE_CASE), "So")
            
            val lines = withContext.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val avgLen = if (lines.isEmpty()) 0.0 else lines.sumOf { it.length }.toDouble() / lines.size
            if (avgLen <= 3.0) {
                lines.joinToString("") // Japanese/CJK: no space
            } else {
                lines.joinToString(" ") // Latin: space between wrapped lines
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                val translated = when (service) {
                    0 -> googleFreeParagraph(normalized, sourceLang, targetLang)
                    1 -> {
                        val key = securePrefs.getApiKey()
                            ?: return@withContext texts.map { "API Key Not Set for DeepL!" }
                        deepLBatch(normalized, sourceLang, targetLang, key)
                    }
                    2 -> {
                        val key = securePrefs.getApiKey()
                            ?: return@withContext texts.map { "API Key Not Set for Google Cloud!" }
                        googleFreeParagraph(normalized, sourceLang, targetLang)
                    }
                    else -> texts.map { "" }
                }
                // Post-process: fix stutter patterns
                translated.map { fixStutterPattern(it) }
            } catch (e: Exception) {
                texts.map { "" }
            }
        }
    }

    // ─── Google Translate Free – Context-aware batch translation ─────────────────────

    private fun googleFreeParagraph(
        texts: List<String>,
        sourceLang: String,
        targetLang: String,
    ): List<String> {
        if (texts.isEmpty()) return emptyList()
        
        val sl = if (sourceLang == "auto") "auto" else sourceLang
        
        // Use numbered markers to preserve bubble order while translating together
        val marker = "§§"
        val numberedTexts = texts.mapIndexed { index, text -> 
            "[$index]${text}[$index]" 
        }
        val combined = numberedTexts.joinToString(" $marker ")
        
        return try {
            val url = "https://translate.googleapis.com/translate_a/single" +
                "?client=gtx" +
                "&sl=$sl" +
                "&tl=$targetLang" +
                "&dt=t" +
                "&q=${java.net.URLEncoder.encode(combined, "UTF-8")}"

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .get()
                .build()

            val response = networkHelper.client.newCall(request).execute()
            val body = response.body?.string()
            
            if (!response.isSuccessful || body == null) return texts

            val json = Json.parseToJsonElement(body)
            val sb = StringBuilder()
            json.jsonArray[0].jsonArray.forEach { segment ->
                sb.append(segment.jsonArray[0].jsonPrimitive.content)
            }
            
            val translated = sb.toString()
            
            // Extract each numbered section
            val results = MutableList(texts.size) { "" }
            texts.indices.forEach { index ->
                val pattern = Regex("""\[$index\](.*?)\[$index\]""")
                val match = pattern.find(translated)
                results[index] = match?.groupValues?.get(1)?.trim() ?: texts[index]
            }
            
            results
        } catch (e: Exception) {
            texts
        }
    }

    // ─── DeepL – Context-aware batch translation ──────────────────

    private fun deepLBatch(
        texts: List<String>,
        sourceLang: String,
        targetLang: String,
        apiKey: String,
    ): List<String> {
        if (texts.isEmpty()) return emptyList()
        
        // Use numbered markers to preserve bubble order while translating together
        // This maintains context across all speech bubbles
        val numberedTexts = texts.mapIndexed { index, text -> 
            "[$index]${text}[$index]" 
        }
        val combined = numberedTexts.joinToString(" ||| ")
        
        val payload = buildJsonObject {
            putJsonArray("text") { add(combined) }
            if (sourceLang != "auto") put("source_lang", sourceLang.uppercase())
            put("target_lang", targetLang.uppercase())
            put("preserve_formatting", true)
        }.toString()

        val request = Request.Builder()
            .url("https://api-free.deepl.com/v2/translate")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "DeepL-Auth-Key ${apiKey.trim()}")
            .build()

        return try {
            val response = networkHelper.client.newCall(request).execute()
            val body = response.body?.string() ?: return texts
            if (!response.isSuccessful) return texts

            val translated = Json.parseToJsonElement(body)
                .jsonObject["translations"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject?.get("text")?.jsonPrimitive?.content?.trim() ?: return texts
            
            // Extract each numbered section
            val results = MutableList(texts.size) { "" }
            texts.indices.forEach { index ->
                val pattern = Regex("""\[$index\](.*?)\[$index\]""")
                val match = pattern.find(translated)
                results[index] = match?.groupValues?.get(1)?.trim() ?: texts[index]
            }
            
            results
        } catch (e: Exception) {
            texts
        }
    }

    // ─── Post-processing helpers ──────────────────────────────────────────────

    /**
     * Fixes stutter patterns in translated manga dialogue.
     *
     * Problem: "I- I am..." in source → Google mistakenly translates "I-" as the
     * letter/exclamation "A" → produces "A-I..." instead of "I-I...".
     *
     * Rule: If a translated string starts with X-Word where X (single letter)
     * is NOT the first letter of Word, replace X with Word's first letter.
     */
    private fun fixStutterPattern(text: String): String {
        val stutterRegex = Regex("""^([A-Za-z])-([A-Za-z]{2,})""")
        val match = stutterRegex.find(text.trimStart()) ?: return text

        val stutterLetter = match.groupValues[1]
        val word = match.groupValues[2]
        val correctLetter = word.first().toString()

        return if (!stutterLetter.equals(correctLetter, ignoreCase = true)) {
            val fixedStutter = if (stutterLetter[0].isUpperCase()) correctLetter.uppercase() else correctLetter.lowercase()
            text.replaceRange(match.range.first, match.range.first + stutterLetter.length, fixedStutter)
        } else {
            text
        }
    }
}
