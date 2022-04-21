package eu.kanade.tachiyomi.ui.recent.history

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.presentation.history.HistoryScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ComposeControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.appcompat.queryTextChanges

/**
 * Fragment that shows recently read manga.
 */
class HistoryController :
    NucleusController<ComposeControllerBinding, HistoryPresenter>(),
    RootController {

    private var query = ""

    override fun getTitle(): String? = resources?.getString(R.string.label_recent_manga)

    override fun createPresenter(): HistoryPresenter = HistoryPresenter()

    override fun createBinding(inflater: LayoutInflater): ComposeControllerBinding =
        ComposeControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.root.setContent {
            HistoryScreen(
                composeView = binding.root,
                presenter = presenter,
                onClickItem = { history ->
                    router.pushController(MangaController(history).withFadeTransaction())
                },
                onClickResume = { history ->
                    presenter.getNextChapterForManga(history.mangaId, history.chapterId)
                },
                onClickDelete = { history, all ->
                    if (all) {
                        // Reset last read of chapter to 0L
                        presenter.removeAllFromHistory(history.mangaId)
                    } else {
                        // Remove all chapters belonging to manga from library
                        presenter.removeFromHistory(history)
                    }
                },
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.history, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.maxWidth = Int.MAX_VALUE
        if (query.isNotEmpty()) {
            searchItem.expandActionView()
            searchView.setQuery(query, true)
            searchView.clearFocus()
        }
        searchView.queryTextChanges()
            .filter { router.backstack.lastOrNull()?.controller == this }
            .onEach {
                query = it.toString()
                presenter.search(query)
            }
            .launchIn(viewScope)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_history -> {
                val dialog = ClearHistoryDialogController()
                dialog.targetController = this@HistoryController
                dialog.showDialog(router)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun openChapter(chapter: Chapter?) {
        val activity = activity ?: return
        if (chapter != null) {
            val intent = ReaderActivity.newIntent(activity, chapter.mangaId, chapter.id)
            startActivity(intent)
        } else {
            activity.toast(R.string.no_next_chapter)
        }
    }
}
