package eu.kanade.tachiyomi.ui.reader.setting

/**
 * Settings for the reader as a whole and for every viewer alike.
 */
data class GeneralSettings(
    val theme: ReaderPreferences.ReaderTheme,
    val keepScreenOn: Boolean,
    val grayscale: Boolean,
    val invertedColors: Boolean,
    val fullscreen: Boolean,
    val drawUnderCutout: Boolean,
    val longTapEnabled: Boolean,
    val usePageTransitions: Boolean,
    val doubleTapAnimDuration: Int,
    val volumeKeysEnabled: Boolean,
    val volumeKeysInverted: Boolean,
    val alwaysShowChapterTransition: Boolean,
    val navigationOverlayOnStart: Boolean,
) {
    val automaticBackground: Boolean
        get() = theme == ReaderPreferences.ReaderTheme.AUTOMATIC
}
