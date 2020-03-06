package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.lang.plusAssign
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.inflate
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import kotlinx.android.synthetic.main.library_category.view.fast_scroller
import kotlinx.android.synthetic.main.library_category.view.swipe_refresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.recyclerview.scrollStateChanges
import reactivecircus.flowbinding.swiperefreshlayout.refreshes
import rx.subscriptions.CompositeSubscription
import uy.kohesive.injekt.injectLazy

/**
 * Fragment containing the library manga for a certain category.
 */
class LibraryCategoryView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs),
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    FlexibleAdapter.OnItemMoveListener, {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    private val preferences: PreferencesHelper by injectLazy()

    /**
     * The fragment containing this view.
     */
    private lateinit var controller: LibraryController

    /**
     * Category for this view.
     */
    lateinit var category: Category
        private set

    /**
     * Recycler view of the list of manga.
     */
    private lateinit var recycler: RecyclerView

    /**
     * Adapter to hold the manga in this category.
     */
    private lateinit var adapter: LibraryCategoryAdapter

    /**
     * Subscriptions while the view is bound.
     */
    private var subscriptions = CompositeSubscription()

    private var lastClickPosition = -1

    // EXH -->
    private var initialLoadHandle: LoadingHandle? = null
    lateinit var scope: CoroutineScope

    private fun newScope() = object : CoroutineScope {
        override val coroutineContext = SupervisorJob() + Dispatchers.Main
    }
    // EXH <--

    fun onCreate(controller: LibraryController) {
        this.controller = controller

        recycler = if (preferences.libraryAsList().get()) {
            (swipe_refresh.inflate(R.layout.library_list_recycler) as RecyclerView).apply {
                layoutManager = LinearLayoutManager(context)
            }
        } else {
            (swipe_refresh.inflate(R.layout.library_grid_recycler) as AutofitRecyclerView).apply {
                spanCount = controller.mangaPerRow
            }
        }

        adapter = LibraryCategoryAdapter(this)

        recycler.setHasFixedSize(true)
        recycler.adapter = adapter
        swipe_refresh.addView(recycler)
        adapter.fastScroller = fast_scroller

        recycler.scrollStateChanges()
            .onEach {
                // Disable swipe refresh when view is not at the top
                val firstPos = (recycler.layoutManager as LinearLayoutManager)
                    .findFirstCompletelyVisibleItemPosition()
                swipe_refresh.isEnabled = firstPos <= 0
            }
            .launchIn(scope)

        // Double the distance required to trigger sync
        swipe_refresh.setDistanceToTriggerSync((2 * 64 * resources.displayMetrics.density).toInt())
        swipe_refresh.refreshes()
            .onEach {
                if (LibraryUpdateService.start(context, category)) {
                    context.toast(R.string.updating_category)
                }

                // It can be a very long operation, so we disable swipe refresh and show a toast.
                swipe_refresh.isRefreshing = false
            }
            .launchIn(scope)
    }

    fun onBind(category: Category) {
        this.category = category

        adapter.mode = if (controller.selectedMangas.isNotEmpty()) {
            SelectableAdapter.Mode.MULTI
        } else {
            SelectableAdapter.Mode.SINGLE
        }
        val sortingMode = preferences.librarySortingMode().getOrDefault()
        adapter.isLongPressDragEnabled = sortingMode == LibrarySort.DRAG_AND_DROP

        // EXH -->
        scope = newScope()
        initialLoadHandle = controller.loaderManager.openProgressBar()
        // EXH <--

        subscriptions += controller.searchRelay
                .doOnNext { adapter.searchText = it }
                .skip(1)
                .debounce(500, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    // EXH -->
                    scope.launch {
                        val handle = controller.loaderManager.openProgressBar()
                        try {
                            // EXH <--
                            adapter.performFilter(this)
                            // EXH -->
                        } finally {
                            controller.loaderManager.closeProgressBar(handle)
                        }
                    }
                    // EXH <--
                }

        subscriptions += controller.libraryMangaRelay
                .subscribe {
                    // EXH -->
                    scope.launch {
                        try {
                            // EXH <--
                            onNextLibraryManga(this, it)
                            // EXH -->
                        } finally {
                            controller.loaderManager.closeProgressBar(initialLoadHandle)
                        }
                    }
                    // EXH <--
                }

        subscriptions += controller.selectionRelay
            .subscribe { onSelectionChanged(it) }

        subscriptions += controller.selectAllRelay
            .filter { it == category.id }
            .subscribe {
                adapter.currentItems.forEach { item ->
                    controller.setSelection(item.manga, true)
                }
                controller.invalidateActionMode()
            }

        subscriptions += controller.selectInverseRelay
            .filter { it == category.id }
            .subscribe {
                adapter.currentItems.forEach { item ->
                    controller.toggleSelection(item.manga)
                }
                controller.invalidateActionMode()
            }

        subscriptions += controller.reorganizeRelay
                .subscribe {
                    if (it.first == category.id) {
                        var items =  when (it.second) {
                            1, 2 -> adapter.currentItems.sortedBy {
//                                if (preferences.removeArticles().getOrDefault())
                                    it.manga.title.removeArticles()
//                                else
//                                    it.manga.title
                        }
                        3, 4 -> adapter.currentItems.sortedBy { it.manga.last_update }
                        else ->  adapter.currentItems.sortedBy { it.manga.title }
                    }
                    if (it.second % 2 == 0)
                        items = items.reversed()
                    runBlocking { adapter.setItems(this, items) }
                    adapter.notifyDataSetChanged()
                    onItemReleased(0)
                }
            }
//        }
    }

    fun onRecycle() {
        runBlocking { adapter.setItems(this, emptyList()) }
        adapter.clearSelection()
        unsubscribe()
    }

    fun unsubscribe() {
        subscriptions.clear()
        // EXH -->
        scope.cancel()
        controller.loaderManager.closeProgressBar(initialLoadHandle)
        // EXH <--
    }

    /**
     * Subscribe to [LibraryMangaEvent]. When an event is received, it updates the content of the
     * adapter.
     *
     * @param event the event received.
     */
    suspend fun onNextLibraryManga(cScope: CoroutineScope, event: LibraryMangaEvent) {
        // Get the manga list for this category.


        val sortingMode = preferences.librarySortingMode().getOrDefault()
        adapter.isLongPressDragEnabled = sortingMode == LibrarySort.DRAG_AND_DROP
        var mangaForCategory = event.getMangaForCategory(category).orEmpty()
        if (sortingMode == LibrarySort.DRAG_AND_DROP) {
            if (category.name == "Default")
                category.mangaOrder = preferences.defaultMangaOrder().getOrDefault().split("/")
                    .mapNotNull { it.toLongOrNull() }
            mangaForCategory = mangaForCategory.sortedBy { category.mangaOrder.indexOf(it.manga
                .id) }
        }
        // Update the category with its manga.
        // EXH -->
        adapter.setItems(cScope, mangaForCategory)
        // EXH <--

        if (adapter.mode == SelectableAdapter.Mode.MULTI) {
            controller.selectedMangas.forEach { manga ->
                val position = adapter.indexOf(manga)
                if (position != -1 && !adapter.isSelected(position)) {
                    adapter.toggleSelection(position)
                    (recycler.findViewHolderForItemId(manga.id!!) as? LibraryHolder)?.toggleActivation()
                }
            }
        }
    }

    /**
     * Subscribe to [LibrarySelectionEvent]. When an event is received, it updates the selection
     * depending on the type of event received.
     *
     * @param event the selection event received.
     */
    private fun onSelectionChanged(event: LibrarySelectionEvent) {
        when (event) {
            is LibrarySelectionEvent.Selected -> {
                if (adapter.mode != SelectableAdapter.Mode.MULTI) {
                    adapter.mode = SelectableAdapter.Mode.MULTI
                    adapter.isLongPressDragEnabled = false
                }
                findAndToggleSelection(event.manga)
            }
            is LibrarySelectionEvent.Unselected -> {
                findAndToggleSelection(event.manga)
                if (adapter.indexOf(event.manga) != -1) lastClickPosition = -1
                if (controller.selectedMangas.isEmpty()) {
                    adapter.mode = SelectableAdapter.Mode.SINGLE
                    adapter.isLongPressDragEnabled = preferences.librarySortingMode()
                        .getOrDefault() == LibrarySort.DRAG_AND_DROP
                }
            }
            is LibrarySelectionEvent.Cleared -> {
                adapter.mode = SelectableAdapter.Mode.SINGLE
                adapter.clearSelection()
                lastClickPosition = -1
                adapter.isLongPressDragEnabled = preferences.librarySortingMode()
                    .getOrDefault() == LibrarySort.DRAG_AND_DROP
            }
        }
    }

    /**
     * Toggles the selection for the given manga and updates the view if needed.
     *
     * @param manga the manga to toggle.
     */
    private fun findAndToggleSelection(manga: Manga) {
        val position = adapter.indexOf(manga)
        if (position != -1) {
            adapter.toggleSelection(position)
            (recycler.findViewHolderForItemId(manga.id!!) as? LibraryHolder)?.toggleActivation()
        }
    }

    /**
     * Called when a manga is clicked.
     *
     * @param position the position of the element clicked.
     * @return true if the item should be selected, false otherwise.
     */
    override fun onItemClick(view: View?, position: Int): Boolean {
        // If the action mode is created and the position is valid, toggle the selection.
        val item = adapter.getItem(position) ?: return false
        return if (adapter.mode == SelectableAdapter.Mode.MULTI) {
            lastClickPosition = position
            toggleSelection(position)
            true
        } else {
            openManga(item.manga)
            false
        }
    }

    /**
     * Called when a manga is long clicked.
     *
     * @param position the position of the element clicked.
     */
    override fun onItemLongClick(position: Int) {
        controller.createActionModeIfNeeded()
        adapter.isLongPressDragEnabled = false
        when {
            lastClickPosition == -1 -> setSelection(position)
            lastClickPosition > position ->
                for (i in position until lastClickPosition)
                    setSelection(i)
            lastClickPosition < position ->
                for (i in lastClickPosition + 1..position)
                    setSelection(i)
            else -> setSelection(position)
        }
        lastClickPosition = position
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {

    }

    override fun onItemReleased(position: Int) {
        if (adapter.selectedItemCount == 0) {
            val mangaIds = adapter.currentItems.mapNotNull { it.manga.id }
            category.mangaOrder = mangaIds
            val db: DatabaseHelper by injectLazy()
            if (category.name == "Default")
                preferences.defaultMangaOrder().set(mangaIds.joinToString("/"))
            else
                db.insertCategory(category).asRxObservable().subscribe()
        }
    }

    override fun shouldMoveItem(fromPosition: Int, toPosition: Int): Boolean {
        if (adapter.selectedItemCount > 1)
            return false
        if (adapter.isSelected(fromPosition))
            toggleSelection(fromPosition)
        return true
    }

    override fun onActionStateChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        val position = viewHolder?.adapterPosition ?: return
        if (actionState == 2)
            onItemLongClick(position)
    }

    /**
     * Opens a manga.
     *
     * @param manga the manga to open.
     */
    private fun openManga(manga: Manga) {
        controller.openManga(manga)
    }

    /**
     * Tells the presenter to toggle the selection for the given position.
     *
     * @param position the position to toggle.
     */
    private fun toggleSelection(position: Int) {
        val item = adapter.getItem(position) ?: return

        controller.setSelection(item.manga, !adapter.isSelected(position))
        controller.invalidateActionMode()
    }

    /**
     * Tells the presenter to set the selection for the given position.
     *
     * @param position the position to toggle.
     */
    private fun setSelection(position: Int) {
        val item = adapter.getItem(position) ?: return

        controller.setSelection(item.manga, true)
        controller.invalidateActionMode()
    }
}
