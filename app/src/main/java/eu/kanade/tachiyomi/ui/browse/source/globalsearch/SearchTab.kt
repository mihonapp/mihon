package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Tab
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Search
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

data object SearchTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            return TabOptions(
                index = 1u,
                title = stringResource(MR.strings.action_search),
                icon = rememberVectorPainter(MaterialSymbols.Rounded.Search),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {}

    @Composable
    override fun Content() {
        GlobalSearchScreen(inSearchTab = true).Content()
    }
}
