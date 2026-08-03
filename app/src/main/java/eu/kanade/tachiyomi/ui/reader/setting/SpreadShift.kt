package eu.kanade.tachiyomi.ui.reader.setting

/**
 * A spread pairing-baseline override tri-state. SHIFTED leaves the first page solo and pairs from the
 * second (the common cover-first layout); UNSHIFTED pairs from the first page; DEFAULT defers.
 *
 * Used at two scopes with the same [flagValue] encoding: as a per-manga override in viewer-flag bits
 * 12-13 (the gap between [SpreadVerticalFit] and [SpreadSoloPage]), where DEFAULT inherits the global
 * [ReaderPreferences.defaultSpreadShift]; and as the per-chapter manual override persisted in the
 * chapter `spread_shift` column, where DEFAULT means "no manual shift remembered" (see [rememberedShift]).
 */
enum class SpreadShift(val flagValue: Int) {
    DEFAULT(0x00000000),
    SHIFTED(0x00001000),
    UNSHIFTED(0x00002000),
    ;

    /** The pairing parity this override forces, or null when it defers ([DEFAULT]). */
    fun asShiftOrNull(): Boolean? = when (this) {
        SHIFTED -> true
        UNSHIFTED -> false
        DEFAULT -> null
    }

    companion object {
        const val MASK = 0x00003000

        fun fromPreference(preference: Int?): SpreadShift =
            entries.find { it.flagValue == preference } ?: DEFAULT

        /** Decodes a persisted per-chapter `spread_shift` [flagValue] to the parity it forces, or null. */
        fun rememberedShift(flagValue: Long): Boolean? = fromPreference(flagValue.toInt()).asShiftOrNull()
    }
}
