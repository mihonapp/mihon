package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import eu.kanade.presentation.more.about.LicensesScreen
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController

class LicensesController : BasicFullComposeController() {

    @Composable
    override fun ComposeContent() {
        LicensesScreen(
            navigateUp = router::popCurrentController,
        )
    }
}
