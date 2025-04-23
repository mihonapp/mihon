package eu.kanade.tachiyomi.ui.duplicates

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.duplicates.PossibleDuplicatesContent
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.isLocal

@Composable
fun Screen.possibleDuplicatesTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { PossibleDuplicatesScreenModel() }
    val onDismissRequest = { screenModel.setDialog(null) }
    val lazyListState = rememberLazyListState()
    val state by screenModel.state.collectAsState()
    val duplicatesMapState by screenModel.duplicatesMapState.collectAsState()

    return TabContent(
        titleRes = MR.strings.label_possible_duplicates,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_filter),
                icon = Icons.Outlined.FilterList,
                onClick = screenModel::showFilterDialog,
            ),
        ),
    ) { contentPadding, _ ->
        when (val dialog = state.dialog) {
            is PossibleDuplicatesScreenModel.Dialog.FilterSheet -> run {
                DuplicateFilterDialog(
                    onDismissRequest = onDismissRequest,
                    screenModel = screenModel,
                )
            }
            is PossibleDuplicatesScreenModel.Dialog.ConfirmRemove -> {
                ConfirmDeleteMangaDialog(
                    isLocalManga = dialog.manga.isLocal(),
                    onDismissRequest = onDismissRequest,
                    onConfirm = { deleteDownloads ->
                        screenModel.removeFavorite(dialog.manga, deleteDownloads)
                    },
                )
            }
            null -> {}
        }

        if (state.loading) {
            LoadingScreen(Modifier.padding(contentPadding), MR.strings.information_long_load)
            return@TabContent
        }

        if (duplicatesMapState.isEmpty()) {
            EmptyScreen(MR.strings.information_empty_possible_duplicates, happyFace = true)
            return@TabContent
        }

        PossibleDuplicatesContent(
            duplicatesMap = duplicatesMapState,
            paddingValues = contentPadding,
            lazyListState = lazyListState,
            onOpenManga = { navigator.push(MangaScreen(it.id)) },
            onDismissRequest = onDismissRequest,
            onToggleFavoriteClicked = screenModel::openDeleteMangaDialog,
            onHideSingleClicked = screenModel::hideSingleDuplicate,
            onHideGroupClicked = screenModel::hideGroupDuplicate,
        )
    }
}
