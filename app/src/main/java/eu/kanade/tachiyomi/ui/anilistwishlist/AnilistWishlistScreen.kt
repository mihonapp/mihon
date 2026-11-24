package eu.kanade.tachiyomi.ui.anilistwishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.result.ResultEffect
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.anilistwishlist.AnilistWishlistScreenContent
import eu.kanade.presentation.more.anilistwishlist.AnilistWishlistScreenState
import eu.kanade.tachiyomi.ui.anilistwishlist.search.AnilistWishlistMangaSelectedEvent
import eu.kanade.tachiyomi.util.system.toast
import mihon.core.navigation.AnilistWishlistSourcesRoute
import mihon.core.navigation.LocalNavigator
import tachiyomi.presentation.core.components.material.Scaffold

@Composable
fun AnilistWishlistScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel = metroViewModel<AnilistWishlistViewModel>()
    val state by viewModel.state.collectAsState()

    ResultEffect<AnilistWishlistMangaSelectedEvent> {
        viewModel.importManga(
            mediaId = it.mediaId,
            selectedManga = it.manga,
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            context.toast(message)
        }
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = "AniList Plan to Read",
                navigateUp = navigator::pop,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        when (val currentState = state) {
            AnilistWishlistScreenState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            AnilistWishlistScreenState.NotLoggedIn -> {
                StateMessage(
                    message = "Please log in to AniList in Settings > Tracking.",
                    paddingValues = paddingValues,
                )
            }

            is AnilistWishlistScreenState.Error -> {
                StateMessage(
                    message = currentState.message,
                    buttonLabel = "Retry",
                    onClick = viewModel::refresh,
                    paddingValues = paddingValues,
                )
            }

            is AnilistWishlistScreenState.Success -> {
                AnilistWishlistScreenContent(
                    entries = currentState.entries,
                    paddingValues = paddingValues,
                    onRefresh = viewModel::refresh,
                    onFindInSource = { entry ->
                        navigator.push(
    AnilistWishlistSourcesRoute(
        mediaId = entry.mediaId,
        query = entry.title,
        englishTitle = entry.englishTitle,
        romajiTitle = entry.romajiTitle,
    ),
)
                    },
                )
            }
        }
    }
}

@Composable
private fun StateMessage(
    message: String,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    buttonLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message)

        if (buttonLabel != null && onClick != null) {
            Button(onClick = onClick) {
                Text(buttonLabel)
            }
        }
    }
}

