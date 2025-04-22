package eu.kanade.tachiyomi.ui.duplicates

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.duplicates.HiddenDuplicatesContent
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun Screen.hiddenDuplicatesTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { HiddenDuplicatesScreenModel() }
    val onDismissRequest = { screenModel.setDialog(null) }
    val lazyListState = rememberLazyListState()
    val state by screenModel.state.collectAsState()
    val hiddenDuplicatesMapState by screenModel.hiddenDuplicatesMapState.collectAsState()

    return TabContent(
        titleRes = MR.strings.label_hidden_duplicates,
    ) { contentPadding, _ ->
        if (state.loading) {
            LoadingScreen(Modifier.padding(contentPadding))
            return@TabContent
        }

        if (hiddenDuplicatesMapState.isEmpty()) {
            EmptyScreen(MR.strings.information_empty_hidden_duplicates, happyFace = true)
            return@TabContent
        }

        HiddenDuplicatesContent(
            hiddenDuplicatesMap = hiddenDuplicatesMapState,
            paddingValues = contentPadding,
            lazyListState = lazyListState,
            onOpenManga = { navigator.push(MangaScreen(it.id)) },
            onDismissRequest = onDismissRequest,
            onUnhideSingleClicked = screenModel::unhideSingleDuplicate,
            onUnhideGroupClicked = screenModel::unhideGroupDuplicate,
        )
    }
}
