package eu.kanade.presentation.more.settings.screen.data

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import kotlinx.coroutines.flow.flow
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.api.get

@Composable
fun BaseMangaListItem(
    manga: Manga,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Square(
            modifier = Modifier
                .padding(vertical = MaterialTheme.padding.small)
                .fillMaxHeight(),
            data = manga,
        )

        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = manga.title,
                modifier = Modifier
                    .padding(start = MaterialTheme.padding.medium),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

class LibraryDebugListScreen : Screen() {

    companion object {
        const val TITLE = "Library List"
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val getFavorites: GetFavorites = Injekt.get()

        val favoritesFlow = remember { flow { emit(getFavorites.await()) } }
        val favoritesState by favoritesFlow.collectAsState(emptyList())

        Scaffold(
            topBar = {
                AppBar(
                    title = TITLE,
                    navigateUp = navigator::pop,
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_copy_to_clipboard),
                                    icon = Icons.Default.ContentCopy,
                                    onClick = {
                                        val csvData = favoritesState.joinToString("\n") { manga ->
                                            val author = manga.author ?: ""
                                            val artist = manga.artist ?: ""
                                            "${manga.title}, $author, $artist"
                                        }
                                        context.copyToClipboard(TITLE, csvData)
                                    },
                                ),
                            ),
                        )
                    },
                )
            },
        ) { contentPadding ->

            if (favoritesState.isEmpty()) {
                EmptyScreen(
                    stringRes = MR.strings.empty_screen,
                    modifier = Modifier.padding(contentPadding),
                )
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(8.dp)
            ) {
                items(favoritesState) { manga ->
                    BaseMangaListItem(
                        manga = manga,
                    )
                }
            }
        }
    }
}
