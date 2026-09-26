package eu.kanade.tachiyomi.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.stats.StatsScreenContent
import eu.kanade.presentation.more.stats.StatsScreenState
import mihon.core.navigation.util.LocalBackStack
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun StatsScreen() {
    val backStack = LocalBackStack.current

    val viewModel = metroViewModel<StatsViewModel>()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_stats),
                navigateUp = backStack::removeLastOrNull,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        if (state is StatsScreenState.Loading) {
            LoadingScreen()
            return@Scaffold
        }

        StatsScreenContent(
            state = state as StatsScreenState.Success,
            paddingValues = paddingValues,
        )
    }
}
