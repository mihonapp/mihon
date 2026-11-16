package eu.kanade.presentation.more.settings.screen.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.webview.WebViewAdBlocker
import eu.kanade.tachiyomi.ui.webview.WebViewAdBlocker.FilterError
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.MoreVert
import tachiyomi.i18n.MR
import tachiyomi.i18n.action_add
import tachiyomi.i18n.action_cancel
import tachiyomi.i18n.action_edit
import tachiyomi.i18n.action_menu_overflow_description
import tachiyomi.i18n.action_remove
import tachiyomi.i18n.action_save
import tachiyomi.i18n.webview_ad_block_add_list
import tachiyomi.i18n.webview_ad_block_built_in
import tachiyomi.i18n.webview_ad_block_custom_rules
import tachiyomi.i18n.webview_ad_block_download_failed
import tachiyomi.i18n.webview_ad_block_duplicate_url
import tachiyomi.i18n.webview_ad_block_enabled
import tachiyomi.i18n.webview_ad_block_engine_basic
import tachiyomi.i18n.webview_ad_block_engine_brave
import tachiyomi.i18n.webview_ad_block_invalid_url
import tachiyomi.i18n.webview_ad_block_list_hint
import tachiyomi.i18n.webview_ad_block_list_hint_brave
import tachiyomi.i18n.webview_ad_block_list_url
import tachiyomi.i18n.webview_ad_block_refresh_list
import tachiyomi.i18n.webview_ad_block_rules_hint
import tachiyomi.i18n.webview_ad_block_rules_hint_brave
import tachiyomi.i18n.webview_ad_block_title
import tachiyomi.i18n.webview_ad_block_too_large
import tachiyomi.i18n.webview_ad_block_unsupported_rules
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

private enum class FilterEditor { Rules, Url }

class WebViewAdBlockSettingsScreen : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val blocker = remember(context) { context.appGraph.webViewAdBlocker }
        val settings by blocker.settings.collectAsState()
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.webview_ad_block_title),
                    navigateUp = { navigator.pop() },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            val current = settings
            if (current == null) {
                LoadingScreen(Modifier.padding(padding))
            } else {
                WebViewAdBlockPreferences(blocker, current, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun WebViewAdBlockPreferences(
    blocker: WebViewAdBlocker,
    settings: WebViewAdBlocker.Settings,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var editor by rememberSaveable { mutableStateOf<FilterEditor?>(null) }
    var operation by remember { mutableStateOf<Job?>(null) }
    val processing = operation != null
    var error by remember { mutableStateOf<FilterError?>(null) }

    fun submit(action: suspend () -> FilterError?, onSuccess: () -> Unit = {}) {
        if (operation != null) return
        error = null
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                error = action()
                if (error == null) onSuccess()
            } finally {
                operation = null
            }
        }
        operation = job
        job.start()
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
            supportsBraveFilters = blocker.supportsBraveFilters,
            processing = processing,
            error = error,
            onDismiss = {
                operation?.cancel()
                error = null
                editor = null
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
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(
                if (blocker.supportsBraveFilters) {
                    MR.strings.webview_ad_block_engine_brave
                } else {
                    MR.strings.webview_ad_block_engine_basic
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        if (currentEditor == null) {
            if (processing) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
        }
    }
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
    supportsBraveFilters: Boolean,
    processing: Boolean,
    error: FilterError?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialValue) }
    val title = if (editingRules) MR.strings.webview_ad_block_custom_rules else MR.strings.webview_ad_block_add_list
    val label = if (editingRules) MR.strings.webview_ad_block_custom_rules else MR.strings.webview_ad_block_list_url
    val hint = when {
        editingRules && supportsBraveFilters -> MR.strings.webview_ad_block_rules_hint_brave
        editingRules -> MR.strings.webview_ad_block_rules_hint
        supportsBraveFilters -> MR.strings.webview_ad_block_list_hint_brave
        else -> MR.strings.webview_ad_block_list_hint
    }
    val action = if (editingRules) MR.strings.action_save else MR.strings.action_add

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                if (!editingRules && supportsBraveFilters) {
                    FlowRow {
                        TextButton(
                            onClick = { text = "https://easylist.to/easylist/easylist.txt" },
                            enabled = !processing,
                        ) { Text("EasyList") }
                        TextButton(
                            onClick = { text = "https://easylist.to/easylist/easyprivacy.txt" },
                            enabled = !processing,
                        ) { Text("EasyPrivacy") }
                    }
                }
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
