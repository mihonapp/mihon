package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.reader.DesktopReaderSettings
import mihon.desktop.reader.ReaderBackgroundColor
import mihon.desktop.reader.ReaderClickAction
import mihon.desktop.reader.ReaderColorFilter
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
    onColorFilter: (ReaderColorFilter) -> Unit = {},
    onBackgroundColor: (ReaderBackgroundColor) -> Unit = {},
    onCropBorders: (Boolean) -> Unit = {},
    onCropBordersWebtoon: (Boolean) -> Unit = {},
    onPageSelected: (Int) -> Unit = {},
    onPreviousPage: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onPreviousChapter: () -> Unit = {},
    onNextChapter: () -> Unit = {},
    hasPreviousChapter: Boolean = false,
    hasNextChapter: Boolean = false,
    bookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    onOpenPageActions: () -> Unit = {},
    onOpenShortcuts: () -> Unit = {},
    chapterCatalog: List<ReaderChapterTransitionChapter> = emptyList(),
    currentChapterId: Long? = null,
    onChapterSelected: (Long) -> Unit = {},
) {
    val strings = LocalStrings.current
    var isChapterDrawerOpen by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(alpha = if (visible) 1f else 0f)
            .testTag("reader-chrome")
            .semantics {
                readerChromeVisible = visible
                readerBookmarked = bookmarked
            },
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
                IconButton(onClick = onBack, modifier = Modifier.testTag("reader-back")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = strings.mangaDetailBack,
                    )
                }
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
                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier.testTag("reader-bookmark-toggle"),
                ) {
                    Icon(
                        imageVector = if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (bookmarked) strings.removeBookmark else strings.bookmarkChapter,
                    )
                }
                IconButton(
                    onClick = onOpenPageActions,
                    modifier = Modifier.testTag("reader-page-actions"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Page actions",
                    )
                }
                IconButton(onClick = onOpenShortcuts, modifier = Modifier.testTag("reader-shortcuts-btn")) {
                    Icon(
                        imageVector = Icons.Rounded.Keyboard,
                        contentDescription = strings.readerShortcutsHelp,
                    )
                }
                IconButton(onClick = onFullscreen, modifier = Modifier.testTag("reader-fullscreen")) {
                    Icon(
                        imageVector = Icons.Rounded.Fullscreen,
                        contentDescription = strings.readerFullscreen,
                    )
                }
                TextButton(onClick = onBorderless, modifier = Modifier.testTag("reader-borderless")) {
                    Text(strings.readerBorderless)
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("reader-settings")) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = strings.settingsTitle,
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.pages.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (chapterCatalog.isNotEmpty()) {
                            IconButton(
                                onClick = { isChapterDrawerOpen = true },
                                modifier = Modifier.testTag("reader-chapter-list-button"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                                    contentDescription = strings.readerChapterList,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        IconButton(
                            onClick = onPreviousChapter,
                            enabled = hasPreviousChapter,
                            modifier = Modifier.testTag("reader-scrubber-prev-chapter"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = strings.readerPreviousChapter,
                            )
                        }
                        IconButton(
                            onClick = onPreviousPage,
                            enabled = state.selectedIndex > 0,
                            modifier = Modifier.testTag("reader-scrubber-prev"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "Previous Page",
                            )
                        }
                        val isRtl = state.mode.isRightToLeft
                        val sliderDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                        CompositionLocalProvider(LocalLayoutDirection provides sliderDirection) {
                            Slider(
                                value = (state.selectedIndex + 1).toFloat(),
                                onValueChange = { onPageSelected((it.toInt() - 1).coerceIn(0, state.pages.size - 1)) },
                                valueRange = 1f..state.pages.size.toFloat(),
                                steps = (state.pages.size - 2).coerceAtLeast(0),
                                modifier = Modifier.weight(1f).testTag("reader-scrubber-slider"),
                            )
                        }
                        IconButton(
                            onClick = onNextPage,
                            enabled = state.selectedIndex < state.pages.size - 1,
                            modifier = Modifier.testTag("reader-scrubber-next"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Next Page",
                            )
                        }
                        IconButton(
                            onClick = onNextChapter,
                            enabled = hasNextChapter,
                            modifier = Modifier.testTag("reader-scrubber-next-chapter"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = strings.readerNextChapter,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReadingModeMenu(state.mode, onMode)
                    ScaleModeMenu(state.scaleMode, onScale)
                    ColorFilterMenu(settings.colorFilter, onColorFilter)
                    val isWebtoon = state.mode == ReadingMode.WEBTOON
                    val cropActive = if (isWebtoon) settings.cropBordersWebtoon else settings.cropBorders
                    TextButton(
                        onClick = {
                            if (isWebtoon) {
                                onCropBordersWebtoon(!settings.cropBordersWebtoon)
                            } else {
                                onCropBorders(!settings.cropBorders)
                            }
                        },
                        modifier = Modifier.testTag("reader-crop-toggle"),
                    ) {
                        Text(strings.readerCropToggle(cropActive))
                    }
                    if (state.mode.isDualPage) {
                        TextButton(
                            onClick = { onCoverOffset(!state.coverOffset) },
                            modifier = Modifier.testTag("reader-cover-toggle"),
                        ) {
                            Text(strings.readerCoverOffsetToggle(state.coverOffset))
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
                        TextButton(onClick = onRetry, modifier = Modifier.testTag("reader-retry")) {
                            Text(strings.downloadsRetry)
                        }
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
        ReaderChapterDrawer(
            isOpen = isChapterDrawerOpen,
            onDismissRequest = { isChapterDrawerOpen = false },
            title = title,
            chapters = chapterCatalog,
            currentChapterId = currentChapterId,
            onSelectChapter = {
                isChapterDrawerOpen = false
                onChapterSelected(it)
            },
        )
    }
}

@Composable
private fun ReadingModeMenu(current: ReadingMode, onSelected: (ReadingMode) -> Unit) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("reader-mode-menu")) {
            Text(strings.readerModeLabel(current))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReadingMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(strings.readerModeLabel(mode)) },
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
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("reader-scale-menu")) {
            Text(strings.readerScaleLabel(current))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ScaleMode.entries.forEach { scale ->
                DropdownMenuItem(
                    text = { Text(strings.readerScaleLabel(scale)) },
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
    val strings = LocalStrings.current
    var draft by remember(settings) { mutableStateOf(settings) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.readerSettingsDialogTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(strings.readerClickRegions, style = MaterialTheme.typography.titleSmall)
                ClickActionMenu(strings.readerRegionLeft, "reader-setting-left-action", draft.clickRegions.leftAction) {
                    draft = draft.copy(clickRegions = draft.clickRegions.copy(leftAction = it))
                }
                ClickActionMenu(
                    strings.readerRegionCenter,
                    "reader-setting-center-action",
                    draft.clickRegions.centerAction,
                ) {
                    draft = draft.copy(clickRegions = draft.clickRegions.copy(centerAction = it))
                }
                ClickActionMenu(
                    strings.readerRegionRight,
                    "reader-setting-right-action",
                    draft.clickRegions.rightAction,
                ) {
                    draft = draft.copy(clickRegions = draft.clickRegions.copy(rightAction = it))
                }
                Text(strings.readerLeftBoundary(draft.clickRegions.leftEndPercent))
                Slider(
                    value = draft.clickRegions.leftEndPercent.toFloat(),
                    onValueChange = { value ->
                        val left = value.toInt().coerceIn(1, draft.clickRegions.centerEndPercent - 1)
                        draft = draft.copy(clickRegions = draft.clickRegions.copy(leftEndPercent = left))
                    },
                    valueRange = 1f..98f,
                    modifier = Modifier.testTag("reader-setting-left-boundary"),
                )
                Text(strings.readerCenterBoundary(draft.clickRegions.centerEndPercent))
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
                    Text(strings.readerReserveCover)
                }
                Text(strings.settingsSectionAppearance, style = MaterialTheme.typography.titleSmall)
                SettingColorFilterMenu(draft.colorFilter) { draft = draft.copy(colorFilter = it) }
                SettingBackgroundColorMenu(draft.backgroundColor) { draft = draft.copy(backgroundColor = it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.cropBorders,
                        onCheckedChange = { draft = draft.copy(cropBorders = it) },
                        modifier = Modifier.testTag("reader-setting-crop-paged"),
                    )
                    Text(strings.readerCropBordersPaged)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.cropBordersWebtoon,
                        onCheckedChange = { draft = draft.copy(cropBordersWebtoon = it) },
                        modifier = Modifier.testTag("reader-setting-crop-webtoon"),
                    )
                    Text(strings.readerCropBordersWebtoon)
                }
                Text(strings.readerWebtoonLayout, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (draft.webtoonMaxWidth == 0) {
                        strings.readerWebtoonWidthFull
                    } else {
                        strings.readerWebtoonWidthDp(draft.webtoonMaxWidth)
                    },
                )
                Slider(
                    value = draft.webtoonMaxWidth.toFloat(),
                    onValueChange = { draft = draft.copy(webtoonMaxWidth = it.toInt()) },
                    valueRange = 0f..1600f,
                    modifier = Modifier.testTag("reader-setting-webtoon-max-width"),
                )
                Text(strings.readerWebtoonSidePaddingPercent(draft.webtoonSidePadding))
                Slider(
                    value = draft.webtoonSidePadding.toFloat(),
                    onValueChange = { draft = draft.copy(webtoonSidePadding = it.toInt()) },
                    valueRange = 0f..30f,
                    modifier = Modifier.testTag("reader-setting-webtoon-side-padding"),
                )
                Text("Chapter transitions", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.alwaysShowChapterTransition,
                        onCheckedChange = { draft = draft.copy(alwaysShowChapterTransition = it) },
                        modifier = Modifier.testTag("reader-setting-always-show-transition"),
                    )
                    Text("Always show chapter transition")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.skipReadChapters,
                        onCheckedChange = { draft = draft.copy(skipReadChapters = it) },
                        modifier = Modifier.testTag("reader-setting-skip-read"),
                    )
                    Text("Skip read chapters")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.skipFilteredChapters,
                        onCheckedChange = { draft = draft.copy(skipFilteredChapters = it) },
                        modifier = Modifier.testTag("reader-setting-skip-filtered"),
                    )
                    Text("Skip filtered chapters")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.skipDuplicateChapters,
                        onCheckedChange = { draft = draft.copy(skipDuplicateChapters = it) },
                        modifier = Modifier.testTag("reader-setting-skip-duplicate"),
                    )
                    Text("Skip duplicate chapters")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft) },
                modifier = Modifier.testTag("reader-settings-save"),
            ) { Text(strings.categorySave) }
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
private fun ClickActionMenu(
    label: String,
    tag: String,
    current: ReaderClickAction,
    onSelected: (ReaderClickAction) -> Unit,
) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag(tag)) {
            Text("$label: ${strings.readerActionLabel(current)}")
        }
        DropdownMenu(expanded, { expanded = false }) {
            ReaderClickAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = { Text(strings.readerActionLabel(action)) },
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
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("reader-setting-wheel")) {
            Text(strings.readerWheelLabel(current))
        }
        DropdownMenu(expanded, { expanded = false }) {
            ReaderWheelBehavior.entries.forEach { value ->
                DropdownMenuItem(
                    text = { Text(strings.readerWheelLabel(value)) },
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
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("reader-setting-mode")) {
            Text("${strings.settingsDefaultReadingMode}: ${strings.readerModeLabel(current)}")
        }
        DropdownMenu(expanded, { expanded = false }) {
            ReadingMode.entries.forEach { value ->
                DropdownMenuItem(
                    text = { Text(strings.readerModeLabel(value)) },
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
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("reader-setting-scale")) {
            Text("${strings.settingsDefaultScaleMode}: ${strings.readerScaleLabel(current)}")
        }
        DropdownMenu(expanded, { expanded = false }) {
            ScaleMode.entries.forEach { value ->
                DropdownMenuItem(
                    text = { Text(strings.readerScaleLabel(value)) },
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
private fun ColorFilterMenu(current: ReaderColorFilter, onSelected: (ReaderColorFilter) -> Unit) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("reader-filter-menu")) {
            Text("${strings.readerColorFilter}: ${strings.readerFilterLabel(current)}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReaderColorFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(strings.readerFilterLabel(filter)) },
                    onClick = {
                        expanded = false
                        onSelected(filter)
                    },
                    modifier = Modifier.testTag("reader-filter-${filter.name}"),
                )
            }
        }
    }
}

@Composable
private fun SettingColorFilterMenu(current: ReaderColorFilter, onSelected: (ReaderColorFilter) -> Unit) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("reader-setting-filter")) {
            Text("${strings.readerColorFilter}: ${strings.readerFilterLabel(current)}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReaderColorFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(strings.readerFilterLabel(filter)) },
                    onClick = {
                        expanded = false
                        onSelected(filter)
                    },
                    modifier = Modifier.testTag("reader-setting-filter-${filter.name}"),
                )
            }
        }
    }
}

