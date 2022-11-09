package eu.kanade.presentation.history

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.kanade.domain.history.model.HistoryWithRelations
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.history.components.HistoryContent
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.history.HistoryScreenModel
import eu.kanade.tachiyomi.ui.history.HistoryState
import eu.kanade.tachiyomi.widget.TachiyomiBottomNavigationView
import java.util.Date

@Composable
fun HistoryScreen(
    state: HistoryState,
    snackbarHostState: SnackbarHostState,
    incognitoMode: Boolean,
    downloadedOnlyMode: Boolean,
    onSearchQueryChange: (String?) -> Unit,
    onClickCover: (mangaId: Long) -> Unit,
    onClickResume: (mangaId: Long, chapterId: Long) -> Unit,
    onDialogChange: (HistoryScreenModel.Dialog?) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            SearchToolbar(
                titleContent = { AppBarTitle(stringResource(R.string.history)) },
                searchQuery = state.searchQuery,
                onChangeSearchQuery = onSearchQueryChange,
                actions = {
                    IconButton(onClick = { onDialogChange(HistoryScreenModel.Dialog.DeleteAll) }) {
                        Icon(
                            Icons.Outlined.DeleteSweep,
                            contentDescription = stringResource(R.string.pref_clear_history),
                        )
                    }
                },
                downloadedOnlyMode = downloadedOnlyMode,
                incognitoMode = incognitoMode,
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = TachiyomiBottomNavigationView.withBottomNavInset(ScaffoldDefaults.contentWindowInsets),
    ) { contentPadding ->
        state.list.let {
            if (it == null) {
                LoadingScreen(modifier = Modifier.padding(contentPadding))
            } else if (it.isEmpty()) {
                EmptyScreen(
                    textResource = R.string.information_no_recent_manga,
                    modifier = Modifier.padding(contentPadding),
                )
            } else {
                HistoryContent(
                    history = it,
                    contentPadding = contentPadding,
                    onClickCover = { history -> onClickCover(history.mangaId) },
                    onClickResume = { history -> onClickResume(history.mangaId, history.chapterId) },
                    onClickDelete = { item -> onDialogChange(HistoryScreenModel.Dialog.Delete(item)) },
                )
            }
        }
    }
}

sealed class HistoryUiModel {
    data class Header(val date: Date) : HistoryUiModel()
    data class Item(val item: HistoryWithRelations) : HistoryUiModel()
}
