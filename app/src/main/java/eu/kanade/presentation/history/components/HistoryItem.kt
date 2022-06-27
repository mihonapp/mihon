package eu.kanade.presentation.history.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.history.model.HistoryWithRelations
import eu.kanade.presentation.components.MangaCover
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.lang.toTimestampString
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

private val HISTORY_ITEM_HEIGHT = 96.dp

@Composable
fun HistoryItem(
    modifier: Modifier = Modifier,
    history: HistoryWithRelations,
    onClickCover: () -> Unit,
    onClickResume: () -> Unit,
    onClickDelete: () -> Unit,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClickResume)
            .height(HISTORY_ITEM_HEIGHT)
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
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
                .padding(start = horizontalPadding, end = 8.dp),
        ) {
            val textStyle = MaterialTheme.typography.bodyMedium
            Text(
                text = history.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = textStyle.copy(fontWeight = FontWeight.SemiBold),
            )
            val readAt = remember { history.readAt?.toTimestampString() ?: "" }
            Text(
                text = if (history.chapterNumber > -1) {
                    stringResource(
                        R.string.recent_manga_time,
                        chapterFormatter.format(history.chapterNumber),
                        readAt,
                    )
                } else {
                    readAt
                },
                modifier = Modifier.padding(top = 4.dp),
                style = textStyle,
            )
        }

        IconButton(onClick = onClickDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun HistoryItemShimmer(brush: Brush) {
    Row(
        modifier = Modifier
            .height(HISTORY_ITEM_HEIGHT)
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(MangaCover.Book.ratio)
                .clip(RoundedCornerShape(4.dp))
                .drawBehind {
                    drawRect(brush = brush)
                },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = horizontalPadding, end = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .drawBehind {
                        drawRect(brush = brush)
                    }
                    .height(14.dp)
                    .fillMaxWidth(0.70f),
            )
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .height(14.dp)
                    .fillMaxWidth(0.45f)
                    .drawBehind {
                        drawRect(brush = brush)
                    },
            )
        }
    }
}

private val chapterFormatter = DecimalFormat(
    "#.###",
    DecimalFormatSymbols().apply { decimalSeparator = '.' },
)
