package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.reader.DesktopReaderSettings
import mihon.desktop.reader.ReaderBackgroundColor
import mihon.desktop.reader.ReaderClickAction
import mihon.desktop.reader.ReaderColorFilter
import mihon.desktop.reader.ReaderWheelBehavior
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode

private enum class ReaderSettingsPage {
    READING,
    GENERAL,
    FILTER,
}

@Composable
internal fun ReaderSettingsDialog(
    settings: DesktopReaderSettings,
    onDismiss: () -> Unit,
    onSave: (DesktopReaderSettings) -> Unit,
) {
    ReaderSettingsPanel(settings, onSave, onDismiss)
}

@Composable
internal fun ReaderSettingsPanel(
    settings: DesktopReaderSettings,
    onSave: (DesktopReaderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var draft by remember(settings) { mutableStateOf(settings) }
    var page by remember { mutableStateOf(ReaderSettingsPage.READING) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.readerSettingsDialogTitle) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SettingsTab(
                        strings.readerSettingsTabReading,
                        "reader-settings-tab-reading",
                        page == ReaderSettingsPage.READING,
                    ) { page = ReaderSettingsPage.READING }
                    SettingsTab(
                        strings.readerSettingsTabGeneral,
                        "reader-settings-tab-general",
                        page == ReaderSettingsPage.GENERAL,
                    ) { page = ReaderSettingsPage.GENERAL }
                    SettingsTab(
                        strings.readerSettingsTabFilter,
                        "reader-settings-tab-filter",
                        page == ReaderSettingsPage.FILTER,
                    ) { page = ReaderSettingsPage.FILTER }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (page) {
                        ReaderSettingsPage.READING -> ReadingSettingsPage(draft) { draft = it }
                        ReaderSettingsPage.GENERAL -> GeneralSettingsPage(draft) { draft = it }
                        ReaderSettingsPage.FILTER -> FilterSettingsPage(draft) { draft = it }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }, modifier = Modifier.testTag("reader-settings-save")) {
                Text(strings.categorySave)
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { draft = DesktopReaderSettings() },
                    modifier = Modifier.testTag("reader-settings-reset"),
                ) { Text(strings.libraryFilterReset) }
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("reader-settings-cancel")) {
                    Text(strings.dialogCancel)
                }
            }
        },
    )
}

@Composable
private fun SettingsTab(label: String, tag: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.testTag(tag)) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadingSettingsPage(settings: DesktopReaderSettings, onChange: (DesktopReaderSettings) -> Unit) {
    val strings = LocalStrings.current
    SettingModeMenu(settings.mode) { onChange(settings.copy(mode = it)) }
    SettingScaleMenu(settings.scaleMode) { onChange(settings.copy(scaleMode = it)) }
    SettingCheckBox("reader-setting-cover", settings.coverOffset, strings.readerReserveCover) {
        onChange(settings.copy(coverOffset = it))
    }
    SettingCheckBox("reader-setting-crop-paged", settings.cropBorders, strings.readerCropBordersPaged) {
        onChange(settings.copy(cropBorders = it))
    }
    SettingCheckBox("reader-setting-crop-webtoon", settings.cropBordersWebtoon, strings.readerCropBordersWebtoon) {
        onChange(settings.copy(cropBordersWebtoon = it))
    }
    Text(strings.readerWebtoonLayout, style = MaterialTheme.typography.titleSmall)
    Text(
        if (settings.webtoonMaxWidth == 0) {
            strings.readerWebtoonWidthFull
        } else {
            strings.readerWebtoonWidthDp(settings.webtoonMaxWidth)
        },
    )
    Slider(
        value = settings.webtoonMaxWidth.toFloat(),
        onValueChange = { onChange(settings.copy(webtoonMaxWidth = it.toInt())) },
        valueRange = 0f..1600f,
        modifier = Modifier.testTag("reader-setting-webtoon-max-width"),
    )
    Text(strings.readerWebtoonSidePaddingPercent(settings.webtoonSidePadding))
    Slider(
        value = settings.webtoonSidePadding.toFloat(),
        onValueChange = { onChange(settings.copy(webtoonSidePadding = it.toInt())) },
        valueRange = 0f..30f,
        modifier = Modifier.testTag("reader-setting-webtoon-side-padding"),
    )
}

