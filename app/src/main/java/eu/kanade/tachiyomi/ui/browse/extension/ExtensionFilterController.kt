package eu.kanade.tachiyomi.ui.browse.extension

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.ui.setting.SettingsController
import eu.kanade.tachiyomi.util.preference.minusAssign
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.plusAssign
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.LocaleHelper
import uy.kohesive.injekt.injectLazy

class ExtensionFilterController : SettingsController() {

    private val extensionManager: ExtensionManager by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.label_extensions

        val activeLangs = preferences.enabledLanguages().get()

        val availableLangs = extensionManager.availableExtensions.groupBy { it.lang }.keys
            .sortedWith(compareBy({ it !in activeLangs }, { LocaleHelper.getSourceDisplayName(it, context) }))

        availableLangs.forEach {
            switchPreference {
                preferenceScreen.addPreference(this)
                title = LocaleHelper.getSourceDisplayName(it, context)
                isPersistent = false
                isChecked = it in activeLangs

                onChange { newValue ->
                    if (newValue as Boolean) {
                        preferences.enabledLanguages() += it
                    } else {
                        preferences.enabledLanguages() -= it
                    }
                    true
                }
            }
        }
    }
}
