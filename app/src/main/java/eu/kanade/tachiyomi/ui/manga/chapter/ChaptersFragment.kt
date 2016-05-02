package eu.kanade.tachiyomi.ui.manga.chapter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.view.ActionMode
import android.view.*
import com.afollestad.materialdialogs.MaterialDialog
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.base.adapter.FlexibleViewHolder
import eu.kanade.tachiyomi.ui.base.decoration.DividerItemDecoration
import eu.kanade.tachiyomi.ui.base.fragment.BaseRxFragment
import eu.kanade.tachiyomi.ui.manga.MangaActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.getCoordinates
import eu.kanade.tachiyomi.util.getResourceDrawable
import eu.kanade.tachiyomi.util.toast
import eu.kanade.tachiyomi.widget.DeletingChaptersDialog
import eu.kanade.tachiyomi.widget.NpaLinearLayoutManager
import kotlinx.android.synthetic.main.fragment_manga_chapters.*
import nucleus.factory.RequiresPresenter
import timber.log.Timber

@RequiresPresenter(ChaptersPresenter::class)
class ChaptersFragment : BaseRxFragment<ChaptersPresenter>(), ActionMode.Callback, FlexibleViewHolder.OnListItemClickListener {

    companion object {
        /**
         * Creates a new instance of this fragment.
         *
         * @return a new instance of [ChaptersFragment].
         */
        fun newInstance(): ChaptersFragment {
            return ChaptersFragment()
        }

    }

    /**
     * Adapter containing a list of chapters.
     */
    private lateinit var adapter: ChaptersAdapter

    /**
     * Action mode for multiple selection.
     */
    private var actionMode: ActionMode? = null

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_manga_chapters, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Init RecyclerView and adapter
        adapter = ChaptersAdapter(this)

        recycler.adapter = adapter
        recycler.layoutManager = NpaLinearLayoutManager(activity)
        recycler.addItemDecoration(DividerItemDecoration(
                context.theme.getResourceDrawable(R.attr.divider_drawable)))
        recycler.setHasFixedSize(true)

        swipe_refresh.setOnRefreshListener { fetchChapters() }

        fab.setOnClickListener {
            val chapter = presenter.getNextUnreadChapter()
            if (chapter != null) {
                // Create animation listener
                val revealAnimationListener: Animator.AnimatorListener = object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        openChapter(chapter, true)
                    }
                }

