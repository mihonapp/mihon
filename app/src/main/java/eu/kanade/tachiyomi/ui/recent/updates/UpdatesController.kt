package eu.kanade.tachiyomi.ui.recent.updates

import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import eu.kanade.presentation.components.ChapterDownloadAction
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.updates.UpdateScreen
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController
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
        Crossfade(targetState = presenter.isLoading) { isLoading ->
            if (isLoading) {
                LoadingScreen()
            } else {
                UpdateScreen(
                    presenter = presenter,
                    onClickCover = { item ->
                        router.pushController(MangaController(item.update.mangaId))
                    },
                    onBackClicked = this::onBackClicked,
                    onDownloadChapter = this::downloadChapters,
                )
            }
        }
        LaunchedEffect(presenter.selectionMode) {
            val activity = (activity as? MainActivity) ?: return@LaunchedEffect
            activity.showBottomNav(presenter.selectionMode.not())
        }
        LaunchedEffect(presenter.isLoading) {
            if (presenter.isLoading.not()) {
                (activity as? MainActivity)?.ready = true
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
            presenter.toggleAllSelection(false)
        }
    }
}
