package eu.kanade.tachiyomi.util.chapter

/**
 * -R> = regex conversion.
 */
object ChapterRecognition {
    /**
     * All cases with Ch.xx
     * Mokushiroku Alice Vol.1 Ch. 4: Misrepresentation -R> 4
     */
    private val basic = Regex("""(?<=ch\.) *([0-9]+)(\.[0-9]+)?(\.?[a-z]+)?""")

    /**
     * Regex used when only one number occurrence
     * Example: Bleach 567: Down With Snowwhite -R> 567
     */
    private val occurrence = Regex("""([0-9]+)(\.[0-9]+)?(\.?[a-z]+)?""")

    /**
     * Regex used when manga title removed
     * Example: Solanin 028 Vol. 2 -> 028 Vol.2 -> 028Vol.2 -R> 028
     */
    private val withoutManga = Regex("""^([0-9]+)(\.[0-9]+)?(\.?[a-z]+)?""")

    /**
     * Regex used to remove unwanted tags
     * Example Prison School 12 v.1 vol004 version1243 volume64 -R> Prison School 12
     */
    private val unwanted = Regex("""(?<![a-z])(v|ver|vol|version|volume|season|s).?[0-9]+""")

    /**
     * Regex used to remove unwanted whitespace
     * Example One Piece 12 special -R> One Piece 12special
     */
    private val unwantedWhiteSpace = Regex("""(\s)(extra|special|omake)""")

    fun parseChapterNumber(mangaTitle: String, chapterName: String, chapterNumber: Float? = null): Float {
        // If chapter number is known return.
        if (chapterNumber != null && (chapterNumber == -2f || chapterNumber > -1f)) {
            return chapterNumber
        }

        // Get chapter title with lower case
        var name = chapterName.lowercase()

        // Remove comma's or hyphens.
        name = name.replace(',', '.').replace('-', '.')

        // Remove unwanted white spaces.
        unwantedWhiteSpace.findAll(name).let {
            it.forEach { occurrence -> name = name.replace(occurrence.value, occurrence.value.trim()) }
        }

        // Remove unwanted tags.
        unwanted.findAll(name).let {
            it.forEach { occurrence -> name = name.replace(occurrence.value, "") }
        }

        // Check base case ch.xx
        getChapterNumberFromMatch(basic.find(name))?.let { return it }

        // Check one number occurrence.
        val occurrences: MutableList<MatchResult> = arrayListOf()
        occurrence.findAll(name).let {
            it.forEach { occurrence -> occurrences.add(occurrence) }
        }

        if (occurrences.size == 1) {
            getChapterNumberFromMatch(occurrences[0])?.let { return it }
        }

        // Remove manga title from chapter title.
        val nameWithoutManga = name.replace(mangaTitle.lowercase(), "").trim()

        // Check if first value is number after title remove.
        getChapterNumberFromMatch(withoutManga.find(nameWithoutManga))?.let { return it }

        // Take the first number encountered.
        getChapterNumberFromMatch(occurrence.find(nameWithoutManga))?.let { return it }

        return chapterNumber ?: -1f
    }

    /**
     * Check if chapter number is found and return it
     * @param match result of regex
     * @return chapter number if found else null
     */
    private fun getChapterNumberFromMatch(match: MatchResult?): Float? {
        return match?.let {
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

            return if (alpha[0] == '.') {
                // Take value after (.)
                parseAlphaPostFix(alpha[1])
            } else {
                parseAlphaPostFix(alpha[0])
            }
        }

        return .0f
    }

    /**
     * x.a -> x.1, x.b -> x.2, etc
     */
    private fun parseAlphaPostFix(alpha: Char): Float {
        return ("0." + (alpha.code - 96).toString()).toFloat()
    }
}
