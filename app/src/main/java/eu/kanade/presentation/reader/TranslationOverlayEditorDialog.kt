package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.data.translation.SavedTranslationPage
import eu.kanade.tachiyomi.data.translation.TranslationBoxEdit
import tachiyomi.data.Translation_boxes
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale

@Composable
fun TranslationOverlayEditorDialog(
    savedPage: SavedTranslationPage?,
    onDismissRequest: () -> Unit,
    onSave: (List<TranslationBoxEdit>) -> Unit,
) {
    var nextLocalId by remember(savedPage?.page?._id) { mutableIntStateOf(savedPage?.boxes?.size ?: 0) }
    var boxes by remember(savedPage?.page?._id, savedPage?.boxes) {
        mutableStateOf(
            savedPage?.boxes
                ?.map { box -> box.toEditableBox() }
                .orEmpty(),
        )
    }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(MR.strings.translation_overlay_editor),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(
                    onClick = {
                        boxes = boxes + EditableTranslationBox(
                            localId = "new-${nextLocalId++}",
                            x = 0.1f,
                            y = 0.1f,
                            width = 0.55f,
                            height = 0.12f,
                            originalText = "",
                            translatedText = "",
                            textType = "dialogue",
                        )
                    },
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(stringResource(MR.strings.action_add))
                }
            }

            if (boxes.isEmpty()) {
                Text(
                    text = stringResource(MR.strings.translation_overlay_no_boxes),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                ) {
                    itemsIndexed(
                        items = boxes,
                        key = { _, box -> box.localId },
                    ) { index, box ->
                        TranslationBoxEditor(
                            index = index,
                            box = box,
                            onChange = { updated ->
                                boxes = boxes.toMutableList().also { it[index] = updated.constrained() }
                            },
                            onDelete = {
                                boxes = boxes.toMutableList().also { it.removeAt(index) }
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(MR.strings.action_cancel))
                }
                Button(
                    onClick = {
                        onSave(boxes.map { it.constrained().toEdit() })
                    },
                ) {
                    Text(stringResource(MR.strings.action_save))
                }
            }
        }
    }
}

@Composable
private fun TranslationBoxEditor(
    index: Int,
    box: EditableTranslationBox,
    onChange: (EditableTranslationBox) -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(MR.strings.translation_overlay_box_label, index + 1),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(MR.strings.action_delete),
                )
            }
        }

        OutlinedTextField(
            value = box.translatedText,
            onValueChange = { onChange(box.copy(translatedText = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(MR.strings.translation_overlay_translated_text)) },
            minLines = 2,
        )
        OutlinedTextField(
            value = box.originalText,
            onValueChange = { onChange(box.copy(originalText = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(MR.strings.translation_overlay_original_text)) },
            minLines = 1,
        )
        OutlinedTextField(
            value = box.textType,
            onValueChange = { onChange(box.copy(textType = it.ifBlank { "dialogue" })) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(MR.strings.translation_overlay_text_type)) },
            singleLine = true,
        )

        CoordinateSlider(
            label = stringResource(MR.strings.translation_overlay_x),
            value = box.x,
            valueRange = 0f..(1f - box.width).coerceAtLeast(0f),
            onValueChange = { onChange(box.copy(x = it)) },
        )
        CoordinateSlider(
            label = stringResource(MR.strings.translation_overlay_y),
            value = box.y,
            valueRange = 0f..(1f - box.height).coerceAtLeast(0f),
            onValueChange = { onChange(box.copy(y = it)) },
        )
        CoordinateSlider(
            label = stringResource(MR.strings.translation_overlay_width),
            value = box.width,
            valueRange = 0.01f..(1f - box.x).coerceAtLeast(0.01f),
            onValueChange = { onChange(box.copy(width = it)) },
        )
        CoordinateSlider(
            label = stringResource(MR.strings.translation_overlay_height),
            value = box.height,
            valueRange = 0.01f..(1f - box.y).coerceAtLeast(0.01f),
            onValueChange = { onChange(box.copy(height = it)) },
        )

        Spacer(modifier = Modifier.fillMaxWidth())
        HorizontalDivider()
    }
}

@Composable
private fun CoordinateSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = String.format(Locale.ROOT, "%.2f", value),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

private data class EditableTranslationBox(
    val localId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val originalText: String,
    val translatedText: String,
    val textType: String,
    val confidence: Double? = null,
    val styleJson: String? = null,
) {
    fun constrained(): EditableTranslationBox {
        val safeX = x.coerceIn(0f, 0.99f)
        val safeY = y.coerceIn(0f, 0.99f)
        return copy(
            x = safeX,
            y = safeY,
            width = width.coerceIn(0.01f, 1f - safeX),
            height = height.coerceIn(0.01f, 1f - safeY),
        )
    }

    fun toEdit(): TranslationBoxEdit {
        return TranslationBoxEdit(
            x = x.toDouble(),
            y = y.toDouble(),
            width = width.toDouble(),
            height = height.toDouble(),
            originalText = originalText,
            translatedText = translatedText,
            textType = textType.ifBlank { "dialogue" },
            confidence = confidence,
            styleJson = styleJson,
        )
    }
}

private fun Translation_boxes.toEditableBox(): EditableTranslationBox {
    return EditableTranslationBox(
        localId = _id.toString(),
        x = x.toFloat(),
        y = y.toFloat(),
        width = width.toFloat(),
        height = height.toFloat(),
        originalText = original_text,
        translatedText = translated_text,
        textType = text_type,
        confidence = confidence,
        styleJson = style_json,
    ).constrained()
}
