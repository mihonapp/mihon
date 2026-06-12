package tachiyomi.domain.reader.model

/**
 * Built-in rules shipped with the app (merged into preferences when missing).
 * [ReadingModeAutoRule.readingModeFlag] uses the same viewer flag values as the reader (LTR=1, RTL=2, WEBTOON=4, etc.).
 */
object ReadingModeAutoRulePresets {

    const val LONG_STRIP = "preset_long_strip"
    const val LTR_WESTERN = "preset_ltr_western"
    const val RTL_MANGA_JP = "preset_rtl_manga_jp"

    /** Order: long strip → western LTR → manga/Japanese RTL (later rules can refine). */
    fun defaults(): List<ReadingModeAutoRule> = listOf(
        ReadingModeAutoRule(
            id = LONG_STRIP,
            presetId = LONG_STRIP,
            title = "",
            enabled = false,
            readingModeFlag = 0x00000004, // WEBTOON / long strip
            tagsAnyOf = listOf(
                "webtoon",
                "web comic",
                "manhua",
                "manhwa",
                "long strip",
                "korean",
            ),
        ),
        ReadingModeAutoRule(
            id = LTR_WESTERN,
            presetId = LTR_WESTERN,
            title = "",
            enabled = false,
            readingModeFlag = 0x00000001, // LEFT_TO_RIGHT
            tagsAnyOf = listOf("western"),
        ),
        ReadingModeAutoRule(
            id = RTL_MANGA_JP,
            presetId = RTL_MANGA_JP,
            title = "",
            enabled = false,
            readingModeFlag = 0x00000002, // RIGHT_TO_LEFT
            tagsAnyOf = listOf("manga", "japanese"),
        ),
    )
}
