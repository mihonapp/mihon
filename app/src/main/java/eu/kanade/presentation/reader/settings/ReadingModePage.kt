package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.domain.manga.model.readerOrientation
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SpinnerItem
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.text.NumberFormat

@Composable
internal fun ColumnScope.ReadingModePage(screenModel: ReaderSettingsScreenModel) {
    HeadingItem(MR.strings.pref_category_for_this_series)
    val manga by screenModel.mangaFlow.collectAsState()

    val readingMode = remember(manga) { ReadingMode.fromPreference(manga?.readingMode?.toInt()) }
    val readingModeLabels = ReadingMode.entries.map { stringResource(it.stringRes) }.toTypedArray()
    SpinnerItem(
        label = stringResource(MR.strings.pref_category_reading_mode),
        options = readingModeLabels,
        selectedIndex = ReadingMode.entries.indexOf(readingMode).coerceAtLeast(0),
        onSelect = { screenModel.onChangeReadingMode(ReadingMode.entries[it]) },
    )

    val orientation = remember(manga) { ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt()) }
    val orientationLabels = ReaderOrientation.entries.map { stringResource(it.stringRes) }.toTypedArray()
    SpinnerItem(
        label = stringResource(MR.strings.rotation_type),
        options = orientationLabels,
        selectedIndex = ReaderOrientation.entries.indexOf(orientation).coerceAtLeast(0),
        onSelect = { screenModel.onChangeOrientation(ReaderOrientation.entries[it]) },
    )

    HorizontalDivider()
    HeadingItem(MR.strings.app_settings)

    val viewer by screenModel.viewerFlow.collectAsState()
    if (viewer is WebtoonViewer) {
        WebtoonViewerSettings(screenModel)
    } else {
        PagerViewerSettings(screenModel)
    }
}

@Composable
private fun ColumnScope.PagerViewerSettings(screenModel: ReaderSettingsScreenModel) {
    HeadingItem(MR.strings.pager_viewer)

    val navigationModePager by screenModel.preferences.navigationModePager.collectAsState()
    val pagerNavInverted by screenModel.preferences.pagerNavInverted.collectAsState()
    TapZonesItems(
        selected = navigationModePager,
        onSelect = screenModel.preferences.navigationModePager::set,
        invertMode = pagerNavInverted,
        onSelectInvertMode = screenModel.preferences.pagerNavInverted::set,
    )

    val imageScaleTypeLabels = ReaderPreferences.ImageScaleType.map { stringResource(it) }.toTypedArray()
    val imageScaleType by screenModel.preferences.imageScaleType.collectAsState()
    SpinnerItem(
        label = stringResource(MR.strings.pref_image_scale_type),
        options = imageScaleTypeLabels,
        selectedIndex = (imageScaleType - 1).coerceIn(0, imageScaleTypeLabels.lastIndex),
        onSelect = { screenModel.preferences.imageScaleType.set(it + 1) },
    )

    val zoomStartLabels = ReaderPreferences.ZoomStart.map { stringResource(it) }.toTypedArray()
    val zoomStart by screenModel.preferences.zoomStart.collectAsState()
    SpinnerItem(
        label = stringResource(MR.strings.pref_zoom_start),
        options = zoomStartLabels,
        selectedIndex = (zoomStart - 1).coerceIn(0, zoomStartLabels.lastIndex),
        onSelect = { screenModel.preferences.zoomStart.set(it + 1) },
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_viewer_nav_smaller_tap_zone),
        pref = screenModel.preferences.smallerTapZone,
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_crop_borders),
        pref = screenModel.preferences.cropBorders,
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_landscape_zoom),
        pref = screenModel.preferences.landscapeZoom,
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_navigate_pan),
        pref = screenModel.preferences.navigateToPan,
    )

    val dualPageSplitPaged by screenModel.preferences.dualPageSplitPaged.collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_dual_page_split),
        pref = screenModel.preferences.dualPageSplitPaged,
    )

    if (dualPageSplitPaged) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_dual_page_invert),
            pref = screenModel.preferences.dualPageInvertPaged,
        )
    }

    val dualPageRotateToFit by screenModel.preferences.dualPageRotateToFit.collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_page_rotate),
        pref = screenModel.preferences.dualPageRotateToFit,
    )

    if (dualPageRotateToFit) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_page_rotate_invert),
            pref = screenModel.preferences.dualPageRotateToFitInvert,
        )
    }
}

