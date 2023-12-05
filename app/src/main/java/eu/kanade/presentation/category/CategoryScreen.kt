package eu.kanade.presentation.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.CategoryListItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.tachiyomi.ui.category.CategoryScreenState
import kotlinx.collections.immutable.persistentListOf
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
    onClickSortAlphabetically: () -> Unit,
    onClickRename: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onClickMoveUp: (Category) -> Unit,
    onClickMoveDown: (Category) -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.action_edit_categories),
                navigateUp = navigateUp,
                actions = {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_sort),
                                icon = Icons.Outlined.SortByAlpha,
                                onClick = onClickSortAlphabetically,
                            ),
                        ),
                    )
                },
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
            paddingValues = paddingValues +
                topSmallPaddingValues +
                PaddingValues(horizontal = MaterialTheme.padding.medium),
            onClickRename = onClickRename,
            onClickDelete = onClickDelete,
            onMoveUp = onClickMoveUp,
            onMoveDown = onClickMoveDown,
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
    onMoveUp: (Category) -> Unit,
    onMoveDown: (Category) -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        itemsIndexed(
            items = categories,
            key = { _, category -> "category-${category.id}" },
        ) { index, category ->
            CategoryListItem(
                modifier = Modifier.animateItemPlacement(),
                category = category,
                canMoveUp = index != 0,
                canMoveDown = index != categories.lastIndex,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onRename = { onClickRename(category) },
                onDelete = { onClickDelete(category) },
            )
        }
    }
}
