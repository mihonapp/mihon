package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import eu.kanade.presentation.more.about.LicensesScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.BasicComposeController

class LicensesController : BasicComposeController() {

    override fun getTitle() = resources?.getString(R.string.licenses)

    @Composable
    override fun ComposeContent(nestedScrollInterop: NestedScrollConnection) {
        LicensesScreen(nestedScrollInterop = nestedScrollInterop)
    }
}
