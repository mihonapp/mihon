package eu.kanade.tachiyomi.ui.deeplink

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.LocalBackStack
import eu.kanade.presentation.util.replace
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchRoute
import eu.kanade.tachiyomi.ui.manga.MangaRoute
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.serialization.Serializable
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

@Serializable
data class DeepLinkRoute(val query: String = "") : NavKey

@Composable
fun DeepLinkScreen(query: String) {
    val context = LocalContext.current
    val backStack = LocalBackStack.current

    val viewModel = assistedMetroViewModel<DeepLinkViewModel, DeepLinkViewModel.Factory> { create(query = query) }
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.action_search_hint),
                navigateUp = backStack::removeLastOrNull,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        when (state) {
            is DeepLinkViewModel.State.Loading -> {
                LoadingScreen(Modifier.padding(contentPadding))
            }
            is DeepLinkViewModel.State.NoResults -> {
                backStack.replace(GlobalSearchRoute(query))
            }
            is DeepLinkViewModel.State.Result -> {
                val resultState = state as DeepLinkViewModel.State.Result
                if (resultState.chapterId == null) {
                    backStack.replace(
                        MangaRoute(
                            resultState.manga.id,
                            true,
                        ),
                    )
                } else {
                    backStack.removeLastOrNull()
                    ReaderActivity.newIntent(
                        context,
                        resultState.manga.id,
                        resultState.chapterId,
                    ).also(context::startActivity)
                }
            }
        }
    }
}
