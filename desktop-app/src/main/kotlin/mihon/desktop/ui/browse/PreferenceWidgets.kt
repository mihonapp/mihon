package mihon.desktop.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mihon.desktop.i18n.LocalStrings

internal val PrefsHorizontalPadding = 16.dp
internal val PrefsVerticalPadding = 12.dp
internal val TrailingWidgetBuffer = 16.dp

@Composable
fun BasePreferenceWidget(
    modifier: Modifier = Modifier,
    title: String? = null,
    subcomponent: @Composable (ColumnScope.() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    widget: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 56.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = PrefsHorizontalPadding, vertical = PrefsVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.padding(end = 16.dp),
                content = { icon() },
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 16.sp,
                )
            }
            subcomponent?.invoke(this)
        }
        if (widget != null) {
            Box(
                modifier = Modifier.padding(start = TrailingWidgetBuffer),
                content = { widget() },
            )
        }
    }
}

@Composable
fun TextPreferenceWidget(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    widget: @Composable (() -> Unit)? = null,
    onPreferenceClick: (() -> Unit)? = null,
) {
    BasePreferenceWidget(
        modifier = modifier,
        title = title,
        subcomponent = if (!subtitle.isNullOrBlank()) {
            {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 10,
                )
            }
        } else {
            null
        },
        icon = if (icon != null) {
            {
                Icon(
                    imageVector = icon,
                    tint = iconTint,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        } else {
            null
        },
        onClick = onPreferenceClick,
        widget = widget,
    )
}

@Composable
fun SwitchPreferenceWidget(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean = false,
    switchTag: String? = null,
    onCheckedChanged: (Boolean) -> Unit,
) {
    TextPreferenceWidget(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        icon = icon,
        widget = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.then(
                    if (switchTag != null) Modifier.testTag(switchTag) else Modifier,
                ),
            )
        },
        onPreferenceClick = { onCheckedChanged(!checked) },
    )
}

@Composable
fun ListPreferenceWidget(
    value: String,
    title: String,
    subtitle: String?,
    icon: ImageVector? = null,
    entries: Map<String, String>,
    selectTag: String? = null,
    optionTagPrefix: String? = null,
    onValueChange: (String) -> Unit,
) {
    var isDialogShown by remember { mutableStateOf(false) }
    val strings = LocalStrings.current
    val currentLabel = entries[value] ?: value

    TextPreferenceWidget(
        title = title,
        subtitle = subtitle ?: currentLabel,
        icon = icon,
        onPreferenceClick = { isDialogShown = true },
        widget = {
            // Provide clickable target for tests expecting selectTag
            if (selectTag != null) {
                TextButton(
                    onClick = { isDialogShown = true },
                    modifier = Modifier.testTag(selectTag),
                ) {
                    Text(currentLabel.ifBlank { "Select…" })
                }
            }
        },
    )

    if (isDialogShown) {
        AlertDialog(
            onDismissRequest = { isDialogShown = false },
            title = { Text(text = title) },
            text = {
                LazyColumn {
                    entries.forEach { (entryKey, entryLabel) ->
                        val isSelected = value == entryKey
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .selectable(
                                        selected = isSelected,
                                        onClick = {
                                            onValueChange(entryKey)
                                            isDialogShown = false
                                        },
                                    )
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                    .then(
                                        if (optionTagPrefix != null) {
                                            Modifier.testTag("$optionTagPrefix-$entryKey")
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = entryLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isDialogShown = false }) {
                    Text(strings.actionCancel)
                }
            },
        )
    }
}

@Composable
fun MultiSelectListPreferenceWidget(
    values: Set<String>,
    title: String,
    subtitle: String?,
    icon: ImageVector? = null,
    entries: Map<String, String>,
    checkboxTagPrefix: String? = null,
    onValuesChange: (Set<String>) -> Unit,
) {
    var isDialogShown by remember { mutableStateOf(false) }
    val strings = LocalStrings.current

    TextPreferenceWidget(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onPreferenceClick = { isDialogShown = true },
    )

    if (isDialogShown) {
        var selectedKeys by remember(values) { mutableStateOf(values) }

        AlertDialog(
            onDismissRequest = { isDialogShown = false },
            title = { Text(text = title) },
            text = {
                LazyColumn {
                    entries.forEach { (entryKey, entryLabel) ->
                        val isChecked = entryKey in selectedKeys
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedKeys = if (isChecked) {
                                            selectedKeys - entryKey
                                        } else {
                                            selectedKeys + entryKey
                                        }
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                                    .then(
                                        if (checkboxTagPrefix != null) {
                                            Modifier.testTag("$checkboxTagPrefix-$entryKey")
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedKeys = if (checked) {
                                            selectedKeys + entryKey
                                        } else {
                                            selectedKeys - entryKey
                                        }
                                    },
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = entryLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValuesChange(selectedKeys)
                        isDialogShown = false
                    },
                ) {
                    Text(strings.actionOk)
                }
            },
            dismissButton = {
                TextButton(onClick = { isDialogShown = false }) {
                    Text(strings.actionCancel)
                }
            },
        )
    }
}

@Composable
fun EditTextPreferenceWidget(
    value: String,
    title: String,
    subtitle: String?,
    icon: ImageVector? = null,
    fieldTag: String? = null,
    validator: ((String) -> Boolean)? = null,
    onConfirm: (String) -> Unit,
) {
    var isDialogShown by remember { mutableStateOf(false) }
    var textInput by remember(value, isDialogShown) { mutableStateOf(value) }
    val strings = LocalStrings.current

    TextPreferenceWidget(
        title = title,
        subtitle = subtitle ?: value.ifBlank { "—" },
        icon = icon,
        onPreferenceClick = { isDialogShown = true },
    )

    if (isDialogShown) {
        val isValid = validator?.invoke(textInput) ?: true

        AlertDialog(
            onDismissRequest = { isDialogShown = false },
            title = { Text(text = title) },
            text = {
                Column {
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text(title) },
                        singleLine = true,
                        isError = !isValid,
                        trailingIcon = {
                            if (textInput.isNotEmpty()) {
                                IconButton(onClick = { textInput = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (fieldTag != null) Modifier.testTag(fieldTag) else Modifier,
                            ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = isValid,
                    onClick = {
                        onConfirm(textInput)
                        isDialogShown = false
                    },
                ) {
                    Text(strings.actionOk)
                }
            },
            dismissButton = {
                TextButton(onClick = { isDialogShown = false }) {
                    Text(strings.actionCancel)
                }
            },
        )
    }
}

@Composable
fun WarningBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
fun NsfwWarningDialog(
    onClickConfirm: () -> Unit,
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onClickConfirm,
        title = {
            Text(
                text = strings.extensionAgeRating,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = strings.extensionNsfwWarning,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onClickConfirm) {
                Text(strings.actionOk)
            }
        },
    )
}
