package eu.kanade.tachiyomi.ui.recent.history

import androidx.compose.runtime.Composable
import eu.kanade.presentation.history.HistoryScreen
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.manga.MangaController

class HistoryController : FullComposeController<HistoryPresenter>(), RootController {

    override fun createPresenter() = HistoryPresenter()

    @Composable
    override fun ComposeContent() {
        HistoryScreen(
            presenter = presenter,
            onClickCover = { history ->
                router.pushController(MangaController(history.mangaId))
            },
            onClickResume = { history ->
                presenter.getNextChapterForManga(history.mangaId, history.chapterId)
            },
        )
    }

    fun resumeLastChapterRead() {
        presenter.resumeLastChapterRead()
    }
}
