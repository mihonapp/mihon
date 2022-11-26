package eu.kanade.tachiyomi.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.more.stats.StatsScreenContent
import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.presentation.util.LocalRouter
import eu.kanade.tachiyomi.R

class StatsScreen : Screen {

    override val key = uniqueScreenKey

    @Composable
    override fun Content() {
        val router = LocalRouter.currentOrThrow
        val context = LocalContext.current

        val screenModel = rememberScreenModel { StatsScreenModel() }
        val state by screenModel.state.collectAsState()

        if (state is StatsScreenState.Loading) {
            LoadingScreen()
            return
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(R.string.label_stats),
                    navigateUp = router::popCurrentController,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            StatsScreenContent(
                state = state as StatsScreenState.Success,
                paddingValues = paddingValues,
            )
        }
    }
}
