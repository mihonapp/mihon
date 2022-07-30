package eu.kanade.tachiyomi.ui.setting.database

import androidx.compose.runtime.Composable
import eu.kanade.presentation.more.settings.database.ClearDatabaseScreen
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController

class ClearDatabaseController : FullComposeController<ClearDatabasePresenter>() {

    override fun createPresenter(): ClearDatabasePresenter {
        return ClearDatabasePresenter()
    }

    @Composable
    override fun ComposeContent() {
        ClearDatabaseScreen(
            presenter = presenter,
            navigateUp = { router.popCurrentController() },
        )
    }
}
