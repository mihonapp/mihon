package eu.kanade.tachiyomi.ui.browse

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.result.ResultEffect
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsViewModel
import eu.kanade.tachiyomi.ui.browse.extension.extensionsTab
import eu.kanade.tachiyomi.ui.browse.migration.sources.migrateSourceTab
import eu.kanade.tachiyomi.ui.browse.source.sourcesTab
import eu.kanade.tachiyomi.ui.home.TopLevelRoute
import eu.kanade.tachiyomi.ui.main.MainActivity
import mihon.core.navigation.GlobalSearchRoute
import mihon.core.navigation.LocalNavigator
import tachiyomi.i18n.MR

@Composable
fun BrowseTab() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    // Hoisted for extensions tab's search bar
    val extensionsViewModel = metroViewModel<ExtensionsViewModel>()
    val extensionsSearchQuery by extensionsViewModel.searchQuery.collectAsStateWithLifecycle()

    val tabs = listOf(
        sourcesTab(),
        extensionsTab(extensionsViewModel),
        migrateSourceTab(),
    )

    val state = rememberPagerState { tabs.size }

    TabbedScreen(
        titleRes = MR.strings.browse,
        tabs = tabs,
        state = state,
        searchQuery = extensionsSearchQuery,
        onChangeSearchQuery = extensionsViewModel::search,
    )

    LaunchedEffect(Unit) {
        (context as? MainActivity)?.ready = true
    }

    ResultEffect<BrowseShowExtensionsEvent> {
        state.scrollToPage(1)
    }

    ResultEffect<Unit>(resultKey = TopLevelRoute.Browse.reselectEventKey) {
        navigator.push(GlobalSearchRoute())
    }
}

data object BrowseShowExtensionsEvent
