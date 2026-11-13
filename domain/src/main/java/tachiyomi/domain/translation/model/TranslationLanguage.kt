package tachiyomi.domain.translation.model

/**
 * A language a page or a title can be translated from or to.
 *
 * [ocrScript] is the ML Kit model used to read the language from an image. It's null for scripts the app
 * can't read on-device, which are still valid targets and sources for text that isn't in an image.
 */
enum class TranslationLanguage(
    val code: String,
    val ocrScript: OcrScript?,
) {
    AUTO("auto", null),
    ARABIC("ar", null),
    BENGALI("bn", null),
    BULGARIAN("bg", null),
    CHINESE_SIMPLIFIED("zh-Hans", OcrScript.CHINESE),
    CHINESE_TRADITIONAL("zh-Hant", OcrScript.CHINESE),
    CROATIAN("hr", OcrScript.LATIN),
    CZECH("cs", OcrScript.LATIN),
    DANISH("da", OcrScript.LATIN),
    DUTCH("nl", OcrScript.LATIN),
    ENGLISH("en", OcrScript.LATIN),
    FILIPINO("tl", OcrScript.LATIN),
    FINNISH("fi", OcrScript.LATIN),
    FRENCH("fr", OcrScript.LATIN),
    GERMAN("de", OcrScript.LATIN),
    GREEK("el", null),
    HEBREW("he", null),
    HINDI("hi", null),
    HUNGARIAN("hu", OcrScript.LATIN),
    INDONESIAN("id", OcrScript.LATIN),
    ITALIAN("it", OcrScript.LATIN),
    JAPANESE("ja", OcrScript.JAPANESE),
    KOREAN("ko", OcrScript.KOREAN),
    MALAY("ms", OcrScript.LATIN),
    NORWEGIAN("no", OcrScript.LATIN),
    PERSIAN("fa", null),
    POLISH("pl", OcrScript.LATIN),
    PORTUGUESE("pt", OcrScript.LATIN),
    ROMANIAN("ro", OcrScript.LATIN),
    RUSSIAN("ru", null),
    SERBIAN("sr", null),
    SLOVAK("sk", OcrScript.LATIN),
    SLOVENIAN("sl", OcrScript.LATIN),
    SPANISH("es", OcrScript.LATIN),
    SWEDISH("sv", OcrScript.LATIN),
    THAI("th", null),
    TURKISH("tr", OcrScript.LATIN),
    UKRAINIAN("uk", null),
    VIETNAMESE("vi", OcrScript.LATIN),
    ;

    companion object {
        fun fromCode(code: String): TranslationLanguage {
            val language = code.substringBefore('-')
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
                ?: entries.firstOrNull {
                    it != AUTO && it.code.substringBefore('-').equals(language, ignoreCase = true)
                }
                ?: AUTO
        }
    }
}
