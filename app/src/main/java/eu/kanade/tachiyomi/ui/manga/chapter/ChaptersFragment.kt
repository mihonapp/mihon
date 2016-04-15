package eu.kanade.tachiyomi.ui.manga.chapter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.os.Bundle
import android.support.v7.view.ActionMode
import android.view.*
import com.afollestad.materialdialogs.MaterialDialog
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.base.adapter.FlexibleViewHolder
import eu.kanade.tachiyomi.ui.base.decoration.DividerItemDecoration
import eu.kanade.tachiyomi.ui.base.fragment.BaseRxFragment
import eu.kanade.tachiyomi.ui.manga.MangaActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.getCoordinates
import eu.kanade.tachiyomi.util.getResourceDrawable
import eu.kanade.tachiyomi.util.toast
import eu.kanade.tachiyomi.widget.NpaLinearLayoutManager
import kotlinx.android.synthetic.main.fragment_manga_chapters.*
import nucleus.factory.RequiresPresenter
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

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

        fab.setOnClickListener { v ->
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
        get() = (activity as MangaActivity).isCatalogueManga

    fun openChapter(chapter: Chapter, hasAnimation: Boolean = false) {
        presenter.onOpenChapter(chapter)
        val intent = ReaderActivity.newIntent(activity)
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
                        onDownload(Observable.from(chapters))
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
            R.id.action_select_all -> onSelectAll()
            R.id.action_mark_as_read -> onMarkAsRead(getSelectedChapters())
            R.id.action_mark_as_unread -> onMarkAsUnread(getSelectedChapters())
            R.id.action_download -> onDownload(getSelectedChapters())
            R.id.action_delete -> onDelete(getSelectedChapters())
            else -> return false
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        adapter.mode = FlexibleAdapter.MODE_SINGLE
        adapter.clearSelection()
        actionMode = null
    }

    fun getSelectedChapters(): Observable<Chapter> {
        val chapters = adapter.selectedItems.map { adapter.getItem(it) }
        return Observable.from(chapters)
    }

    fun destroyActionModeIfNeeded() {
        actionMode?.finish()
    }

    protected fun onSelectAll() {
        adapter.selectAll()
        setContextTitle(adapter.selectedItemCount)
    }

    fun onMarkAsRead(chapters: Observable<Chapter>) {
        presenter.markChaptersRead(chapters, true)
    }

    fun onMarkAsUnread(chapters: Observable<Chapter>) {
        presenter.markChaptersRead(chapters, false)
    }

    fun onMarkPreviousAsRead(chapter: Chapter) {
        presenter.markPreviousChaptersAsRead(chapter)
    }

    fun onDownload(chapters: Observable<Chapter>) {
        DownloadService.start(activity)

        val observable = chapters.doOnCompleted { adapter.notifyDataSetChanged() }

        presenter.downloadChapters(observable)
        destroyActionModeIfNeeded()
    }

    fun onDelete(chapters: Observable<Chapter>) {
        val size = adapter.selectedItemCount

        val dialog = MaterialDialog.Builder(activity)
                .title(R.string.deleting)
                .progress(false, size, true)
                .cancelable(false)
                .show()

        val observable = chapters
                .concatMap { chapter ->
                    presenter.deleteChapter(chapter)
                    Observable.just(chapter)
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { chapter ->
                    dialog.incrementProgress(1)
                    chapter.status = Download.NOT_DOWNLOADED
                }
                .doOnCompleted { adapter.notifyDataSetChanged() }
                .doAfterTerminate { dialog.dismiss() }

        presenter.deleteChapters(observable)
        destroyActionModeIfNeeded()
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
            actionMode = baseActivity.startSupportActionMode(this)

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
        this.activity.supportInvalidateOptionsMenu()
    }

    fun setDownloadedFilter() {
        this.activity.supportInvalidateOptionsMenu()
    }
}
