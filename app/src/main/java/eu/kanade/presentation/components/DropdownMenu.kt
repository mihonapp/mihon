package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import eu.kanade.tachiyomi.R
import me.saket.cascade.CascadeColumnScope
import me.saket.cascade.CascadeDropdownMenu
import androidx.compose.material3.DropdownMenu as ComposeDropdownMenu

@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(8.dp, (-56).dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ComposeDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.sizeIn(minWidth = 196.dp, maxWidth = 196.dp),
        offset = offset,
        properties = properties,
        content = content,
    )
}

@Composable
fun RadioMenuItem(
    text: @Composable () -> Unit,
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        trailingIcon = {
            if (isChecked) {
                Icon(
                    imageVector = Icons.Outlined.RadioButtonChecked,
                    contentDescription = stringResource(R.string.selected),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = stringResource(R.string.not_selected),
                )
            }
        },
    )
}

@Composable
fun OverflowMenu(
    content: @Composable CascadeColumnScope.(() -> Unit) -> Unit,
) {
    var moreExpanded by remember { mutableStateOf(false) }
    val closeMenu = { moreExpanded = false }

    Box {
        IconButton(onClick = { moreExpanded = !moreExpanded }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.abc_action_menu_overflow_description),
            )
        }
        CascadeDropdownMenu(
            expanded = moreExpanded,
            onDismissRequest = closeMenu,
            offset = DpOffset(8.dp, (-56).dp),
        ) {
            content(closeMenu)
        }
    }
}
