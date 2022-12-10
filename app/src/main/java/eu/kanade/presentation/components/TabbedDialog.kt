package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.launch

object TabbedDialogPaddings {
    val Horizontal = 24.dp
    val Vertical = 8.dp
}

@Composable
fun TabbedDialog(
    onDismissRequest: () -> Unit,
    tabTitles: List<String>,
    tabOverflowMenuContent: (@Composable ColumnScope.(() -> Unit) -> Unit)? = null,
    content: @Composable (PaddingValues, Int) -> Unit,
) {
    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
    ) { contentPadding ->
        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState()

        Column {
            Row {
                TabRow(
                    modifier = Modifier.weight(1f),
                    selectedTabIndex = pagerState.currentPage,
                    indicator = { TabIndicator(it[pagerState.currentPage]) },
                    divider = {},
                ) {
                    tabTitles.fastForEachIndexed { i, tab ->
                        val selected = pagerState.currentPage == i
                        Tab(
                            selected = selected,
                            onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                            text = {
                                Text(
                                    text = tab,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                        )
                    }
                }

                tabOverflowMenuContent?.let { MoreMenu(it) }
            }
            Divider()

            val density = LocalDensity.current
            var largestHeight by rememberSaveable { mutableStateOf(0f) }
            HorizontalPager(
                modifier = Modifier.heightIn(min = largestHeight.dp),
                count = tabTitles.size,
                state = pagerState,
                verticalAlignment = Alignment.Top,
            ) { page ->
                Box(
                    modifier = Modifier.onSizeChanged {
                        with(density) {
                            val heightDp = it.height.toDp()
                            if (heightDp.value > largestHeight) {
                                largestHeight = heightDp.value
                            }
                        }
                    },
                ) {
                    content(contentPadding, page)
                }
            }
        }
    }
}

@Composable
private fun MoreMenu(
    content: @Composable ColumnScope.(() -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.label_more),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            content { expanded = false }
        }
    }
}
