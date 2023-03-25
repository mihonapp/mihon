package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults.assistChipColors
import androidx.compose.material3.AssistChipDefaults.assistChipElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R

@Composable
fun ChapterHeader(
    enabled: Boolean,
    chapterCount: Int?,
    missingChapters: Int?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (chapterCount == null) {
                stringResource(R.string.chapters)
            } else {
                pluralStringResource(id = R.plurals.manga_num_chapters, count = chapterCount, chapterCount)
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onBackground,
        )

        // Missing chapters
        if (missingChapters == null) {
            DrawWarning(
                text = stringResource(R.string.missing_chapters_unknown),
            )
        } else if (missingChapters > 0) {
            DrawWarning(
                text = pluralStringResource(
                    id = R.plurals.missing_chapters,
                    count = missingChapters,
                    missingChapters,
                ),
            )
        }
    }
}

@Composable
private fun DrawWarning(text: String) {
    AssistChip(
        onClick = {
            // TODO Show missing chapters
        },
        label = {
            Text(
                text = text,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        shape = MaterialTheme.shapes.small,
        border = null,
        colors = assistChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = assistChipElevation(1.dp),
    )
}
