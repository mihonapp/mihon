package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Serializable
data class CodeSnippet(
    val title: String,
    val code: String,
    val enabled: Boolean = true,
)

private val novelThemes = listOf(
    "Light" to "light",
    "Dark" to "dark",
    "Sepia" to "sepia",
    "Black" to "black",
    "Custom" to "custom",
)

private val novelFonts = listOf(
    "Sans Serif" to "sans-serif",
    "Serif" to "serif",
    "Monospace" to "monospace",
    "Georgia" to "Georgia, serif",
    "Times New Roman" to "Times New Roman, serif",
    "Arial" to "Arial, sans-serif",
)

private val textAlignments = listOf(
    "Left" to "left",
    "Center" to "center",
    "Right" to "right",
    "Justify" to "justify",
)

private val renderingModes = listOf(
    "Default (Custom Parser)" to "default",
    "WebView" to "webview",
)

// Predefined font colors (ARGB int format, 0 = theme default, -2 = custom)
// Note: Using 0 instead of -1 for default because 0xFFFFFFFF (white) = -1 as signed int
private val fontColors = listOf(
    "Default" to 0,
    "Black" to 0xFF000000.toInt(),
    "White" to 0xFFFFFFFF.toInt(),
    "Gray" to 0xFF808080.toInt(),
    "Dark Gray" to 0xFF404040.toInt(),
    "Light Gray" to 0xFFC0C0C0.toInt(),
    "Sepia" to 0xFF5C4033.toInt(),
    "Custom" to -2,
)

// Predefined background colors (ARGB int format, 0 = theme default, -2 = custom)
// Note: Using 0 instead of -1 for default because 0xFFFFFFFF (white) = -1 as signed int
private val backgroundColors = listOf(
    "Default" to 0,
    "White" to 0xFFFFFFFF.toInt(),
    "Black" to 0xFF000000.toInt(),
    "Light Gray" to 0xFFF5F5F5.toInt(),
    "Dark Gray" to 0xFF1A1A1A.toInt(),
    "Sepia" to 0xFFF4ECD8.toInt(),
    "Cream" to 0xFFFFFDD0.toInt(),
    "Custom" to -2,
)

