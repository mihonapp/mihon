package eu.kanade.tachiyomi.ui.reader.translation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.Edit
import tachiyomi.translation.model.EntryState
import tachiyomi.translation.model.TranslationEntry

/**
 * Bottom sheet in portrait, right rail in landscape. The header holds the drag handle, the bulk
 * editor and close in one row; items are deleted by swiping left.
 */
@Composable
fun TranslationPanel(
    controller: TranslationController,
    landscape: Boolean,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val state by controller.state.collectAsState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    var draftFraction by remember { mutableFloatStateOf(state.panelFraction) }
    var draftWidthFraction by remember { mutableFloatStateOf(state.panelWidthFraction) }
    var bulkEditing by remember { mutableStateOf(false) }

    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    Surface(
        modifier = if (landscape) {
            modifier
                .fillMaxHeight()
                .width((configuration.screenWidthDp * draftWidthFraction).dp)
        } else {
            modifier
                .fillMaxWidth()
                .height((configuration.screenHeightDp * draftFraction).dp)
        },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!landscape) {
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .fillMaxHeight()
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    draftFraction = (draftFraction - delta / screenHeightPx)
                                        .coerceIn(
                                            TranslationPreferences.MIN_PANEL_FRACTION,
                                            TranslationPreferences.MAX_PANEL_FRACTION,
                                        )
                                    // The reader's bottom padding tracks this live: a drag
                                    // must never cover art while the panel grows.
                                    controller.setPanelFractionLive(draftFraction)
                                },
                                onDragStopped = { controller.setPanelFraction(draftFraction) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                        )
                    }
                }

                // The landscape rail can be as narrow as 22% of the screen, so the title yields
                // its space to the language chip, which is the only interactive label there.
                if (!landscape) {
                    Text(
                        text = "译文",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 0.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                }

                // OCR dictionaries are per script, so the language is a real setting, not a hint.
                LanguageSelector(
                    language = state.sourceLanguage,
                    onSelect = { controller.setSourceLanguage(it) },
                )

                if (!state.translationEnabled && !landscape) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "仅原文",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = { bulkEditing = true }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Edit,
                        contentDescription = "编辑原文",
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Close,
                        contentDescription = "关闭",
                    )
                }
            }

            HorizontalDivider()

            if (state.entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "在画面上框选气泡",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // A growing transcript must follow its tail: with several long translations the
                // newest entry otherwise lands below the fold and reads as "nothing happened".
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                LaunchedEffect(state.entries.size) {
                    if (state.entries.isNotEmpty()) {
                        listState.animateScrollToItem(state.entries.lastIndex)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        SwipeableEntry(
                            entry = entry,
                            onToggle = { controller.toggleExpanded(entry.id) },
                            onCopy = { controller.copy(entry.sourceText ?: entry.translation.orEmpty()) },
                            onRetry = { controller.retry(entry.id) },
                            onDelete = { controller.delete(entry.id) },
                        )
                    }
                }
            }
        }

        if (landscape) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(16.dp)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            draftWidthFraction = (draftWidthFraction - delta / screenWidthPx)
                                .coerceIn(
                                    TranslationPreferences.MIN_WIDTH_FRACTION,
                                    TranslationPreferences.MAX_WIDTH_FRACTION,
                                )
                            controller.setPanelWidthFractionLive(draftWidthFraction)
                        },
                        onDragStopped = { controller.setPanelWidthFraction(draftWidthFraction) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )
            }
        }
        }
    }

    if (bulkEditing) {
        BulkEditDialog(
            entries = state.entries,
            onDismiss = { bulkEditing = false },
            onConfirm = { edits ->
                bulkEditing = false
                edits.forEach { (id, text) -> controller.editSource(id, text) }
            },
        )
    }
}

/**
 * Compact language chip in the panel header. Picking another language re-runs the page scan with
 * that language's OCR dictionary.
 */
@Composable
private fun LanguageSelector(
    language: TranslationSourceLanguage,
    onSelect: (TranslationSourceLanguage) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Text(
            text = "${language.label} ▾",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TranslationSourceLanguage.entries.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.label) },
                    onClick = {
                        expanded = false
                        onSelect(candidate)
                    },
                )
            }
        }
    }
}

@Composable
private fun SwipeableEntry(
    entry: TranslationEntry,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        },
    ) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            EntryRow(
                entry = entry,
                onToggle = onToggle,
                onCopy = onCopy,
                onRetry = onRetry,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: TranslationEntry,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = entry.seq.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(onClick = onToggle, onLongClick = onCopy),
        ) {
            val source = entry.sourceText
            val hasTranslation = !entry.translation.isNullOrBlank()
            // Source-only mode: the recognised text *is* the result, so it takes the primary slot
            // instead of sitting above an empty translation line.
            val sourceIsResult = !hasTranslation && entry.state == EntryState.READY && !source.isNullOrBlank()

            if (!source.isNullOrBlank() && !sourceIsResult) {
                Text(
                    text = source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = if (entry.state == EntryState.EXPANDED) 6 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when {
                entry.state == EntryState.OCR -> BusyLine("识别中…")
                entry.state == EntryState.MT -> BusyLine("翻译中…")
                entry.state == EntryState.FAILED -> Text(
                    text = entry.error?.let { "$it，点按重试" } ?: "翻译失败，点按重试",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.combinedClickable(onClick = onRetry, onLongClick = onRetry),
                )
                hasTranslation -> Text(
                    text = entry.translation.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                sourceIsResult -> Column {
                    Text(
                        text = source.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "未开启联网翻译，仅显示原文",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> Text(
                    text = entry.translation.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun BusyLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Edits every entry at once; only the ones the user actually changed are re-submitted. */
@Composable
private fun BulkEditDialog(
    entries: List<TranslationEntry>,
    onDismiss: () -> Unit,
    onConfirm: (Map<Long, String>) -> Unit,
) {
    val drafts = remember(entries) {
        mutableStateMapOf<Long, String>().apply {
            entries.forEach { put(it.id, it.sourceText.orEmpty()) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑原文") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(entries, key = { it.id }) { entry ->
                    OutlinedTextField(
                        value = drafts[entry.id].orEmpty(),
                        onValueChange = { drafts[entry.id] = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        label = { Text("${entry.seq}") },
                        maxLines = 4,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val changed = entries
                    .mapNotNull { entry ->
                        val draft = drafts[entry.id].orEmpty()
                        if (draft.trim() != entry.sourceText.orEmpty().trim()) entry.id to draft else null
                    }
                    .toMap()
                onConfirm(changed)
            }) {
                Text("重新翻译")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
