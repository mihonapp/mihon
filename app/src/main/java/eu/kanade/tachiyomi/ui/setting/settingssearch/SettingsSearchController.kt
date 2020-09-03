package eu.kanade.tachiyomi.ui.setting.settingssearch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SettingsSearchControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.setting.SettingsController

/**
 * This controller shows and manages the different search result in settings search.
 * [SettingsSearchAdapter.OnTitleClickListener] called when preference is clicked in settings search
 */
class SettingsSearchController :
    NucleusController<SettingsSearchControllerBinding, SettingsSearchPresenter>(),
    SettingsSearchAdapter.OnTitleClickListener {

    /**
     * Adapter containing search results grouped by lang.
     */
    protected var adapter: SettingsSearchAdapter? = null

    init {
        setHasOptionsMenu(true)
    }

    /**
     * Initiate the view with [R.layout.settings_search_controller].
     *
     * @param inflater used to load the layout xml.
     * @param container containing parent views.
     * @return inflated view
     */
    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        binding = SettingsSearchControllerBinding.inflate(inflater)
        return binding.root
    }

    override fun getTitle(): String? {
        return presenter.query
    }

    /**
     * Create the [SettingsSearchPresenter] used in controller.
     *
     * @return instance of [SettingsSearchPresenter]
     */
    override fun createPresenter(): SettingsSearchPresenter {
        return SettingsSearchPresenter()
    }

    /**
     * Adds items to the options menu.
     *
     * @param menu menu containing options.
     * @param inflater used to load the menu xml.
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate menu.
        inflater.inflate(R.menu.settings_main, menu)

        // Initialize search menu
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.maxWidth = Int.MAX_VALUE

        // Change hint to show "search settings."
        searchView.queryHint = applicationContext?.getString(R.string.action_search_settings)

        searchItem.expandActionView()
        setItems(getResultSet())

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                searchView.onActionViewExpanded() // Required to show the query in the view
                setItems(getResultSet())
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                return true
            }
        })

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchItem.collapseActionView()
                setTitle(query) // Update toolbar title
                setItems(getResultSet(query))
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                setItems(getResultSet(newText))
                return false
            }
        })
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        adapter = SettingsSearchAdapter(this)

        // Create recycler and set adapter.
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.adapter = adapter

        // load all search results
        SettingsSearchHelper.initPreferenceSearchResultCollection(presenter.preferences.context)
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
    }

    override fun onSaveViewState(view: View, outState: Bundle) {
        super.onSaveViewState(view, outState)
        adapter?.onSaveInstanceState(outState)
    }

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        adapter?.onRestoreInstanceState(savedViewState)
    }

    /**
     * returns a list of `SettingsSearchItem` to be shown as search results
     */
    fun getResultSet(query: String? = null): List<SettingsSearchItem> {
        val list = mutableListOf<SettingsSearchItem>()

        if (!query.isNullOrBlank()) {
            SettingsSearchHelper.getFilteredResults(query)
                .forEach {
                    list.add(SettingsSearchItem(it, null))
                }
        }

        return list
    }

    /**
     * Add search result to adapter.
     *
     * @param searchResult result of search.
     */
    fun setItems(searchResult: List<SettingsSearchItem>) {
        adapter?.updateDataSet(searchResult)
    }

    /**
     * Opens a catalogue with the given search.
     */
    override fun onTitleClick(ctrl: SettingsController) {
        router.pushController(ctrl.withFadeTransaction())
    }
}
