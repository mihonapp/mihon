package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.util.system.isReleaseBuildType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.WheelTextPicker
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun DeleteChaptersDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.are_you_sure))
        },
        text = {
            Text(text = stringResource(MR.strings.confirm_delete_chapters))
        },
    )
}

@Composable
private fun GlossaryDialog(
    initialText: String,
    onDismissRequest: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(text)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_save))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.translation_glossary_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                Text(text = stringResource(MR.strings.translation_glossary_help))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 10,
                )
            }
        },
    )
}

@Composable
fun DeleteTranslatedChapterDialog(
    chapterName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(text = stringResource(MR.strings.action_delete))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.translation_delete_title))
        },
        text = {
            Text(text = stringResource(MR.strings.translation_delete_text, chapterName))
        },
    )
}

@Composable
fun TranslateChapterDialog(
    pageCount: Int,
    nextPageCounts: List<Int>,
    targetLanguage: String,
    overlayMode: Boolean,
    onlyWhenIdleDefault: Boolean,
    glossaryText: String,
    onGlossarySave: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: (extraChapters: Int, onlyWhenIdle: Boolean) -> Unit,
) {
    var glossary by remember { mutableStateOf(glossaryText) }
    var editingGlossary by remember { mutableStateOf(false) }
    var extraChapters by rememberSaveable { mutableIntStateOf(0) }
    var onlyWhenIdle by rememberSaveable { mutableStateOf(onlyWhenIdleDefault) }

    val extraOptions = remember(nextPageCounts.size) {
        listOf(0, 1, 3, 5, 9).map { min(it, nextPageCounts.size) }.distinct()
    }
    val totalPages = pageCount + nextPageCounts.take(extraChapters).sum()

    if (editingGlossary) {
        GlossaryDialog(
            initialText = glossary,
            onDismissRequest = { editingGlossary = false },
            onSave = {
                glossary = it
                onGlossarySave(it)
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm(extraChapters, onlyWhenIdle)
                },
            ) {
                Text(text = stringResource(MR.strings.translation_confirm_action))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.translation_confirm_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                Text(text = pluralStringResource(MR.plurals.translation_confirm_pages, count = totalPages, totalPages))
                Text(text = stringResource(MR.strings.translation_confirm_privacy, targetLanguage))
                if (overlayMode) {
                    Text(text = stringResource(MR.strings.translation_confirm_privacy_overlay))
                    Text(text = stringResource(MR.strings.translation_estimate_overlay, totalPages))
                } else {
                    Text(text = stringResource(MR.strings.translation_estimate_redraw, totalPages))
                }
                TextButton(onClick = { editingGlossary = true }) {
                    Text(text = stringResource(MR.strings.translation_glossary_edit))
                }
                if (nextPageCounts.isNotEmpty()) {
                    Text(text = stringResource(MR.strings.translation_next_title))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                        extraOptions.forEach { count ->
                            FilterChip(
                                selected = extraChapters == count,
                                onClick = { extraChapters = count },
                                label = {
                                    Text(
                                        text = if (count == 0) {
                                            stringResource(MR.strings.translation_next_none)
                                        } else {
                                            stringResource(MR.strings.translation_next_count, count)
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    Text(
                        text = stringResource(MR.strings.translation_only_idle),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = onlyWhenIdle, onCheckedChange = { onlyWhenIdle = it })
                }
            }
        },
    )
}

@Composable
fun SetIntervalDialog(
    interval: Int,
    nextUpdate: Instant?,
    onDismissRequest: () -> Unit,
    onValueChanged: ((Int) -> Unit)? = null,
) {
    var selectedInterval by rememberSaveable { mutableIntStateOf(if (interval < 0) -interval else 0) }

    val nextUpdateDays = remember(nextUpdate) {
        return@remember if (nextUpdate != null) {
            val now = Clock.System.now()
            now.daysUntil(nextUpdate, TimeZone.currentSystemDefault()).coerceAtLeast(0)
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MR.strings.pref_library_update_smart_update)) },
        text = {
            Column {
                if (nextUpdateDays != null && nextUpdateDays >= 0 && interval >= 0) {
                    Text(
                        stringResource(
                            MR.strings.manga_interval_expected_update,
                            pluralStringResource(
                                MR.plurals.day,
                                count = nextUpdateDays,
                                nextUpdateDays,
                            ),
                            pluralStringResource(
                                MR.plurals.day,
                                count = interval.absoluteValue,
                                interval.absoluteValue,
                            ),
                        ),
                    )
                } else {
                    Text(
                        stringResource(MR.strings.manga_interval_expected_update_null),
                    )
                }
                Spacer(Modifier.height(MaterialTheme.padding.small))

                if (onValueChanged != null && (!isReleaseBuildType)) {
                    Text(stringResource(MR.strings.manga_interval_custom_amount))

                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val size = DpSize(width = maxWidth / 2, height = 128.dp)
                        val items = (0..FetchInterval.MAX_INTERVAL)
                            .map {
                                if (it == 0) {
                                    stringResource(MR.strings.label_default)
                                } else {
                                    it.toString()
                                }
                            }

                        WheelTextPicker(
                            items = items,
                            size = size,
                            startIndex = selectedInterval,
                            onSelectionChanged = { selectedInterval = it },
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onValueChanged?.invoke(selectedInterval)
                onDismissRequest()
            }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
