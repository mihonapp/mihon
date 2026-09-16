package mihon.desktop.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.SourcePreferenceDefinition
import mihon.desktop.extension.SourcePreferenceType
import mihon.desktop.extension.SourcePreferencesSnapshot
import mihon.desktop.extension.decodeSourcePreferenceListValue
import mihon.desktop.extension.encodeSourcePreferenceListValue
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.extension.model.SourceDescriptor

/**
 * Desktop pixel-perfect replication of upstream Mihon's `SourcePreferencesScreen`.
 * Renders source settings with Material 3 styling, authoritative extension-host state,
 * and seamless fallback support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcePreferencesScreen(
    source: SourceDescriptor,
    definitions: List<SourcePreferenceDefinition>,
    values: Map<String, String>,
    onBack: () -> Unit,
    onValueChange: (String, String) -> Unit,
    preferencesProvider: (suspend (Long) -> SourcePreferencesSnapshot?)? = null,
    preferenceSetter: (suspend (Long, String, String) -> Unit)? = null,
    loadPreferences: (suspend (Long) -> SourcePreferencesSnapshot?)? = null,
    onSetPreference: (suspend (Long, String, String) -> Unit)? = null,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val activeManager = remember(source.id) { DesktopSourceManager.findActiveManagerForSource(source.id) }
    val explicitProvider = preferencesProvider ?: loadPreferences
    val explicitSetter = preferenceSetter ?: onSetPreference
    val globalProvider: (suspend (Long) -> SourcePreferencesSnapshot?)? = remember(activeManager) {
        activeManager?.let { manager ->
            { sourceId: Long -> manager.getSourcePreferencesSnapshot(sourceId) }
        }
    }
    val provider by rememberUpdatedState(explicitProvider ?: globalProvider)

    var remoteSnapshot by remember(source.id) { mutableStateOf<SourcePreferencesSnapshot?>(null) }
    var localValues by remember(source.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember(source.id) { mutableStateOf(provider != null) }
    var loadError by remember(source.id) { mutableStateOf<String?>(null) }
    var saveError by remember(source.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(source.id, provider != null) {
        val currentProvider = provider ?: return@LaunchedEffect
        isLoading = true
        loadError = null
        try {
            val snapshot = currentProvider(source.id)
            if (snapshot != null) {
                remoteSnapshot = snapshot
                if (snapshot.supported) {
                    localValues = snapshot.definitions.associate { definition ->
                        definition.key to (definition.currentValue ?: definition.defaultValue)
                    }
                } else {
                    localValues = emptyMap()
                }
            } else {
                loadError = strings.sourcePreferencesEmpty
            }
        } catch (e: Exception) {
            loadError = e.message ?: strings.sourcePreferencesEmpty
        } finally {
            isLoading = false
        }
    }

    val remoteSupported = remoteSnapshot?.supported == true
    val effectiveDefinitions = if (remoteSupported) remoteSnapshot?.definitions.orEmpty() else definitions
    val showUnsupported = remoteSnapshot != null && !remoteSupported && definitions.isEmpty()
    val globalSetter: (
        suspend (
            Long,
            String,
            String,
        ) -> Unit
    )? = remember(activeManager, remoteSupported, explicitProvider) {
        if (explicitProvider == null && remoteSupported) {
            activeManager?.let { manager ->
                { sourceId: Long, key: String, value: String -> manager.setSourcePreference(sourceId, key, value) }
            }
        } else {
            null
        }
    }
    val setter by rememberUpdatedState(explicitSetter ?: globalSetter)

    val handleValueChange: (String, String) -> Unit = { key, value ->
        localValues = localValues + (key to value)
        onValueChange(key, value)
        val currentSetter = setter
        if (currentSetter != null) {
            scope.launch {
                try {
                    currentSetter(source.id, key, value)
                    remoteSnapshot = remoteSnapshot?.let { snapshot ->
                        snapshot.copy(
                            definitions = snapshot.definitions.map { definition ->
                                if (definition.key == key) definition.copy(currentValue = value) else definition
                            },
                        )
                    }
                    saveError = null
                } catch (e: Exception) {
                    saveError = e.message ?: strings.sourcePreferencesSaveError
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("source-preferences-screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = source.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("source-preferences-title"),
                        )
                        Text(
                            text = strings.sourcePreferencesTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("source-preferences-back"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = strings.mangaDetailBack,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = contentPadding,
        ) {
            when {
                isLoading && remoteSnapshot == null -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.testTag("source-preferences-loading"))
                        }
                    }
                }

                loadError != null && effectiveDefinitions.isEmpty() -> {
                    item {
                        PreferencesStateMessage(
                            tag = "source-preferences-error",
                            title = strings.sourcePreferencesEmpty,
                            detail = loadError.orEmpty(),
                        )
                    }
                }

                showUnsupported -> {
                    item {
                        PreferencesStateMessage(
                            tag = "source-preferences-unsupported",
                            title = strings.sourcePreferencesUnsupported,
                            detail = strings.text(UiText.SourceNoDesktopSettings),
                        )
                    }
                }

                effectiveDefinitions.isEmpty() -> {
                    item {
                        PreferencesStateMessage(
                            tag = "source-preferences-empty",
                            title = strings.sourcePreferencesEmpty,
                            detail = strings.sourcePreferencesEmpty,
                        )
                    }
                }

                else -> {
                    if (saveError != null) {
                        item {
                            Text(
                                text = saveError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .testTag("source-preferences-save-error"),
                            )
                        }
                    }

                    items(effectiveDefinitions, key = { it.key }) { definition ->
                        val value = localValues[definition.key]
                            ?: values[definition.key]
                            ?: definition.currentValue
                            ?: definition.defaultValue

                        when {
                            definition.isReadOnly || definition.type == SourcePreferenceType.Unsupported -> {
                                ReadOnlyPreference(definition = definition, value = value)
                            }

                            definition.type == SourcePreferenceType.Boolean -> {
                                BooleanPreference(
                                    definition = definition,
                                    value = value.toBooleanStrictOrNull() ?: false,
                                    onValueChange = handleValueChange,
                                )
                            }

                            definition.type == SourcePreferenceType.Select -> {
                                SelectPreference(
                                    definition = definition,
                                    value = value,
                                    onValueChange = handleValueChange,
                                )
                            }

                            definition.type == SourcePreferenceType.List -> {
                                ListPreference(
                                    definition = definition,
                                    value = value,
                                    onValueChange = handleValueChange,
                                )
                            }

                            else -> {
                                TextPreference(
                                    definition = definition,
                                    value = value,
                                    onValueChange = handleValueChange,
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PreferencesStateMessage(
    tag: String,
    title: String,
    detail: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag(tag),
            )
            if (detail.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun BooleanPreference(
    definition: SourcePreferenceDefinition,
    value: Boolean,
    onValueChange: (String, String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onValueChange(definition.key, (!value).toString()) }
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .testTag("source-preference-${definition.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = definition.title,
                style = MaterialTheme.typography.titleMedium,
            )
            if (definition.summary.isNotBlank()) {
                Text(
                    text = definition.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = value,
            onCheckedChange = { onValueChange(definition.key, it.toString()) },
            modifier = Modifier.testTag("source-preference-switch-${definition.key}"),
        )
    }
}

@Composable
private fun TextPreference(
    definition: SourcePreferenceDefinition,
    value: String,
    onValueChange: (String, String) -> Unit,
) {
    val acceptsInput: (String) -> Boolean = when (definition.type) {
        SourcePreferenceType.Int, SourcePreferenceType.Long -> { input -> input.matches(Regex("-?\\d*")) }
        SourcePreferenceType.Float -> { input -> input.matches(Regex("-?\\d*\\.?\\d*")) }
        else -> { _ -> true }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp)
            .testTag("source-preference-${definition.key}"),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { input -> if (acceptsInput(input)) onValueChange(definition.key, input) },
            label = { Text(definition.title) },
            supportingText = if (definition.summary.isNotBlank()) {
                { Text(definition.summary) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("source-preference-field-${definition.key}"),
        )
    }
}

@Composable
private fun SelectPreference(
    definition: SourcePreferenceDefinition,
    value: String,
    onValueChange: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = definition.options.firstOrNull { it.value == value }?.label ?: value

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp)
            .testTag("source-preference-${definition.key}"),
    ) {
        Text(
            text = definition.title,
            style = MaterialTheme.typography.titleMedium,
        )
        if (definition.summary.isNotBlank()) {
            Text(
                text = definition.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = definition.options.isNotEmpty(),
                modifier = Modifier.testTag("source-preference-select-${definition.key}"),
            ) {
                Text(selectedLabel.ifBlank { "Select…" })
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                definition.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            onValueChange(definition.key, option.value)
                        },
                        modifier = Modifier.testTag(
                            "source-preference-select-option-${definition.key}-${option.value}",
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListPreference(
    definition: SourcePreferenceDefinition,
    value: String,
    onValueChange: (String, String) -> Unit,
) {
    val selected = remember(value) { decodeSourcePreferenceListValue(value).toSet() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp)
            .testTag("source-preference-${definition.key}"),
    ) {
        Text(
            text = definition.title,
            style = MaterialTheme.typography.titleMedium,
        )
        if (definition.summary.isNotBlank()) {
            Text(
                text = definition.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (definition.options.isEmpty()) {
            Text(
                text = value.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("source-preference-readonly-${definition.key}"),
            )
        } else {
            definition.options.forEach { option ->
                val checked = option.value in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val updated = if (checked) selected - option.value else selected + option.value
                            onValueChange(definition.key, encodeSourcePreferenceListValue(updated))
                        }
                        .padding(vertical = 4.dp)
                        .testTag("source-preference-list-${definition.key}-${option.value}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { isChecked ->
                            val updated = if (isChecked) selected + option.value else selected - option.value
                            onValueChange(definition.key, encodeSourcePreferenceListValue(updated))
                        },
                        modifier = Modifier.testTag(
                            "source-preference-list-checkbox-${definition.key}-${option.value}",
                        ),
                    )
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyPreference(
    definition: SourcePreferenceDefinition,
    value: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp)
            .testTag("source-preference-${definition.key}"),
    ) {
        Text(
            text = definition.title,
            style = MaterialTheme.typography.titleMedium,
        )
        if (definition.summary.isNotBlank()) {
            Text(
                text = definition.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 4.dp)
                .testTag("source-preference-readonly-${definition.key}"),
        )
    }
}
