package eu.kanade.tachiyomi.data.preference

import android.content.Context
import android.os.Build
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.core.preference.getEnum
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.data.preference.PreferenceValues as Values

class PreferencesHelper(
    val context: Context,
    private val preferenceStore: PreferenceStore,
) {

    fun confirmExit() = preferenceStore.getBoolean("pref_confirm_exit", false)

    fun sideNavIconAlignment() = preferenceStore.getInt("pref_side_nav_icon_alignment", 0)

    fun themeMode() = preferenceStore.getEnum(
        "pref_theme_mode_key",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { Values.ThemeMode.system } else { Values.ThemeMode.light },
    )

    fun appTheme() = preferenceStore.getEnum(
        "pref_app_theme",
        if (DeviceUtil.isDynamicColorAvailable) { Values.AppTheme.MONET } else { Values.AppTheme.DEFAULT },
    )

    fun themeDarkAmoled() = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    fun lastVersionCode() = preferenceStore.getInt("last_version_code", 0)

    fun relativeTime() = preferenceStore.getInt("relative_time", 7)

    fun dateFormat(format: String = preferenceStore.getString(Keys.dateFormat, "").get()): DateFormat = when (format) {
        "" -> DateFormat.getDateInstance(DateFormat.SHORT)
        else -> SimpleDateFormat(format, Locale.getDefault())
    }

    fun downloadedOnly() = preferenceStore.getBoolean("pref_downloaded_only", false)

    fun automaticExtUpdates() = preferenceStore.getBoolean("automatic_ext_updates", true)

    fun lastAppCheck() = preferenceStore.getLong("last_app_check", 0)
    fun lastExtCheck() = preferenceStore.getLong("last_ext_check", 0)

    fun migrateFlags() = preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)

    fun incognitoMode() = preferenceStore.getBoolean("incognito_mode", false)

    fun tabletUiMode() = preferenceStore.getEnum("tablet_ui_mode", Values.TabletUiMode.AUTOMATIC)

    fun extensionInstaller() = preferenceStore.getEnum(
        "extension_installer",
        if (DeviceUtil.isMiui) Values.ExtensionInstaller.LEGACY else Values.ExtensionInstaller.PACKAGEINSTALLER,
    )

    fun autoClearChapterCache() = preferenceStore.getBoolean("auto_clear_chapter_cache", false)
}
