package eu.kanade.tachiyomi.ui.reader.setting

/**
 * How the two halves of a spread are reconciled vertically when they differ in height: one axis
 * with three mutually-exclusive answers, surfaced as the per-manga "Unequal page heights" control.
 * Options are ordered best/most-common first (the global default resolves to [MATCH]):
 *
 * - [MATCH] upsamples the shorter half to the taller one's height, eliminating the difference, the
 *   equal-height result mainstream readers produce by fitting each page to the viewport height.
 * - [CENTER] keeps native sizes and centres the shorter half against the taller.
 * - [TOP] keeps native sizes and aligns the shorter half to the taller's top edge.
 *
 * Packs into `Manga.viewerFlags` as a 2-bit field. Scaling makes alignment moot (equal heights have
 * nothing left to align), which is why the three share one control rather than a toggle plus a
 * conditional alignment picker.
 */
enum class SpreadVerticalFit(val flagValue: Int) {
    DEFAULT(0x00000000),
    MATCH(0x00000400),
    CENTER(0x00000800),
    TOP(0x00000C00),
    ;

    companion object {
        const val MASK = 0x00000C00

        fun fromPreference(preference: Int?): SpreadVerticalFit =
            entries.find { it.flagValue == preference } ?: DEFAULT
    }
}
