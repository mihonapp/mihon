package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.reader.DesktopReaderSettings
import mihon.desktop.reader.ReaderClickAction
import mihon.desktop.reader.ReaderWheelBehavior
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import mihon.reader.session.ReaderState

@Composable
internal fun ReaderChrome(
    state: ReaderState,
    title: String,
    chapterTitle: String,
    settings: DesktopReaderSettings,
    visible: Boolean,
    canRetry: Boolean,
    debugEnabled: Boolean,
    onBack: () -> Unit,
    onMode: (ReadingMode) -> Unit,
    onScale: (ScaleMode) -> Unit,
    onCoverOffset: (Boolean) -> Unit,
    onZoom: (Float) -> Unit,
    onRetry: () -> Unit,
    onFullscreen: () -> Unit,
    onBorderless: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(alpha = if (visible) 1f else 0f)
            .testTag("reader-chrome")
            .semantics { readerChromeVisible = visible },
    ) {
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack, modifier = Modifier.testTag("reader-back")) { Text("Back") }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        modifier = Modifier.testTag("reader-title"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        chapterTitle,
                        modifier = Modifier.testTag("reader-chapter"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${(state.selectedIndex + 1).coerceAtMost(state.pages.size)} / ${state.pages.size}",
                    modifier = Modifier.testTag("reader-page-counter"),
                    style = MaterialTheme.typography.labelLarge,
                )
                TextButton(onClick = onFullscreen, modifier = Modifier.testTag("reader-fullscreen")) {
                    Text("Fullscreen")
                }
                TextButton(onClick = onBorderless, modifier = Modifier.testTag("reader-borderless")) {
                    Text("Borderless")
                }
                TextButton(onClick = onOpenSettings, modifier = Modifier.testTag("reader-settings")) {
                    Text("Settings")
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReadingModeMenu(state.mode, onMode)
                ScaleModeMenu(state.scaleMode, onScale)
                if (state.mode.isDualPage) {
                    TextButton(
                        onClick = { onCoverOffset(!state.coverOffset) },
                        modifier = Modifier.testTag("reader-cover-toggle"),
                    ) {
                        Text(if (state.coverOffset) "Cover offset: On" else "Cover offset: Off")
                    }
                }
                TextButton(
                    onClick = { onZoom(state.zoom - 0.25f) },
                    modifier = Modifier.testTag("reader-zoom-out"),
                ) { Text("−") }
                TextButton(onClick = { onZoom(1f) }, modifier = Modifier.testTag("reader-zoom-reset")) {
                    Text("${(state.zoom * 100).toInt()}%")
                }
                TextButton(
                    onClick = { onZoom(state.zoom + 0.25f) },
                    modifier = Modifier.testTag("reader-zoom-in"),
                ) { Text("+") }
                if (canRetry) {
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("reader-retry")) { Text("Retry") }
                }
                if (debugEnabled) {
                    Text(
                        "Core ${state.cacheMetrics.residentBytes / MIB} MiB · " +
                            "pinned ${state.cacheMetrics.pinnedBytes / MIB} MiB",
                        modifier = Modifier.testTag("reader-cache-diagnostic"),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingModeMenu(current: ReadingMode, onSelected: (ReadingMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("reader-mode-menu")) {
            Text(modeLabel(current))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReadingMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(modeLabel(mode)) },
                    onClick = {
                        expanded = false
                        onSelected(mode)
                    },
                    modifier = Modifier.testTag("reader-mode-${mode.name}"),
                )
            }
        }
    }
}

@Composable
private fun ScaleModeMenu(current: ScaleMode, onSelected: (ScaleMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("reader-scale-menu")) {
            Text(scaleLabel(current))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ScaleMode.entries.forEach { scale ->
                DropdownMenuItem(
                    text = { Text(scaleLabel(scale)) },
                    onClick = {
                        expanded = false
                        onSelected(scale)
                    },
                    modifier = Modifier.testTag("reader-scale-${scale.name}"),
                )
            }
        }
    }
}

