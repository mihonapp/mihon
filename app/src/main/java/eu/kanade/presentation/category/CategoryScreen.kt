package eu.kanade.presentation.category

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.category.components.CategoryContent
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.CategoryRenameDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.topPaddingValues
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.category.CategoryPresenter
import eu.kanade.tachiyomi.ui.category.CategoryPresenter.Dialog
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CategoryScreen(
    presenter: CategoryPresenter,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            AppBar(
                title = stringResource(R.string.action_edit_categories),
                navigateUp = navigateUp,
            )
        },
        floatingActionButton = {
            CategoryFloatingActionButton(
                lazyListState = lazyListState,
                onCreate = { presenter.dialog = Dialog.Create },
            )
        },
    ) { paddingValues ->
        val context = LocalContext.current
        when {
            presenter.isLoading -> LoadingScreen()
            presenter.isEmpty -> EmptyScreen(textResource = R.string.information_empty_category)
            else -> {
                CategoryContent(
                    state = presenter,
                    lazyListState = lazyListState,
                    paddingValues = paddingValues + topPaddingValues + PaddingValues(horizontal = horizontalPadding),
                    onMoveUp = { presenter.moveUp(it) },
                    onMoveDown = { presenter.moveDown(it) },
                )
            }
        }

        val onDismissRequest = { presenter.dialog = null }
        when (val dialog = presenter.dialog) {
            Dialog.Create -> {
                CategoryCreateDialog(
                    onDismissRequest = onDismissRequest,
                    onCreate = { presenter.createCategory(it) },
                )
            }
            is Dialog.Rename -> {
                CategoryRenameDialog(
                    onDismissRequest = onDismissRequest,
                    onRename = { presenter.renameCategory(dialog.category, it) },
                    category = dialog.category,
                )
            }
            is Dialog.Delete -> {
                CategoryDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = { presenter.deleteCategory(dialog.category) },
                    category = dialog.category,
                )
            }
            else -> {}
        }
        LaunchedEffect(Unit) {
            presenter.events.collectLatest { event ->
                when (event) {
                    is CategoryPresenter.Event.CategoryWithNameAlreadyExists -> {
                        context.toast(R.string.error_category_exists)
                    }
                    is CategoryPresenter.Event.InternalError -> {
                        context.toast(R.string.internal_error)
                    }
                }
            }
        }
    }
}