@Composable
internal fun ColumnScope.NovelPage(screenModel: ReaderSettingsScreenModel) {
    val fontSize by screenModel.preferences.novelFontSize().collectAsState()
    var showFontColorPicker by remember { mutableStateOf(false) }
    var showBgColorPicker by remember { mutableStateOf(false) }
    val fontFamily by screenModel.preferences.novelFontFamily().collectAsState()
    val theme by screenModel.preferences.novelTheme().collectAsState()
    val lineHeight by screenModel.preferences.novelLineHeight().collectAsState()
    val textAlign by screenModel.preferences.novelTextAlign().collectAsState()
    val autoScrollSpeed by screenModel.preferences.novelAutoScrollSpeed().collectAsState()
    val volumeKeysScroll by screenModel.preferences.novelVolumeKeysScroll().collectAsState()
    val tapToScroll by screenModel.preferences.novelTapToScroll().collectAsState()
    val textSelectable by screenModel.preferences.novelTextSelectable().collectAsState()
    val fontColor by screenModel.preferences.novelFontColor().collectAsState()
    val backgroundColor by screenModel.preferences.novelBackgroundColor().collectAsState()
    val paragraphIndent by screenModel.preferences.novelParagraphIndent().collectAsState()
    val marginLeft by screenModel.preferences.novelMarginLeft().collectAsState()
    val marginRight by screenModel.preferences.novelMarginRight().collectAsState()
    val marginTop by screenModel.preferences.novelMarginTop().collectAsState()
    val marginBottom by screenModel.preferences.novelMarginBottom().collectAsState()
    val renderingMode by screenModel.preferences.novelRenderingMode().collectAsState()

    // Show color picker dialogs
    if (showFontColorPicker) {
        ColorPickerDialog(
            title = stringResource(MR.strings.pref_novel_font_color),
            initialColor = if (fontColor > 0) fontColor else 0xFF000000.toInt(),
            onDismiss = { showFontColorPicker = false },
            onConfirm = { color ->
                screenModel.preferences.novelFontColor().set(color)
                showFontColorPicker = false
            },
        )
    }

    if (showBgColorPicker) {
        ColorPickerDialog(
            title = stringResource(MR.strings.pref_novel_background_color),
            initialColor = if (backgroundColor > 0) backgroundColor else 0xFFFFFFFF.toInt(),
            onDismiss = { showBgColorPicker = false },
            onConfirm = { color ->
                screenModel.preferences.novelBackgroundColor().set(color)
                // Switch to custom theme when custom background is set
                screenModel.preferences.novelTheme().set("custom")
                showBgColorPicker = false
            },
        )
    }

    // Rendering Mode
    SettingsChipRow(MR.strings.pref_novel_rendering_mode) {
        renderingModes.map { (label, value) ->
            FilterChip(
                selected = renderingMode == value,
                onClick = { screenModel.preferences.novelRenderingMode().set(value) },
                label = { Text(label) },
            )
        }
    }

    // Font Size
    SliderItem(
        label = stringResource(MR.strings.pref_font_size),
        value = fontSize,
        valueRange = 10..40,
        onChange = { screenModel.preferences.novelFontSize().set(it) },
    )

    // Line Height
    SliderItem(
        label = stringResource(MR.strings.pref_novel_line_height),
        value = (lineHeight * 10).toInt(),
        valueRange = 10..30,
        onChange = { screenModel.preferences.novelLineHeight().set(it / 10f) },
    )

    // Paragraph Indentation (WebView mode only - CSS text-indent doesn't work with Html.fromHtml)
    if (renderingMode == "webview") {
        SliderItem(
            label = stringResource(MR.strings.pref_novel_paragraph_indent),
            value = (paragraphIndent * 10).toInt(),
            valueRange = 0..50, // 0 to 5em
            onChange = { screenModel.preferences.novelParagraphIndent().set(it / 10f) },
        )
    }

    // Font Color
    SettingsChipRow(MR.strings.pref_novel_font_color) {
        fontColors.map { (label, colorValue) ->
            val isCustom = colorValue == -2
            val isSelected = if (isCustom) {
                // Custom is selected if fontColor is not in predefined list (0 = default)
                fontColors.none { it.second == fontColor } && fontColor != 0
            } else {
                fontColor == colorValue
            }
            FilterChip(
                selected = isSelected,
                onClick = {
                    if (isCustom) {
                        showFontColorPicker = true
                    } else {
                        screenModel.preferences.novelFontColor().set(colorValue)
                    }
                },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val displayColor = when {
                            isCustom && isSelected -> Color(fontColor)
                            colorValue > 0 -> Color(colorValue)
                            else -> null
                        }
                        if (displayColor != null) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(displayColor)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    .padding(end = 4.dp),
                            )
                        }
                        if (isCustom) {
                            Icon(
                                Icons.Outlined.Palette,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).padding(end = 4.dp),
                            )
                        }
                        Text(label, modifier = Modifier.padding(start = if (displayColor != null || isCustom) 4.dp else 0.dp))
                    }
                },
            )
        }
    }

    // Background Color
    SettingsChipRow(MR.strings.pref_novel_background_color) {
        backgroundColors.map { (label, colorValue) ->
            val isCustom = colorValue == -2
            val isSelected = if (isCustom) {
                // Custom is selected if backgroundColor is not in predefined list (0 = default)
                backgroundColors.none { it.second == backgroundColor } && backgroundColor != 0
            } else {
                backgroundColor == colorValue
            }
            FilterChip(
                selected = isSelected,
                onClick = {
                    if (isCustom) {
                        showBgColorPicker = true
                    } else {
                        screenModel.preferences.novelBackgroundColor().set(colorValue)
                        if (colorValue != 0) {
                            screenModel.preferences.novelTheme().set("custom")
                        }
                    }
                },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val displayColor = when {
                            isCustom && isSelected -> Color(backgroundColor)
                            colorValue > 0 -> Color(colorValue)
                            else -> null
                        }
                        if (displayColor != null) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(displayColor)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    .padding(end = 4.dp),
                            )
                        }
                        if (isCustom) {
                            Icon(
                                Icons.Outlined.Palette,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).padding(end = 4.dp),
                            )
                        }
                        Text(label, modifier = Modifier.padding(start = if (displayColor != null || isCustom) 4.dp else 0.dp))
                    }
                },
            )
        }
    }

    // Margin Left
    SliderItem(
        label = stringResource(MR.strings.pref_novel_margin_left),
        value = marginLeft,
        valueRange = 0..1000,
        onChange = { screenModel.preferences.novelMarginLeft().set(it) },
    )

    // Margin Right
    SliderItem(
        label = stringResource(MR.strings.pref_novel_margin_right),
        value = marginRight,
        valueRange = 0..1000,
        onChange = { screenModel.preferences.novelMarginRight().set(it) },
    )

    // Margin Top
    SliderItem(
        label = stringResource(MR.strings.pref_novel_margin_top),
        value = marginTop,
        valueRange = 0..1000,
        onChange = { screenModel.preferences.novelMarginTop().set(it) },
    )

    // Margin Bottom
    SliderItem(
        label = stringResource(MR.strings.pref_novel_margin_bottom),
        value = marginBottom,
        valueRange = 0..1000,
        onChange = { screenModel.preferences.novelMarginBottom().set(it) },
    )

    // Auto Scroll Speed
    SliderItem(
        label = stringResource(MR.strings.pref_novel_auto_scroll_speed),
        value = autoScrollSpeed,
        valueRange = 0..30,
        onChange = { screenModel.preferences.novelAutoScrollSpeed().set(it) },
    )

    // Volume Keys to Scroll
    CheckboxItem(
        label = stringResource(MR.strings.pref_novel_volume_keys_scroll),
        pref = screenModel.preferences.novelVolumeKeysScroll(),
    )

    // Tap to Scroll
    CheckboxItem(
        label = stringResource(MR.strings.pref_novel_tap_to_scroll),
        pref = screenModel.preferences.novelTapToScroll(),
    )

    // Text Selection
    CheckboxItem(
        label = stringResource(MR.strings.pref_novel_text_selectable),
        pref = screenModel.preferences.novelTextSelectable(),
    )

    // Hide Chapter Title in Content
    CheckboxItem(
        label = stringResource(MR.strings.pref_novel_hide_chapter_title),
        pref = screenModel.preferences.novelHideChapterTitle(),
    )

    // Progress Slider (show slider in menu to navigate within chapter)
    CheckboxItem(
        label = stringResource(MR.strings.pref_novel_progress_slider),
        pref = screenModel.preferences.novelShowProgressSlider(),
    )

    // Custom Brightness
    val novelCustomBrightness by screenModel.preferences.novelCustomBrightness().collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_custom_brightness),
        pref = screenModel.preferences.novelCustomBrightness(),
    )

    /*
     * Sets the brightness of the screen. Range is [-75, 100].
     * From -75 to -1 a semi-transparent black view is shown at the top with the minimum brightness.
     * From 1 to 100 it sets that value as brightness.
     * 0 sets system brightness and hides the overlay.
     */
    if (novelCustomBrightness) {
        val novelCustomBrightnessValue by screenModel.preferences.novelCustomBrightnessValue().collectAsState()
        SliderItem(
            value = novelCustomBrightnessValue,
            valueRange = -75..100,
            steps = 0,
            label = stringResource(MR.strings.pref_custom_brightness),
            onChange = { screenModel.preferences.novelCustomBrightnessValue().set(it) },
        )
    }

    // Infinite Scroll (for custom mode)
    if (renderingMode == "default") {
        CheckboxItem(
            label = stringResource(MR.strings.pref_novel_infinite_scroll),
            pref = screenModel.preferences.novelInfiniteScroll(),
        )
    }
    
    // Chapter Sort Order
    val chapterSortOrder by screenModel.preferences.novelChapterSortOrder().collectAsState()
    val sortOrderOptions = listOf(
        "Source order" to "source",
        "Chapter number" to "chapter_number",
    )
    SettingsChipRow(MR.strings.pref_novel_chapter_sort_order) {
        sortOrderOptions.map { (label, value) ->
            FilterChip(
                selected = chapterSortOrder == value,
                onClick = { screenModel.preferences.novelChapterSortOrder().set(value) },
                label = { Text(label) },
            )
        }
    }

    // Theme
    SettingsChipRow(MR.strings.pref_novel_theme) {
        novelThemes.map { (label, value) ->
            FilterChip(
                selected = theme == value,
                onClick = { screenModel.preferences.novelTheme().set(value) },
                label = { Text(label) },
            )
        }
    }

    // Font Family
    SettingsChipRow(MR.strings.pref_font_family) {
        novelFonts.map { (label, value) ->
            FilterChip(
                selected = fontFamily == value,
                onClick = { screenModel.preferences.novelFontFamily().set(value) },
                label = { Text(label) },
            )
        }
    }

    // Use Original Fonts (WebView mode only - allows HTML to use its native fonts, bold, italics)
    if (renderingMode == "webview") {
        CheckboxItem(
            label = stringResource(MR.strings.pref_novel_use_original_fonts),
            pref = screenModel.preferences.novelUseOriginalFonts(),
        )
    }

    // Text Alignment
    SettingsChipRow(MR.strings.pref_novel_text_align) {
        textAlignments.map { (label, value) ->
            FilterChip(
                selected = textAlign == value,
                onClick = { screenModel.preferences.novelTextAlign().set(value) },
                label = { Text(label) },
            )
        }
    }

    // CSS/JS Snippets (WebView mode only)
    if (renderingMode == "webview") {
        val customCss by screenModel.preferences.novelCustomCss().collectAsState()
        val customJs by screenModel.preferences.novelCustomJs().collectAsState()
        val cssSnippetsJson by screenModel.preferences.novelCustomCssSnippets().collectAsState()
        val jsSnippetsJson by screenModel.preferences.novelCustomJsSnippets().collectAsState()
        
        var showCssDialog by remember { mutableStateOf(false) }
        var showJsDialog by remember { mutableStateOf(false) }
        var editingCssSnippet by remember { mutableStateOf<Pair<Int, CodeSnippet>?>(null) }
        var editingJsSnippet by remember { mutableStateOf<Pair<Int, CodeSnippet>?>(null) }
        
        val cssSnippets = remember(cssSnippetsJson) {
            try {
                Json.decodeFromString<List<CodeSnippet>>(cssSnippetsJson)
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        val jsSnippets = remember(jsSnippetsJson) {
            try {
                Json.decodeFromString<List<CodeSnippet>>(jsSnippetsJson)
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        // CSS Snippets Section
        SnippetSection(
            title = stringResource(MR.strings.pref_novel_css_snippets),
            snippets = cssSnippets,
            onAddClick = { showCssDialog = true },
            onEditClick = { index, snippet -> editingCssSnippet = index to snippet },
            onDeleteClick = { index ->
                val updated = cssSnippets.toMutableList().apply { removeAt(index) }
                screenModel.preferences.novelCustomCssSnippets().set(Json.encodeToString(updated))
            },
            onToggleClick = { index ->
                val updated = cssSnippets.toMutableList().apply {
                    this[index] = this[index].copy(enabled = !this[index].enabled)
                }
                screenModel.preferences.novelCustomCssSnippets().set(Json.encodeToString(updated))
            },
        )
        
        // JS Snippets Section
        SnippetSection(
            title = stringResource(MR.strings.pref_novel_js_snippets),
            snippets = jsSnippets,
            onAddClick = { showJsDialog = true },
            onEditClick = { index, snippet -> editingJsSnippet = index to snippet },
            onDeleteClick = { index ->
                val updated = jsSnippets.toMutableList().apply { removeAt(index) }
                screenModel.preferences.novelCustomJsSnippets().set(Json.encodeToString(updated))
            },
            onToggleClick = { index ->
                val updated = jsSnippets.toMutableList().apply {
                    this[index] = this[index].copy(enabled = !this[index].enabled)
                }
                screenModel.preferences.novelCustomJsSnippets().set(Json.encodeToString(updated))
            },
        )
        
        // CSS Add/Edit Dialog
        if (showCssDialog || editingCssSnippet != null) {
            SnippetEditDialog(
                title = if (editingCssSnippet != null) {
                    stringResource(MR.strings.novel_edit_css_snippet)
                } else {
                    stringResource(MR.strings.novel_add_css_snippet)
                },
                initialSnippet = editingCssSnippet?.second,
                onDismiss = {
                    showCssDialog = false
                    editingCssSnippet = null
                },
                onConfirm = { snippet ->
                    val updated = cssSnippets.toMutableList()
                    if (editingCssSnippet != null) {
                        updated[editingCssSnippet!!.first] = snippet
                    } else {
                        updated.add(snippet)
                    }
                    screenModel.preferences.novelCustomCssSnippets().set(Json.encodeToString(updated))
                    showCssDialog = false
                    editingCssSnippet = null
                },
            )
        }
        
        // JS Add/Edit Dialog
        if (showJsDialog || editingJsSnippet != null) {
            SnippetEditDialog(
                title = if (editingJsSnippet != null) {
                    stringResource(MR.strings.novel_edit_js_snippet)
                } else {
                    stringResource(MR.strings.novel_add_js_snippet)
                },
                initialSnippet = editingJsSnippet?.second,
                onDismiss = {
                    showJsDialog = false
                    editingJsSnippet = null
                },
                onConfirm = { snippet ->
                    val updated = jsSnippets.toMutableList()
                    if (editingJsSnippet != null) {
                        updated[editingJsSnippet!!.first] = snippet
                    } else {
                        updated.add(snippet)
                    }
                    screenModel.preferences.novelCustomJsSnippets().set(Json.encodeToString(updated))
                    showJsDialog = false
                    editingJsSnippet = null
                },
            )
        }
    }
}

@Composable
private fun SnippetSection(
    title: String,
    snippets: List<CodeSnippet>,
    onAddClick: () -> Unit,
    onEditClick: (Int, CodeSnippet) -> Unit,
    onDeleteClick: (Int) -> Unit,
    onToggleClick: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Code,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            IconButton(onClick = onAddClick) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(MR.strings.novel_add_snippet))
            }
        }

        snippets.forEachIndexed { index, snippet ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onToggleClick(index) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = snippet.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (snippet.enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            },
                        )
                        Text(
                            text = if (snippet.enabled) "Enabled" else "Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (snippet.enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            },
                        )
                    }
                    Row {
                        IconButton(onClick = { onEditClick(index, snippet) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(MR.strings.novel_edit_snippet))
                        }
                        IconButton(onClick = { onDeleteClick(index) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(MR.strings.novel_delete_snippet))
                        }
                    }
                }
            }
        }

        if (snippets.isEmpty()) {
            Text(
                text = "No snippets added",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SnippetEditDialog(
    title: String,
    initialSnippet: CodeSnippet?,
    onDismiss: () -> Unit,
    onConfirm: (CodeSnippet) -> Unit,
) {
    var snippetTitle by remember { mutableStateOf(initialSnippet?.title ?: "") }
    var snippetCode by remember { mutableStateOf(initialSnippet?.code ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = snippetTitle,
                    onValueChange = { snippetTitle = it },
                    label = { Text(stringResource(MR.strings.novel_snippet_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = snippetCode,
                    onValueChange = { snippetCode = it },
                    label = { Text(stringResource(MR.strings.novel_snippet_code)) },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (snippetTitle.isNotBlank() && snippetCode.isNotBlank()) {
                        onConfirm(CodeSnippet(snippetTitle.trim(), snippetCode, initialSnippet?.enabled ?: true))
                    }
                },
            ) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

/**
 * A simple RGB color picker dialog with sliders.
 */
@Composable
private fun ColorPickerDialog(
    title: String,
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var red by remember { mutableIntStateOf((initialColor shr 16) and 0xFF) }
    var green by remember { mutableIntStateOf((initialColor shr 8) and 0xFF) }
    var blue by remember { mutableIntStateOf(initialColor and 0xFF) }
    var hexInput by remember { mutableStateOf(String.format("%06X", initialColor and 0xFFFFFF)) }
    
    val currentColor = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                // Color preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(currentColor))
                        .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Hex input
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        val sanitized = input.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        hexInput = sanitized
                        if (sanitized.length == 6) {
                            try {
                                val parsed = sanitized.toLong(16).toInt()
                                red = (parsed shr 16) and 0xFF
                                green = (parsed shr 8) and 0xFF
                                blue = parsed and 0xFF
                            } catch (_: Exception) {}
                        }
                    },
                    label = { Text("Hex Color") },
                    prefix = { Text("#") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Red slider
                Text("Red: $red", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = red.toFloat(),
                    onValueChange = { 
                        red = it.toInt()
                        hexInput = String.format("%06X", currentColor and 0xFFFFFF)
                    },
                    valueRange = 0f..255f,
                    modifier = Modifier.fillMaxWidth(),
                )
                
                // Green slider
                Text("Green: $green", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = green.toFloat(),
                    onValueChange = { 
                        green = it.toInt()
                        hexInput = String.format("%06X", currentColor and 0xFFFFFF)
                    },
                    valueRange = 0f..255f,
                    modifier = Modifier.fillMaxWidth(),
                )
                
                // Blue slider
                Text("Blue: $blue", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = blue.toFloat(),
                    onValueChange = { 
                        blue = it.toInt()
                        hexInput = String.format("%06X", currentColor and 0xFFFFFF)
                    },
                    valueRange = 0f..255f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentColor) }) {
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
