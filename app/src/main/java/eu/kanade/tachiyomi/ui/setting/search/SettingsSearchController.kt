package eu.kanade.tachiyomi.ui.setting.search

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import eu.kanade.presentation.more.settings.SettingsSearchScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.ComposeController
import eu.kanade.tachiyomi.ui.base.controller.pushController

class SettingsSearchController : ComposeController<SettingsSearchPresenter>() {

    private lateinit var searchView: SearchView

    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle() = presenter.query

    override fun createPresenter() = SettingsSearchPresenter()

    @Composable
    override fun ComposeContent(nestedScrollInterop: NestedScrollConnection) {
        SettingsSearchScreen(
            nestedScroll = nestedScrollInterop,
            presenter = presenter,
            onClickResult = { controller ->
                searchView.query.let {
                    presenter.setLastSearchQuerySearchSettings(it.toString())
                }
                router.pushController(controller)
            },
        )
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.settings_main, menu)

        // Initialize search menu
        val searchItem = menu.findItem(R.id.action_search)
        searchView = searchItem.actionView as SearchView
        searchView.maxWidth = Int.MAX_VALUE
        searchView.queryHint = applicationContext?.getString(R.string.action_search_settings)

        searchItem.expandActionView()

        searchItem.setOnActionExpandListener(
            object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    router.popCurrentController()
                    return false
                }
            },
        )

        searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    presenter.searchSettings(query)
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    presenter.searchSettings(newText)
                    return false
                }
            },
        )

        searchView.setQuery(presenter.getLastSearchQuerySearchSettings(), true)
    }
}
