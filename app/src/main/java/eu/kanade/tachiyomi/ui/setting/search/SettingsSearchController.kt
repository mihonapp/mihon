package eu.kanade.tachiyomi.ui.setting.search

import androidx.compose.runtime.Composable
import eu.kanade.presentation.more.settings.SettingsSearchScreen
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.base.controller.pushController

class SettingsSearchController : FullComposeController<SettingsSearchPresenter>() {

    override fun createPresenter() = SettingsSearchPresenter()

    @Composable
    override fun ComposeContent() {
        SettingsSearchScreen(
            navigateUp = router::popCurrentController,
            presenter = presenter,
            onClickResult = { router.pushController(it) },
        )
    }
}
