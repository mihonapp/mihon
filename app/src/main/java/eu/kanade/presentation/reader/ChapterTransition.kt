package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.calculateChapterGap
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun ChapterTransition(
    transition: ChapterTransition,
    currChapterDownloaded: Boolean,
    goingToChapterDownloaded: Boolean,
) {
    val currChapter = transition.from.chapter.toDomainChapter()
    val goingToChapter = transition.to?.chapter?.toDomainChapter()

    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
        when (transition) {
            is ChapterTransition.Prev -> {
                TransitionText(
                    topLabel = stringResource(MR.strings.transition_previous),
                    topChapter = goingToChapter,
                    topChapterDownloaded = goingToChapterDownloaded,
                    bottomLabel = stringResource(MR.strings.transition_current),
                    bottomChapter = currChapter,
                    bottomChapterDownloaded = currChapterDownloaded,
                    fallbackLabel = stringResource(MR.strings.transition_no_previous),
                    chapterGap = calculateChapterGap(currChapter, goingToChapter),
                )
            }
            is ChapterTransition.Next -> {
                TransitionText(
                    topLabel = stringResource(MR.strings.transition_finished),
                    topChapter = currChapter,
                    topChapterDownloaded = currChapterDownloaded,
                    bottomLabel = stringResource(MR.strings.transition_next),
                    bottomChapter = goingToChapter,
                    bottomChapterDownloaded = goingToChapterDownloaded,
                    fallbackLabel = stringResource(MR.strings.transition_no_next),
                    chapterGap = calculateChapterGap(goingToChapter, currChapter),
                )
            }
        }
    }
}

@Composable
private fun TransitionText(
    topLabel: String,
    topChapter: Chapter?,
    topChapterDownloaded: Boolean,
    bottomLabel: String,
    bottomChapter: Chapter?,
    bottomChapterDownloaded: Boolean,
    fallbackLabel: String,
    chapterGap: Int,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth(),
    ) {
        if (topChapter != null) {
            ChapterText(
                header = topLabel,
                name = topChapter.name,
                scanlator = topChapter.scanlator,
                downloaded = topChapterDownloaded,
            )

            Spacer(Modifier.height(VerticalSpacerSize))
        } else {
            NoChapterNotification(
                text = fallbackLabel,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        if (bottomChapter != null) {
            if (chapterGap > 0) {
                ChapterGapWarning(
                    gapCount = chapterGap,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            Spacer(Modifier.height(VerticalSpacerSize))

            ChapterText(
                header = bottomLabel,
                name = bottomChapter.name,
                scanlator = bottomChapter.scanlator,
                downloaded = bottomChapterDownloaded,
            )
        } else {
            NoChapterNotification(
                text = fallbackLabel,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun NoChapterNotification(
    text: String,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier,
        colors = CardColor,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = null,
            )

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ChapterGapWarning(
    gapCount: Int,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier,
        colors = CardColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                tint = MaterialTheme.colorScheme.error,
                contentDescription = null,
            )

            Text(
                text = pluralStringResource(MR.plurals.missing_chapters_warning, count = gapCount, gapCount),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ChapterHeaderText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun ChapterText(
    header: String,
    name: String,
    scanlator: String?,
    downloaded: Boolean,
) {
    Column {
        ChapterHeaderText(
            text = header,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Text(
            text = buildAnnotatedString {
                if (downloaded) {
                    appendInlineContent(DownloadedIconContentId)
                    append(' ')
                }
                append(name)
            },
            fontSize = 20.sp,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge,
            inlineContent = persistentMapOf(
                DownloadedIconContentId to InlineTextContent(
                    Placeholder(
                        width = 22.sp,
                        height = 22.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(MR.strings.label_downloaded),
                    )
                },
            ),
        )

        scanlator?.let {
            Text(
                text = it,
                modifier = Modifier
                    .secondaryItemAlpha()
                    .padding(top = 2.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private val CardColor: CardColors
    @Composable
    get() = CardDefaults.outlinedCardColors(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )

private val VerticalSpacerSize = 24.dp
private const val DownloadedIconContentId = "downloaded"

private fun previewChapter(name: String, scanlator: String, chapterNumber: Double) = Chapter.create().copy(
    id = 0L,
    mangaId = 0L,
    url = "",
    name = name,
    scanlator = scanlator,
    chapterNumber = chapterNumber,
)
private val FakeChapter = previewChapter(
    name = "Vol.1, Ch.1 - Fake Chapter Title",
    scanlator = "Scanlator Name",
    chapterNumber = 1.0,
)
private val FakeGapChapter = previewChapter(
    name = "Vol.5, Ch.44 - Fake Gap Chapter Title",
    scanlator = "Scanlator Name",
    chapterNumber = 44.0,
)
private val FakeChapterLongTitle = previewChapter(
    name = "Vol.1, Ch.0 - The Mundane Musings of a Metafictional Manga: A Chapter About a Chapter, Featuring" +
        " an Absurdly Long Title and a Surprisingly Normal Day in the Lives of Our Heroes, as They Grapple with the " +
        "Daily Challenges of Existence, from Paying Rent to Finding Love, All While Navigating the Strange World of " +
        "Fictional Realities and Reality-Bending Fiction, Where the Fourth Wall is Always in Danger of Being Broken " +
        "and the Line Between Author and Character is Forever Blurred.",
    scanlator = "Long Long Funny Scanlator Sniper Group Name Reborn",
    chapterNumber = 1.0,
)

@PreviewLightDark
@Composable
private fun TransitionTextPreview() {
    TachiyomiPreviewTheme {
        Surface(modifier = Modifier.padding(48.dp)) {
            ChapterTransition(
                transition = ChapterTransition.Next(ReaderChapter(FakeChapter), ReaderChapter(FakeChapter)),
                currChapterDownloaded = false,
                goingToChapterDownloaded = true,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TransitionTextLongTitlePreview() {
    TachiyomiPreviewTheme {
        Surface(modifier = Modifier.padding(48.dp)) {
            ChapterTransition(
                transition = ChapterTransition.Next(ReaderChapter(FakeChapterLongTitle), ReaderChapter(FakeChapter)),
                currChapterDownloaded = true,
                goingToChapterDownloaded = true,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TransitionTextWithGapPreview() {
    TachiyomiPreviewTheme {
        Surface(modifier = Modifier.padding(48.dp)) {
            ChapterTransition(
                transition = ChapterTransition.Next(ReaderChapter(FakeChapter), ReaderChapter(FakeGapChapter)),
                currChapterDownloaded = true,
                goingToChapterDownloaded = false,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TransitionTextNoNextPreview() {
    TachiyomiPreviewTheme {
        Surface(modifier = Modifier.padding(48.dp)) {
            ChapterTransition(
                transition = ChapterTransition.Next(ReaderChapter(FakeChapter), null),
                currChapterDownloaded = true,
                goingToChapterDownloaded = false,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TransitionTextNoPreviousPreview() {
    TachiyomiPreviewTheme {
        Surface(modifier = Modifier.padding(48.dp)) {
            ChapterTransition(
                transition = ChapterTransition.Prev(ReaderChapter(FakeChapter), null),
                currChapterDownloaded = true,
                goingToChapterDownloaded = false,
            )
        }
    }
}
