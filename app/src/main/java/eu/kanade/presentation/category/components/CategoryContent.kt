package eu.kanade.presentation.category.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.domain.category.model.Category
import eu.kanade.presentation.category.CategoryState
import eu.kanade.presentation.components.LazyColumn
import eu.kanade.tachiyomi.ui.category.CategoryPresenter.Dialog

@Composable
fun CategoryContent(
    state: CategoryState,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onMoveUp: (Category) -> Unit,
    onMoveDown: (Category) -> Unit,
) {
    val categories = state.categories
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = categories,
            key = { _, category -> category.id },
        ) { index, category ->
            CategoryListItem(
                modifier = Modifier.animateItemPlacement(),
                category = category,
                canMoveUp = index != 0,
                canMoveDown = index != categories.lastIndex,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onRename = { state.dialog = Dialog.Rename(category) },
                onDelete = { state.dialog = Dialog.Delete(category) },
            )
        }
    }
}
