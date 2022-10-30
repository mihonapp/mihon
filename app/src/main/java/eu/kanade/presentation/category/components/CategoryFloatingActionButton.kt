package eu.kanade.presentation.category.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.ExtendedFloatingActionButton
import eu.kanade.presentation.util.isScrolledToEnd
import eu.kanade.presentation.util.isScrollingUp
import eu.kanade.tachiyomi.R

@Composable
fun CategoryFloatingActionButton(
    lazyListState: LazyListState,
    onCreate: () -> Unit,
) {
    ExtendedFloatingActionButton(
        text = { Text(text = stringResource(R.string.action_add)) },
        icon = { Icon(imageVector = Icons.Outlined.Add, contentDescription = "") },
        onClick = onCreate,
        expanded = lazyListState.isScrollingUp() || lazyListState.isScrolledToEnd(),
    )
}
