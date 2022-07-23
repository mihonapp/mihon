package eu.kanade.tachiyomi.ui.recent.updates

import androidx.activity.OnBackPressedDispatcherOwner
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.presentation.components.ChapterDownloadAction
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.updates.UpdateScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.materialdialogs.await
import kotlinx.coroutines.launch

/**
 * Fragment that shows recent chapters.
 */
class UpdatesController :
    FullComposeController<UpdatesPresenter>(),
    RootController {

    override fun createPresenter() = UpdatesPresenter()

    @Composable
    override fun ComposeContent() {
        val state by presenter.state.collectAsState()
        when (state) {
            is UpdatesState.Loading -> LoadingScreen()
            is UpdatesState.Error -> Text(text = (state as UpdatesState.Error).error.message.orEmpty())
            is UpdatesState.Success ->
                UpdateScreen(
                    state = (state as UpdatesState.Success),
                    onClickCover = this::openManga,
                    onClickUpdate = this::openChapter,
                    onDownloadChapter = this::downloadChapters,
                    onUpdateLibrary = this::updateLibrary,
                    onBackClicked = this::onBackClicked,
                    // For bottom action menu
                    onMultiBookmarkClicked = { updatesItems, bookmark ->
                        presenter.bookmarkUpdates(updatesItems, bookmark)
                    },
                    onMultiMarkAsReadClicked = { updatesItems, read ->
                        presenter.markUpdatesRead(updatesItems, read)
                    },
                    onMultiDeleteClicked = this::deleteChaptersWithConfirmation,
                )
        }
        LaunchedEffect(state) {
            if (state !is UpdatesState.Loading) {
                (activity as? MainActivity)?.ready = true
            }
        }
    }

    private fun updateLibrary() {
        activity?.let {
            if (LibraryUpdateService.start(it)) {
                it.toast(R.string.updating_library)
            }
        }
    }

    // Let compose view handle this
    override fun handleBack(): Boolean {
        (activity as? OnBackPressedDispatcherOwner)?.onBackPressedDispatcher?.onBackPressed()
        return true
    }

    private fun onBackClicked() {
        (activity as? MainActivity)?.moveToStartScreen()
    }

    private fun downloadChapters(items: List<UpdatesItem>, action: ChapterDownloadAction) {
        if (items.isEmpty()) return
        viewScope.launch {
            when (action) {
                ChapterDownloadAction.START -> {
                    presenter.downloadChapters(items)
                    if (items.any { it.downloadStateProvider() == Download.State.ERROR }) {
                        DownloadService.start(activity!!)
                    }
                }
                ChapterDownloadAction.START_NOW -> {
                    val chapterId = items.singleOrNull()?.update?.chapterId ?: return@launch
                    presenter.startDownloadingNow(chapterId)
                }
                ChapterDownloadAction.CANCEL -> {
                    val chapterId = items.singleOrNull()?.update?.chapterId ?: return@launch
                    presenter.cancelDownload(chapterId)
                }
                ChapterDownloadAction.DELETE -> {
                    presenter.deleteChapters(items)
                }
            }
        }
    }

    private fun deleteChaptersWithConfirmation(items: List<UpdatesItem>) {
        if (items.isEmpty()) return
        viewScope.launch {
            val result = MaterialAlertDialogBuilder(activity!!)
                .setMessage(R.string.confirm_delete_chapters)
                .await(android.R.string.ok, android.R.string.cancel)
            if (result == AlertDialog.BUTTON_POSITIVE) presenter.deleteChapters(items)
        }
    }

    private fun openChapter(item: UpdatesItem) {
        val activity = activity ?: return
        val intent = ReaderActivity.newIntent(activity, item.update.mangaId, item.update.chapterId)
        startActivity(intent)
    }

    private fun openManga(item: UpdatesItem) {
        router.pushController(MangaController(item.update.mangaId))
    }
}
