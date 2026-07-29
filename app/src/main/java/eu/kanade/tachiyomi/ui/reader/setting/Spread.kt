package eu.kanade.tachiyomi.ui.reader.setting

enum class Spread(val flagValue: Int) {
    DEFAULT(0x00000000),
    ENABLED(0x00000040),
    DISABLED(0x00000080),
    ;

    companion object {
        const val MASK = 0x000000C0

        fun fromPreference(preference: Int?): Spread = entries.find { it.flagValue == preference } ?: DEFAULT
    }
}
