package eu.kanade.tachiyomi.ui.storage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.storage.StorageScreenContent
import eu.kanade.presentation.more.storage.StorageScreenState
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class StorageScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { StorageScreenModel() }
        val state by screenModel.state.collectAsState()
        val selectedCategory by screenModel.selectedCategory.collectAsState()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.pref_storage_overview),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            if (state is StorageScreenState.Loading) {
                LoadingScreen(
                    percentage = (state as StorageScreenState.Loading).progress,
                    message = MR.strings.calculating_storage_overview,
                )
                return@Scaffold
            }

            StorageScreenContent(
                state = state as StorageScreenState.Success,
                selectedCategory = selectedCategory,
                paddingValues = paddingValues,
                onCategorySelected = screenModel::setSelectedCategory,
                onDelete = screenModel::deleteManga,
                onClickCover = { item -> navigator.push(MangaScreen(item.manga.id)) },
            )
        }
    }
}