@Composable
private fun SettingBackgroundColorMenu(current: ReaderBackgroundColor, onSelected: (ReaderBackgroundColor) -> Unit) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("reader-setting-bg")) {
            Text("${strings.readerBackgroundColor}: ${strings.readerBackgroundLabel(current)}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReaderBackgroundColor.entries.forEach { bg ->
                DropdownMenuItem(
                    text = { Text(strings.readerBackgroundLabel(bg)) },
                    onClick = {
                        expanded = false
                        onSelected(bg)
                    },
                    modifier = Modifier.testTag("reader-setting-bg-${bg.name}"),
                )
            }
        }
    }
}

@Composable
internal fun ReaderShortcutsDialog(onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(strings.readerShortcutsTitle, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShortcutSectionHeader("Navigation")
                ShortcutItemRow("← / → or A / D", "Previous / Next page")
                ShortcutItemRow("PageUp / PageDown", "Scroll / Flip page")
                ShortcutItemRow("Home / End", "First / Last page")

                ShortcutSectionHeader("Zoom & Window")
                ShortcutItemRow("+ / −", "Zoom in / Zoom out")
                ShortcutItemRow("0", "Reset zoom (100%)")
                ShortcutItemRow("F", "Toggle fullscreen")
                ShortcutItemRow("B", "Toggle borderless window")
                ShortcutItemRow("Escape", "Exit reader")

                ShortcutSectionHeader("Controls & Cheatsheet")
                ShortcutItemRow("Click Center", "Show / hide controls")
                ShortcutItemRow("? or F1", "Show this shortcuts cheatsheet")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.dialogOk)
            }
        },
    )
}

@Composable
private fun ShortcutSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ShortcutItemRow(keys: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = keys,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val MIB = 1024L * 1024L