@Composable
internal fun ReaderSettingsDialog(
    settings: DesktopReaderSettings,
    onDismiss: () -> Unit,
    onSave: (DesktopReaderSettings) -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reader settings") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Click regions", style = MaterialTheme.typography.titleSmall)
                ClickActionMenu("Left", "reader-setting-left-action", draft.clickRegions.leftAction) {
                    draft = draft.copy(clickRegions = draft.clickRegions.copy(leftAction = it))
                }
                ClickActionMenu("Center", "reader-setting-center-action", draft.clickRegions.centerAction) {
                    draft = draft.copy(clickRegions = draft.clickRegions.copy(centerAction = it))
                }
                ClickActionMenu("Right", "reader-setting-right-action", draft.clickRegions.rightAction) {
                    draft = draft.copy(clickRegions = draft.clickRegions.copy(rightAction = it))
                }
                Text("Left boundary ${draft.clickRegions.leftEndPercent}%")
                Slider(
                    value = draft.clickRegions.leftEndPercent.toFloat(),
                    onValueChange = { value ->
                        val left = value.toInt().coerceIn(1, draft.clickRegions.centerEndPercent - 1)
                        draft = draft.copy(clickRegions = draft.clickRegions.copy(leftEndPercent = left))
                    },
                    valueRange = 1f..98f,
                    modifier = Modifier.testTag("reader-setting-left-boundary"),
                )
                Text("Center boundary ${draft.clickRegions.centerEndPercent}%")
                Slider(
                    value = draft.clickRegions.centerEndPercent.toFloat(),
                    onValueChange = { value ->
                        val center = value.toInt().coerceIn(draft.clickRegions.leftEndPercent + 1, 99)
                        draft = draft.copy(clickRegions = draft.clickRegions.copy(centerEndPercent = center))
                    },
                    valueRange = 2f..99f,
                    modifier = Modifier.testTag("reader-setting-center-boundary"),
                )
                WheelMenu(draft.wheelBehavior) { draft = draft.copy(wheelBehavior = it) }
                SettingModeMenu(draft.mode) { draft = draft.copy(mode = it) }
                SettingScaleMenu(draft.scaleMode) { draft = draft.copy(scaleMode = it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.coverOffset,
                        onCheckedChange = { draft = draft.copy(coverOffset = it) },
                        modifier = Modifier.testTag("reader-setting-cover"),
                    )
                    Text("Reserve cover in dual-page modes")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft) },
                modifier = Modifier.testTag("reader-settings-save"),
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { draft = DesktopReaderSettings() },
                    modifier = Modifier.testTag("reader-settings-reset"),
                ) { Text("Reset") }
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("reader-settings-cancel")) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun ClickActionMenu(
    label: String,
    tag: String,
    current: ReaderClickAction,
    onSelected: (ReaderClickAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag(tag)) {
            Text("$label: ${current.name.lowercase()}")
        }
        DropdownMenu(expanded, { expanded = false }) {
            ReaderClickAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.name.lowercase()) },
                    onClick = {
                        expanded = false
                        onSelected(action)
                    },
                )
            }
        }
    }
}

@Composable
private fun WheelMenu(current: ReaderWheelBehavior, onSelected: (ReaderWheelBehavior) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("reader-setting-wheel")) {
            Text("Wheel: ${current.name.lowercase()}")
        }
        DropdownMenu(expanded, { expanded = false }) {
            ReaderWheelBehavior.entries.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value.name.lowercase()) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingModeMenu(current: ReadingMode, onSelected: (ReadingMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("reader-setting-mode")) {
            Text("Mode: ${modeLabel(current)}")
        }
        DropdownMenu(expanded, { expanded = false }) {
            ReadingMode.entries.forEach { value ->
                DropdownMenuItem(
                    text = { Text(modeLabel(value)) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingScaleMenu(current: ScaleMode, onSelected: (ScaleMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("reader-setting-scale")) {
            Text("Scale: ${scaleLabel(current)}")
        }
        DropdownMenu(expanded, { expanded = false }) {
            ScaleMode.entries.forEach { value ->
                DropdownMenuItem(
                    text = { Text(scaleLabel(value)) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    },
                )
            }
        }
    }
}

private fun modeLabel(mode: ReadingMode): String = when (mode) {
    ReadingMode.SINGLE_LTR -> "Single LTR"
    ReadingMode.SINGLE_RTL -> "Single RTL"
    ReadingMode.DUAL_LTR -> "Dual LTR"
    ReadingMode.DUAL_RTL -> "Dual RTL"
    ReadingMode.VERTICAL -> "Vertical"
    ReadingMode.WEBTOON -> "Webtoon"
}

private fun scaleLabel(scale: ScaleMode): String = when (scale) {
    ScaleMode.ORIGINAL -> "Original"
    ScaleMode.FIT_WIDTH -> "Fit width"
    ScaleMode.FIT_HEIGHT -> "Fit height"
}

private const val MIB = 1024L * 1024L
