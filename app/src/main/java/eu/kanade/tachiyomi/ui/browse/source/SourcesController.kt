package eu.kanade.tachiyomi.ui.browse.source

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.browse.SourcesScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.controller.SearchableComposeController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.base.controller.requestPermissionsSafe
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.browse.source.latest.LatestUpdatesController
import eu.kanade.tachiyomi.ui.main.MainActivity
import uy.kohesive.injekt.injectLazy

class SourcesController : SearchableComposeController<SourcesPresenter>() {

    private val preferences: PreferencesHelper by injectLazy()

    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle() = resources?.getString(R.string.label_sources)

    override fun createPresenter() = SourcesPresenter()

    @Composable
    override fun ComposeContent(nestedScrollInterop: NestedScrollConnection) {
        SourcesScreen(
            nestedScrollInterop = nestedScrollInterop,
            presenter = presenter,
            onClickItem = { source ->
                openSource(source, BrowseSourceController(source))
            },
            onClickDisable = { source ->
                presenter.toggleSource(source)
            },
            onClickLatest = { source ->
                openSource(source, LatestUpdatesController(source))
            },
            onClickPin = { source ->
                presenter.togglePin(source)
            },
        )

        LaunchedEffect(Unit) {
            (activity as? MainActivity)?.ready = true
        }
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        requestPermissionsSafe(arrayOf(WRITE_EXTERNAL_STORAGE), 301)
    }

    /**
     * Opens a catalogue with the given controller.
     */
    private fun openSource(source: Source, controller: BrowseSourceController) {
        if (!preferences.incognitoMode().get()) {
            preferences.lastUsedSource().set(source.id)
        }
        parentController!!.router.pushController(controller)
    }

    /**
     * Called when an option menu item has been selected by the user.
     *
     * @param item The selected item.
     * @return True if this event has been consumed, false if it has not.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            // Initialize option to open catalogue settings.
            R.id.action_settings -> {
                parentController!!.router.pushController(SourceFilterController())
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        createOptionsMenu(
            menu,
            inflater,
            R.menu.browse_sources,
            R.id.action_search,
            R.string.action_global_search_hint,
            false, // GlobalSearch handles the searching here
        )
    }

    override fun onSearchViewQueryTextSubmit(query: String?) {
        parentController!!.router.pushController(GlobalSearchController(query))
    }
}
