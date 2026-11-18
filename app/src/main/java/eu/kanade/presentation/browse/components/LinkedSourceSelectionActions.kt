package eu.kanade.presentation.browse.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.Deselect
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun LinkedSourceSelectionActions(onApply: () -> Unit, onClear: () -> Unit, enabled: Boolean) {
    SelectionAction(
        icon = MaterialSymbols.Rounded.Deselect,
        label = stringResource(MR.strings.linked_sources_clear_selections),
        onClick = onClear,
        enabled = enabled,
    )
    SelectionAction(
        icon = MaterialSymbols.Rounded.Check,
        label = stringResource(MR.strings.action_apply),
        onClick = onApply,
        enabled = enabled,
    )
}

@Composable
private fun SelectionAction(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean) {
    TooltipBox(
        positionProvider = rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        focusable = false,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(imageVector = icon, contentDescription = label)
        }
    }
}
