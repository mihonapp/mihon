package eu.kanade.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ContentAlpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DisabledByDefault
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.domain.manga.model.TriStateFilter

@Composable
fun TriStateItem(
    label: String,
    state: TriStateFilter,
    onClick: ((TriStateFilter) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .clickable(
                enabled = onClick != null,
                onClick = {
                    when (state) {
                        TriStateFilter.DISABLED -> onClick?.invoke(TriStateFilter.ENABLED_IS)
                        TriStateFilter.ENABLED_IS -> onClick?.invoke(TriStateFilter.ENABLED_NOT)
                        TriStateFilter.ENABLED_NOT -> onClick?.invoke(TriStateFilter.DISABLED)
                    }
                },
            )
            .fillMaxWidth()
            .padding(horizontal = TabbedDialogPaddings.Horizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val stateAlpha = if (onClick != null) 1f else ContentAlpha.disabled

        Icon(
            imageVector = when (state) {
                TriStateFilter.DISABLED -> Icons.Rounded.CheckBoxOutlineBlank
                TriStateFilter.ENABLED_IS -> Icons.Rounded.CheckBox
                TriStateFilter.ENABLED_NOT -> Icons.Rounded.DisabledByDefault
            },
            contentDescription = null,
            tint = if (state == TriStateFilter.DISABLED) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = stateAlpha)
            } else {
                when (onClick) {
                    null -> MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.disabled)
                    else -> MaterialTheme.colorScheme.primary
                }
            },
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = stateAlpha),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun SortItem(
    label: String,
    sortDescending: Boolean?,
    onClick: () -> Unit,
) {
    val arrowIcon = when (sortDescending) {
        true -> Icons.Default.ArrowDownward
        false -> Icons.Default.ArrowUpward
        null -> null
    }

    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = TabbedDialogPaddings.Horizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (arrowIcon != null) {
            Icon(
                imageVector = arrowIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun RadioItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = TabbedDialogPaddings.Horizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
