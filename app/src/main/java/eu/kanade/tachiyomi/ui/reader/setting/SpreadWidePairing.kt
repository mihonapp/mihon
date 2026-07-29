package eu.kanade.tachiyomi.ui.reader.setting

enum class SpreadWidePairing(val flagValue: Int) {
    DEFAULT(0x00000000),
    ENABLED(0x00010000),
    DISABLED(0x00020000),
    ;

    companion object {
        const val MASK = 0x00030000

        fun fromPreference(preference: Int?): SpreadWidePairing =
            entries.find { it.flagValue == preference } ?: DEFAULT
    }
}
