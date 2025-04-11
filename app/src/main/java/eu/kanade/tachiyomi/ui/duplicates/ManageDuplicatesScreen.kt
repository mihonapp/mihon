package eu.kanade.tachiyomi.ui.duplicates

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Screen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR

class ManageDuplicatesScreen : Screen() {

    @Composable
    override fun Content() {
        val tabs = persistentListOf(
            possibleDuplicatesTab(),
            hiddenDuplicatesTab()
        )

        val state = rememberPagerState { tabs.size }

        TabbedScreen(
            titleRes = MR.strings.label_manage_duplicates,
            tabs = tabs,
            state = state,
        )
    }
}
