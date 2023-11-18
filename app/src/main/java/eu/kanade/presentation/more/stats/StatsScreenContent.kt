package eu.kanade.presentation.more.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.stats.components.StatsItem
import eu.kanade.presentation.more.stats.components.StatsOverviewItem
import eu.kanade.presentation.more.stats.components.StatsSection
import eu.kanade.presentation.more.stats.data.StatsData
import eu.kanade.presentation.util.toDurationString
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.localize
import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Composable
fun StatsScreenContent(
    state: StatsScreenState.Success,
    paddingValues: PaddingValues,
) {
    val statListState = rememberLazyListState()
    LazyColumn(
        state = statListState,
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
    }
}

@Composable
private fun OverviewSection(
    data: StatsData.Overview,
) {
    val none = localize(MR.strings.none)
    val context = LocalContext.current
    val readDurationString = remember(data.totalReadDuration) {
        data.totalReadDuration
            .toDuration(DurationUnit.MILLISECONDS)
            .toDurationString(context, fallback = none)
    }
    StatsSection(MR.strings.label_overview_section) {
        Row {
            StatsOverviewItem(
                title = data.libraryMangaCount.toString(),
                subtitle = localize(MR.strings.in_library),
                icon = Icons.Outlined.CollectionsBookmark,
            )
            StatsOverviewItem(
                title = data.completedMangaCount.toString(),
                subtitle = localize(MR.strings.label_completed_titles),
                icon = Icons.Outlined.LocalLibrary,
            )
            StatsOverviewItem(
                title = readDurationString,
                subtitle = localize(MR.strings.label_read_duration),
                icon = Icons.Outlined.Schedule,
            )
        }
    }
}

@Composable
private fun TitlesStats(
    data: StatsData.Titles,
) {
    StatsSection(MR.strings.label_titles_section) {
        Row {
            StatsItem(
                data.globalUpdateItemCount.toString(),
                localize(MR.strings.label_titles_in_global_update),
            )
            StatsItem(
                data.startedMangaCount.toString(),
                localize(MR.strings.label_started),
            )
            StatsItem(
                data.localMangaCount.toString(),
                localize(MR.strings.label_local),
            )
        }
    }
}

@Composable
private fun ChapterStats(
    data: StatsData.Chapters,
) {
    StatsSection(MR.strings.chapters) {
        Row {
            StatsItem(
                data.totalChapterCount.toString(),
                localize(MR.strings.label_total_chapters),
            )
            StatsItem(
                data.readChapterCount.toString(),
                localize(MR.strings.label_read_chapters),
            )
            StatsItem(
                data.downloadCount.toString(),
                localize(MR.strings.label_downloaded),
            )
        }
    }
}

@Composable
private fun TrackerStats(
    data: StatsData.Trackers,
) {
    val notApplicable = localize(MR.strings.not_applicable)
    val meanScoreStr = remember(data.trackedTitleCount, data.meanScore) {
        if (data.trackedTitleCount > 0 && !data.meanScore.isNaN()) {
            // All other numbers are localized in English
            String.format(Locale.ENGLISH, "%.2f ★", data.meanScore)
        } else {
            notApplicable
        }
    }
    StatsSection(MR.strings.label_tracker_section) {
        Row {
            StatsItem(
                data.trackedTitleCount.toString(),
                localize(MR.strings.label_tracked_titles),
            )
            StatsItem(
                meanScoreStr,
                localize(MR.strings.label_mean_score),
            )
            StatsItem(
                data.trackerCount.toString(),
                localize(MR.strings.label_used),
            )
        }
    }
}
