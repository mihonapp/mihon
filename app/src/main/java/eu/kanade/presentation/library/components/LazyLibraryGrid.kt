package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.plus

@Composable
fun LazyLibraryGrid(
    modifier: Modifier = Modifier,
    columns: Int,
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        modifier = modifier,
        columns = if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns),
        contentPadding = PaddingValues(8.dp) + WindowInsets.navigationBars.asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
