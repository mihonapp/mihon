package eu.kanade.tachiyomi.ui.recent.updates

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.ActionMode
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import dev.chrisbanes.insetter.applyInsetter
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.databinding.UpdatesControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.manga.chapter.base.BaseChaptersAdapter
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.notificationManager
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.onAnimationsFinished
import eu.kanade.tachiyomi.widget.ActionModeWithToolbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority
import reactivecircus.flowbinding.recyclerview.scrollStateChanges
import reactivecircus.flowbinding.swiperefreshlayout.refreshes

/**
 * Fragment that shows recent chapters.
 */
class UpdatesController :
    NucleusController<UpdatesControllerBinding, UpdatesPresenter>(),
    RootController,
    ActionModeWithToolbar.Callback,
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    FlexibleAdapter.OnUpdateListener,
    BaseChaptersAdapter.OnChapterClickListener,
    ConfirmDeleteChaptersDialog.Listener,
    UpdatesAdapter.OnCoverClickListener {

    /**
     * Action mode for multiple selection.
     */
    private var actionMode: ActionModeWithToolbar? = null

    /**
     * Adapter containing the recent chapters.
     */
    var adapter: UpdatesAdapter? = null
        private set

    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? {
        return resources?.getString(R.string.label_recent_updates)
    }

    override fun createPresenter(): UpdatesPresenter {
        return UpdatesPresenter()
    }

    override fun createBinding(inflater: LayoutInflater) = UpdatesControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        binding.recycler.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }

        view.context.notificationManager.cancel(Notifications.ID_NEW_CHAPTERS)

        // Init RecyclerView and adapter
        val layoutManager = LinearLayoutManager(view.context)
        binding.recycler.layoutManager = layoutManager
        binding.recycler.setHasFixedSize(true)
        binding.recycler.scrollStateChanges()
            .onEach {
                // Disable swipe refresh when view is not at the top
                val firstPos = layoutManager.findFirstCompletelyVisibleItemPosition()
                binding.swipeRefresh.isEnabled = firstPos <= 0
            }
            .launchIn(viewScope)

        binding.swipeRefresh.isRefreshing = true
        binding.swipeRefresh.setDistanceToTriggerSync((2 * 64 * view.resources.displayMetrics.density).toInt())
        binding.swipeRefresh.refreshes()
            .onEach {
                updateLibrary()

                // It can be a very long operation, so we disable swipe refresh and show a toast.
                binding.swipeRefresh.isRefreshing = false
            }
            .launchIn(viewScope)

        viewScope.launch {
            presenter.updates.collectLatest { updatesItems ->
                destroyActionModeIfNeeded()
                if (adapter == null) {
                    adapter = UpdatesAdapter(this@UpdatesController, binding.recycler.context, updatesItems)
                    binding.recycler.adapter = adapter
                    adapter!!.fastScroller = binding.fastScroller
                } else {
                    adapter?.updateDataSet(updatesItems)
                }
                binding.swipeRefresh.isRefreshing = false
                binding.fastScroller.isVisible = true
                binding.recycler.onAnimationsFinished {
                    (activity as? MainActivity)?.ready = true
                }
            }
        }
    }

    override fun onDestroyView(view: View) {
        destroyActionModeIfNeeded()
        adapter = null
        super.onDestroyView(view)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.updates, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_update_library -> updateLibrary()
        }

        return super.onOptionsItemSelected(item)
    }

    private fun updateLibrary() {
        activity?.let {
            if (LibraryUpdateService.start(it)) {
                it.toast(R.string.updating_library)
            }
        }
    }

    /**
     * Returns selected chapters
     * @return list of selected chapters
     */
    private fun getSelectedChapters(): List<UpdatesItem> {
        val adapter = adapter ?: return emptyList()
        return adapter.selectedPositions.mapNotNull { adapter.getItem(it) as? UpdatesItem }
    }

    /**
     * Called when item in list is clicked
     * @param position position of clicked item
     */
    override fun onItemClick(view: View, position: Int): Boolean {
        val adapter = adapter ?: return false

        // Get item from position
        val item = adapter.getItem(position) as? UpdatesItem ?: return false
        return if (actionMode != null && adapter.mode == SelectableAdapter.Mode.MULTI) {
            toggleSelection(position)
            true
        } else {
            openChapter(item)
            false
        }
    }

    /**
     * Called when item in list is long clicked
     * @param position position of clicked item
     */
    override fun onItemLongClick(position: Int) {
        val activity = activity
        if (actionMode == null && activity is MainActivity) {
            actionMode = activity.startActionModeAndToolbar(this)
            activity.showBottomNav(false)
        }
        toggleSelection(position)
    }

    /**
     * Called to toggle selection
     * @param position position of selected item
     */
    private fun toggleSelection(position: Int) {
        val adapter = adapter ?: return
        adapter.toggleSelection(position)
        actionMode?.invalidate()
    }

    /**
     * Open chapter in reader
     * @param chapter selected chapter
     */
    private fun openChapter(item: UpdatesItem) {
        val activity = activity ?: return
        val intent = ReaderActivity.newIntent(activity, item.manga.id, item.chapter.id)
        startActivity(intent)
    }

    /**
     * Download selected items
     * @param chapters list of selected [UpdatesItem]s
     */
    private fun downloadChapters(chapters: List<UpdatesItem>) {
        presenter.downloadChapters(chapters)
        destroyActionModeIfNeeded()
    }

    override fun onUpdateEmptyView(size: Int) {
        if (size > 0) {
            binding.emptyView.hide()
        } else {
            binding.emptyView.show(R.string.information_no_recent)
        }
    }

    /**
     * Update download status of chapter
     * @param download [Download] object containing download progress.
     */
    fun onChapterDownloadUpdate(download: Download) {
        adapter?.currentItems
            ?.filterIsInstance<UpdatesItem>()
            ?.find { it.chapter.id == download.chapter.id }?.let {
                adapter?.updateItem(it, it.status)
            }
    }

    /**
     * Mark chapter as read
     * @param chapters list of chapters
     */
    private fun markAsRead(chapters: List<UpdatesItem>) {
        presenter.markChapterRead(chapters, true)
        destroyActionModeIfNeeded()
    }

    /**
     * Mark chapter as unread
     * @param chapters list of selected [UpdatesItem]
     */
    private fun markAsUnread(chapters: List<UpdatesItem>) {
        presenter.markChapterRead(chapters, false)
        destroyActionModeIfNeeded()
    }

    override fun deleteChapters(chaptersToDelete: List<UpdatesItem>) {
        presenter.deleteChapters(chaptersToDelete)
        destroyActionModeIfNeeded()
    }

    private fun destroyActionModeIfNeeded() {
        actionMode?.finish()
    }

    override fun onCoverClick(position: Int) {
        destroyActionModeIfNeeded()

        val chapterClicked = adapter?.getItem(position) as? UpdatesItem ?: return
        openManga(chapterClicked)
    }

    private fun openManga(chapter: UpdatesItem) {
        router.pushController(MangaController(chapter.manga.id!!))
    }

    /**
     * Called when chapters are deleted
     */
    fun onChaptersDeleted() {
        adapter?.notifyDataSetChanged()
    }

    /**
     * Called when error while deleting
     * @param error error message
     */
    fun onChaptersDeletedError(error: Throwable) {
        logcat(LogPriority.ERROR, error)
    }

    override fun downloadChapter(position: Int) {
        val item = adapter?.getItem(position) as? UpdatesItem ?: return
        if (item.status == Download.State.ERROR) {
            DownloadService.start(activity!!)
        } else {
            downloadChapters(listOf(item))
        }
        adapter?.updateItem(item)
    }

    override fun deleteChapter(position: Int) {
        val item = adapter?.getItem(position) as? UpdatesItem ?: return
        deleteChapters(listOf(item))
        adapter?.updateItem(item)
    }

    override fun startDownloadNow(position: Int) {
        val item = adapter?.getItem(position) as? UpdatesItem ?: return
        presenter.startDownloadingNow(item.chapter)
    }

    private fun bookmarkChapters(chapters: List<UpdatesItem>, bookmarked: Boolean) {
        presenter.bookmarkChapters(chapters, bookmarked)
        destroyActionModeIfNeeded()
    }

    /**
     * Called when ActionMode created.
     * @param mode the ActionMode object
     * @param menu menu object of ActionMode
     */
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.generic_selection, menu)
        adapter?.mode = SelectableAdapter.Mode.MULTI
        return true
    }

    override fun onCreateActionToolbar(menuInflater: MenuInflater, menu: Menu) {
        menuInflater.inflate(R.menu.updates_chapter_selection, menu)
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val count = adapter?.selectedItemCount ?: 0
        if (count == 0) {
            // Destroy action mode if there are no items selected.
            destroyActionModeIfNeeded()
        } else {
            mode.title = count.toString()
        }
        return true
    }

    override fun onPrepareActionToolbar(toolbar: ActionModeWithToolbar, menu: Menu) {
        val chapters = getSelectedChapters()
        if (chapters.isEmpty()) return
        toolbar.findToolbarItem(R.id.action_download)?.isVisible = chapters.any { !it.isDownloaded }
        toolbar.findToolbarItem(R.id.action_delete)?.isVisible = chapters.any { it.isDownloaded }
        toolbar.findToolbarItem(R.id.action_bookmark)?.isVisible = chapters.any { !it.chapter.bookmark }
        toolbar.findToolbarItem(R.id.action_remove_bookmark)?.isVisible = chapters.all { it.chapter.bookmark }
        toolbar.findToolbarItem(R.id.action_mark_as_read)?.isVisible = chapters.any { !it.chapter.read }
        toolbar.findToolbarItem(R.id.action_mark_as_unread)?.isVisible = chapters.all { it.chapter.read }
    }

    /**
     * Called when ActionMode item clicked
     * @param mode the ActionMode object
     * @param item item from ActionMode.
     */
    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return onActionItemClicked(item)
    }

    private fun onActionItemClicked(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_select_all -> selectAll()
            R.id.action_select_inverse -> selectInverse()
            R.id.action_download -> downloadChapters(getSelectedChapters())
            R.id.action_delete ->
                ConfirmDeleteChaptersDialog(this, getSelectedChapters())
                    .showDialog(router)
            R.id.action_bookmark -> bookmarkChapters(getSelectedChapters(), true)
            R.id.action_remove_bookmark -> bookmarkChapters(getSelectedChapters(), false)
            R.id.action_mark_as_read -> markAsRead(getSelectedChapters())
            R.id.action_mark_as_unread -> markAsUnread(getSelectedChapters())
            else -> return false
        }
        return true
    }

    /**
     * Called when ActionMode destroyed
     * @param mode the ActionMode object
     */
    override fun onDestroyActionMode(mode: ActionMode) {
        adapter?.mode = SelectableAdapter.Mode.IDLE
        adapter?.clearSelection()

        (activity as? MainActivity)?.showBottomNav(true)

        actionMode = null
    }

    private fun selectAll() {
        val adapter = adapter ?: return
        adapter.selectAll()
        actionMode?.invalidate()
    }

    private fun selectInverse() {
        val adapter = adapter ?: return
        for (i in 0..adapter.itemCount) {
            adapter.toggleSelection(i)
        }
        actionMode?.invalidate()
        adapter.notifyDataSetChanged()
    }
}