@Composable
private fun ColumnScope.WebtoonViewerSettings(screenModel: ReaderSettingsScreenModel) {
    val numberFormat = remember { NumberFormat.getPercentInstance() }

    HeadingItem(MR.strings.webtoon_viewer)

    val navigationModeWebtoon by screenModel.preferences.navigationModeWebtoon.collectAsState()
    val webtoonNavInverted by screenModel.preferences.webtoonNavInverted.collectAsState()
    TapZonesItems(
        selected = navigationModeWebtoon,
        onSelect = screenModel.preferences.navigationModeWebtoon::set,
        invertMode = webtoonNavInverted,
        onSelectInvertMode = screenModel.preferences.webtoonNavInverted::set,
    )

    val webtoonSidePadding by screenModel.preferences.webtoonSidePadding.collectAsState()
    SliderItem(
        value = webtoonSidePadding,
        valueRange = ReaderPreferences.let { it.WEBTOON_PADDING_MIN..it.WEBTOON_PADDING_MAX },
        label = stringResource(MR.strings.pref_webtoon_side_padding),
        valueString = numberFormat.format(webtoonSidePadding / 100f),
        onChange = {
            screenModel.preferences.webtoonSidePadding.set(it)
        },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_viewer_nav_smaller_tap_zone),
        pref = screenModel.preferences.smallerTapZone,
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_crop_borders),
        pref = screenModel.preferences.cropBordersWebtoon,
    )

    val dualPageSplitWebtoon by screenModel.preferences.dualPageSplitWebtoon.collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_dual_page_split),
        pref = screenModel.preferences.dualPageSplitWebtoon,
    )

    if (dualPageSplitWebtoon) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_dual_page_invert),
            pref = screenModel.preferences.dualPageInvertWebtoon,
        )
    }

    val dualPageRotateToFitWebtoon by screenModel.preferences.dualPageRotateToFitWebtoon.collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_page_rotate),
        pref = screenModel.preferences.dualPageRotateToFitWebtoon,
    )

    if (dualPageRotateToFitWebtoon) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_page_rotate_invert),
            pref = screenModel.preferences.dualPageRotateToFitInvertWebtoon,
        )
    }

    CheckboxItem(
        label = stringResource(MR.strings.pref_double_tap_zoom),
        pref = screenModel.preferences.webtoonDoubleTapZoomEnabled,
    )
    CheckboxItem(
        label = stringResource(MR.strings.pref_webtoon_disable_zoom_out),
        pref = screenModel.preferences.webtoonDisableZoomOut,
    )
}

@Composable
private fun ColumnScope.TapZonesItems(
    selected: Int,
    onSelect: (Int) -> Unit,
    invertMode: ReaderPreferences.TappingInvertMode,
    onSelectInvertMode: (ReaderPreferences.TappingInvertMode) -> Unit,
) {
    val tapZoneLabels = ReaderPreferences.TapZones.map { stringResource(it) }.toTypedArray()
    SpinnerItem(
        label = stringResource(MR.strings.pref_viewer_nav),
        options = tapZoneLabels,
        selectedIndex = selected.coerceIn(0, tapZoneLabels.lastIndex),
        onSelect = onSelect,
    )

    if (selected != 6) {
        val invertLabels = ReaderPreferences.TappingInvertMode.entries.map { stringResource(it.titleRes) }.toTypedArray()
        SpinnerItem(
            label = stringResource(MR.strings.pref_read_with_tapping_inverted),
            options = invertLabels,
            selectedIndex = ReaderPreferences.TappingInvertMode.entries.indexOf(invertMode).coerceAtLeast(0),
            onSelect = { onSelectInvertMode(ReaderPreferences.TappingInvertMode.entries[it]) },
        )
    }
}
