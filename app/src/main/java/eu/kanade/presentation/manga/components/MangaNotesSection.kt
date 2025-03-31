package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.components.material.ButtonDefaults
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MangaNotesSection(
    content: String,
    expanded: Boolean,
    onEditNotes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (content.isBlank()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MangaNotesDisplay(
            content = content,
            modifier = modifier.fillMaxWidth(),
        )
        if (expanded) {
            Button(
                onClick = onEditNotes,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.EditNote,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp),
                    )
                    Text(
                        stringResource(MR.strings.action_edit_notes),
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier
                .padding(
                    top = if (expanded) 0.dp else 12.dp,
                    bottom = if (expanded) 16.dp else 12.dp,
                ),
        )
    }
}

@PreviewLightDark
@Composable
private fun MangaNotesSectionPreview() {
    MangaNotesSection(
        onEditNotes = {},
        expanded = true,
        content = "# Hello world\ntest1234 hi there!",
    )
}
