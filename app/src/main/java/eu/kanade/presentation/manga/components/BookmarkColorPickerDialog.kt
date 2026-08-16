package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AdaptiveSheet
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun BookmarkColor.asComposeColor(): Color {
    return when (this) {
        BookmarkColor.DEFAULT -> MaterialTheme.colorScheme.primary
        BookmarkColor.RED -> Color(0xFFE53935)
        BookmarkColor.ORANGE -> Color(0xFFFB8C00)
        BookmarkColor.YELLOW -> Color(0xFFFDD835)
        BookmarkColor.GREEN -> Color(0xFF43A047)
        BookmarkColor.BLUE -> Color(0xFF1E88E5)
        BookmarkColor.PURPLE -> Color(0xFF8E24AA)
        BookmarkColor.PINK -> Color(0xFFD81B60)
    }
}

fun BookmarkColor.titleRes(): StringResource {
    return when (this) {
        BookmarkColor.DEFAULT -> MR.strings.bookmark_color_default
        BookmarkColor.RED -> MR.strings.bookmark_color_red
        BookmarkColor.ORANGE -> MR.strings.bookmark_color_orange
        BookmarkColor.YELLOW -> MR.strings.bookmark_color_yellow
        BookmarkColor.GREEN -> MR.strings.bookmark_color_green
        BookmarkColor.BLUE -> MR.strings.bookmark_color_blue
        BookmarkColor.PURPLE -> MR.strings.bookmark_color_purple
        BookmarkColor.PINK -> MR.strings.bookmark_color_pink
    }
}

@Composable
fun BookmarkColorPickerDialog(
    selectedColor: BookmarkColor,
    bookmarked: Boolean,
    onColorSelected: (BookmarkColor) -> Unit,
    onRemoveBookmark: (() -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SettingsItemsPaddings.Horizontal,
                    vertical = MaterialTheme.padding.medium,
                ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            Text(
                text = stringResource(MR.strings.action_bookmark_color),
                style = MaterialTheme.typography.titleMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookmarkColor.entries.forEach { color ->
                    val composeColor = color.asComposeColor()
                    val selected = bookmarked && color == selectedColor
                    val description = stringResource(color.titleRes())
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(composeColor)
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .semantics { contentDescription = description }
                            .clickable(
                                role = Role.Button,
                                onClick = { onColorSelected(color) },
                            ),
                    )
                }
            }

            if (onRemoveBookmark != null && bookmarked) {
                TextButton(
                    onClick = onRemoveBookmark,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(text = stringResource(MR.strings.action_remove_bookmark))
                }
            }
        }
    }
}
