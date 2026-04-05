package eu.kanade.presentation.more.settings.screen.readingmode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.collections.immutable.persistentListOf
import dev.icerock.moko.resources.StringResource
import tachiyomi.domain.reader.parseTagInput
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.reader.model.ReadingModeAutoRule
import tachiyomi.domain.reader.model.ReadingModeAutoRulesConfig
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import java.util.UUID

class ReadingModeRuleEditScreen(
    private val ruleId: String?,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val readerPreferences = remember { Injekt.get<ReaderPreferences>() }
        val getCategories = remember { Injekt.get<GetCategories>() }
        val sourceManager = remember { Injekt.get<SourceManager>() }

        var allCategories by remember { mutableStateOf<List<Category>>(emptyList()) }
        LaunchedEffect(Unit) {
            allCategories = getCategories.await().sortedBy { it.name }
        }

        val sources = remember {
            sourceManager.getCatalogueSources().sortedWith(
                compareBy({ it.name.lowercase(Locale.getDefault()) }, { it.lang }),
            )
        }

        var draft by remember { mutableStateOf<ReadingModeAutoRule?>(null) }
        LaunchedEffect(ruleId) {
            val cfg = readerPreferences.readingModeAutoRules.get()
            draft = when {
                ruleId != null -> {
                    val found = cfg.rules.find { it.id == ruleId }
                    if (found == null) {
                        navigator.pop()
                        return@LaunchedEffect
                    }
                    found.copy(
                        sourceIds = found.sourceIds.toList(),
                        tagsAllOf = found.tagsAllOf.toList(),
                        tagsAnyOf = found.tagsAnyOf.toList(),
                        tagsNoneOf = found.tagsNoneOf.toList(),
                        categoriesAllOf = found.categoriesAllOf.toList(),
                        categoriesAnyOf = found.categoriesAnyOf.toList(),
                        categoriesNoneOf = found.categoriesNoneOf.toList(),
                        enabled = found.enabled,
                        presetId = found.presetId,
                    )
                }
                else -> newRule()
            }
        }

        var showSourcesDialog by remember { mutableStateOf(false) }
        var showAllDialog by remember { mutableStateOf(false) }
        var showAnyDialog by remember { mutableStateOf(false) }
        var showNoneDialog by remember { mutableStateOf(false) }

        fun save() {
            val toSave = draft ?: return
            val cfg = readerPreferences.readingModeAutoRules.get()
            val nextRules = if (ruleId == null) {
                cfg.rules + toSave
            } else {
                cfg.rules.map { if (it.id == toSave.id) toSave else it }
            }
            readerPreferences.readingModeAutoRules.set(
                ReadingModeAutoRulesConfig(version = cfg.version, rules = nextRules),
            )
            navigator.pop()
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(
                        if (ruleId == null) {
                            MR.strings.pref_reading_mode_rule_new
                        } else {
                            MR.strings.pref_reading_mode_rule_edit
                        },
                    ),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_save),
                                    icon = Icons.Outlined.Check,
                                    onClick = { save() },
                                ),
                            ),
                        )
                    },
                )
            },
        ) { contentPadding ->
            val d = draft
            if (d == null) {
                return@Scaffold
            }

            MultiSelectLongDialog(
                visible = showSourcesDialog,
                title = stringResource(MR.strings.pref_reading_mode_rule_pick_sources),
                items = sources.map { it.id to formatCatalogueSourceLabel(it) },
                initialSelected = d.sourceIds.toSet(),
                onDismiss = { showSourcesDialog = false },
                onConfirm = { selected ->
                    draft = draft?.copy(sourceIds = selected.toList())
                    showSourcesDialog = false
                },
            )

            MultiSelectLongDialog(
                visible = showAllDialog,
                title = stringResource(MR.strings.pref_reading_mode_rule_pick_categories),
                items = allCategories.map { it.id to it.name },
                initialSelected = d.categoriesAllOf.toSet(),
                onDismiss = { showAllDialog = false },
                onConfirm = { selected ->
                    draft = draft?.copy(categoriesAllOf = selected.toList())
                    showAllDialog = false
                },
            )

            MultiSelectLongDialog(
                visible = showAnyDialog,
                title = stringResource(MR.strings.pref_reading_mode_rule_pick_categories),
                items = allCategories.map { it.id to it.name },
                initialSelected = d.categoriesAnyOf.toSet(),
                onDismiss = { showAnyDialog = false },
                onConfirm = { selected ->
                    draft = draft?.copy(categoriesAnyOf = selected.toList())
                    showAnyDialog = false
                },
            )

            MultiSelectLongDialog(
                visible = showNoneDialog,
                title = stringResource(MR.strings.pref_reading_mode_rule_pick_categories),
                items = allCategories.map { it.id to it.name },
                initialSelected = d.categoriesNoneOf.toSet(),
                onDismiss = { showNoneDialog = false },
                onConfirm = { selected ->
                    draft = draft?.copy(categoriesNoneOf = selected.toList())
                    showNoneDialog = false
                },
            )

            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = d.title,
                    onValueChange = { draft = draft?.copy(title = it) },
                    label = { Text(stringResource(MR.strings.pref_reading_mode_rule_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                if (d.presetId != null) {
                    Text(
                        text = stringResource(MR.strings.pref_reading_mode_rule_preset_badge),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Text(
                    text = stringResource(MR.strings.pref_reading_mode_rule_mode),
                    style = MaterialTheme.typography.titleSmall,
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ReadingMode.entries.drop(1).toList()) { mode ->
                        FilterChip(
                            selected = d.readingModeFlag == mode.flagValue,
                            onClick = { draft = draft?.copy(readingModeFlag = mode.flagValue) },
                            label = { Text(stringResource(mode.stringRes)) },
                        )
                    }
                }

                ListItem(
                    headlineContent = { Text(stringResource(MR.strings.pref_reading_mode_rule_sources)) },
                    supportingContent = {
                        Text(
                            if (d.sourceIds.isEmpty()) {
                                stringResource(MR.strings.pref_reading_mode_rule_sources_summary)
                            } else {
                                stringResource(MR.strings.pref_reading_mode_rule_sources_summary) +
                                    " (${d.sourceIds.size} selected)"
                            },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
                TextButton(onClick = { showSourcesDialog = true }) {
                    Text(stringResource(MR.strings.pref_reading_mode_rule_pick_sources))
                }

                Text(
                    text = stringResource(MR.strings.pref_reading_mode_rule_tags_section),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(MR.strings.pref_reading_mode_rule_tags_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                tagListEditor(
                    label = MR.strings.pref_reading_mode_rule_tags_all,
                    value = d.tagsAllOf,
                    onChange = { draft = draft?.copy(tagsAllOf = it) },
                )
                tagListEditor(
                    label = MR.strings.pref_reading_mode_rule_tags_any,
                    value = d.tagsAnyOf,
                    onChange = { draft = draft?.copy(tagsAnyOf = it) },
                )
                tagListEditor(
                    label = MR.strings.pref_reading_mode_rule_tags_none,
                    value = d.tagsNoneOf,
                    onChange = { draft = draft?.copy(tagsNoneOf = it) },
                )

                Text(
                    text = stringResource(MR.strings.pref_reading_mode_rule_categories_section),
                    style = MaterialTheme.typography.titleSmall,
                )
                categoryRow(
                    title = MR.strings.pref_reading_mode_rule_categories_all,
                    count = d.categoriesAllOf.size,
                    onClick = { showAllDialog = true },
                )
                categoryRow(
                    title = MR.strings.pref_reading_mode_rule_categories_any,
                    count = d.categoriesAnyOf.size,
                    onClick = { showAnyDialog = true },
                )
                categoryRow(
                    title = MR.strings.pref_reading_mode_rule_categories_none,
                    count = d.categoriesNoneOf.size,
                    onClick = { showNoneDialog = true },
                )
            }
        }
    }
}

@Composable
private fun MultiSelectLongDialog(
    visible: Boolean,
    title: String,
    items: List<Pair<Long, String>>,
    initialSelected: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
) {
    if (!visible) return

    var selection by remember {
        mutableStateOf(initialSelected.toMutableSet())
    }
    LaunchedEffect(visible, initialSelected) {
        if (visible) {
            selection = initialSelected.toMutableSet()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(items, key = { it.first }) { (id, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = id in selection,
                            onCheckedChange = { checked ->
                                selection = selection.toMutableSet().apply {
                                    if (checked) add(id) else remove(id)
                                }
                            },
                        )
                        Text(
                            text = label,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selection) }) {
                Text(stringResource(MR.strings.action_ok))
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
private fun tagListEditor(
    label: StringResource,
    value: List<String>,
    onChange: (List<String>) -> Unit,
) {
    var input by remember { mutableStateOf("") }

    fun commitInput() {
        val parts = parseTagInput(input).distinct().filter { it.isNotEmpty() && it !in value }
        if (parts.isEmpty()) return
        onChange(value + parts)
        input = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.titleSmall,
        )
        if (value.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(value, key = { index, tag -> "$index:$tag" }) { index, tag ->
                    FilterChip(
                        selected = true,
                        onClick = {
                            onChange(value.filterIndexed { i, _ -> i != index })
                        },
                        label = { Text(tag, modifier = Modifier.widthIn(max = 280.dp)) },
                        trailingIcon = {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(MR.strings.action_remove),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(MR.strings.pref_reading_mode_rule_tags_placeholder)) },
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitInput() }),
            )
            TextButton(onClick = { commitInput() }) {
                Text(stringResource(MR.strings.pref_reading_mode_rule_tags_add))
            }
        }
    }
}

@Composable
private fun categoryRow(
    title: StringResource,
    count: Int,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text("$count selected") },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

/**
 * Catalogue sources often reuse the same [CatalogueSource.name] for each language variant
 * (e.g. multiple MangaDex entries); include the language so rows are distinguishable.
 */
private fun formatCatalogueSourceLabel(source: CatalogueSource): String {
    val lang = source.lang.trim()
    if (lang.isEmpty()) return source.name
    val langDisplay = LocaleHelper.getLocalizedDisplayName(lang)
    return "${source.name} (${langDisplay})"
}

private fun newRule(): ReadingModeAutoRule = ReadingModeAutoRule(
    id = UUID.randomUUID().toString(),
    title = "",
    enabled = true,
    presetId = null,
    readingModeFlag = ReadingMode.RIGHT_TO_LEFT.flagValue,
    sourceIds = emptyList(),
    tagsAllOf = emptyList(),
    tagsAnyOf = emptyList(),
    tagsNoneOf = emptyList(),
    categoriesAllOf = emptyList(),
    categoriesAnyOf = emptyList(),
    categoriesNoneOf = emptyList(),
)