package eu.kanade.tachiyomi.ui.library

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.base.adapter.FlexibleViewHolder
import eu.kanade.tachiyomi.ui.base.fragment.BaseFragment
import eu.kanade.tachiyomi.ui.manga.MangaActivity
import eu.kanade.tachiyomi.util.inflate
import eu.kanade.tachiyomi.util.toast
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import kotlinx.android.synthetic.main.fragment_library_category.*
import rx.Subscription
import uy.kohesive.injekt.injectLazy

/**
 * Fragment containing the library manga for a certain category.
 * Uses R.layout.fragment_library_category.
 */
class LibraryCategoryFragment : BaseFragment(), FlexibleViewHolder.OnListItemClickListener {

    /**
     * Preferences.
     */
    val preferences: PreferencesHelper by injectLazy()

    /**
     * Adapter to hold the manga in this category.
     */
    lateinit var adapter: LibraryCategoryAdapter
        private set

    /**
     * Position in the adapter from [LibraryAdapter].
     */
    private var position: Int = 0

    /**
     * Subscription for the library manga.
     */
    private var libraryMangaSubscription: Subscription? = null

    /**
     * Subscription of the library search.
     */
    private var searchSubscription: Subscription? = null

    companion object {
        /**
         * Key to save and restore [position] from a [Bundle].
         */
        const val POSITION_KEY = "position_key"

        /**
         * Creates a new instance of this class.
         *
         * @param position the position in the adapter from [LibraryAdapter].
         * @return a new instance of [LibraryCategoryFragment].
         */
        fun newInstance(position: Int): LibraryCategoryFragment {
            val fragment = LibraryCategoryFragment()
            fragment.position = position

            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_library_category, container, false)
    }

    override fun onViewCreated(view: View, savedState: Bundle?) {
        adapter = LibraryCategoryAdapter(this)

        val recycler = if (preferences.libraryAsList().getOrDefault()) {
            (swipe_refresh.inflate(R.layout.library_list_recycler) as RecyclerView).apply {
                layoutManager = LinearLayoutManager(context)
            }
        } else {
            (swipe_refresh.inflate(R.layout.library_grid_recycler) as AutofitRecyclerView).apply {
                spanCount = libraryFragment.mangaPerRow
            }
        }

        // This crashes when opening a manga after changing categories, but then viewholders aren't
        // recycled between pages. It may be fixed if this fragment is replaced with a custom view.
        //(recycler.layoutManager as LinearLayoutManager).recycleChildrenOnDetach = true
        //recycler.recycledViewPool = libraryFragment.pool
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter
        swipe_refresh.addView(recycler)

        if (libraryFragment.actionMode != null) {
            setSelectionMode(FlexibleAdapter.MODE_MULTI)
        }

        searchSubscription = libraryPresenter.searchSubject.subscribe { text ->
            adapter.searchText = text
            adapter.updateDataSet()
        }

        if (savedState != null) {
            position = savedState.getInt(POSITION_KEY)
            adapter.onRestoreInstanceState(savedState)

            if (adapter.mode == FlexibleAdapter.MODE_SINGLE) {
                adapter.clearSelection()
            }
        }

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recycler: RecyclerView, newState: Int) {
                // Disable swipe refresh when view is not at the top
                val firstPos = (recycler.layoutManager as LinearLayoutManager)
                        .findFirstCompletelyVisibleItemPosition()
                swipe_refresh.isEnabled = firstPos == 0
            }
        })

        // Double the distance required to trigger sync
        swipe_refresh.setDistanceToTriggerSync((2 * 64 * resources.displayMetrics.density).toInt())
        swipe_refresh.setOnRefreshListener {
            if (!LibraryUpdateService.isRunning(context)) {
                libraryPresenter.categories.getOrNull(position)?.let {
                    LibraryUpdateService.start(context, true, it)
                    context.toast(R.string.updating_category)
                }
            }
            // It can be a very long operation, so we disable swipe refresh and show a toast.
            swipe_refresh.isRefreshing = false
        }
    }

    override fun onDestroyView() {
        searchSubscription?.unsubscribe()
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        libraryMangaSubscription = libraryPresenter.libraryMangaSubject
                .subscribe { onNextLibraryManga(it) }
    }

    override fun onPause() {
        libraryMangaSubscription?.unsubscribe()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(POSITION_KEY, position)
        adapter.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    /**
     * Subscribe to [LibraryMangaEvent]. When an event is received, it updates the content of the
     * adapter.
     *
     * @param event the event received.
     */
    fun onNextLibraryManga(event: LibraryMangaEvent) {
        // Get the categories from the parent fragment.
        val categories = libraryFragment.adapter.categories ?: return

        // When a category is deleted, the index can be greater than the number of categories.
        if (position >= categories.size) return

        // Get the manga list for this category.
        val mangaForCategory = event.getMangaForCategory(categories[position]) ?: emptyList()

        // Update the category with its manga.
        adapter.setItems(mangaForCategory)
    }

    /**
     * Called when a manga is clicked.
     *
     * @param position the position of the element clicked.
     * @return true if the item should be selected, false otherwise.
     */
    override fun onListItemClick(position: Int): Boolean {
        // If the action mode is created and the position is valid, toggle the selection.
        val item = adapter.getItem(position) ?: return false
        if (libraryFragment.actionMode != null) {
            toggleSelection(position)
            return true
        } else {
            openManga(item)
            return false
        }
    }

    /**
     * Called when a manga is long clicked.
     *
     * @param position the position of the element clicked.
     */
    override fun onListItemLongClick(position: Int) {
        libraryFragment.createActionModeIfNeeded()
        toggleSelection(position)
    }

    /**
     * Opens a manga.
     *
     * @param manga the manga to open.
     */
    private fun openManga(manga: Manga) {
        // Notify the presenter a manga is being opened.
        libraryPresenter.onOpenManga()

        // Create a new activity with the manga.
        val intent = MangaActivity.newIntent(context, manga)
        startActivity(intent)
    }


    /**
     * Toggles the selection for a manga.
     *
     * @param position the position to toggle.
     */
    private fun toggleSelection(position: Int) {
        val library = libraryFragment

        // Toggle the selection.
        adapter.toggleSelection(position, false)

        // Notify the selection to the presenter.
        library.presenter.setSelection(adapter.getItem(position), adapter.isSelected(position))

        // Get the selected count.
        val count = library.presenter.selectedMangas.size
        if (count == 0) {
            // Destroy action mode if there are no items selected.
            library.destroyActionModeIfNeeded()
        } else {
            // Update action mode with the new selection.
            library.setContextTitle(count)
            library.setVisibilityOfCoverEdit(count)
            library.invalidateActionMode()
        }
    }

    /**
     * Sets the mode for the adapter.
     *
     * @param mode the mode to set. It should be MODE_SINGLE or MODE_MULTI.
     */
    fun setSelectionMode(mode: Int) {
        adapter.mode = mode
        if (mode == FlexibleAdapter.MODE_SINGLE) {
            adapter.clearSelection()
        }
    }

    /**
     * Property to get the library fragment.
     */
    private val libraryFragment: LibraryFragment
        get() = parentFragment as LibraryFragment

    /**
     * Property to get the library presenter.
     */
    private val libraryPresenter: LibraryPresenter
        get() = libraryFragment.presenter

}
