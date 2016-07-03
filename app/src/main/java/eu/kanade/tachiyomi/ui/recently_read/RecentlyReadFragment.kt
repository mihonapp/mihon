package eu.kanade.tachiyomi.ui.recently_read

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaChapterHistory
import eu.kanade.tachiyomi.ui.base.fragment.BaseRxFragment
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.toast
import kotlinx.android.synthetic.main.fragment_recently_read.*
import nucleus.factory.RequiresPresenter

/**
 * Fragment that shows recently read manga.
 * Uses R.layout.fragment_recently_read.
 * UI related actions should be called from here.
 */
@RequiresPresenter(RecentlyReadPresenter::class)
class RecentlyReadFragment : BaseRxFragment<RecentlyReadPresenter>() {
    companion object {
        /**
         * Create new RecentChaptersFragment.
         */
        fun newInstance(): RecentlyReadFragment {
            return RecentlyReadFragment()
        }
    }

    /**
     * Adapter containing the recent manga.
     */
    lateinit var adapter: RecentlyReadAdapter
        private set

    /**
     * Called when view gets created
     *
     * @param inflater layout inflater
     * @param container view group
     * @param savedState status of saved state
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_recently_read, container, false)
    }

    /**
     * Called when view is created
     *
     * @param view created view
     * @param savedState status of saved sate
     */
    override fun onViewCreated(view: View?, savedState: Bundle?) {
        // Initialize adapter
        recycler.layoutManager = LinearLayoutManager(activity)
        adapter = RecentlyReadAdapter(this)
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter

        // Update toolbar text
        setToolbarTitle(R.string.label_recent_manga)
    }

    /**
     * Populate adapter with chapters
     *
     * @param mangaHistory list of manga history
     */
    fun onNextManga(mangaHistory: List<MangaChapterHistory>) {
        (activity as MainActivity).updateEmptyView(mangaHistory.isEmpty(),
                R.string.information_no_recent_manga, R.drawable.ic_glasses_black_128dp)

        adapter.setItems(mangaHistory)
    }

    /**
     * Reset last read of chapter to 0L
     * @param history history belonging to chapter
     */
    fun removeFromHistory(history: History) {
        presenter.removeFromHistory(history)
    }

    /**
     * Removes all chapters belonging to manga from library
     * @param mangaId id of manga
     */
    fun removeAllFromHistory(mangaId: Long) {
        presenter.removeAllFromHistory(mangaId)
    }

    /**
     * Open chapter to continue reading
     * @param chapter chapter that is opened
     * @param manga manga belonging to chapter
     */
    fun openChapter(chapter: Chapter, manga: Manga) {
        if (!chapter.read) {
            val intent = ReaderActivity.newIntent(activity, manga, chapter)
            startActivity(intent)
        } else {
            presenter.openNextChapter(chapter, manga)
        }
    }

    /**
     * Called from the presenter when wanting to open the next chapter of the current one.
     * @param chapter the next chapter or null if it doesn't exist.
     * @param manga the manga of the chapter.
     */
    fun onOpenNextChapter(chapter: Chapter?, manga: Manga) {
        if (chapter == null) {
            context.toast(R.string.no_next_chapter)
        }
        // Avoid crashes if the fragment isn't resumed, the event will be ignored but it's unlikely
        // to happen.
        else if (isResumed) {
            val intent = ReaderActivity.newIntent(activity, manga, chapter)
            startActivity(intent)
        }
    }

    /**
     * Open manga info page
     * @param manga manga belonging to info page
     */
    fun openMangaInfo(manga: Manga) {
        val intent = MangaActivity.newIntent(activity, manga, true)
        startActivity(intent)
    }

}