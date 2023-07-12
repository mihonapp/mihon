package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.util.collectAsState
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.util.system.isReleaseBuildType
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SliderItem
import java.text.NumberFormat

@Composable
internal fun ColumnScope.ReadingModePage(screenModel: ReaderSettingsScreenModel) {
    HeadingItem(R.string.pref_category_for_this_series)

    // Reading mode
    // Rotation type

    // if (pager)
    PagerViewerSettings(screenModel)

    WebtoonViewerSettings(screenModel)
}

@Composable
private fun ColumnScope.PagerViewerSettings(screenModel: ReaderSettingsScreenModel) {
    HeadingItem(R.string.pager_viewer)

    // Tap zones
    // Invert tap zones
    // Scale type
    // Zoom start position

    val cropBorders by screenModel.preferences.cropBorders().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.pref_crop_borders),
        checked = cropBorders,
        onClick = {
            screenModel.togglePreference(ReaderPreferences::cropBorders)
        },
    )

    val landscapeZoom by screenModel.preferences.landscapeZoom().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.pref_landscape_zoom),
        checked = landscapeZoom,
        onClick = {
            screenModel.togglePreference(ReaderPreferences::landscapeZoom)
        },
    )

    val navigateToPan by screenModel.preferences.navigateToPan().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.pref_navigate_pan),
        checked = navigateToPan,
        onClick = {
            screenModel.togglePreference(ReaderPreferences::navigateToPan)
        },
    )

    val dualPageSplitPaged by screenModel.preferences.dualPageSplitPaged().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.pref_dual_page_split),
        checked = dualPageSplitPaged,
        onClick = {
            screenModel.togglePreference(ReaderPreferences::dualPageSplitPaged)
        },
    )

    if (dualPageSplitPaged) {
        val dualPageInvertPaged by screenModel.preferences.dualPageInvertPaged().collectAsState()
        CheckboxItem(
            label = stringResource(R.string.pref_dual_page_invert),
            checked = dualPageInvertPaged,
            onClick = {
                screenModel.togglePreference(ReaderPreferences::dualPageInvertPaged)
            },
        )
    }

    val dualPageRotateToFit by screenModel.preferences.dualPageRotateToFit().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.pref_page_rotate),
        checked = dualPageRotateToFit,
        onClick = {
            screenModel.togglePreference(ReaderPreferences::dualPageRotateToFit)
        },
    )

    if (dualPageRotateToFit) {
        val dualPageRotateToFitInvert by screenModel.preferences.dualPageRotateToFitInvert().collectAsState()
        CheckboxItem(
            label = stringResource(R.string.pref_page_rotate_invert),
            checked = dualPageRotateToFitInvert,
            onClick = {
                screenModel.togglePreference(ReaderPreferences::dualPageRotateToFitInvert)
            },
        )
    }
}

@Composable
private fun ColumnScope.WebtoonViewerSettings(screenModel: ReaderSettingsScreenModel) {
    val numberFormat = remember { NumberFormat.getPercentInstance() }

    HeadingItem(R.string.webtoon_viewer)

    // TODO: Tap zones
    // TODO: Invert tap zones

    val webtoonSidePadding by screenModel.preferences.webtoonSidePadding().collectAsState()
    SliderItem(
        label = stringResource(R.string.pref_webtoon_side_padding),
        min = ReaderPreferences.WEBTOON_PADDING_MIN,
        max = ReaderPreferences.WEBTOON_PADDING_MAX,
        value = webtoonSidePadding,
        valueText = numberFormat.format(webtoonSidePadding / 100f),
        onChange = {
            screenModel.preferences.webtoonSidePadding().set(it)
        },
    )

    val cropBordersWebtoon by screenModel.preferences.cropBordersWebtoon().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.pref_crop_borders),
        checked = cropBordersWebtoon,
        onClick = {
            screenModel.togglePreference(ReaderPreferences::cropBordersWebtoon)
        },
    )

    val dualPageSplitWebtoon by screenModel.preferences.dualPageSplitWebtoon().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.pref_dual_page_split),
        checked = dualPageSplitWebtoon,
        onClick = {
            screenModel.togglePreference(ReaderPreferences::dualPageSplitWebtoon)
        },
    )

    if (dualPageSplitWebtoon) {
        val dualPageInvertWebtoon by screenModel.preferences.dualPageInvertWebtoon()
            .collectAsState()
        CheckboxItem(
            label = stringResource(R.string.pref_dual_page_invert),
            checked = dualPageInvertWebtoon,
            onClick = {
                screenModel.togglePreference(ReaderPreferences::dualPageInvertWebtoon)
            },
        )
    }

    if (!isReleaseBuildType) {
        val longStripSplitWebtoon by screenModel.preferences.longStripSplitWebtoon()
            .collectAsState()
        CheckboxItem(
            label = stringResource(R.string.pref_long_strip_split),
            checked = longStripSplitWebtoon,
            onClick = {
                screenModel.togglePreference(ReaderPreferences::longStripSplitWebtoon)
            },
        )
    }

    val webtoonDoubleTapZoomEnabled by screenModel.preferences.webtoonDoubleTapZoomEnabled().collectAsState()
    CheckboxItem(
        label = stringResource(R.string.pref_double_tap_zoom),
        checked = webtoonDoubleTapZoomEnabled,
        onClick = {
            screenModel.togglePreference(ReaderPreferences::webtoonDoubleTapZoomEnabled)
        },
    )
}
