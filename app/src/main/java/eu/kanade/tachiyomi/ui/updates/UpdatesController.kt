package eu.kanade.tachiyomi.ui.updates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import eu.kanade.presentation.updates.UpdateScreen
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController

class UpdatesController :
    FullComposeController<UpdatesPresenter>(),
    RootController {

    override fun createPresenter() = UpdatesPresenter()

    @Composable
    override fun ComposeContent() {
        UpdateScreen(
            presenter = presenter,
            onClickCover = { item ->
                router.pushController(MangaController(item.update.mangaId))
            },
            onBackClicked = {
                (activity as? MainActivity)?.moveToStartScreen()
            },
        )

        LaunchedEffect(presenter.selectionMode) {
            (activity as? MainActivity)?.showBottomNav(presenter.selectionMode.not())
        }
        LaunchedEffect(presenter.isLoading) {
            if (!presenter.isLoading) {
                (activity as? MainActivity)?.ready = true
            }
        }
    }
}
