package eu.kanade.tachiyomi.ui.library.setting

enum class DisplayModeSetting(val flag: Long) {
    COMPACT_GRID(0b00000000),
    COMFORTABLE_GRID(0b00000001),
    LIST(0b00000010),
    COVER_ONLY_GRID(0b00000011);

    companion object {
        const val MASK = 0b00000011L

        fun fromFlag(flag: Long?): DisplayModeSetting {
            return values()
                .find { mode -> mode.flag == flag } ?: COMPACT_GRID
        }
    }
}
