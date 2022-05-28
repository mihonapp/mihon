package eu.kanade.tachiyomi.data.preference

import eu.kanade.tachiyomi.R

const val DEVICE_ONLY_ON_WIFI = "wifi"
const val DEVICE_NETWORK_NOT_METERED = "network_not_metered"
const val DEVICE_CHARGING = "ac"
const val DEVICE_BATTERY_NOT_LOW = "battery_not_low"

const val MANGA_NON_COMPLETED = "manga_ongoing"
const val MANGA_HAS_UNREAD = "manga_fully_read"
const val MANGA_NON_READ = "manga_started"

/**
 * This class stores the values for the preferences in the application.
 */
object PreferenceValues {

    /* ktlint-disable experimental:enum-entry-name-case */

    // Keys are lowercase to match legacy string values
    enum class ThemeMode {
        light,
        dark,
        system,
    }

    /* ktlint-enable experimental:enum-entry-name-case */

    enum class AppTheme(val titleResId: Int?) {
        DEFAULT(R.string.label_default),
        MONET(R.string.theme_monet),
        MIDNIGHT_DUSK(R.string.theme_midnightdusk),
        STRAWBERRY_DAIQUIRI(R.string.theme_strawberrydaiquiri),
        YOTSUBA(R.string.theme_yotsuba),
        TAKO(R.string.theme_tako),
        GREEN_APPLE(R.string.theme_greenapple),
        TEALTURQUOISE(R.string.theme_tealturquoise),
        YINYANG(R.string.theme_yinyang),

        // Deprecated
        DARK_BLUE(null),
        HOT_PINK(null),
        BLUE(null),
    }

    enum class TappingInvertMode(val shouldInvertHorizontal: Boolean = false, val shouldInvertVertical: Boolean = false) {
        NONE,
        HORIZONTAL(shouldInvertHorizontal = true),
        VERTICAL(shouldInvertVertical = true),
        BOTH(shouldInvertHorizontal = true, shouldInvertVertical = true),
    }

    enum class ReaderHideThreshold(val threshold: Int) {
        HIGHEST(5),
        HIGH(13),
        LOW(31),
        LOWEST(47),
    }

    enum class TabletUiMode(val titleResId: Int) {
        AUTOMATIC(R.string.automatic_background),
        ALWAYS(R.string.lock_always),
        LANDSCAPE(R.string.landscape),
        NEVER(R.string.lock_never),
    }

    enum class ExtensionInstaller(val titleResId: Int) {
        LEGACY(R.string.ext_installer_legacy),
        PACKAGEINSTALLER(R.string.ext_installer_packageinstaller),
        SHIZUKU(R.string.ext_installer_shizuku),
    }

    enum class SecureScreenMode(val titleResId: Int) {
        ALWAYS(R.string.lock_always),
        INCOGNITO(R.string.pref_incognito_mode),
        NEVER(R.string.lock_never),
    }
}
