package eu.kanade.tachiyomi.ui.setting

import android.view.Menu
import android.view.MenuInflater
import androidx.appcompat.widget.SearchView
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.setting.settingssearch.SettingsSearchController
import eu.kanade.tachiyomi.ui.setting.settingssearch.SettingsSearchHelper
import eu.kanade.tachiyomi.util.preference.iconRes
import eu.kanade.tachiyomi.util.preference.iconTint
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.getResourceColor
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.appcompat.QueryTextEvent
import reactivecircus.flowbinding.appcompat.queryTextEvents

class SettingsMainController : SettingsController() {

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        SettingsSearchHelper.initPreferenceSearchResultCollection(context, preferenceManager)

        titleRes = R.string.label_settings

        val tintColor = context.getResourceColor(R.attr.colorAccent)

        preference {
            iconRes = R.drawable.ic_tune_24dp
            iconTint = tintColor
            titleRes = R.string.pref_category_general
            onClick { navigateTo(SettingsGeneralController()) }
        }
        preference {
            iconRes = R.drawable.ic_collections_bookmark_outline_24dp
            iconTint = tintColor
            titleRes = R.string.pref_category_library
            onClick { navigateTo(SettingsLibraryController()) }
        }
        preference {
            iconRes = R.drawable.ic_chrome_reader_mode_24dp
            iconTint = tintColor
            titleRes = R.string.pref_category_reader
            onClick { navigateTo(SettingsReaderController()) }
        }
        preference {
            iconRes = R.drawable.ic_get_app_24dp
            iconTint = tintColor
            titleRes = R.string.pref_category_downloads
            onClick { navigateTo(SettingsDownloadController()) }
        }
        preference {
            iconRes = R.drawable.ic_sync_24dp
            iconTint = tintColor
            titleRes = R.string.pref_category_tracking
            onClick { navigateTo(SettingsTrackingController()) }
        }
        preference {
            iconRes = R.drawable.ic_explore_outline_24dp
            iconTint = tintColor
            titleRes = R.string.browse
            onClick { navigateTo(SettingsBrowseController()) }
        }
        preference {
            iconRes = R.drawable.ic_backup_24dp
            iconTint = tintColor
            titleRes = R.string.backup
            onClick { navigateTo(SettingsBackupController()) }
        }
        preference {
            iconRes = R.drawable.ic_security_24dp
            iconTint = tintColor
            titleRes = R.string.pref_category_security
            onClick { navigateTo(SettingsSecurityController()) }
        }
        preference {
            iconRes = R.drawable.ic_outline_people_alt_24dp
            iconTint = tintColor
            titleRes = R.string.pref_category_parental_controls
            onClick { navigateTo(SettingsParentalControlsController()) }
        }
        preference {
            iconRes = R.drawable.ic_code_24dp
            iconTint = tintColor
            titleRes = R.string.pref_category_advanced
            onClick { navigateTo(SettingsAdvancedController()) }
        }
    }

    private fun navigateTo(controller: SettingsController) {
        router.pushController(controller.withFadeTransaction())
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate menu
        inflater.inflate(R.menu.settings_main, menu)

        // Initialize search option.
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.maxWidth = Int.MAX_VALUE

        // Change hint to show global search.
        searchView.queryHint = applicationContext?.getString(R.string.action_global_search_hint)

        // Create query listener which opens the global search view.
        searchView.queryTextEvents()
            .filterIsInstance<QueryTextEvent.QueryChanged>()
            .onEach {
                performSettingsSearch(it.queryText.toString())
            }
            .launchIn(scope)
    }

    private fun performSettingsSearch(query: String) {
        router.pushController(
            SettingsSearchController(query).withFadeTransaction()
        )
    }
}
