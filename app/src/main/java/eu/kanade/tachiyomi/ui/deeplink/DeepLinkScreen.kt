package eu.kanade.tachiyomi.ui.deeplink

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import mihon.core.navigation.GlobalSearchRoute
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MangaRoute
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun DeepLinkScreen(query: String) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    val viewModel = assistedMetroViewModel<DeepLinkViewModel, DeepLinkViewModel.Factory> { create(query = query) }
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.action_search_hint),
                navigateUp = navigator::pop,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        LoadingScreen(Modifier.padding(contentPadding))
    }

    LaunchedEffect(state) {
        when (val result = state) {
            is DeepLinkViewModel.State.Loading -> Unit
            is DeepLinkViewModel.State.NoResults -> navigator.replace(GlobalSearchRoute(query))
            is DeepLinkViewModel.State.Result -> {
                if (result.chapterId == null) {
                    navigator.replace(MangaRoute(result.manga.id, true))
                } else {
                    navigator.pop()
                    ReaderActivity.newIntent(context, result.manga.id, result.chapterId)
                        .also(context::startActivity)
                }
            }
        }
    }
}
