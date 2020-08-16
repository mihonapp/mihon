package eu.kanade.tachiyomi.ui.setting.settingssearch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.preference.Preference
import androidx.recyclerview.widget.LinearLayoutManager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SettingsSearchControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.setting.SettingsControllerFactory
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.appcompat.QueryTextEvent
import reactivecircus.flowbinding.appcompat.queryTextEvents

/**
 * This controller shows and manages the different search result in settings search.
 * This controller should only handle UI actions, IO actions should be done by [SettingsSearchPresenter]
 * [SettingsSearchAdapter.WhatListener] called when preference is clicked in settings search
 */
open class SettingsSearchController(
    protected val initialQuery: String? = null,
    protected val extensionFilter: String? = null
) : NucleusController<SettingsSearchControllerBinding, SettingsSearchPresenter>(),
    SettingsSearchAdapter.OnTitleClickListener {

    /**
     * Adapter containing search results grouped by lang.
     */
    protected var adapter: SettingsSearchAdapter? = null

    protected var controllers = SettingsControllerFactory.controllers

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
        return SettingsSearchPresenter(initialQuery, extensionFilter)
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

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                searchView.onActionViewExpanded() // Required to show the query in the view
                searchView.setQuery(presenter.query, false)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                return true
            }
        })

        searchView.queryTextEvents()
            .filterIsInstance<QueryTextEvent.QuerySubmitted>()
            .onEach {
                presenter.search(it.queryText.toString())
                searchItem.collapseActionView()
                setTitle() // Update toolbar title
            }
            .launchIn(scope)
    }

    /**
     * Called when the view is created
     *
     * @param view view of controller
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        adapter = SettingsSearchAdapter(this)

        // Create recycler and set adapter.
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.adapter = adapter
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
     * Returns the view holder for the given preference.
     *
     * @param pref used to find holder containing source
     * @return the holder of the preference or null if it's not bound.
     */
    private fun getHolder(pref: Preference): SettingsSearchHolder? {
        val adapter = adapter ?: return null

        adapter.allBoundViewHolders.forEach { holder ->
            val item = adapter.getItem(holder.bindingAdapterPosition)
            if (item != null && pref.key == item.pref.key) {
                return holder as SettingsSearchHolder
            }
        }

        return null
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
    override fun onTitleClick(pref: Preference) {
        // TODO - These asserts will be the death of me, fix them.
        for (ctrl in this!!.controllers!!) {
            if (ctrl.findPreference(pref.key) != null) {
                router.pushController(ctrl.withFadeTransaction())
            }
        }
    }
}
