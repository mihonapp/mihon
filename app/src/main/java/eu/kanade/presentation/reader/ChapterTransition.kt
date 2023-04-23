package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import tachiyomi.domain.chapter.service.calculateChapterGap
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.components.material.SecondaryItemAlpha

@Composable
fun ChapterTransition(
    transition: ChapterTransition,
    downloadManager: DownloadManager,
    manga: Manga?,
) {
    manga ?: return

    val currChapter = transition.from.chapter
    val currChapterDownloaded = transition.from.pageLoader?.isLocal == true

    val goingToChapter = transition.to?.chapter
    val goingToChapterDownloaded = if (goingToChapter != null) {
        downloadManager.isChapterDownloaded(
            goingToChapter.name,
            goingToChapter.scanlator,
            manga.title,
            manga.source,
            skipCache = true,
        )
    } else {
        false
    }

    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
        when (transition) {
            is ChapterTransition.Prev -> {
                TransitionText(
                    topLabel = stringResource(R.string.transition_previous),
                    topChapter = goingToChapter,
                    topChapterDownloaded = goingToChapterDownloaded,
                    bottomLabel = stringResource(R.string.transition_current),
                    bottomChapter = currChapter,
                    bottomChapterDownloaded = currChapterDownloaded,
                    fallbackLabel = stringResource(R.string.transition_no_previous),
                    chapterGap = calculateChapterGap(currChapter.toDomainChapter(), goingToChapter?.toDomainChapter()),
                )
            }
            is ChapterTransition.Next -> {
                TransitionText(
                    topLabel = stringResource(R.string.transition_finished),
                    topChapter = currChapter,
                    topChapterDownloaded = currChapterDownloaded,
                    bottomLabel = stringResource(R.string.transition_next),
                    bottomChapter = goingToChapter,
                    bottomChapterDownloaded = goingToChapterDownloaded,
                    fallbackLabel = stringResource(R.string.transition_no_next),
                    chapterGap = calculateChapterGap(goingToChapter?.toDomainChapter(), currChapter.toDomainChapter()),
                )
            }
        }
    }
}

@Composable
private fun TransitionText(
    topLabel: String,
    topChapter: Chapter? = null,
    topChapterDownloaded: Boolean,
    bottomLabel: String,
    bottomChapter: Chapter? = null,
    bottomChapterDownloaded: Boolean,
    fallbackLabel: String,
    chapterGap: Int,
) {
    val hasTopChapter = topChapter != null
    val hasBottomChapter = bottomChapter != null

    Column {
        Text(
            text = if (hasTopChapter) topLabel else fallbackLabel,
            fontWeight = FontWeight.Bold,
            textAlign = if (hasTopChapter) TextAlign.Start else TextAlign.Center,
        )
        topChapter?.let { ChapterText(chapter = it, downloaded = topChapterDownloaded) }

        Spacer(Modifier.height(16.dp))

        if (chapterGap > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    tint = MaterialTheme.colorScheme.error,
                    contentDescription = null,
                )

                Text(text = pluralStringResource(R.plurals.missing_chapters_warning, count = chapterGap, chapterGap))
            }

            Spacer(Modifier.height(16.dp))
        }

        Text(
            text = if (hasBottomChapter) bottomLabel else fallbackLabel,
            fontWeight = FontWeight.Bold,
            textAlign = if (hasBottomChapter) TextAlign.Start else TextAlign.Center,
        )
        bottomChapter?.let { ChapterText(chapter = it, downloaded = bottomChapterDownloaded) }
    }
}

@Composable
private fun ColumnScope.ChapterText(
    chapter: Chapter,
    downloaded: Boolean,
) {
    FlowRow(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (downloaded) {
            Icon(
                imageVector = Icons.Outlined.OfflinePin,
                contentDescription = stringResource(R.string.label_downloaded),
            )

            Spacer(Modifier.width(8.dp))
        }

        Text(chapter.name)
    }

    chapter.scanlator?.let {
        ProvideTextStyle(
            MaterialTheme.typography.bodyMedium.copy(
                color = LocalContentColor.current.copy(alpha = SecondaryItemAlpha),
            ),
        ) {
            Text(it)
        }
    }
}
