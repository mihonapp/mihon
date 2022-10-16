package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.Divider
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.util.isScrolledToEnd
import eu.kanade.presentation.util.isScrolledToStart
import eu.kanade.presentation.util.minimumTouchTargetSize

@Composable
fun <T> ListPreferenceWidget(
    value: T,
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    entries: Map<out T, String>,
    onValueChange: (T) -> Unit,
) {
    val (isDialogShown, showDialog) = remember { mutableStateOf(false) }

    TextPreferenceWidget(
        title = title,
        subtitle = subtitle?.format(entries[value]),
        icon = icon,
        onPreferenceClick = { showDialog(true) },
    )

    if (isDialogShown) {
        AlertDialog(
            onDismissRequest = { showDialog(false) },
            title = { Text(text = title) },
            text = {
                Box {
                    val state = rememberLazyListState()
                    ScrollbarLazyColumn(state = state) {
                        entries.forEach { current ->
                            val isSelected = value == current.key
                            item {
                                DialogRow(
                                    label = current.value,
                                    isSelected = isSelected,
                                    onSelected = {
                                        onValueChange(current.key!!)
                                        showDialog(false)
                                    },
                                )
                            }
                        }
                    }
                    if (!state.isScrolledToStart()) Divider(modifier = Modifier.align(Alignment.TopCenter))
                    if (!state.isScrolledToEnd()) Divider(modifier = Modifier.align(Alignment.BottomCenter))
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog(false) }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun DialogRow(
    label: String,
    isSelected: Boolean,
    onSelected: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .selectable(
                selected = isSelected,
                onClick = { if (!isSelected) onSelected() },
            )
            .fillMaxWidth()
            .minimumTouchTargetSize(),
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.merge(),
            modifier = Modifier.padding(start = 24.dp),
        )
    }
}
