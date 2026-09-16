package mihon.desktop.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.MangaDetails
import kotlin.math.floor

// Android-compatible chapter flag bits (kept local to the desktop module).
internal const val CHAPTER_SORT_DESC = 0x00000000L
internal const val CHAPTER_SORT_ASC = 0x00000001L
internal const val CHAPTER_SORT_DIR_MASK = 0x00000001L

internal const val CHAPTER_SHOW_UNREAD = 0x00000002L
internal const val CHAPTER_SHOW_READ = 0x00000004L
internal const val CHAPTER_UNREAD_MASK = 0x00000006L

internal const val CHAPTER_SHOW_DOWNLOADED = 0x00000008L
internal const val CHAPTER_SHOW_NOT_DOWNLOADED = 0x00000010L
internal const val CHAPTER_DOWNLOADED_MASK = 0x00000018L

internal const val CHAPTER_SHOW_BOOKMARKED = 0x00000020L
internal const val CHAPTER_SHOW_NOT_BOOKMARKED = 0x00000040L
internal const val CHAPTER_BOOKMARKED_MASK = 0x00000060L

internal const val CHAPTER_SORTING_SOURCE = 0x00000000L
internal const val CHAPTER_SORTING_NUMBER = 0x00000100L
internal const val CHAPTER_SORTING_UPLOAD_DATE = 0x00000200L
internal const val CHAPTER_SORTING_MASK = 0x00000300L

internal const val CHAPTER_DISPLAY_NAME = 0x00000000L
internal const val CHAPTER_DISPLAY_NUMBER = 0x00100000L

// Desktop extension bit. Android only reserves 0x00100000 for the display mode, so this
// remains backup-compatible while still allowing "source order" labels on desktop.
internal const val CHAPTER_DISPLAY_SOURCE_ORDER = 0x00200000L
internal const val CHAPTER_DISPLAY_MASK = 0x00300000L

internal const val CHAPTER_DEFAULT_FLAGS_KEY = "chapter.default_flags"
internal const val CHAPTER_DEFAULT_SHOW_MISSING_KEY = "chapter.default_show_missing_chapters"

enum class ChapterDisplayMode {
    Name,
    Number,
    SourceOrder,
}

data class ChapterSettings(
    val displayMode: ChapterDisplayMode = ChapterDisplayMode.Name,
    val sortMode: ChapterSortMode = ChapterSortMode.SourceOrder,
    val sortAscending: Boolean = false,
    val unreadFilter: TriStateFilter = TriStateFilter.Disabled,
    val downloadedFilter: TriStateFilter = TriStateFilter.Disabled,
    val bookmarkedFilter: TriStateFilter = TriStateFilter.Disabled,
    val excludedScanlators: Set<String> = emptySet(),
    val showMissingChapters: Boolean = true,
) {
    fun toFilterState(): ChapterFilterState = ChapterFilterState(
        unread = unreadFilter,
        downloaded = downloadedFilter,
        bookmarked = bookmarkedFilter,
    )

    fun toSortState(): ChapterSortState = ChapterSortState(
        mode = sortMode,
        ascending = sortAscending,
    )
}

sealed interface ChapterListItem {
    val key: String

    data class Chapter(
        val chapter: LibraryChapter,
        val label: String,
    ) : ChapterListItem {
        override val key: String get() = "chapter-${chapter.id}"
    }

    data class MissingCount(
        val afterChapterId: Long?,
        val beforeChapterId: Long?,
        val count: Int,
    ) : ChapterListItem {
        override val key: String get() = "missing-${afterChapterId ?: -1}-${beforeChapterId ?: -1}"
    }
}

