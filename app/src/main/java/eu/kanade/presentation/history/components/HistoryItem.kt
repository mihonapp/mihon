package eu.kanade.presentation.history.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.DotSeparatorText
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.util.lang.toTimestampString
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

private val HistoryItemHeight = 96.dp

@Composable
fun HistoryItem(
    history: HistoryWithRelations,
    onClickCover: () -> Unit,
    onClickResume: () -> Unit,
    onClickDelete: () -> Unit,
    onClickFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClickResume)
            .height(HistoryItemHeight)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Book(
            modifier = Modifier.fillMaxHeight(),
            data = history.coverData,
            onClick = onClickCover,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MaterialTheme.padding.medium, end = MaterialTheme.padding.small),
        ) {
            val textStyle = MaterialTheme.typography.bodyMedium
            Text(
                text = history.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = textStyle,
            )
            val readAt = remember(history.readAt) { history.readAt?.toTimestampString() ?: "" }
            val chapterName = remember(history.chapterName) { history.chapterName.trim() }
            val chapterLabel = when {
                chapterName.isNotEmpty() -> chapterName
                history.chapterNumber > -1 -> stringResource(
                    MR.strings.display_mode_chapter,
                    formatChapterNumber(history.chapterNumber),
                )
                else -> ""
            }

            ProvideTextStyle(
                value = MaterialTheme.typography.bodySmall.merge(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
                ),
            ) {
                when {
                    readAt.isNotEmpty() && chapterLabel.isNotEmpty() -> Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = readAt, maxLines = 1)
                        DotSeparatorText()
                        Text(
                            text = chapterLabel,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    readAt.isNotEmpty() -> Text(
                        text = readAt,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                    )
                    chapterLabel.isNotEmpty() -> Text(
                        text = chapterLabel,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (!history.coverData.isMangaFavorite) {
            IconButton(onClick = onClickFavorite) {
                Icon(
                    imageVector = Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(MR.strings.add_to_library),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        IconButton(onClick = onClickDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(MR.strings.action_delete),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun HistoryItemPreviews(
    @PreviewParameter(HistoryWithRelationsProvider::class)
    historyWithRelations: HistoryWithRelations,
) {
    TachiyomiPreviewTheme {
        Surface {
            HistoryItem(
                history = historyWithRelations,
                onClickCover = {},
                onClickResume = {},
                onClickDelete = {},
                onClickFavorite = {},
            )
        }
    }
}
