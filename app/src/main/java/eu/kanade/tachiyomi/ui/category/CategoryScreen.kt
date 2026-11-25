package eu.kanade.tachiyomi.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.category.CategoryScreen
import eu.kanade.presentation.category.buildCategoryDescendants
import eu.kanade.presentation.category.buildCategoryHierarchy
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategoryRenameDialog
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import mihon.core.navigation.LocalNavigator
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun CategoryScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val viewModel = metroViewModel<CategoryViewModel>()

    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state is CategoryScreenState.Loading) {
        LoadingScreen()
        return
    }

    val successState = state as CategoryScreenState.Success

    CategoryScreen(
        state = successState,
        onClickCreate = { viewModel.showDialog(CategoryDialog.Create) },
        onClickRename = { viewModel.showDialog(CategoryDialog.Rename(it)) },
        onClickDelete = { viewModel.showDialog(CategoryDialog.Delete(it)) },
        onChangeOrder = viewModel::changeOrder,
        navigateUp = navigator::pop,
    )

    val parentOptions =
        remember(successState.categories) { buildCategoryHierarchy(successState.categories).map { it.category } }
    when (val dialog = successState.dialog) {
        null -> {}
        CategoryDialog.Create -> {
            CategoryCreateDialog(
                onDismissRequest = viewModel::dismissDialog,
                onCreate = viewModel::createCategory,
                parentOptions = parentOptions,
                categories = successState.categories.fastMap { it.name },
            )
        }
        is CategoryDialog.Rename -> {
            val descendants = remember(dialog.category.id, parentOptions) {
                buildCategoryDescendants(parentOptions)[dialog.category.id].orEmpty()
            }
            CategoryRenameDialog(
                onDismissRequest = viewModel::dismissDialog,
                onRename = { name, parent -> viewModel.renameCategory(dialog.category, name, parent) },
                initialParentId = dialog.category.parentId,
                parentOptions = parentOptions.filter { it.id != dialog.category.id && it !in descendants },
                categories = successState.categories.fastMap { it.name },
                category = dialog.category.name,
            )
        }
        is CategoryDialog.Delete -> {
            CategoryDeleteDialog(
                onDismissRequest = viewModel::dismissDialog,
                onDelete = { viewModel.deleteCategory(dialog.category.id) },
                category = dialog.category.name,
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            if (event is CategoryEvent.LocalizedMessage) {
                context.toast(event.stringRes)
            }
        }
    }
}
