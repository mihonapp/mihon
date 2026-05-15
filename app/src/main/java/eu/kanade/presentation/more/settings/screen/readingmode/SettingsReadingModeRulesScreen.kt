package eu.kanade.presentation.more.settings.screen.readingmode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.reader.model.ReadingModeAutoRule
import tachiyomi.domain.reader.model.ReadingModeAutoRulePresets
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsReadingModeRulesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val readerPreferences = remember { Injekt.get<ReaderPreferences>() }
        val config by readerPreferences.readingModeAutoRules.collectAsState()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.pref_reading_mode_auto_rules_configure),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navigator.push(ReadingModeRuleEditScreen(ruleId = null)) },
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(MR.strings.action_add))
                }
            },
        ) { contentPadding ->
            val lazyListState = rememberLazyListState()
            val rulesState = remember { mutableStateListOf<ReadingModeAutoRule>() }
            val reorderableState = rememberReorderableLazyListState(lazyListState, contentPadding) { from, to ->
                // LazyColumn indices include the leading info item; map by stable item keys instead.
                val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
                val toKey = to.key as? String ?: return@rememberReorderableLazyListState
                val fromIdx = rulesState.indexOfFirst { it.id == fromKey }
                val toIdx = rulesState.indexOfFirst { it.id == toKey }
                if (fromIdx < 0 || toIdx < 0) return@rememberReorderableLazyListState
                rulesState.add(toIdx, rulesState.removeAt(fromIdx))
                val current = readerPreferences.readingModeAutoRules.get()
                readerPreferences.readingModeAutoRules.set(current.copy(rules = rulesState.toList()))
            }

            LaunchedEffect(config.rules) {
                if (!reorderableState.isAnyItemDragging) {
                    rulesState.clear()
                    rulesState.addAll(config.rules)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    Text(
                        text = stringResource(MR.strings.pref_reading_mode_auto_rules_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                if (config.rules.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(MR.strings.pref_reading_mode_auto_rules_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        )
                    }
                } else {
                    items(
                        items = rulesState,
                        key = { it.id },
                    ) { rule ->
                        ReorderableItem(reorderableState, rule.id) {
                            RuleRow(
                                rule = rule,
                                onToggleEnabled = { enabled ->
                                    val current = readerPreferences.readingModeAutoRules.get()
                                    readerPreferences.readingModeAutoRules.set(
                                        current.copy(
                                            rules = current.rules.map {
                                                if (it.id == rule.id) it.copy(enabled = enabled) else it
                                            },
                                        ),
                                    )
                                },
                                onOpen = { navigator.push(ReadingModeRuleEditScreen(ruleId = rule.id)) },
                                onDelete = if (rule.presetId != null) {
                                    null
                                } else {
                                    {
                                        val current = readerPreferences.readingModeAutoRules.get()
                                        readerPreferences.readingModeAutoRules.set(
                                            current.copy(rules = current.rules.filterNot { it.id == rule.id }),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.RuleRow(
    rule: ReadingModeAutoRule,
    onToggleEnabled: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val mode = ReadingMode.entries.find { it.flagValue == rule.readingModeFlag }
    val modeLabel = mode?.let { stringResource(it.stringRes) }
        ?: stringResource(MR.strings.label_default)
    val headline = when {
        rule.presetId != null -> presetRuleTitle(rule)
        else -> rule.title.ifBlank { modeLabel }
    }
    val parts = buildList {
        add(modeLabel)
        if (rule.sourceIds.isNotEmpty()) add("${rule.sourceIds.size} sources")
        val t = rule.tagsAllOf.size + rule.tagsAnyOf.size + rule.tagsNoneOf.size
        if (t > 0) add("$t tag conditions")
        val c = rule.categoriesAllOf.size + rule.categoriesAnyOf.size + rule.categoriesNoneOf.size
        if (c > 0) add("$c category conditions")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.DragHandle,
            contentDescription = stringResource(MR.strings.pref_reading_mode_auto_rules_drag_handle),
            modifier = Modifier
                .padding(end = 4.dp)
                .draggableHandle(),
        )
        Switch(
            checked = rule.enabled,
            onCheckedChange = onToggleEnabled,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(if (rule.enabled) 1f else 0.55f)
                .clickable(onClick = onOpen)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(headline, style = MaterialTheme.typography.titleMedium)
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(MR.strings.action_delete),
                )
            }
        }
    }
}

@Composable
private fun presetRuleTitle(rule: ReadingModeAutoRule): String {
    val id = rule.presetId ?: return rule.title
    return when (id) {
        ReadingModeAutoRulePresets.LONG_STRIP -> stringResource(MR.strings.pref_reading_mode_preset_long_strip)
        ReadingModeAutoRulePresets.LTR_WESTERN -> stringResource(MR.strings.pref_reading_mode_preset_ltr_western)
        ReadingModeAutoRulePresets.RTL_MANGA_JP -> stringResource(MR.strings.pref_reading_mode_preset_rtl_manga_jp)
        else -> rule.title
    }
}
