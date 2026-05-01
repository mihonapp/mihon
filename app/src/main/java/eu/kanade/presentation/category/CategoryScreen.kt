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
    onChangeOrder: (Category, Int) -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListStateSuper = rememberLazyListState()
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
                expanded = lazyListState.shouldExpandFAB() || lazyListStateSuper.shouldExpandFAB(),
            )
        },
    ) { paddingValues ->
        if (state.isEmpty && state.isSuperCatsEmpty) {
            EmptyScreen(
                stringRes = MR.strings.information_empty_category,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
        ) {
            Text(
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                text = stringResource(MR.strings.categories_super),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.isSuperCatsEmpty) {
                Text(
                    text = stringResource(MR.strings.information_empty_super_category),
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                )
            } else {
                CategoryContent(
                    categories = state.superCategories,
                    lazyListState = lazyListStateSuper,
                    paddingValues = paddingValues,
                    onClickRename = onClickRename,
                    onClickDelete = onClickDelete,
                    onChangeOrder = onChangeOrder,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(MaterialTheme.padding.medium),
            )

            Text(
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                text = stringResource(MR.strings.categories),
                style = MaterialTheme.typography.titleSmall,
            )
            if (state.isEmpty) {
                Text(
                    text = stringResource(MR.strings.information_empty_category),
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                )
            } else {
                CategoryContent(
                    modifier = Modifier.weight(1f, fill = true),
                    categories = state.categories,
                    lazyListState = lazyListState,
                    paddingValues = paddingValues,
                    onClickRename = onClickRename,
                    onClickDelete = onClickDelete,
                    onChangeOrder = onChangeOrder,
                )
            }
        }
    }
}

@Composable
private fun CategoryContent(
    modifier: Modifier = Modifier,
    categories: List<Category>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onClickRename: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
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
                )
            }
        }
    }
}

private val Category.key inline get() = "category-$id"
