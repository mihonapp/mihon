package eu.kanade.tachiyomi.ui.library

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.ActionMode
import androidx.core.view.isVisible
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.fredporciuncula.flow.preferences.Preference
import com.google.android.material.tabs.TabLayout
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.category.model.toDbCategory
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.toDomainManga
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.LibraryControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.ui.base.controller.SearchableNucleusController
import eu.kanade.tachiyomi.ui.base.controller.TabbedController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.ActionModeWithToolbar
import eu.kanade.tachiyomi.widget.EmptyView
import eu.kanade.tachiyomi.widget.materialdialogs.QuadStateTextView
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.viewpager.pageSelections
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class LibraryController(
    bundle: Bundle? = null,
    private val preferences: PreferencesHelper = Injekt.get(),
) : SearchableNucleusController<LibraryControllerBinding, LibraryPresenter>(bundle),
    RootController,
    TabbedController,
    ActionModeWithToolbar.Callback,
    ChangeMangaCategoriesDialog.Listener,
    DeleteLibraryMangasDialog.Listener {

    /**
     * Position of the active category.
     */
    private var activeCategory: Int = preferences.lastUsedCategory().get()

    /**
     * Action mode for selections.
     */
    private var actionMode: ActionModeWithToolbar? = null

    /**
     * Relay to notify the library's viewpager for updates.
     */
    val libraryMangaRelay: BehaviorRelay<LibraryMangaEvent> = BehaviorRelay.create()

    /**
     * Adapter of the view pager.
     */
    private var adapter: LibraryAdapter? = null

    /**
     * Sheet containing filter/sort/display items.
     */
    private var settingsSheet: LibrarySettingsSheet? = null

    private var tabsVisibilityRelay: BehaviorRelay<Boolean> = BehaviorRelay.create(false)

    private var mangaCountVisibilityRelay: BehaviorRelay<Boolean> = BehaviorRelay.create(false)

    private var tabsVisibilitySubscription: Subscription? = null

    private var mangaCountVisibilitySubscription: Subscription? = null

    init {
        setHasOptionsMenu(true)
        retainViewMode = RetainViewMode.RETAIN_DETACH
    }

    private var currentTitle: String? = null
        set(value) {
            if (field != value) {
                field = value
                setTitle()
            }
        }

    override fun getTitle(): String? {
        return currentTitle ?: resources?.getString(R.string.label_library)
    }

    private fun updateTitle() {
        val showCategoryTabs = preferences.categoryTabs().get()
        val currentCategory = adapter?.categories?.getOrNull(binding.libraryPager.currentItem)

        var title = if (showCategoryTabs) {
            resources?.getString(R.string.label_library)
        } else {
            currentCategory?.name
        }

        if (preferences.categoryNumberOfItems().get() && libraryMangaRelay.hasValue()) {
            libraryMangaRelay.value.mangas.let { mangaMap ->
                if (!showCategoryTabs || adapter?.categories?.size == 1) {
                    title += " (${mangaMap[currentCategory?.id]?.size ?: 0})"
                }
            }
        }

        currentTitle = title
    }

    override fun createPresenter(): LibraryPresenter {
        return LibraryPresenter()
    }

    override fun createBinding(inflater: LayoutInflater) = LibraryControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        adapter = LibraryAdapter(
            presenter = presenter,
            onClickManga = {
                openManga(it.id!!)
            },
        )

        getColumnsPreferenceForCurrentOrientation()
            .asFlow()
            .onEach { presenter.columns = it }
            .launchIn(viewScope)

        binding.libraryPager.adapter = adapter
        binding.libraryPager.pageSelections()
            .drop(1)
            .onEach {
                preferences.lastUsedCategory().set(it)
                activeCategory = it
                updateTitle()
            }
            .launchIn(viewScope)

        if (adapter!!.categories.isNotEmpty()) {
            createActionModeIfNeeded()
        }

        settingsSheet = LibrarySettingsSheet(router) { group ->
            when (group) {
                is LibrarySettingsSheet.Filter.FilterGroup -> onFilterChanged()
                is LibrarySettingsSheet.Sort.SortGroup -> onSortChanged()
                is LibrarySettingsSheet.Display.DisplayGroup -> {
                    val delay = if (preferences.categorizedDisplaySettings().get()) 125L else 0L

                    Observable.timer(delay, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                        .subscribe {
                            reattachAdapter()
                        }
                }
                is LibrarySettingsSheet.Display.BadgeGroup -> onBadgeSettingChanged()
                is LibrarySettingsSheet.Display.TabsGroup -> onTabsSettingsChanged()
            }
        }

        binding.btnGlobalSearch.clicks()
            .onEach {
                router.pushController(GlobalSearchController(presenter.query))
            }
            .launchIn(viewScope)
    }

    private fun getColumnsPreferenceForCurrentOrientation(): Preference<Int> {
        return if (resources?.configuration?.orientation == Configuration.ORIENTATION_PORTRAIT) {
            preferences.portraitColumns()
        } else {
            preferences.landscapeColumns()
        }
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (type.isEnter) {
            (activity as? MainActivity)?.binding?.tabs?.setupWithViewPager(binding.libraryPager)
            presenter.subscribeLibrary()
        }
    }

    override fun onDestroyView(view: View) {
        destroyActionModeIfNeeded()
        adapter = null
        settingsSheet?.sheetScope?.cancel()
        settingsSheet = null
        tabsVisibilitySubscription?.unsubscribe()
        tabsVisibilitySubscription = null
        super.onDestroyView(view)
    }

    override fun configureTabs(tabs: TabLayout): Boolean {
        with(tabs) {
            isVisible = false
            tabGravity = TabLayout.GRAVITY_START
            tabMode = TabLayout.MODE_SCROLLABLE
        }
        tabsVisibilitySubscription?.unsubscribe()
        tabsVisibilitySubscription = tabsVisibilityRelay.subscribe { visible ->
            tabs.isVisible = visible
        }
        mangaCountVisibilitySubscription?.unsubscribe()
        mangaCountVisibilitySubscription = mangaCountVisibilityRelay.subscribe {
            adapter?.notifyDataSetChanged()
        }

        return false
    }

    override fun cleanupTabs(tabs: TabLayout) {
        tabsVisibilitySubscription?.unsubscribe()
        tabsVisibilitySubscription = null
    }

    fun showSettingsSheet() {
        if (adapter?.categories?.isNotEmpty() == true) {
            adapter?.categories?.get(binding.libraryPager.currentItem)?.let { category ->
                settingsSheet?.show(category.toDbCategory())
            }
        } else {
            settingsSheet?.show()
        }
    }

    fun onNextLibraryUpdate(categories: List<Category>, mangaMap: LibraryMap) {
        val view = view ?: return
        val adapter = adapter ?: return

        // Show empty view if needed
        if (mangaMap.isNotEmpty()) {
            binding.emptyView.hide()
        } else {
            binding.emptyView.show(
                R.string.information_empty_library,
                listOf(
                    EmptyView.Action(R.string.getting_started_guide, R.drawable.ic_help_24dp) {
                        activity?.openInBrowser("https://tachiyomi.org/help/guides/getting-started")
                    },
                ),
            )
            (activity as? MainActivity)?.ready = true
        }

        // Get the current active category.
        val activeCat = if (adapter.categories.isNotEmpty()) {
            binding.libraryPager.currentItem
        } else {
            activeCategory
        }

        // Set the categories
        adapter.updateCategories(categories.map { it to (mangaMap[it.id]?.size ?: 0) })

        // Restore active category.
        binding.libraryPager.setCurrentItem(activeCat, false)

        // Trigger display of tabs
        onTabsSettingsChanged(firstLaunch = true)

        // Delay the scroll position to allow the view to be properly measured.
        view.post {
            if (isAttached) {
                (activity as? MainActivity)?.binding?.tabs?.setScrollPosition(binding.libraryPager.currentItem, 0f, true)
            }
        }

        presenter.loadedManga.clear()
        mangaMap.forEach {
            presenter.loadedManga[it.key] = it.value
        }
        presenter.loadedMangaFlow.value = presenter.loadedManga

        // Send the manga map to child fragments after the adapter is updated.
        libraryMangaRelay.call(LibraryMangaEvent(mangaMap))

        // Finally update the title
        updateTitle()
    }

    private fun onFilterChanged() {
        presenter.requestFilterUpdate()
        activity?.invalidateOptionsMenu()
    }

    private fun onBadgeSettingChanged() {
        presenter.requestBadgesUpdate()
    }

    private fun onTabsSettingsChanged(firstLaunch: Boolean = false) {
        if (!firstLaunch) {
            mangaCountVisibilityRelay.call(preferences.categoryNumberOfItems().get())
        }
        tabsVisibilityRelay.call(preferences.categoryTabs().get() && (adapter?.categories?.size ?: 0) > 1)
        updateTitle()
    }

    /**
     * Called when the sorting mode is changed.
     */
    private fun onSortChanged() {
        presenter.requestSortUpdate()
    }

    /**
     * Reattaches the adapter to the view pager to recreate fragments
     */
    private fun reattachAdapter() {
        val adapter = adapter ?: return

        val position = binding.libraryPager.currentItem

        adapter.recycle = false
        binding.libraryPager.adapter = adapter
        binding.libraryPager.currentItem = position
        adapter.recycle = true
    }

    /**
     * Creates the action mode if it's not created already.
     */
    fun createActionModeIfNeeded() {
        val activity = activity
        if (actionMode == null && activity is MainActivity) {
            actionMode = activity.startActionModeAndToolbar(this)
            activity.showBottomNav(false)
        }
    }

    /**
     * Destroys the action mode.
     */
    private fun destroyActionModeIfNeeded() {
        actionMode?.finish()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        createOptionsMenu(menu, inflater, R.menu.library, R.id.action_search)
        // Mutate the filter icon because it needs to be tinted and the resource is shared.
        menu.findItem(R.id.action_filter).icon?.mutate()
    }

    fun search(query: String) {
        presenter.query = query
    }

    private fun performSearch() {
        if (presenter.query.isNotEmpty()) {
            binding.btnGlobalSearch.isVisible = true
            binding.btnGlobalSearch.text =
                resources?.getString(R.string.action_global_search_query, presenter.query)
        } else {
            binding.btnGlobalSearch.isVisible = false
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val settingsSheet = settingsSheet ?: return

        val filterItem = menu.findItem(R.id.action_filter)

        // Tint icon if there's a filter active
        if (settingsSheet.filters.hasActiveFilters()) {
            val filterColor = activity!!.getResourceColor(R.attr.colorFilterActive)
            filterItem.icon?.setTint(filterColor)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> expandActionViewFromInteraction = true
            R.id.action_filter -> showSettingsSheet()
            R.id.action_update_library -> {
                activity?.let {
                    if (LibraryUpdateService.start(it)) {
                        it.toast(R.string.updating_library)
                    }
                }
            }
        }

        return super.onOptionsItemSelected(item)
    }

    /**
     * Invalidates the action mode, forcing it to refresh its content.
     */
    fun invalidateActionMode() {
        actionMode?.invalidate()
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.generic_selection, menu)
        return true
    }

    override fun onCreateActionToolbar(menuInflater: MenuInflater, menu: Menu) {
        menuInflater.inflate(R.menu.library_selection, menu)
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val count = presenter.selection.size
        if (count == 0) {
            // Destroy action mode if there are no items selected.
            destroyActionModeIfNeeded()
        } else {
            mode.title = count.toString()
        }
        return true
    }

    override fun onPrepareActionToolbar(toolbar: ActionModeWithToolbar, menu: Menu) {
        if (presenter.hasSelection().not()) return
        toolbar.findToolbarItem(R.id.action_download_unread)?.isVisible =
            presenter.selection.any { presenter.loadedManga.values.any { it.any { it.isLocal } } }
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_move_to_category -> showMangaCategoriesDialog()
            R.id.action_download_unread -> downloadUnreadChapters()
            R.id.action_mark_as_read -> markReadStatus(true)
            R.id.action_mark_as_unread -> markReadStatus(false)
            R.id.action_delete -> showDeleteMangaDialog()
            R.id.action_select_all -> selectAllCategoryManga()
            R.id.action_select_inverse -> selectInverseCategoryManga()
            else -> return false
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        // Clear all the manga selections and notify child views.
        presenter.clearSelection()

        (activity as? MainActivity)?.showBottomNav(true)

        actionMode = null
    }

    private fun openManga(mangaId: Long) {
        // Notify the presenter a manga is being opened.
        presenter.onOpenManga()

        router.pushController(MangaController(mangaId))
    }

    /**
     * Clear all of the manga currently selected, and
     * invalidate the action mode to revert the top toolbar
     */
    fun clearSelection() {
        presenter.clearSelection()
        invalidateActionMode()
    }

    /**
     * Move the selected manga to a list of categories.
     */
    private fun showMangaCategoriesDialog() {
        viewScope.launchIO {
            // Create a copy of selected manga
            val mangas = presenter.selection.toList()

            // Hide the default category because it has a different behavior than the ones from db.
            val categories = presenter.categories.filter { it.id != 0L }

            // Get indexes of the common categories to preselect.
            val common = presenter.getCommonCategories(mangas.mapNotNull { it.toDomainManga() })
            // Get indexes of the mix categories to preselect.
            val mix = presenter.getMixCategories(mangas.mapNotNull { it.toDomainManga() })
            val preselected = categories.map {
                when (it) {
                    in common -> QuadStateTextView.State.CHECKED.ordinal
                    in mix -> QuadStateTextView.State.INDETERMINATE.ordinal
                    else -> QuadStateTextView.State.UNCHECKED.ordinal
                }
            }.toTypedArray()
            launchUI {
                ChangeMangaCategoriesDialog(this@LibraryController, mangas.mapNotNull { it.toDomainManga() }, categories, preselected)
                    .showDialog(router)
            }
        }
    }

    private fun downloadUnreadChapters() {
        val mangas = presenter.selection.toList()
        presenter.downloadUnreadChapters(mangas.mapNotNull { it.toDomainManga() })
        destroyActionModeIfNeeded()
    }

    private fun markReadStatus(read: Boolean) {
        val mangas = presenter.selection.toList()
        presenter.markReadStatus(mangas.mapNotNull { it.toDomainManga() }, read)
        destroyActionModeIfNeeded()
    }

    private fun showDeleteMangaDialog() {
        val mangas = presenter.selection.toList()
        DeleteLibraryMangasDialog(this, mangas.mapNotNull { it.toDomainManga() }).showDialog(router)
    }

    override fun updateCategoriesForMangas(mangas: List<Manga>, addCategories: List<Category>, removeCategories: List<Category>) {
        presenter.setMangaCategories(mangas, addCategories, removeCategories)
        destroyActionModeIfNeeded()
    }

    override fun deleteMangas(mangas: List<Manga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        presenter.removeMangas(mangas.map { it.toDbManga() }, deleteFromLibrary, deleteChapters)
        destroyActionModeIfNeeded()
    }

    private fun selectAllCategoryManga() {
        presenter.selectAll(binding.libraryPager.currentItem)
    }

    private fun selectInverseCategoryManga() {
        presenter.invertSelection(binding.libraryPager.currentItem)
    }

    override fun onSearchViewQueryTextChange(newText: String?) {
        // Ignore events if this controller isn't at the top to avoid query being reset
        if (router.backstack.lastOrNull()?.controller == this) {
            presenter.query = newText ?: ""
            presenter.searchQuery = newText ?: ""
            performSearch()
        }
    }
}
