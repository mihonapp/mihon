package eu.kanade.tachiyomi.util.chapter

/**
 * -R> = regex conversion.
 */
object ChapterRecognition {

    private const val NUMBER_PATTERN = """([0-9]+)(\.[0-9]+)?(\.?[a-z]+)?"""

    /**
     * All cases with Ch.xx
     * Mokushiroku Alice Vol.1 Ch. 4: Misrepresentation -R> 4
     */
    private val basic = Regex("""(?<=ch\.) *$NUMBER_PATTERN""")

    /**
     * Example: Bleach 567: Down With Snowwhite -R> 567
     */
    private val number = Regex(NUMBER_PATTERN)

    /**
     * Regex used to remove unwanted tags
     * Example Prison School 12 v.1 vol004 version1243 volume64 -R> Prison School 12
     */
    private val unwanted = Regex("""\b(?:v|ver|vol|version|volume|season|s)[^a-z]?[0-9]+""")

    /**
     * Regex used to remove unwanted whitespace
     * Example One Piece 12 special -R> One Piece 12special
     */
    private val unwantedWhiteSpace = Regex("""\s(?=extra|special|omake)""")

    fun parseChapterNumber(mangaTitle: String, chapterName: String, chapterNumber: Float? = null): Float {
        // If chapter number is known return.
        if (chapterNumber != null && (chapterNumber == -2f || chapterNumber > -1f)) {
            return chapterNumber
        }

        // Get chapter title with lower case
        var name = chapterName.lowercase()

        // Remove manga title from chapter title.
        name = name.replace(mangaTitle.lowercase(), "").trim()

        // Remove comma's or hyphens.
        name = name.replace(',', '.').replace('-', '.')

        // Remove unwanted white spaces.
        name = unwantedWhiteSpace.replace(name, "")

        // Remove unwanted tags.
        name = unwanted.replace(name, "")

        // Check base case ch.xx
        basic.find(name)?.let { return getChapterNumberFromMatch(it) }

        // Take the first number encountered.
        number.find(name)?.let { return getChapterNumberFromMatch(it) }

        return chapterNumber ?: -1f
    }

    /**
     * Check if chapter number is found and return it
     * @param match result of regex
     * @return chapter number if found else null
     */
    private fun getChapterNumberFromMatch(match: MatchResult): Float {
        return match.let {
            val initial = it.groups[1]?.value?.toFloat()!!
            val subChapterDecimal = it.groups[2]?.value
            val subChapterAlpha = it.groups[3]?.value
            val addition = checkForDecimal(subChapterDecimal, subChapterAlpha)
            initial.plus(addition)
        }
    }

    /**
     * Check for decimal in received strings
     * @param decimal decimal value of regex
     * @param alpha alpha value of regex
     * @return decimal/alpha float value
     */
    private fun checkForDecimal(decimal: String?, alpha: String?): Float {
        if (!decimal.isNullOrEmpty()) {
            return decimal.toFloat()
        }

        if (!alpha.isNullOrEmpty()) {
            if (alpha.contains("extra")) {
                return .99f
            }

            if (alpha.contains("omake")) {
                return .98f
            }

            if (alpha.contains("special")) {
                return .97f
            }

            val trimmedAlpha = alpha.trimStart('.')
            if (trimmedAlpha.length == 1) {
                return parseAlphaPostFix(trimmedAlpha[0])
            }
        }

        return .0f
    }

    /**
     * x.a -> x.1, x.b -> x.2, etc
     */
    private fun parseAlphaPostFix(alpha: Char): Float {
        val number = alpha.code - ('a'.code - 1)
        if (number >= 10) return 0f
        return number / 10f
    }
}
