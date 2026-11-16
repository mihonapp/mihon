package eu.kanade.presentation.webview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.webview.WebViewAdBlocker
import eu.kanade.tachiyomi.ui.webview.WebViewAdBlocker.FilterError
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.MoreVert
import tachiyomi.i18n.MR
import tachiyomi.i18n.action_add
import tachiyomi.i18n.action_cancel
import tachiyomi.i18n.action_close
import tachiyomi.i18n.action_edit
import tachiyomi.i18n.action_menu_overflow_description
import tachiyomi.i18n.action_remove
import tachiyomi.i18n.action_save
import tachiyomi.i18n.action_webview_refresh
import tachiyomi.i18n.webview_ad_block_add_list
import tachiyomi.i18n.webview_ad_block_built_in
import tachiyomi.i18n.webview_ad_block_custom_rules
import tachiyomi.i18n.webview_ad_block_download_failed
import tachiyomi.i18n.webview_ad_block_duplicate_url
import tachiyomi.i18n.webview_ad_block_enabled
import tachiyomi.i18n.webview_ad_block_invalid_url
import tachiyomi.i18n.webview_ad_block_list_hint
import tachiyomi.i18n.webview_ad_block_list_url
import tachiyomi.i18n.webview_ad_block_refresh_list
import tachiyomi.i18n.webview_ad_block_rules_hint
import tachiyomi.i18n.webview_ad_block_title
import tachiyomi.i18n.webview_ad_block_too_large
import tachiyomi.i18n.webview_ad_block_unsupported_rules
import tachiyomi.presentation.core.i18n.stringResource

private enum class FilterEditor { Rules, Url }

@Composable
internal fun WebViewAdBlockDialog(
    blocker: WebViewAdBlocker,
    settings: WebViewAdBlocker.Settings,
    onDismiss: () -> Unit,
    onReload: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var editor by rememberSaveable { mutableStateOf<FilterEditor?>(null) }
    var processing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<FilterError?>(null) }

    fun submit(action: suspend () -> FilterError?, onSuccess: () -> Unit = {}) {
        if (processing) return
        processing = true
        error = null
        scope.launch {
            try {
                error = action()
                if (error == null) onSuccess()
            } finally {
                processing = false
            }
        }
    }

    fun change(action: suspend () -> Unit) = submit({
        action()
        null
    })

    val currentEditor = editor
    if (currentEditor != null) {
        val editingRules = currentEditor == FilterEditor.Rules
        FilterEditorDialog(
            editingRules = editingRules,
            initialValue = if (editingRules) settings.customRules else "",
            processing = processing,
            error = error,
            onDismiss = {
                if (processing) {
                    onDismiss()
                } else {
                    error = null
                    editor = null
                }
            },
            onSave = { value ->
                submit(
                    action = {
                        if (editingRules) blocker.saveCustomRules(value) else blocker.downloadList(value, add = true)
                    },
                    onSuccess = { editor = null },
                )
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.webview_ad_block_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AdBlockSwitch(
                    label = stringResource(MR.strings.webview_ad_block_enabled),
                    checked = settings.enabled,
                    enabled = !processing,
                    onCheckedChange = { change { blocker.setEnabled(it) } },
                )
                HorizontalDivider()
                AdBlockSwitch(
                    label = stringResource(MR.strings.webview_ad_block_built_in),
                    checked = settings.builtInEnabled,
                    enabled = !processing,
                    onCheckedChange = { change { blocker.setBuiltInEnabled(it) } },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AdBlockSwitch(
                        label = stringResource(MR.strings.webview_ad_block_custom_rules),
                        checked = settings.customRulesEnabled,
                        enabled = !processing,
                        onCheckedChange = { change { blocker.setCustomRulesEnabled(it) } },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            error = null
                            editor = FilterEditor.Rules
                        },
                        enabled = !processing,
                    ) {
                        Text(stringResource(MR.strings.action_edit))
                    }
                }

                for (list in settings.lists) {
                    key(list.url) {
                        AdBlockListRow(
                            list = list,
                            enabled = !processing,
                            onCheckedChange = { change { blocker.setListEnabled(list.url, it) } },
                            onRefresh = { submit({ blocker.downloadList(list.url, add = false) }) },
                            onRemove = { change { blocker.removeList(list.url) } },
                        )
                    }
                }
                TextButton(
                    onClick = {
                        error = null
                        editor = FilterEditor.Url
                    },
                    enabled = !processing,
                ) {
                    Text(stringResource(MR.strings.webview_ad_block_add_list))
                }
                if (processing) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onReload, enabled = !processing) {
                Text(stringResource(MR.strings.action_webview_refresh))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_close))
            }
        },
    )
}

@Composable
private fun AdBlockListRow(
    list: WebViewAdBlocker.FilterList,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AdBlockSwitch(
            label = list.url,
            checked = list.enabled,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { expanded = true }, enabled = enabled) {
                Icon(
                    MaterialSymbols.Rounded.MoreVert,
                    contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.webview_ad_block_refresh_list)) },
                    onClick = {
                        expanded = false
                        onRefresh()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.action_remove)) },
                    onClick = {
                        expanded = false
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
private fun AdBlockSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun FilterEditorDialog(
    editingRules: Boolean,
    initialValue: String,
    processing: Boolean,
    error: FilterError?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialValue) }
    val title = if (editingRules) MR.strings.webview_ad_block_custom_rules else MR.strings.webview_ad_block_add_list
    val label = if (editingRules) MR.strings.webview_ad_block_custom_rules else MR.strings.webview_ad_block_list_url
    val hint = if (editingRules) MR.strings.webview_ad_block_rules_hint else MR.strings.webview_ad_block_list_hint
    val action = if (editingRules) MR.strings.action_save else MR.strings.action_add

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(label)) },
                    supportingText = { Text(error?.message() ?: stringResource(hint)) },
                    isError = error != null,
                    enabled = !processing,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (editingRules) KeyboardType.Text else KeyboardType.Uri,
                        autoCorrectEnabled = false,
                    ),
                    singleLine = !editingRules,
                    minLines = if (editingRules) 4 else 1,
                    maxLines = if (editingRules) 8 else 1,
                )
                if (processing) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = !processing && (editingRules || text.isNotBlank()),
            ) {
                Text(stringResource(action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun FilterError.message(): String = stringResource(
    when (this) {
        FilterError.InvalidUrl -> MR.strings.webview_ad_block_invalid_url
        FilterError.DuplicateUrl -> MR.strings.webview_ad_block_duplicate_url
        FilterError.UnsupportedRules -> MR.strings.webview_ad_block_unsupported_rules
        FilterError.TooLarge -> MR.strings.webview_ad_block_too_large
        FilterError.DownloadFailed -> MR.strings.webview_ad_block_download_failed
    },
)
