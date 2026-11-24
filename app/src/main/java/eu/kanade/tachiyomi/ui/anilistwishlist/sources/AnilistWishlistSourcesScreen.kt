package eu.kanade.tachiyomi.ui.anilistwishlist.sources

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.components.AppBar
import mihon.core.navigation.AnilistWishlistSearchRoute
import mihon.core.navigation.LocalNavigator
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.FilterList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun AnilistWishlistSourcesScreen(
    mediaId: Long,
    query: String,
    englishTitle: String? = null,
    romajiTitle: String? = null,
) {
    val navigator = LocalNavigator.current
    val viewModel =
        assistedMetroViewModel<AnilistWishlistSourcesViewModel, AnilistWishlistSourcesViewModel.Factory> {
            create(
                englishTitle = englishTitle,
                romajiTitle = romajiTitle,
            )
        }
    val state by viewModel.state.collectAsState()

    val visibleSources = state.sources.filter { source ->
        !state.onlyShowHasResults ||
            state.results[source.id] != AnilistWishlistSourcesViewModel.SearchStatus.NoResults
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = "Select source",
                navigateUp = navigator::pop,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingScreen()

            state.error != null -> {
                Text(
                    text = state.error ?: "Failed to load sources",
                    modifier = Modifier.padding(paddingValues),
                )
            }

            state.sources.isEmpty() -> {
                Text(
                    text = "No installed online sources available.",
                    modifier = Modifier.padding(paddingValues),
                )
            }

            else -> {
                ScrollbarLazyColumn(
                    contentPadding = paddingValues,
                ) {
                    item {
                        FilterChip(
                            selected = state.onlyShowHasResults,
                            onClick = viewModel::toggleHasResults,
                            leadingIcon = {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(MR.strings.has_results))
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    items(
                        items = visibleSources,
                        key = { "source-${it.id}" },
                    ) { source ->
                        BaseSourceItem(
                            source = source,
                            onClickItem = {
                                navigator.push(
                                    AnilistWishlistSearchRoute(
                                        mediaId = mediaId,
                                        sourceId = source.id,
                                        query = query,
                                        englishTitle = englishTitle,
                                        romajiTitle = romajiTitle,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
