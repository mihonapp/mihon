package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.KeyboardArrowDown
import mihon.icons.materialsymbols.rounded.KeyboardArrowUp
import mihon.icons.materialsymbols.rounded.KeyboardDoubleArrowUp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun ChapterNavigationButtons(
    listState: LazyListState,
    showNavigation: Boolean,
    showJumpToTop: Boolean,
    onPageChange: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    if (!showNavigation && (!showJumpToTop || !listState.canScrollBackward)) {
        content()
        return
    }
    val scope = rememberCoroutineScope()
    val jumpToTop: @Composable () -> Unit = {
        ChapterNavigationButton(
            icon = MaterialSymbols.Rounded.KeyboardDoubleArrowUp,
            description = stringResource(MR.strings.library_jump_to_top),
            enabled = listState.canScrollBackward,
            onClick = { scope.launch { listState.scrollToItem(0) } },
        )
    }
    if (!showNavigation) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            jumpToTop()
            content()
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showJumpToTop) jumpToTop()
            ChapterNavigationButton(
                icon = MaterialSymbols.Rounded.KeyboardArrowUp,
                description = stringResource(MR.strings.spen_previous_page),
                enabled = listState.canScrollBackward,
                onClick = { onPageChange(-1) },
            )
            ChapterNavigationButton(
                icon = MaterialSymbols.Rounded.KeyboardArrowDown,
                description = stringResource(MR.strings.spen_next_page),
                enabled = listState.canScrollForward,
                onClick = { onPageChange(1) },
            )
        }
        Spacer(Modifier.weight(1f))
        content()
    }
}

@Composable
private fun ChapterNavigationButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(56.dp)) {
            Icon(icon, description)
        }
    }
}
