package eu.kanade.presentation.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.CategoryListItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.category.CategoryScreenState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus
import tachiyomi.presentation.core.util.shouldExpandFAB

@Composable
fun CategoryScreen(
    state: CategoryScreenState.Success,
    onClickCreate: () -> Unit,
    onClickRename: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onClickPin: (Category) -> Unit,
    onChangeOrder: (Category, Int) -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListStatePinned = rememberLazyListState()
    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.action_edit_categories),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            CategoryFloatingActionButton(
                onCreate = onClickCreate,
                expanded = lazyListState.shouldExpandFAB() || lazyListStatePinned.shouldExpandFAB(),
            )
        },
    ) { paddingValues ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.information_empty_category,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        CategoryContent(
            categories = state.categories,
            pinnedCategories = state.pinnedCategories,
            lazyListState = lazyListState,
            lazyListStatePinned = lazyListStatePinned,
            paddingValues = paddingValues,
            onClickRename = onClickRename,
            onClickDelete = onClickDelete,
            onClickPin = onClickPin,
            onChangeOrder = onChangeOrder,
        )
    }
}

@Composable
private fun CategoryContent(
    pinnedCategories: List<Category>,
    categories: List<Category>,
    lazyListState: LazyListState,
    lazyListStatePinned: LazyListState,
    paddingValues: PaddingValues,
    onClickRename: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onClickPin: (Category) -> Unit,
    onChangeOrder: (Category, Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize(),
    ) {
        if (pinnedCategories.isEmpty()) {
            Text(
                text = stringResource(MR.strings.information_empty_pinned_category),
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
            )
        } else {
            CategoryList(
                categories = pinnedCategories,
                lazyListState = lazyListStatePinned,
                paddingValues = paddingValues,
                onClickRename = onClickRename,
                onClickDelete = onClickDelete,
                onClickPin = onClickPin,
                onChangeOrder = onChangeOrder,
            )

            HorizontalDivider(
                modifier = Modifier.padding(MaterialTheme.padding.medium),
            )
        }

        if (categories.isEmpty()) {
            Text(
                text = stringResource(MR.strings.information_empty_category),
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
            )
        } else {
            CategoryList(
                modifier = Modifier.weight(1f, fill = true),
                categories = categories,
                lazyListState = lazyListState,
                paddingValues = paddingValues,
                onClickRename = onClickRename,
                onClickDelete = onClickDelete,
                onClickPin = onClickPin,
                onChangeOrder = onChangeOrder,
            )
        }
    }
}

@Composable
private fun CategoryList(
    categories: List<Category>,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues,
    onClickRename: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onClickPin: (Category) -> Unit,
    onChangeOrder: (Category, Int) -> Unit,
) {
    val categoriesState = remember { categories.toMutableStateList() }
    val reorderableState = rememberReorderableLazyListState(lazyListState, paddingValues) { from, to ->
        val item = categoriesState.removeAt(from.index)
        categoriesState.add(to.index, item)
        onChangeOrder(item, to.index)
    }

    LaunchedEffect(categories) {
        if (!reorderableState.isAnyItemDragging) {
            categoriesState.clear()
            categoriesState.addAll(categories)
        }
    }

    LazyColumn(
        modifier = modifier,
        state = lazyListState,
        contentPadding = topSmallPaddingValues +
            PaddingValues(horizontal = MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            items = categoriesState,
            key = { category -> category.key },
        ) { category ->
            ReorderableItem(reorderableState, category.key) {
                CategoryListItem(
                    modifier = Modifier.animateItem(),
                    category = category,
                    onRename = { onClickRename(category) },
                    onDelete = { onClickDelete(category) },
                    onPin = { onClickPin(category) },
                )
            }
        }
    }
}

private val Category.key inline get() = "category-$id"
