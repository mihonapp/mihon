// Adapted from https://github.com/komikku-app/komikku/pull/1356
// Copyright Komikku contributors, Apache-2.0

package eu.kanade.presentation.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

@Composable
fun CategoryScreen(
    state: CategoryScreenState.Success,
    onClickCreate: () -> Unit,
    onClickRename: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onChangeOrder: (List<Category>) -> Unit,
    navigateUp: () -> Unit,
) {
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
                lazyListState = lazyListState,
                onCreate = onClickCreate,
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
            lazyListState = lazyListState,
            paddingValues = paddingValues,
            onClickRename = onClickRename,
            onClickDelete = onClickDelete,
            onChangeOrder = onChangeOrder,
        )
    }
}

@Composable
private fun CategoryContent(
    categories: List<Category>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onClickRename: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onChangeOrder: (List<Category>) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    val hierarchyState = remember { buildCategoryHierarchy(categories).toMutableStateList() }
    val visibleEntries = visibleCategoryHierarchy(hierarchyState, expanded)
    var hasPendingOrderChange by remember { mutableStateOf(false) }

    val reorderableState = rememberReorderableLazyListState(lazyListState, paddingValues) { from, to ->
        val currentVisibleEntries = visibleCategoryHierarchy(hierarchyState, expanded)
        val fromCategoryId = currentVisibleEntries.getOrNull(from.index)?.category?.id
            ?: return@rememberReorderableLazyListState
        val toCategoryId = currentVisibleEntries.getOrNull(to.index)?.category?.id
            ?: return@rememberReorderableLazyListState
        val reordered = moveCategoryHierarchy(hierarchyState, fromCategoryId, toCategoryId)
        if (reordered == hierarchyState) return@rememberReorderableLazyListState

        hierarchyState.clear()
        hierarchyState.addAll(reordered)
        hasPendingOrderChange = true
    }

    LaunchedEffect(categories) {
        if (!reorderableState.isAnyItemDragging) {
            hierarchyState.clear()
            hierarchyState.addAll(buildCategoryHierarchy(categories))
        }
    }

    val categoriesWithChildren = remember(categories) {
        categories.mapNotNullTo(mutableSetOf()) { it.parentId }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = paddingValues +
            topSmallPaddingValues +
            PaddingValues(horizontal = MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            items = visibleEntries,
            key = { entry -> entry.category.key },
        ) { entry ->
            ReorderableItem(reorderableState, entry.category.key) {
                val hasChildren = entry.category.id in categoriesWithChildren
                val isExpanded = expanded.contains(entry.category.id)

                CategoryListItem(
                    modifier = Modifier.animateItem(),
                    category = entry.category,
                    indentLevel = entry.depth,
                    onRename = { onClickRename(entry.category) },
                    onDelete = { onClickDelete(entry.category) },
                    hasChildren = hasChildren,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        expanded =
                            if (isExpanded) expanded - entry.category.id else expanded + entry.category.id
                    },
                    onDragStopped = {
                        if (hasPendingOrderChange) {
                            hasPendingOrderChange = false
                            onChangeOrder(hierarchyState.map { it.category })
                        }
                    },
                )
            }
        }
    }
}

private val Category.key inline get() = "category-$id"
