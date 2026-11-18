package eu.kanade.presentation.more.stats.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.model.StatsCoverStyle
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.toDurationString
import tachiyomi.domain.history.model.ReadDurationByManga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Composable
fun MangaReadTimeItem(
    item: ReadDurationByManga,
    scaleBase: Long,
    coverStyle: StatsCoverStyle,
    onMangaClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = item.title
    val timeRead = item.totalTimeRead
    val cover = item.cover
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onMangaClick(item.mangaId) }
            .padding(
                horizontal = MaterialTheme.padding.medium,
                // The list already spaces rows by padding.small; book covers get padding.medium in total
                vertical = if (coverStyle == StatsCoverStyle.BOOK) MaterialTheme.padding.extraSmall else 0.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (coverStyle) {
            StatsCoverStyle.SQUARE -> {
                MangaCover.Square(
                    data = cover,
                    modifier = Modifier.size(48.dp),
                    contentDescription = title,
                )
            }
            StatsCoverStyle.BOOK -> {
                MangaCover.Book(
                    data = cover,
                    modifier = Modifier.size(width = 40.dp, height = 60.dp),
                    contentDescription = title,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            LinearProgressIndicator(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .fillMaxWidth()
                    .height(12.dp),
                progress = { (timeRead.toFloat() / scaleBase.toFloat()).coerceIn(0f, 1f) },
            )

            val context = LocalContext.current
            val none = stringResource(MR.strings.none)
            val durationString = remember(timeRead) {
                timeRead.toDuration(DurationUnit.MILLISECONDS).toDurationString(context, fallback = none)
            }
            Text(
                text = durationString,
                modifier = Modifier.secondaryItemAlpha(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
