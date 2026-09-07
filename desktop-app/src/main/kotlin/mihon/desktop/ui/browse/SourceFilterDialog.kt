package mihon.desktop.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import mihon.desktop.i18n.LocalStrings
import mihon.extension.source.model.Filter
import mihon.extension.source.model.FilterList

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceFilterDialog(
    filterList: FilterList,
    onDismissRequest: () -> Unit,
    onReset: () -> Unit,
    onApply: (FilterList) -> Unit,
) {
    val strings = LocalStrings.current
    // Re-render trigger when filter state mutates
    var mutationCount by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .width(520.dp)
                .height(640.dp)
                .testTag("source-filter-dialog"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.filterDialogTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(
                        onClick = {
                            onReset()
                            mutationCount++
                        },
                        modifier = Modifier.testTag("filter-reset-btn"),
                    ) {
                        Text(strings.filterReset)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable filters
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (filterList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = strings.filterNoAvailable,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        // Keyed by mutationCount to force recomposition on in-place state changes
                        filterList.forEach { filter ->
                            FilterItem(
                                filter = filter,
                                onStateChanged = { mutationCount++ },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.testTag("filter-cancel-btn"),
                    ) {
                        Text(strings.dialogCancel)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onApply(filterList) },
                        modifier = Modifier.testTag("filter-apply-btn"),
                    ) {
                        Text(strings.filterApply)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterItem(
    filter: Filter<*>,
    onStateChanged: () -> Unit,
) {
    when (filter) {
        is Filter.Header -> {
            Text(
                text = filter.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        is Filter.Separator -> {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }
        is Filter.CheckBox -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        filter.state = !filter.state
                        onStateChanged()
                    }
                    .padding(vertical = 4.dp),
            ) {
                Checkbox(
                    checked = filter.state,
                    onCheckedChange = {
                        filter.state = it
                        onStateChanged()
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = filter.name, style = MaterialTheme.typography.bodyMedium)
            }
        }
        is Filter.Group<*> -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = filter.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val items = filter.state
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items.forEach { child ->
                        if (child is Filter.CheckBox) {
                            FilterChip(
                                selected = child.state,
                                onClick = {
                                    child.state = !child.state
                                    onStateChanged()
                                },
                                label = { Text(child.name) },
                            )
                        } else if (child is Filter.TriState) {
                            TriStateChip(
                                name = child.name,
                                state = child.state,
                                onClick = {
                                    child.state = when (child.state) {
                                        Filter.TriState.STATE_IGNORE -> Filter.TriState.STATE_INCLUDE
                                        Filter.TriState.STATE_INCLUDE -> Filter.TriState.STATE_EXCLUDE
                                        else -> Filter.TriState.STATE_IGNORE
                                    }
                                    onStateChanged()
                                },
                            )
                        }
                    }
                }
            }
        }
        is Filter.Select<*> -> {
            var expanded by remember { mutableStateOf(false) }
            val selectedIndex = filter.state.coerceIn(0, (filter.values.size - 1).coerceAtLeast(0))
            val selectedLabel = filter.values.getOrNull(selectedIndex)?.toString() ?: ""

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = filter.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = selectedLabel,
                            modifier = Modifier.weight(1f),
                        )
                        Text(text = "▼", style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        filter.values.forEachIndexed { index, value ->
                            DropdownMenuItem(
                                text = { Text(value.toString()) },
                                onClick = {
                                    filter.state = index
                                    expanded = false
                                    onStateChanged()
                                },
                            )
                        }
                    }
                }
            }
        }
        is Filter.Sort -> {
            val selection = filter.state
            var expanded by remember { mutableStateOf(false) }
            val currentIndex = selection?.index ?: 0
            val ascending = selection?.ascending ?: false
            val currentLabel = filter.values.getOrNull(currentIndex) ?: ""

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = filter.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = currentLabel,
                                modifier = Modifier.weight(1f),
                            )
                            Text(text = "▼", style = MaterialTheme.typography.labelSmall)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            filter.values.forEachIndexed { index, value ->
                                DropdownMenuItem(
                                    text = { Text(value) },
                                    onClick = {
                                        filter.state = Filter.Sort.Selection(index, ascending)
                                        expanded = false
                                        onStateChanged()
                                    },
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            filter.state = Filter.Sort.Selection(currentIndex, !ascending)
                            onStateChanged()
                        },
                    ) {
                        val strings = LocalStrings.current
                        Text(if (ascending) strings.filterAscending else strings.filterDescending)
                    }
                }
            }
        }
        is Filter.Text -> {
            OutlinedTextField(
                value = filter.state,
                onValueChange = {
                    filter.state = it
                    onStateChanged()
                },
                label = { Text(filter.name) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        is Filter.TriState -> {
            TriStateChip(
                name = filter.name,
                state = filter.state,
                onClick = {
                    filter.state = when (filter.state) {
                        Filter.TriState.STATE_IGNORE -> Filter.TriState.STATE_INCLUDE
                        Filter.TriState.STATE_INCLUDE -> Filter.TriState.STATE_EXCLUDE
                        else -> Filter.TriState.STATE_IGNORE
                    }
                    onStateChanged()
                },
            )
        }
    }
}

@Composable
private fun TriStateChip(
    name: String,
    state: Int,
    onClick: () -> Unit,
) {
    val (prefix, containerColor, labelColor) = when (state) {
        Filter.TriState.STATE_INCLUDE -> Triple(
            "+ ",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Filter.TriState.STATE_EXCLUDE -> Triple(
            "- ",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        else -> Triple(
            "",
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    FilterChip(
        selected = state != Filter.TriState.STATE_IGNORE,
        onClick = onClick,
        label = { Text(prefix + name) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = containerColor,
            selectedLabelColor = labelColor,
        ),
    )
}
