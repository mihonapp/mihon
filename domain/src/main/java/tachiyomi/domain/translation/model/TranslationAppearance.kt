package tachiyomi.domain.translation.model

/**
 * Colour of the translated lettering. "Auto" follows the page: dark text on light panels, light text on dark ones.
 */
enum class TranslationTextColor {
    AUTO,
    WHITE,
    BLACK,
    RED,
    YELLOW,
    ;

    companion object {
        /** Argb value of the chosen colour, or null when the page should decide. */
        fun of(color: TranslationTextColor): Int? = when (color) {
            AUTO -> null
            WHITE -> 0xFFFFFFFF.toInt()
            BLACK -> 0xFF000000.toInt()
            RED -> 0xFFE53935.toInt()
            YELLOW -> 0xFFFFD600.toInt()
        }
    }
}

/**
 * Case of the translated lettering.
 */
enum class TranslationLetterCase {
    ORIGINAL,
    UPPER,
    LOWER,
    ;

    fun apply(text: String): String = when (this) {
        ORIGINAL -> text
        UPPER -> text.uppercase()
        LOWER -> text.lowercase()
    }
}