                // Get coordinates and start animation
                val coordinates = fab.getCoordinates()
                if (!reveal_view.showRevealEffect(coordinates.x, coordinates.y, revealAnimationListener)) {
                    openChapter(chapter)
                }
            } else {
                context.toast(R.string.no_next_chapter)
            }
        }

    }

    override fun onPause() {
        // Stop recycler's scrolling when onPause is called. If the activity is finishing
        // the presenter will be destroyed, and it could cause NPE
        // https://github.com/inorichi/tachiyomi/issues/159
        recycler.stopScroll()

        super.onPause()
    }

    override fun onResume() {
        // Check if animation view is visible
        if (reveal_view.visibility == View.VISIBLE) {
            // Show the unReveal effect
            val coordinates = fab.getCoordinates()
            reveal_view.hideRevealEffect(coordinates.x, coordinates.y, 1920)
        }
        super.onResume()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.chapters, menu)
        menu.findItem(R.id.action_filter_unread).isChecked = presenter.onlyUnread()
        menu.findItem(R.id.action_filter_downloaded).isChecked = presenter.onlyDownloaded()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_display_mode -> showDisplayModeDialog()
            R.id.manga_download -> showDownloadDialog()
            R.id.action_filter_unread -> {
                item.isChecked = !item.isChecked
                presenter.setReadFilter(item.isChecked)
            }
            R.id.action_filter_downloaded -> {
                item.isChecked = !item.isChecked
                presenter.setDownloadedFilter(item.isChecked)
            }
            R.id.action_filter_empty -> {
                presenter.setReadFilter(false)
                presenter.setDownloadedFilter(false)
                activity.supportInvalidateOptionsMenu();
            }
            R.id.action_sort -> presenter.revertSortOrder()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    fun onNextManga(manga: Manga) {
        // Set initial values
        setReadFilter()
        setDownloadedFilter()
    }

    fun onNextChapters(chapters: List<Chapter>) {
        // If the list is empty, fetch chapters from source if the conditions are met
        // We use presenter chapters instead because they are always unfiltered
        if (presenter.chapters.isEmpty())
            initialFetchChapters()

        destroyActionModeIfNeeded()
        adapter.setItems(chapters)
    }

    private fun initialFetchChapters() {
        // Only fetch if this view is from the catalog and it hasn't requested previously
        if (isCatalogueManga && !presenter.hasRequested) {
            fetchChapters()
        }
    }

    fun fetchChapters() {
        swipe_refresh.isRefreshing = true
        presenter.fetchChaptersFromSource()
    }

    fun onFetchChaptersDone() {
        swipe_refresh.isRefreshing = false
    }

    fun onFetchChaptersError(error: Throwable) {
        swipe_refresh.isRefreshing = false
        context.toast(error.message)
    }

    val isCatalogueManga: Boolean
        get() = (activity as MangaActivity).fromCatalogue

    fun openChapter(chapter: Chapter, hasAnimation: Boolean = false) {
        val intent = ReaderActivity.newIntent(activity, presenter.manga, chapter)
        if (hasAnimation) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        startActivity(intent)
    }

    private fun showDisplayModeDialog() {

        // Get available modes, ids and the selected mode
        val modes = listOf(getString(R.string.show_title), getString(R.string.show_chapter_number))
        val ids = intArrayOf(Manga.DISPLAY_NAME, Manga.DISPLAY_NUMBER)
        val selectedIndex = if (presenter.manga.displayMode == Manga.DISPLAY_NAME) 0 else 1

        MaterialDialog.Builder(activity)
                .title(R.string.action_display_mode)
                .items(modes)
                .itemsIds(ids)
                .itemsCallbackSingleChoice(selectedIndex) { dialog, itemView, which, text ->
                    // Save the new display mode
                    presenter.setDisplayMode(itemView.id)
                    // Refresh ui
                    adapter.notifyDataSetChanged()
                    true
                }
                .show()
    }

    private fun showDownloadDialog() {
        // Get available modes
        val modes = listOf(getString(R.string.download_1), getString(R.string.download_5), getString(R.string.download_10),
                getString(R.string.download_unread), getString(R.string.download_all))

        MaterialDialog.Builder(activity)
                .title(R.string.manga_download)
                .negativeText(android.R.string.cancel)
                .items(modes)
                .itemsCallback { dialog, view, i, charSequence ->
                    var chapters: MutableList<Chapter> = arrayListOf()

                    // i = 0: Download 1
                    // i = 1: Download 5
                    // i = 2: Download 10
                    // i = 3: Download unread
                    // i = 4: Download all
                    for (chapter in presenter.chapters) {
                        if (!chapter.isDownloaded) {
                            if (i == 4 || (i != 4 && !chapter.read)) {
                                chapters.add(chapter)
                            }
                        }
                    }
                    if (chapters.size > 0) {
                        if (!presenter.sortOrder()) {
                            chapters.reverse()
                        }
                        when (i) {
                        // Set correct chapters size if desired
                            0 -> chapters = chapters.subList(0, 1)
                            1 -> {
                                if (chapters.size >= 5)
                                    chapters = chapters.subList(0, 5)
                            }
                            2 -> {
                                if (chapters.size >= 10)
                                    chapters = chapters.subList(0, 10)
                            }
                        }
                        downloadChapters(chapters)
                    }
                }
                .show()
    }

    fun onChapterStatusChange(download: Download) {
        getHolder(download.chapter)?.notifyStatus(download.status)
    }

    private fun getHolder(chapter: Chapter): ChaptersHolder? {
        return recycler.findViewHolderForItemId(chapter.id) as? ChaptersHolder
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.chapter_selection, menu)
        adapter.mode = FlexibleAdapter.MODE_MULTI
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        return false
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_select_all -> selectAll()
            R.id.action_mark_as_read -> markAsRead(getSelectedChapters())
            R.id.action_mark_as_unread -> markAsUnread(getSelectedChapters())
            R.id.action_download -> downloadChapters(getSelectedChapters())
            R.id.action_delete -> deleteChapters(getSelectedChapters())
            else -> return false
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        adapter.mode = FlexibleAdapter.MODE_SINGLE
        adapter.clearSelection()
        actionMode = null
    }

    fun getSelectedChapters(): List<Chapter> {
        return adapter.selectedItems.map { adapter.getItem(it) }
    }

    fun destroyActionModeIfNeeded() {
        actionMode?.finish()
    }

    fun selectAll() {
        adapter.selectAll()
        setContextTitle(adapter.selectedItemCount)
    }

    fun markAsRead(chapters: List<Chapter>) {
        presenter.markChaptersRead(chapters, true)
        if (presenter.preferences.removeAfterMarkedAsRead()) {
            deleteChapters(chapters)
        }
    }

    fun markAsUnread(chapters: List<Chapter>) {
        presenter.markChaptersRead(chapters, false)
    }

    fun markPreviousAsRead(chapter: Chapter) {
        presenter.markPreviousChaptersAsRead(chapter)
    }

    fun downloadChapters(chapters: List<Chapter>) {
        destroyActionModeIfNeeded()
        presenter.downloadChapters(chapters)
    }

    fun deleteChapters(chapters: List<Chapter>) {
        destroyActionModeIfNeeded()
        DeletingChaptersDialog().show(childFragmentManager, DeletingChaptersDialog.TAG)
        presenter.deleteChapters(chapters)
    }

    fun onChaptersDeleted() {
        dismissDeletingDialog()
        adapter.notifyDataSetChanged()
    }

    fun onChaptersDeletedError(error: Throwable) {
        dismissDeletingDialog()
        Timber.e(error, error.message)
    }

    fun dismissDeletingDialog() {
        (childFragmentManager.findFragmentByTag(DeletingChaptersDialog.TAG) as? DialogFragment)?.dismiss()
    }

    override fun onListItemClick(position: Int): Boolean {
        val item = adapter.getItem(position) ?: return false
        if (actionMode != null && adapter.mode == FlexibleAdapter.MODE_MULTI) {
            toggleSelection(position)
            return true
        } else {
            openChapter(item)
            return false
        }
    }

    override fun onListItemLongClick(position: Int) {
        if (actionMode == null)
            actionMode = activity.startSupportActionMode(this)

        toggleSelection(position)
    }

    private fun toggleSelection(position: Int) {
        adapter.toggleSelection(position, false)

        val count = adapter.selectedItemCount
        if (count == 0) {
            actionMode?.finish()
        } else {
            setContextTitle(count)
            actionMode?.invalidate()
        }
    }

    private fun setContextTitle(count: Int) {
        actionMode?.title = getString(R.string.label_selected, count)
    }

    fun setReadFilter() {
        activity.supportInvalidateOptionsMenu()
    }

    fun setDownloadedFilter() {
        activity.supportInvalidateOptionsMenu()
    }
}