fun chapterSettingsFromFlags(
    chapterFlags: Long,
    showMissingChapters: Boolean = true,
    excludedScanlators: Set<String> = emptySet(),
): ChapterSettings {
    val displayMode = when (chapterFlags and CHAPTER_DISPLAY_MASK) {
        CHAPTER_DISPLAY_NUMBER -> ChapterDisplayMode.Number
        CHAPTER_DISPLAY_SOURCE_ORDER -> ChapterDisplayMode.SourceOrder
        else -> ChapterDisplayMode.Name
    }
    val sortMode = when (chapterFlags and CHAPTER_SORTING_MASK) {
        CHAPTER_SORTING_NUMBER -> ChapterSortMode.ChapterNumber
        CHAPTER_SORTING_UPLOAD_DATE -> ChapterSortMode.UploadDate
        else -> ChapterSortMode.SourceOrder
    }
    val sortAscending = chapterFlags and CHAPTER_SORT_DIR_MASK == CHAPTER_SORT_ASC
    val unreadFilter = when (chapterFlags and CHAPTER_UNREAD_MASK) {
        CHAPTER_SHOW_UNREAD -> TriStateFilter.Include
        CHAPTER_SHOW_READ -> TriStateFilter.Exclude
        else -> TriStateFilter.Disabled
    }
    val downloadedFilter = when (chapterFlags and CHAPTER_DOWNLOADED_MASK) {
        CHAPTER_SHOW_DOWNLOADED -> TriStateFilter.Include
        CHAPTER_SHOW_NOT_DOWNLOADED -> TriStateFilter.Exclude
        else -> TriStateFilter.Disabled
    }
    val bookmarkedFilter = when (chapterFlags and CHAPTER_BOOKMARKED_MASK) {
        CHAPTER_SHOW_BOOKMARKED -> TriStateFilter.Include
        CHAPTER_SHOW_NOT_BOOKMARKED -> TriStateFilter.Exclude
        else -> TriStateFilter.Disabled
    }
    return ChapterSettings(
        displayMode = displayMode,
        sortMode = sortMode,
        sortAscending = sortAscending,
        unreadFilter = unreadFilter,
        downloadedFilter = downloadedFilter,
        bookmarkedFilter = bookmarkedFilter,
        excludedScanlators = excludedScanlators,
        showMissingChapters = showMissingChapters,
    )
}

fun encodeChapterFlags(existingFlags: Long, settings: ChapterSettings): Long {
    val displayFlag = when (settings.displayMode) {
        ChapterDisplayMode.Name -> CHAPTER_DISPLAY_NAME
        ChapterDisplayMode.Number -> CHAPTER_DISPLAY_NUMBER
        ChapterDisplayMode.SourceOrder -> CHAPTER_DISPLAY_SOURCE_ORDER
    }
    val sortFlag = when (settings.sortMode) {
        ChapterSortMode.SourceOrder -> CHAPTER_SORTING_SOURCE
        ChapterSortMode.ChapterNumber -> CHAPTER_SORTING_NUMBER
        ChapterSortMode.UploadDate -> CHAPTER_SORTING_UPLOAD_DATE
    }
    val directionFlag = if (settings.sortAscending) CHAPTER_SORT_ASC else CHAPTER_SORT_DESC
    val unreadFlag = when (settings.unreadFilter) {
        TriStateFilter.Include -> CHAPTER_SHOW_UNREAD
        TriStateFilter.Exclude -> CHAPTER_SHOW_READ
        TriStateFilter.Disabled -> 0L
    }
    val downloadedFlag = when (settings.downloadedFilter) {
        TriStateFilter.Include -> CHAPTER_SHOW_DOWNLOADED
        TriStateFilter.Exclude -> CHAPTER_SHOW_NOT_DOWNLOADED
        TriStateFilter.Disabled -> 0L
    }
    val bookmarkedFlag = when (settings.bookmarkedFilter) {
        TriStateFilter.Include -> CHAPTER_SHOW_BOOKMARKED
        TriStateFilter.Exclude -> CHAPTER_SHOW_NOT_BOOKMARKED
        TriStateFilter.Disabled -> 0L
    }
    return existingFlags
        .setFlag(displayFlag, CHAPTER_DISPLAY_MASK)
        .setFlag(sortFlag, CHAPTER_SORTING_MASK)
        .setFlag(directionFlag, CHAPTER_SORT_DIR_MASK)
        .setFlag(unreadFlag, CHAPTER_UNREAD_MASK)
        .setFlag(downloadedFlag, CHAPTER_DOWNLOADED_MASK)
        .setFlag(bookmarkedFlag, CHAPTER_BOOKMARKED_MASK)
}

private fun Long.setFlag(flag: Long, mask: Long): Long = (this and mask.inv()) or (flag and mask)

fun parseExcludedScanlators(raw: String): Set<String> = runCatching {
    Json.decodeFromString<List<String>>(raw)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}.getOrDefault(emptySet())

fun encodeExcludedScanlators(values: Set<String>): String = Json.encodeToString(values.sorted())

fun parseShowMissingChapters(memoJson: String): Boolean {
    val root = runCatching { Json.parseToJsonElement(memoJson) as? JsonObject }.getOrNull() ?: return true
    val chapterSettings = root["chapterSettings"] as? JsonObject ?: return true
    return chapterSettings["showMissingChapters"]?.jsonPrimitive?.booleanOrNull ?: true
}

