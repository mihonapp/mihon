package eu.kanade.tachiyomi.ui.setting.settingssearch

import android.annotation.SuppressLint
import android.content.Context
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionFilterController
import eu.kanade.tachiyomi.ui.browse.source.SourceFilterController
import eu.kanade.tachiyomi.ui.more.AboutController
import eu.kanade.tachiyomi.ui.setting.SettingsAdvancedController
import eu.kanade.tachiyomi.ui.setting.SettingsBackupController
import eu.kanade.tachiyomi.ui.setting.SettingsBrowseController
import eu.kanade.tachiyomi.ui.setting.SettingsController
import eu.kanade.tachiyomi.ui.setting.SettingsDownloadController
import eu.kanade.tachiyomi.ui.setting.SettingsGeneralController
import eu.kanade.tachiyomi.ui.setting.SettingsLibraryController
import eu.kanade.tachiyomi.ui.setting.SettingsParentalControlsController
import eu.kanade.tachiyomi.ui.setting.SettingsReaderController
import eu.kanade.tachiyomi.ui.setting.SettingsSecurityController
import eu.kanade.tachiyomi.ui.setting.SettingsTrackingController
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

object SettingsSearchHelper {
    var prefSearchResultList: MutableList<SettingsSearchResult> = mutableListOf()
        private set

    /**
     * All subclasses of `SettingsController` should be listed here, in order to have their preferences searchable.
     */
    private val settingControllersList: List<KClass<out SettingsController>> = listOf(
        SettingsAdvancedController::class,
        SettingsBackupController::class,
        SettingsBrowseController::class,
        SettingsDownloadController::class,
        SettingsGeneralController::class,
        SettingsLibraryController::class,
        SettingsParentalControlsController::class,
        SettingsReaderController::class,
        SettingsSecurityController::class,
        SettingsTrackingController::class,
        ExtensionFilterController::class,
        SourceFilterController::class,
        AboutController::class
    )

    /**
     * Must be called to populate `prefSearchResultList`
     */
    @SuppressLint("RestrictedApi")
    fun initPreferenceSearchResultCollection(context: Context) {
        val preferenceManager = PreferenceManager(context)

        prefSearchResultList.clear()

        settingControllersList.forEach { kClass ->
            val ctrl = kClass.createInstance()
            val settingsPrefScreen = ctrl.setupPreferenceScreen(preferenceManager.createPreferenceScreen(context))
            val prefCount = settingsPrefScreen.preferenceCount
            for (i in 0 until prefCount) {
                val rootPref = settingsPrefScreen.getPreference(i)
                if (rootPref.title == null) continue // no title, not a preference. (note: only info notes appear to not have titles)
                getSettingSearchResult(ctrl, rootPref, "${settingsPrefScreen.title}")
            }
        }
    }

    /**
     * Extracts the data needed from a `Preference` to create a `SettingsSearchResult`, and then adds it to `prefSearchResultList`
     */
    private fun getSettingSearchResult(ctrl: SettingsController, pref: Preference, breadcrumbs: String = "") {
        when (pref) {
            is PreferenceGroup -> {
                val breadcrumbsStr = breadcrumbs + " > ${pref.title}"

                for (x in 0 until pref.preferenceCount) {
                    val subPref = pref.getPreference(x)
                    getSettingSearchResult(ctrl, subPref, breadcrumbsStr) // recursion
                }
            }
            is PreferenceCategory -> {
                val breadcrumbsStr = breadcrumbs + " > ${pref.title}"

                for (x in 0 until pref.preferenceCount) {
                    val subPref = pref.getPreference(x)
                    getSettingSearchResult(ctrl, subPref, breadcrumbsStr) // recursion
                }
            }
            else -> {
                // Is an actual preference
                val title = pref.title.toString()
                val summary = if (pref.summary != null) pref.summary.toString() else ""
                val breadcrumbsStr = breadcrumbs + " > ${pref.title}"

                prefSearchResultList.add(SettingsSearchResult(title, summary, breadcrumbsStr, ctrl))
            }
        }
    }

    data class SettingsSearchResult(
        val title: String,
        val summary: String,
        val breadcrumb: String,
        val searchController: SettingsController
    )
}
