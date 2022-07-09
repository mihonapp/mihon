package eu.kanade.presentation.category

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.category.components.CategoryContent
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.CategoryRenameDialog
import eu.kanade.presentation.category.components.CategoryTopAppBar
import eu.kanade.presentation.components.EmptyScreen
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
    val topAppBarScrollState = rememberTopAppBarScrollState()
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarScrollState)
    Scaffold(
        modifier = Modifier
            .statusBarsPadding()
            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = {
            CategoryTopAppBar(
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                navigateUp = navigateUp,
            )
        },
        floatingActionButton = {
            CategoryFloatingActionButton(
                lazyListState = lazyListState,
                onCreate = { presenter.dialog = CategoryPresenter.Dialog.Create },
            )
        },
    ) { paddingValues ->
        val context = LocalContext.current
        val categories by presenter.categories.collectAsState(initial = emptyList())
        if (categories.isEmpty()) {
            EmptyScreen(textResource = R.string.information_empty_category)
        } else {
            CategoryContent(
                categories = categories,
                lazyListState = lazyListState,
                paddingValues = paddingValues + topPaddingValues + PaddingValues(horizontal = horizontalPadding),
                onMoveUp = { presenter.moveUp(it) },
                onMoveDown = { presenter.moveDown(it) },
                onRename = { presenter.dialog = Dialog.Rename(it) },
                onDelete = { presenter.dialog = Dialog.Delete(it) },
            )
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
