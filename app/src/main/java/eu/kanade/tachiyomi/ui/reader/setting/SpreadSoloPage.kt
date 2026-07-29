package eu.kanade.tachiyomi.ui.reader.setting

enum class SpreadSoloPage(val flagValue: Int) {
    DEFAULT(0x00000000),
    CENTER(0x00004000),
    JUSTIFY(0x00008000),
    ;

    companion object {
        const val MASK = 0x0000C000

        fun fromPreference(preference: Int?): SpreadSoloPage =
            entries.find { it.flagValue == preference } ?: DEFAULT
    }
}
