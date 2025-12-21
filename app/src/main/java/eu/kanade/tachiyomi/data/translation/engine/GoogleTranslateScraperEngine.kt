package eu.kanade.tachiyomi.data.translation.engine

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.domain.translation.model.TranslationEngine
import tachiyomi.domain.translation.model.TranslationResult
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder

class GoogleTranslateScraperEngine : TranslationEngine {
    override val id = 999L // Custom ID for scraper
    override val name = "Google Translate (Scraper)"
    override val requiresApiKey = false
    override val isRateLimited = true
    override val isOffline = false

    override val supportedLanguages: List<Pair<String, String>> = listOf(
        "auto" to "Auto-detect",
        "af" to "Afrikaans",
        "sq" to "Albanian",
        "am" to "Amharic",
        "ar" to "Arabic",
        "hy" to "Armenian",
        "az" to "Azerbaijani",
        "eu" to "Basque",
        "be" to "Belarusian",
        "bn" to "Bengali",
        "bs" to "Bosnian",
        "bg" to "Bulgarian",
        "ca" to "Catalan",
        "ceb" to "Cebuano",
        "ny" to "Chichewa",
        "zh-CN" to "Chinese (Simplified)",
        "zh-TW" to "Chinese (Traditional)",
        "co" to "Corsican",
        "hr" to "Croatian",
        "cs" to "Czech",
        "da" to "Danish",
        "nl" to "Dutch",
        "en" to "English",
        "eo" to "Esperanto",
        "et" to "Estonian",
        "tl" to "Filipino",
        "fi" to "Finnish",
        "fr" to "French",
        "fy" to "Frisian",
        "gl" to "Galician",
        "ka" to "Georgian",
        "de" to "German",
        "el" to "Greek",
        "gu" to "Gujarati",
        "ht" to "Haitian Creole",
        "ha" to "Hausa",
        "haw" to "Hawaiian",
        "iw" to "Hebrew",
        "hi" to "Hindi",
        "hmn" to "Hmong",
        "hu" to "Hungarian",
        "is" to "Icelandic",
        "ig" to "Igbo",
        "id" to "Indonesian",
        "ga" to "Irish",
        "it" to "Italian",
        "ja" to "Japanese",
        "jw" to "Javanese",
        "kn" to "Kannada",
        "kk" to "Kazakh",
        "km" to "Khmer",
        "ko" to "Korean",
        "ku" to "Kurdish (Kurmanji)",
        "ky" to "Kyrgyz",
        "lo" to "Lao",
        "la" to "Latin",
        "lv" to "Latvian",
        "lt" to "Lithuanian",
        "lb" to "Luxembourgish",
        "mk" to "Macedonian",
        "mg" to "Malagasy",
        "ms" to "Malay",
        "ml" to "Malayalam",
        "mt" to "Maltese",
        "mi" to "Maori",
        "mr" to "Marathi",
        "mn" to "Mongolian",
        "my" to "Myanmar (Burmese)",
        "ne" to "Nepali",
        "no" to "Norwegian",
        "ps" to "Pashto",
        "fa" to "Persian",
        "pl" to "Polish",
        "pt" to "Portuguese",
        "pa" to "Punjabi",
        "ro" to "Romanian",
        "ru" to "Russian",
        "sm" to "Samoan",
        "gd" to "Scots Gaelic",
        "sr" to "Serbian",
        "st" to "Sesotho",
        "sn" to "Shona",
        "sd" to "Sindhi",
        "si" to "Sinhala",
        "sk" to "Slovak",
        "sl" to "Slovenian",
        "so" to "Somali",
        "es" to "Spanish",
        "su" to "Sundanese",
        "sw" to "Swahili",
        "sv" to "Swedish",
        "tg" to "Tajik",
        "ta" to "Tamil",
        "te" to "Telugu",
        "th" to "Thai",
        "tr" to "Turkish",
        "uk" to "Ukrainian",
        "ur" to "Urdu",
        "uz" to "Uzbek",
        "vi" to "Vietnamese",
        "cy" to "Welsh",
        "xh" to "Xhosa",
        "yi" to "Yiddish",
        "yo" to "Yoruba",
        "zu" to "Zulu",
    )

    private val network: NetworkHelper by injectLazy()
    private val client = network.client
    private val json = Json { ignoreUnknownKeys = true }

