package eu.kanade.domain.base

import android.content.Context
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.core.preference.getEnum
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import eu.kanade.tachiyomi.util.system.isReleaseBuildType

class BasePreferences(
    val context: Context,
    private val preferenceStore: PreferenceStore,
) {

    fun confirmExit() = preferenceStore.getBoolean("pref_confirm_exit", false)

    fun downloadedOnly() = preferenceStore.getBoolean("pref_downloaded_only", false)

    fun incognitoMode() = preferenceStore.getBoolean("incognito_mode", false)

    fun automaticExtUpdates() = preferenceStore.getBoolean("automatic_ext_updates", true)

    fun extensionInstaller() = preferenceStore.getEnum(
        "extension_installer",
        if (DeviceUtil.isMiui) PreferenceValues.ExtensionInstaller.LEGACY else PreferenceValues.ExtensionInstaller.PACKAGEINSTALLER,
    )

    fun acraEnabled() = preferenceStore.getBoolean("acra.enable", isPreviewBuildType || isReleaseBuildType)
}