@Composable
private fun GeneralSettingsPage(settings: DesktopReaderSettings, onChange: (DesktopReaderSettings) -> Unit) {
    val strings = LocalStrings.current
    Text(strings.readerClickRegions, style = MaterialTheme.typography.titleSmall)
    ClickActionMenu(strings.readerRegionLeft, "reader-setting-left-action", settings.clickRegions.leftAction) {
        onChange(settings.copy(clickRegions = settings.clickRegions.copy(leftAction = it)))
    }
    ClickActionMenu(strings.readerRegionCenter, "reader-setting-center-action", settings.clickRegions.centerAction) {
        onChange(settings.copy(clickRegions = settings.clickRegions.copy(centerAction = it)))
    }
    ClickActionMenu(strings.readerRegionRight, "reader-setting-right-action", settings.clickRegions.rightAction) {
        onChange(settings.copy(clickRegions = settings.clickRegions.copy(rightAction = it)))
    }
    Text(strings.readerLeftBoundary(settings.clickRegions.leftEndPercent))
    Slider(
        value = settings.clickRegions.leftEndPercent.toFloat(),
        onValueChange = { value ->
            val left = value.toInt().coerceIn(1, settings.clickRegions.centerEndPercent - 1)
            onChange(settings.copy(clickRegions = settings.clickRegions.copy(leftEndPercent = left)))
        },
        valueRange = 1f..98f,
        modifier = Modifier.testTag("reader-setting-left-boundary"),
    )
    Text(strings.readerCenterBoundary(settings.clickRegions.centerEndPercent))
    Slider(
        value = settings.clickRegions.centerEndPercent.toFloat(),
        onValueChange = { value ->
            val center = value.toInt().coerceIn(settings.clickRegions.leftEndPercent + 1, 99)
            onChange(settings.copy(clickRegions = settings.clickRegions.copy(centerEndPercent = center)))
        },
        valueRange = 2f..99f,
        modifier = Modifier.testTag("reader-setting-center-boundary"),
    )
    WheelMenu(settings.wheelBehavior) { onChange(settings.copy(wheelBehavior = it)) }
    Text(strings.readerChapterTransitions, style = MaterialTheme.typography.titleSmall)
    SettingCheckBox(
        "reader-setting-always-show-transition",
        settings.alwaysShowChapterTransition,
        strings.readerAlwaysShowChapterTransition,
    ) { onChange(settings.copy(alwaysShowChapterTransition = it)) }
    SettingCheckBox("reader-setting-skip-read", settings.skipReadChapters, strings.readerSkipReadChapters) {
        onChange(settings.copy(skipReadChapters = it))
    }
    SettingCheckBox("reader-setting-skip-filtered", settings.skipFilteredChapters, strings.readerSkipFilteredChapters) {
        onChange(settings.copy(skipFilteredChapters = it))
    }
    SettingCheckBox(
        "reader-setting-skip-duplicate",
        settings.skipDuplicateChapters,
        strings.readerSkipDuplicateChapters,
    ) {
        onChange(settings.copy(skipDuplicateChapters = it))
    }
}

@Composable
private fun FilterSettingsPage(settings: DesktopReaderSettings, onChange: (DesktopReaderSettings) -> Unit) {
    val strings = LocalStrings.current
    Text(strings.settingsSectionAppearance, style = MaterialTheme.typography.titleSmall)
    SettingColorFilterMenu(settings.colorFilter) { onChange(settings.copy(colorFilter = it)) }
    SettingBackgroundColorMenu(settings.backgroundColor) { onChange(settings.copy(backgroundColor = it)) }
}

@Composable
private fun SettingCheckBox(tag: String, checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(tag))
        Text(label)
    }
}

@Composable
private fun ClickActionMenu(
    label: String,
    tag: String,
    current: ReaderClickAction,
    onSelected: (ReaderClickAction) -> Unit,
) {
    val strings = LocalStrings.current
    SettingsMenu(
        tag = tag,
        label = "$label: ${strings.readerActionLabel(current)}",
        values = ReaderClickAction.entries,
        valueLabel = { strings.readerActionLabel(it) },
        onSelected = onSelected,
    )
}

@Composable
private fun WheelMenu(current: ReaderWheelBehavior, onSelected: (ReaderWheelBehavior) -> Unit) {
    val strings = LocalStrings.current
    SettingsMenu(
        "reader-setting-wheel",
        strings.readerWheelLabel(current),
        ReaderWheelBehavior.entries,
        { strings.readerWheelLabel(it) },
        onSelected,
    )
}

@Composable
private fun SettingModeMenu(current: ReadingMode, onSelected: (ReadingMode) -> Unit) {
    val strings = LocalStrings.current
    SettingsMenu(
        "reader-setting-mode",
        "${strings.settingsDefaultReadingMode}: ${strings.readerModeLabel(current)}",
        ReadingMode.entries,
        { strings.readerModeLabel(it) },
        onSelected,
        optionTag = { "reader-setting-mode-${it.name}" },
    )
}

@Composable
private fun SettingScaleMenu(current: ScaleMode, onSelected: (ScaleMode) -> Unit) {
    val strings = LocalStrings.current
    SettingsMenu(
        "reader-setting-scale",
        "${strings.settingsDefaultScaleMode}: ${strings.readerScaleLabel(current)}",
        ScaleMode.entries,
        { strings.readerScaleLabel(it) },
        onSelected,
        optionTag = { "reader-setting-scale-${it.name}" },
    )
}

@Composable
private fun SettingColorFilterMenu(current: ReaderColorFilter, onSelected: (ReaderColorFilter) -> Unit) {
    val strings = LocalStrings.current
    SettingsMenu(
        "reader-setting-filter",
        "${strings.readerColorFilter}: ${strings.readerFilterLabel(current)}",
        ReaderColorFilter.entries,
        { strings.readerFilterLabel(it) },
        onSelected,
        optionTag = { "reader-setting-filter-${it.name}" },
    )
}

@Composable
private fun SettingBackgroundColorMenu(
    current: ReaderBackgroundColor,
    onSelected: (ReaderBackgroundColor) -> Unit,
) {
    val strings = LocalStrings.current
    SettingsMenu(
        "reader-setting-bg",
        "${strings.readerBackgroundColor}: ${strings.readerBackgroundLabel(current)}",
        ReaderBackgroundColor.entries,
        { strings.readerBackgroundLabel(it) },
        onSelected,
        optionTag = { "reader-setting-bg-${it.name}" },
    )
}

@Composable
private fun <T> SettingsMenu(
    tag: String,
    label: String,
    values: Iterable<T>,
    valueLabel: (T) -> String,
    onSelected: (T) -> Unit,
    optionTag: ((T) -> String)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag(tag)) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(valueLabel(value)) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    },
                    modifier = optionTag?.let { Modifier.testTag(it(value)) } ?: Modifier,
                )
            }
        }
    }
}
