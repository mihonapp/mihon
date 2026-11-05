package eu.kanade.tachiyomi.data.translation.overlay

/**
 * Drops boxes that are clearly not part of the story: a logo or watermark in another script than the rest of the
 * page, such as a Chinese site mark on a page whose lettering is English. Pure, so it can be tested without Android.
 */
object BoxFilter {

    /**
     * When most boxes of a page have source text without CJK characters, the boxes that do contain some are
     * dropped. A page that is mostly CJK keeps everything, and so does a page with too few boxes to tell.
     */
    fun withoutScriptOutliers(boxes: List<TextBox>): List<TextBox> {
        val withSource = boxes.filter { it.sourceText.isNotBlank() }
        if (withSource.size < MIN_BOXES) return boxes
        val outliers = withSource.count { hasCjk(it.sourceText) }
        if (outliers == 0) return boxes
        val share = (withSource.size - outliers).toFloat() / withSource.size
        if (share < MIN_MAJORITY) return boxes
        return boxes.filterNot { hasCjk(it.sourceText) }
    }

    /** True when [text] has Han, kana or Hangul characters. */
    fun hasCjk(text: String): Boolean = text.any { it.code >= CJK_START && isCjk(it) }

    private fun isCjk(char: Char): Boolean = when (Character.UnicodeScript.of(char.code)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL,
        -> true
        else -> false
    }

    private const val MIN_BOXES = 3
    private const val MIN_MAJORITY = 0.6f
    private const val CJK_START = 0x1100
}
