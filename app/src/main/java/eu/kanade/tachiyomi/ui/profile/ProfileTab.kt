package eu.kanade.tachiyomi.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.profile.ProfileScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.library.LibraryViewModel
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Person
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

data object ProfileTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 1u,
            title = stringResource(MR.strings.profile_title),
            icon = rememberVectorPainter(MaterialSymbols.Rounded.Person),
        )

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val tabNavigator = LocalTabNavigator.current
        val viewModel = metroViewModel<LibraryViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        ProfileScreen(
            items = state.libraryData.favorites,
            onOpenUpdates = { tabNavigator.current = UpdatesTab },
            onOpenHistory = { tabNavigator.current = HistoryTab },
            onOpenSettings = { tabNavigator.current = MoreTab },
            onOpenManga = { navigator.push(MangaScreen(it)) },
        )

        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) (context as? MainActivity)?.ready = true
        }
    }
}
