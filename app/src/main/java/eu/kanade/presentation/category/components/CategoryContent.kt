package eu.kanade.presentation.category.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import eu.kanade.domain.category.model.Category
import eu.kanade.presentation.components.LazyColumn

@Composable
fun CategoryContent(
    categories: List<Category>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onMoveUp: (Category) -> Unit,
    onMoveDown: (Category) -> Unit,
    onRename: (Category) -> Unit,
    onDelete: (Category) -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(categories) { index, category ->
            CategoryListItem(
                category = category,
                canMoveUp = index != 0,
                canMoveDown = index != categories.lastIndex,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onRename = onRename,
                onDelete = onDelete,
            )
        }
    }
}