    private var tkk: String = "0"
    private var tkkTimestamp: Long = 0

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): TranslationResult {
        return try {
            val translated = texts.map { text ->
                translateSingleInternal(text, sourceLanguage, targetLanguage)
            }
            TranslationResult.Success(translated, null)
        } catch (e: Exception) {
            TranslationResult.Error(e.message ?: "Unknown error", TranslationResult.ErrorCode.UNKNOWN)
        }
    }

    private suspend fun translateSingleInternal(text: String, source: String, target: String): String {
        try {
            val token = calculateToken(text)
            val url = "https://translate.google.com/translate_a/single".toHttpUrl().newBuilder()
                .addQueryParameter("client", "gtx")
                .addQueryParameter("sl", source)
                .addQueryParameter("tl", target)
                .addQueryParameter("hl", target)
                .addQueryParameter("dt", "at")
                .addQueryParameter("dt", "bd")
                .addQueryParameter("dt", "ex")
                .addQueryParameter("dt", "ld")
                .addQueryParameter("dt", "md")
                .addQueryParameter("dt", "qca")
                .addQueryParameter("dt", "rw")
                .addQueryParameter("dt", "rm")
                .addQueryParameter("dt", "ss")
                .addQueryParameter("dt", "t")
                .addQueryParameter("ie", "UTF-8")
                .addQueryParameter("oe", "UTF-8")
                .addQueryParameter("otf", "1")
                .addQueryParameter("ssel", "0")
                .addQueryParameter("tsel", "0")
                .addQueryParameter("kc", "7")
                .addQueryParameter("tk", token)
                .addQueryParameter("q", text)
                .build()

            val request = GET(url)
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return text
            }

            val body = response.body.string()
            val jsonArray = json.parseToJsonElement(body).jsonArray

            val result = StringBuilder()
            val sentences = jsonArray[0].jsonArray

            for (sentence in sentences) {
                val sentenceArray = sentence.jsonArray
                if (sentenceArray.isNotEmpty()) {
                    val translatedPart = sentenceArray[0].jsonPrimitive.content
                    result.append(translatedPart)
                }
            }

            return result.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return text
        }
    }

    // TKK calculation logic ported from various open source implementations
    private fun calculateToken(text: String): String {
        // Update TKK if needed (every hour)
        if (System.currentTimeMillis() - tkkTimestamp > 3600000) {
            updateTKK()
        }

        val parts = tkk.split(".")
        val a = parts.getOrElse(0) { "0" }.toLong()
        val b = parts.getOrElse(1) { "0" }.toLong()

        val d = mutableListOf<Int>()
        for (i in 0 until text.length) {
            var c = text[i].code
            if (c < 128) {
                d.add(c)
            } else {
                if (c < 2048) {
                    d.add((c shr 6) or 192)
                } else {
                    if (c in 55296..56319 && i + 1 < text.length && text[i + 1].code in 56320..57343) {
                        c = 65536 + ((c and 1023) shl 10) + (text[i + 1].code and 1023)
                        d.add((c shr 18) or 240)
                        d.add(((c shr 12) and 63) or 128)
                    } else {
                        d.add((c shr 12) or 224)
                    }
                    d.add(((c shr 6) and 63) or 128)
                }
                d.add((c and 63) or 128)
            }
        }

        var param = a
        for (value in d) {
            param += value
            param = workToken(param, "+-a^+6")
        }
        param = workToken(param, "+-3^+b+-f")
        param = param xor b
        if (param < 0) {
            param = (param and 2147483647) + 2147483648
        }
        param %= 1000000

        return "$param.${param xor a}"
    }

    private fun workToken(a: Long, seed: String): Long {
        var res = a
        for (i in 0 until seed.length - 2 step 3) {
            val c = seed[i + 2]
            val d = if (c >= 'a') c.code - 87 else c.toString().toInt()
            val e = if (seed[i + 1] == '+') res ushr d else res shl d
            res = if (seed[i] == '+') res + e else res xor e
        }
        return res
    }

    private fun updateTKK() {
        try {
            val request = GET("https://translate.google.com")
            val response = client.newCall(request).execute()
            val body = response.body.string()

            // Regex to find TKK: "tkk":"427110.1469889687"
            val regex = "tkk\":\"(\\d+\\.\\d+)\"".toRegex()
            val match = regex.find(body)

            if (match != null) {
                tkk = match.groupValues[1]
                tkkTimestamp = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            // Fallback to default
            tkk = "427110.1469889687"
        }
    }
}