fun encodeShowMissingChapters(memoJson: String, showMissingChapters: Boolean): String {
    val root = runCatching { Json.parseToJsonElement(memoJson) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
    val existing = (root["chapterSettings"] as? JsonObject) ?: JsonObject(emptyMap())
    val updatedSettings = JsonObject(existing + ("showMissingChapters" to JsonPrimitive(showMissingChapters)))
    return Json.encodeToString(JsonObject(root + ("chapterSettings" to updatedSettings)))
}

fun MangaDetails.toChapterSettings(): ChapterSettings = chapterSettingsFromFlags(
    chapterFlags = chapterFlags,
    showMissingChapters = parseShowMissingChapters(memoJson),
    excludedScanlators = parseExcludedScanlators(excludedScanlatorsJson),
)

fun formatChapterNumber(number: Double): String? {
    if (!number.isFinite() || number < 0.0) return null
    val integral = floor(number)
    return if (number == integral) integral.toLong().toString() else number.toString()
}

fun chapterDisplayLabel(
    chapter: LibraryChapter,
    displayMode: ChapterDisplayMode,
    strings: mihon.desktop.i18n.DesktopStrings = mihon.desktop.i18n.EnglishStrings,
): String = when (displayMode) {
    ChapterDisplayMode.Name -> chapter.name
    ChapterDisplayMode.Number -> formatChapterNumber(chapter.chapterNumber) ?: chapter.name
    ChapterDisplayMode.SourceOrder -> strings.text(UiText.SourceOrderNumber, chapter.sourceOrder + 1)
}

fun calculateChapterGap(higherChapterNumber: Double, lowerChapterNumber: Double): Int {
    if (!higherChapterNumber.isFinite() || !lowerChapterNumber.isFinite()) return 0
    if (higherChapterNumber < 0.0 || lowerChapterNumber < 0.0) return 0
    return floor(higherChapterNumber).toInt() - floor(lowerChapterNumber).toInt() - 1
}

fun calculateMissingChapterCount(chapters: List<LibraryChapter>): Int {
    val numbers = chapters
        .map { it.chapterNumber }
        .filter { it.isFinite() && it >= 0.0 }
        .sorted()
    return numbers.zipWithNext().sumOf { (lower, higher) ->
        calculateChapterGap(higherChapterNumber = higher, lowerChapterNumber = lower).coerceAtLeast(0)
    }
}

fun buildChapterListItems(
    chapters: List<LibraryChapter>,
    settings: ChapterSettings,
): List<ChapterListItem> {
    if (chapters.isEmpty()) return emptyList()
    val items = ArrayList<ChapterListItem>(chapters.size)
    chapters.forEachIndexed { index, chapter ->
        items += ChapterListItem.Chapter(
            chapter = chapter,
            label = chapterDisplayLabel(chapter, settings.displayMode),
        )
        if (!settings.showMissingChapters) return@forEachIndexed
        val next = chapters.getOrNull(index + 1) ?: return@forEachIndexed
        val higher = maxOf(chapter.chapterNumber, next.chapterNumber)
        val lower = minOf(chapter.chapterNumber, next.chapterNumber)
        val gap = calculateChapterGap(higher, lower)
        if (gap > 0) {
            items += ChapterListItem.MissingCount(
                afterChapterId = chapter.id,
                beforeChapterId = next.id,
                count = gap,
            )
        }
    }
    return items
}

@Composable
fun ChapterSettingsDialog(
    settings: ChapterSettings,
    availableScanlators: Set<String>,
    onDismissRequest: () -> Unit,
    onDisplayModeChange: (ChapterDisplayMode) -> Unit,
    onSortModeChange: (ChapterSortMode, Boolean) -> Unit,
    onShowMissingChaptersChange: (Boolean) -> Unit,
    onExcludedScanlatorsChange: (Set<String>) -> Unit,
    onSetAsDefault: (applyToExistingManga: Boolean) -> Unit,
    onResetToDefault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var showScanlatorDialog by remember { mutableStateOf(false) }
    var showSetAsDefaultDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag("chapter-settings-dialog"),
        title = { Text(strings.text(UiText.ChapterSettings)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsSection(strings.text(UiText.Scanlators))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chapter-settings-scanlators")
                        .clickable { showScanlatorDialog = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.text(UiText.ScanlatorFilter),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (settings.excludedScanlators.isEmpty()) {
                            strings.text(UiText.All)
                        } else {
                            strings.text(UiText.ExcludedCount, settings.excludedScanlators.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider()

                SettingsSection(strings.text(UiText.ChapterDisplay))
                ChapterDisplayMode.entries.forEach { mode ->
                    RadioOption(
                        label = when (mode) {
                            ChapterDisplayMode.Name -> strings.text(UiText.ChapterName)
                            ChapterDisplayMode.Number -> strings.text(UiText.ChapterNumber)
                            ChapterDisplayMode.SourceOrder -> strings.text(UiText.SourceOrder)
                        },
                        selected = settings.displayMode == mode,
                        onClick = { onDisplayModeChange(mode) },
                        testTag = "chapter-settings-display-${mode.name}",
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chapter-settings-missing")
                        .clickable { onShowMissingChaptersChange(!settings.showMissingChapters) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = settings.showMissingChapters,
                        onCheckedChange = onShowMissingChaptersChange,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.text(UiText.ShowMissingChapters), style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider()

                SettingsSection(strings.text(UiText.Sort))
                listOf(
                    ChapterSortMode.SourceOrder to strings.sortSourceOrder,
                    ChapterSortMode.ChapterNumber to strings.sortChapterNumber,
                    ChapterSortMode.UploadDate to strings.sortUploadDate,
                ).forEach { (mode, label) ->
                    RadioOption(
                        label = label,
                        selected = settings.sortMode == mode,
                        onClick = { onSortModeChange(mode, settings.sortAscending) },
                        testTag = "chapter-settings-sort-${mode.name}",
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chapter-settings-sort-ascending")
                        .clickable { onSortModeChange(settings.sortMode, !settings.sortAscending) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = settings.sortAscending,
                        onCheckedChange = { onSortModeChange(settings.sortMode, it) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.librarySortAscending, style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { showSetAsDefaultDialog = true },
                        modifier = Modifier.testTag("chapter-settings-set-default"),
                    ) {
                        Text(strings.text(UiText.SetDefault))
                    }
                    TextButton(
                        onClick = onResetToDefault,
                        modifier = Modifier.testTag("chapter-settings-reset"),
                    ) {
                        Text(strings.filterReset)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag("chapter-settings-done"),
            ) {
                Text(strings.libraryBatchDone)
            }
        },
    )

    if (showScanlatorDialog) {
        ScanlatorFilterDialog(
            availableScanlators = availableScanlators,
            excludedScanlators = settings.excludedScanlators,
            onDismissRequest = { showScanlatorDialog = false },
            onConfirm = { excluded ->
                onExcludedScanlatorsChange(excluded)
                showScanlatorDialog = false
            },
        )
    }
    if (showSetAsDefaultDialog) {
        SetChapterSettingsAsDefaultDialog(
            onDismissRequest = { showSetAsDefaultDialog = false },
            onConfirmed = { applyToExisting ->
                onSetAsDefault(applyToExisting)
                showSetAsDefaultDialog = false
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun RadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ScanlatorFilterDialog(
    availableScanlators: Set<String>,
    excludedScanlators: Set<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val sortedScanlators = remember(availableScanlators) {
        availableScanlators.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    var selected by remember(excludedScanlators) {
        mutableStateOf(excludedScanlators.toSet())
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag("scanlator-filter-dialog"),
        title = { Text(strings.text(UiText.ExcludeScanlators)) },
        text = {
            if (sortedScanlators.isEmpty()) {
                Text(strings.text(UiText.NoScanlators))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        strings.text(UiText.ExcludeScanlatorsHint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(sortedScanlators, key = { it }) { scanlator ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("scanlator-filter-option-$scanlator")
                                    .clickable {
                                        selected = if (scanlator in selected) {
                                            selected - scanlator
                                        } else {
                                            selected + scanlator
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = scanlator in selected,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + scanlator else selected - scanlator
                                    },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(scanlator, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(strings.dialogCancel)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (sortedScanlators.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            selected = if (selected.isEmpty()) availableScanlators.toSet() else emptySet()
                        },
                        modifier = Modifier.testTag("scanlator-filter-toggle-all"),
                    ) {
                        Text(if (selected.isEmpty()) strings.chapterBatchSelectAll else strings.text(UiText.Clear))
                    }
                }
                TextButton(
                    onClick = {
                        onConfirm(selected)
                        onDismissRequest()
                    },
                    modifier = Modifier.testTag("scanlator-filter-confirm"),
                ) {
                    Text(strings.dialogOk)
                }
            }
        },
    )
}

@Composable
private fun SetChapterSettingsAsDefaultDialog(
    onDismissRequest: () -> Unit,
    onConfirmed: (applyToExistingManga: Boolean) -> Unit,
) {
    val strings = LocalStrings.current
    var applyToExisting by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("chapter-settings-set-default-dialog"),
        title = { Text(strings.text(UiText.DefaultChapterSettings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.text(UiText.ApplyChapterDefaults))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chapter-settings-apply-existing")
                        .clickable { applyToExisting = !applyToExisting }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = applyToExisting,
                        onCheckedChange = { applyToExisting = it },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.text(UiText.ApplyExistingManga), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(strings.dialogCancel)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmed(applyToExisting) },
                modifier = Modifier.testTag("chapter-settings-set-default-confirm"),
            ) {
                Text(strings.dialogOk)
            }
        },
    )
}
