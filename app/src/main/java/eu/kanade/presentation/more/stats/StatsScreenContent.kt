package eu.kanade.presentation.more.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.ui.model.StatsCoverStyle
import eu.kanade.domain.ui.model.StatsProgressBarStyle
import eu.kanade.presentation.more.stats.components.MangaReadTimeItem
import eu.kanade.presentation.more.stats.components.StatsItem
import eu.kanade.presentation.more.stats.components.StatsOverviewItem
import eu.kanade.presentation.more.stats.data.StatsData
import eu.kanade.presentation.util.toDurationString
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CollectionsBookmark
import mihon.icons.materialsymbols.rounded.LocalLibrary
import mihon.icons.materialsymbols.rounded.Schedule
import tachiyomi.domain.history.model.ReadDurationByManga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Composable
fun StatsScreenContent(
    state: StatsScreenState.Success,
    paddingValues: PaddingValues,
    onMangaClick: (Long) -> Unit,
) {
    val context = LocalContext.current
    val uiPreferences = remember { context.appGraph.uiPreferences }
    val coverStyle by uiPreferences.statsScreenCoverStyle.collectAsState()
    var progressBarStyle by rememberSaveable { mutableStateOf(StatsProgressBarStyle.RELATIVE_TO_MAX) }
    var visibleReadTimeCount by rememberSaveable { mutableIntStateOf(READ_TIME_PAGE_SIZE) }

    LazyColumn(
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        item {
            OverviewSection(state.overview)
        }
        item {
            TitlesStats(state.titles)
        }
        item {
            ChapterStats(state.chapters)
        }
        item {
            TrackerStats(state.trackers)
        }
        mangaReadTimeSection(
            readDurations = state.readDurationByManga,
            visibleCount = visibleReadTimeCount,
            onShowMoreClick = { visibleReadTimeCount += READ_TIME_PAGE_SIZE },
            progressBarStyle = progressBarStyle,
            onProgressBarStyleChange = { progressBarStyle = it },
            coverStyle = coverStyle,
            onMangaClick = onMangaClick,
        )
    }
}

@Composable
private fun LazyItemScope.OverviewSection(
    data: StatsData.Overview,
) {
    val none = stringResource(MR.strings.none)
    val context = LocalContext.current
    val readDurationString = remember(data.totalReadDuration) {
        data.totalReadDuration
            .toDuration(DurationUnit.MILLISECONDS)
            .toDurationString(context, fallback = none)
    }
    SectionCard(MR.strings.label_overview_section) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            StatsOverviewItem(
                title = data.libraryMangaCount.toString(),
                subtitle = stringResource(MR.strings.in_library),
                icon = MaterialSymbols.Rounded.CollectionsBookmark,
            )
            StatsOverviewItem(
                title = readDurationString,
                subtitle = stringResource(MR.strings.label_read_duration),
                icon = MaterialSymbols.Rounded.Schedule,
            )
            StatsOverviewItem(
                title = data.completedMangaCount.toString(),
                subtitle = stringResource(MR.strings.label_completed_titles),
                icon = MaterialSymbols.Rounded.LocalLibrary,
            )
        }
    }
}

@Composable
private fun LazyItemScope.TitlesStats(
    data: StatsData.Titles,
) {
    SectionCard(MR.strings.label_titles_section) {
        Row {
            StatsItem(
                data.globalUpdateItemCount.toString(),
                stringResource(MR.strings.label_titles_in_global_update),
            )
            StatsItem(
                data.startedMangaCount.toString(),
                stringResource(MR.strings.label_started),
            )
            StatsItem(
                data.localMangaCount.toString(),
                stringResource(MR.strings.label_local),
            )
        }
    }
}

@Composable
private fun LazyItemScope.ChapterStats(
    data: StatsData.Chapters,
) {
    SectionCard(MR.strings.chapters) {
        Row {
            StatsItem(
                data.totalChapterCount.toString(),
                stringResource(MR.strings.label_total_chapters),
            )
            StatsItem(
                data.readChapterCount.toString(),
                stringResource(MR.strings.label_read_chapters),
            )
            StatsItem(
                data.downloadCount.toString(),
                stringResource(MR.strings.label_downloaded),
            )
        }
    }
}

@Composable
private fun LazyItemScope.TrackerStats(
    data: StatsData.Trackers,
) {
    val notApplicable = stringResource(MR.strings.not_applicable)
    val meanScoreStr = remember(data.trackedTitleCount, data.meanScore) {
        if (data.trackedTitleCount > 0 && !data.meanScore.isNaN()) {
            // All other numbers are localized in English
            "%.2f ★".format(Locale.ENGLISH, data.meanScore)
        } else {
            notApplicable
        }
    }
    SectionCard(MR.strings.label_tracker_section) {
        Row {
            StatsItem(
                data.trackedTitleCount.toString(),
                stringResource(MR.strings.label_tracked_titles),
            )
            StatsItem(
                meanScoreStr,
                stringResource(MR.strings.label_mean_score),
            )
            StatsItem(
                data.trackerCount.toString(),
                stringResource(MR.strings.label_used),
            )
        }
    }
}

private const val READ_TIME_PAGE_SIZE = 30

private fun LazyListScope.mangaReadTimeSection(
    readDurations: List<ReadDurationByManga>,
    visibleCount: Int,
    onShowMoreClick: () -> Unit,
    progressBarStyle: StatsProgressBarStyle,
    onProgressBarStyleChange: (StatsProgressBarStyle) -> Unit,
    coverStyle: StatsCoverStyle,
    onMangaClick: (Long) -> Unit,
) {
    if (readDurations.isEmpty()) return

    val scaleBase = when (progressBarStyle) {
        StatsProgressBarStyle.RELATIVE_TO_MAX -> readDurations.first().totalTimeRead
        StatsProgressBarStyle.RELATIVE_TO_TOTAL -> readDurations.sumOf { it.totalTimeRead }
    }

    item(key = "read_time_header") {
        Column {
            Text(
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.extraLarge),
                text = stringResource(MR.strings.label_read_duration),
                style = MaterialTheme.typography.titleSmall,
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
            ) {
                SegmentedButton(
                    selected = progressBarStyle == StatsProgressBarStyle.RELATIVE_TO_MAX,
                    onClick = { onProgressBarStyleChange(StatsProgressBarStyle.RELATIVE_TO_MAX) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text(stringResource(MR.strings.stats_scale_by_manga))
                }
                SegmentedButton(
                    selected = progressBarStyle == StatsProgressBarStyle.RELATIVE_TO_TOTAL,
                    onClick = { onProgressBarStyleChange(StatsProgressBarStyle.RELATIVE_TO_TOTAL) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text(stringResource(MR.strings.stats_scale_total))
                }
            }
        }
    }

    items(
        items = readDurations.take(visibleCount),
        key = { "read_time_${it.mangaId}" },
    ) { item ->
        MangaReadTimeItem(
            item = item,
            scaleBase = scaleBase,
            coverStyle = coverStyle,
            onMangaClick = onMangaClick,
        )
    }

    if (visibleCount < readDurations.size) {
        item(key = "read_time_show_more") {
            TextButton(
                onClick = onShowMoreClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium),
            ) {
                Text(stringResource(MR.strings.stats_show_more))
            }
        }
    }
}
