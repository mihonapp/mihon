package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.kanade.presentation.components.FastScrollLazyVerticalGrid
import eu.kanade.presentation.components.TextButton
import eu.kanade.presentation.util.bottomNavPaddingValues
import eu.kanade.presentation.util.plus
import eu.kanade.tachiyomi.R

@Composable
fun LazyLibraryGrid(
    modifier: Modifier = Modifier,
    columns: Int,
    content: LazyGridScope.() -> Unit,
) {
    FastScrollLazyVerticalGrid(
        columns = if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = bottomNavPaddingValues + PaddingValues(end = 12.dp, start = 12.dp, bottom = 2.dp, top = 12.dp),
        topContentPadding = bottomNavPaddingValues.calculateTopPadding(),
        endContentPadding = bottomNavPaddingValues.calculateEndPadding(LocalLayoutDirection.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

fun LazyGridScope.globalSearchItem(
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    if (searchQuery.isNullOrEmpty().not()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            TextButton(onClick = onGlobalSearchClicked) {
                Text(
                    text = stringResource(R.string.action_global_search_query, searchQuery!!),
                    modifier = Modifier.zIndex(99f),
                )
            }
        }
    }
}
