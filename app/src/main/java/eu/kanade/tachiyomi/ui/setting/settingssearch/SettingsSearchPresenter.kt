package eu.kanade.tachiyomi.ui.setting.settingssearch

import android.os.Bundle
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import rx.Subscription
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

/**
 * Presenter of [SettingsSearchController]
 * Function calls should be done from here. UI calls should be done from the controller.
 *
 * @param sourceManager manages the different sources.
 * @param db manages the database calls.
 * @param preferences manages the preference calls.
 */
open class SettingsSearchPresenter(
    val initialQuery: String? = "",
    val initialExtensionFilter: String? = null,
    val sourceManager: SourceManager = Injekt.get(),
    val db: DatabaseHelper = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get()
) : BasePresenter<SettingsSearchController>() {

    /**
     * Query from the view.
     */
    var query = ""
        private set

    /**
     * Fetches the different sources by user settings.
     */
    private var fetchSourcesSubscription: Subscription? = null

    /**
     * Subscription for fetching images of manga.
     */
    private var fetchImageSubscription: Subscription? = null

    private val extensionManager by injectLazy<ExtensionManager>()

    private var extensionFilter: String? = null

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        extensionFilter = savedState?.getString(SettingsSearchPresenter::extensionFilter.name)
            ?: initialExtensionFilter
    }

    override fun onDestroy() {
        fetchSourcesSubscription?.unsubscribe()
        fetchImageSubscription?.unsubscribe()
        super.onDestroy()
    }

    override fun onSave(state: Bundle) {
        state.putString(BrowseSourcePresenter::query.name, query)
        state.putString(SettingsSearchPresenter::extensionFilter.name, extensionFilter)
        super.onSave(state)
    }
}
