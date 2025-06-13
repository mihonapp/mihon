package mihon.feature.migration.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.fastForEach
import eu.kanade.core.util.fastFilterNot
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.AdaptiveSheet
import mihon.domain.migration.models.MigrationFlag
import mihon.feature.common.utils.getLabel
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.preference.toggle
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun MigrationConfigScreenSheet(
    preferences: SourcePreferences,
    onDismissRequest: () -> Unit,
    onStartMigration: (extraSearchQuery: String?) -> Unit,
) {
    var extraSearchQuery by rememberSaveable { mutableStateOf("") }
    val migrationFlags by preferences.migrationFlags().collectAsState()
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = MaterialTheme.padding.medium),
            ) {
                Text(
                    text = stringResource(MR.strings.migrationConfigScreen_dataToMigrateHeader),
                    style = MaterialTheme.typography.header,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.padding.extraSmall)
                        .padding(horizontal = MaterialTheme.padding.medium),
                )
                Spacer(modifier = Modifier.height(MaterialTheme.padding.extraSmall))
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium)
                        .padding(bottom = MaterialTheme.padding.extraSmall),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    MigrationFlag.entries
                        .fastFilterNot { it == MigrationFlag.REMOVE_DOWNLOAD }
                        .fastForEach { flag ->
                            val selected = flag in migrationFlags
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    preferences.migrationFlags().getAndSet { currentFlags ->
                                        if (flag in currentFlags) {
                                            currentFlags - flag
                                        } else {
                                            currentFlags + flag
                                        }
                                    }
                                },
                                label = { Text(stringResource(flag.getLabel())) },
                                leadingIcon = {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                        )
                                    }
                                },
                            )
                        }
                }
                val removeDownloads = MigrationFlag.REMOVE_DOWNLOAD in migrationFlags
                MigrationSheetSwitchItem(
                    title = stringResource(MR.strings.migrationConfigScreen_removeDownloadsTitle),
                    subtitle = null,
                    checked = removeDownloads,
                    onClick = {
                        preferences.migrationFlags().getAndSet {
                            if (removeDownloads) {
                                it - MigrationFlag.REMOVE_DOWNLOAD
                            } else {
                                it + MigrationFlag.REMOVE_DOWNLOAD
                            }
                        }
                    },
                )
                MigrationSheetDividerItem()
                OutlinedTextField(
                    value = extraSearchQuery,
                    onValueChange = { extraSearchQuery = it },
                    label = { Text(stringResource(MR.strings.migrationConfigScreen_additionalSearchQueryLabel)) },
                    supportingText = {
                        Text(stringResource(MR.strings.migrationConfigScreen_additionalSearchQuerySupportingText))
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.extraSmall,
                        ),
                )
                MigrationSheetSwitchItem(
                    title = stringResource(MR.strings.migrationConfigScreen_hideUnmatchedTitle),
                    subtitle = null,
                    preference = preferences.migrationHideUnmatched(),
                )
                MigrationSheetSwitchItem(
                    title = stringResource(MR.strings.migrationConfigScreen_hideWithoutUpdatesTitle),
                    subtitle = stringResource(MR.strings.migrationConfigScreen_hideWithoutUpdatesSubtitle),
                    preference = preferences.migrationHideWithoutUpdates(),
                )
                MigrationSheetDividerItem()
                MigrationSheetWarningItem(stringResource(MR.strings.migrationConfigScreen_enhancedOptionsWarning))
                MigrationSheetSwitchItem(
                    title = stringResource(MR.strings.migrationConfigScreen_deepSearchModeTitle),
                    subtitle = stringResource(MR.strings.migrationConfigScreen_deepSearchModeSubtitle),
                    preference = preferences.migrationDeepSearchMode(),
                )
                MigrationSheetSwitchItem(
                    title = stringResource(MR.strings.migrationConfigScreen_prioritizeByChaptersTitle),
                    subtitle = stringResource(MR.strings.migrationConfigScreen_prioritizeByChaptersSubtitle),
                    preference = preferences.migrationPrioritizeByChapters(),
                )
            }
            HorizontalDivider()
            Button(
                onClick = {
                    val cleanedExtraSearchQuery = extraSearchQuery.trim().ifBlank { null }
                    onStartMigration(cleanedExtraSearchQuery)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
            ) {
                Text(text = stringResource(MR.strings.migrationConfigScreen_continueButtonText))
            }
        }
    }
}

@Composable
private fun MigrationSheetSwitchItem(
    title: String,
    subtitle: String?,
    preference: Preference<Boolean>,
) {
    MigrationSheetSwitchItem(
        title = title,
        subtitle = subtitle,
        checked = preference.collectAsState().value,
        onClick = { preference.toggle() },
    )
}

@Composable
private fun MigrationSheetSwitchItem(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = subtitle?.let { { Text(text = subtitle) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun MigrationSheetDividerItem() {
    HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.extraSmall))
}

@Composable
private fun MigrationSheetWarningItem(
    text: String,
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.active,
            )
        },
        headlineContent = {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
