package eu.kanade.tachiyomi.ui.reader.setting

enum class SpreadForcePairing(val flagValue: Int) {
    DEFAULT(0x00000000),
    ENABLED(0x00000100),
    DISABLED(0x00000200),
    ;

    companion object {
        const val MASK = 0x00000300

        fun fromPreference(preference: Int?): SpreadForcePairing =
            entries.find { it.flagValue == preference } ?: DEFAULT
    }
}
