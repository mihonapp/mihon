package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.domain.manga.model.readerOrientation
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.domain.manga.model.spread
import eu.kanade.domain.manga.model.spreadForcePairing
import eu.kanade.domain.manga.model.spreadShift
import eu.kanade.domain.manga.model.spreadSoloPage
import eu.kanade.domain.manga.model.spreadVerticalFit
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsViewModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.setting.Spread
import eu.kanade.tachiyomi.ui.reader.setting.SpreadForcePairing
import eu.kanade.tachiyomi.ui.reader.setting.SpreadShift
import eu.kanade.tachiyomi.ui.reader.setting.SpreadSoloPage
import eu.kanade.tachiyomi.ui.reader.setting.SpreadVerticalFit
import eu.kanade.tachiyomi.ui.reader.viewer.pager.L2RPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.CollapsibleBox
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.text.NumberFormat

@Composable
internal fun ColumnScope.ReadingModePage(viewModel: ReaderSettingsViewModel) {
    HeadingItem(MR.strings.pref_category_for_this_series)
    val manga by viewModel.mangaFlow.collectAsState()
    val viewer by viewModel.viewerFlow.collectAsState()

    val readingMode = remember(manga) { ReadingMode.fromPreference(manga?.readingMode?.toInt()) }
    SettingsChipRow(MR.strings.pref_category_reading_mode) {
        ReadingMode.entries.map {
            FilterChip(
                selected = it == readingMode,
                onClick = { viewModel.onChangeReadingMode(it) },
                label = { Text(stringResource(it.stringRes)) },
            )
        }
    }

    val spread = remember(manga) { Spread.fromPreference(manga?.spread?.toInt()) }
    val defaultSpread by viewModel.preferences.defaultSpread.collectAsState()
    val spreadEffective = when (spread) {
        Spread.DEFAULT -> defaultSpread
        Spread.ENABLED -> true
        Spread.DISABLED -> false
    }

    // Its visibility is gated on the reading mode above, so it's placed directly beneath it
    // rather than further down with the rest of the pager-only settings, minimizing the visual
    // distance between a setting and what determines whether it's even shown.
    if (viewer is L2RPagerViewer || viewer is R2LPagerViewer) {
        SettingsChipRow(MR.strings.pref_spread) {
            Spread.entries.map {
                FilterChip(
                    selected = it == spread,
                    onClick = { viewModel.onChangeSpread(it) },
                    label = {
                        Text(
                            stringResource(
                                when (it) {
                                    Spread.DEFAULT -> MR.strings.label_default
                                    Spread.ENABLED -> MR.strings.on
                                    Spread.DISABLED -> MR.strings.off
                                },
                            ),
                        )
                    },
                )
            }
        }

        if (spreadEffective) {
            val spreadForcePairing =
                remember(manga) { SpreadForcePairing.fromPreference(manga?.spreadForcePairing?.toInt()) }
            val spreadVerticalFit = remember(manga) {
                SpreadVerticalFit.fromPreference(manga?.spreadVerticalFit?.toInt())
            }
            val spreadSoloPage = remember(manga) {
                SpreadSoloPage.fromPreference(manga?.spreadSoloPage?.toInt())
            }
            // The shift selection tracks the session holder (updated in place on tap), not manga's
            // persisted flag; changing manga in State would rebuild the viewer. See ReaderViewModel.
            val spreadShift by viewModel.spreadShiftForce.collectAsState()

            CollapsibleBox(heading = stringResource(MR.strings.pref_spread_fine_tune)) {
                Column {
                    SettingsChipRow(MR.strings.pref_spread_force_pairing) {
                        SpreadForcePairing.entries.map {
                            FilterChip(
                                selected = it == spreadForcePairing,
                                onClick = { viewModel.onChangeSpreadForcePairing(it) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (it) {
                                                SpreadForcePairing.DEFAULT -> MR.strings.label_default
                                                SpreadForcePairing.ENABLED -> MR.strings.on
                                                SpreadForcePairing.DISABLED -> MR.strings.off
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }

                    SettingsChipRow(MR.strings.pref_spread_shift_series) {
                        SpreadShift.entries.map {
                            FilterChip(
                                selected = it == spreadShift,
                                onClick = { viewModel.onChangeSpreadShift(it) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (it) {
                                                SpreadShift.DEFAULT -> MR.strings.label_default
                                                SpreadShift.SHIFTED -> MR.strings.spread_shift_shifted
                                                SpreadShift.UNSHIFTED -> MR.strings.spread_shift_unshifted
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }

                    SettingsChipRow(MR.strings.pref_spread_vertical_fit) {
                        SpreadVerticalFit.entries.map {
                            FilterChip(
                                selected = it == spreadVerticalFit,
                                onClick = { viewModel.onChangeSpreadVerticalFit(it) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (it) {
                                                SpreadVerticalFit.DEFAULT -> MR.strings.label_default
                                                SpreadVerticalFit.MATCH ->
                                                    MR.strings.spread_vertical_fit_match
                                                SpreadVerticalFit.TOP ->
                                                    MR.strings.spread_vertical_fit_top
                                                SpreadVerticalFit.CENTER ->
                                                    MR.strings.spread_vertical_fit_center
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }

                    SettingsChipRow(MR.strings.pref_spread_solo_page) {
                        SpreadSoloPage.entries.map {
                            FilterChip(
                                selected = it == spreadSoloPage,
                                onClick = { viewModel.onChangeSpreadSoloPage(it) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (it) {
                                                SpreadSoloPage.DEFAULT -> MR.strings.label_default
                                                SpreadSoloPage.CENTER ->
                                                    MR.strings.spread_solo_page_center
                                                SpreadSoloPage.JUSTIFY ->
                                                    MR.strings.spread_solo_page_justify
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    val orientation = remember(manga) { ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt()) }
    SettingsChipRow(MR.strings.rotation_type) {
        ReaderOrientation.entries.map {
            FilterChip(
                selected = it == orientation,
                onClick = { viewModel.onChangeOrientation(it) },
                label = { Text(stringResource(it.stringRes)) },
            )
        }
    }

    if (viewer is WebtoonViewer) {
        WebtoonViewerSettings(viewModel)
    } else {
        PagerViewerSettings(viewModel, spreadEffective)
    }
}

@Composable
private fun ColumnScope.PagerViewerSettings(viewModel: ReaderSettingsViewModel, spreadEffective: Boolean) {
    HeadingItem(MR.strings.pager_viewer)

    val navigationModePager by viewModel.preferences.navigationModePager.collectAsState()
    val pagerNavInverted by viewModel.preferences.pagerNavInverted.collectAsState()
    TapZonesItems(
        selected = navigationModePager,
        onSelect = viewModel.preferences.navigationModePager::set,
        invertMode = pagerNavInverted,
        onSelectInvertMode = viewModel.preferences.pagerNavInverted::set,
    )

    val imageScaleType by viewModel.preferences.imageScaleType.collectAsState()
    SettingsChipRow(MR.strings.pref_image_scale_type) {
        ReaderPreferences.ImageScaleType.mapIndexed { index, it ->
            FilterChip(
                selected = imageScaleType == index + 1,
                onClick = { viewModel.preferences.imageScaleType.set(index + 1) },
                label = { Text(stringResource(it)) },
            )
        }
    }

    val zoomStart by viewModel.preferences.zoomStart.collectAsState()
    SettingsChipRow(MR.strings.pref_zoom_start) {
        ReaderPreferences.ZoomStart.mapIndexed { index, it ->
            FilterChip(
                selected = zoomStart == index + 1,
                onClick = { viewModel.preferences.zoomStart.set(index + 1) },
                label = { Text(stringResource(it)) },
            )
        }
    }

    CheckboxItem(
        label = stringResource(MR.strings.pref_crop_borders),
        pref = viewModel.preferences.cropBorders,
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_landscape_zoom),
        pref = viewModel.preferences.landscapeZoom,
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_navigate_pan),
        pref = viewModel.preferences.navigateToPan,
    )

    // Both wide-page reformatters are inert while two-page spread is on (the spread owns wide-page
    // presentation), so they are greyed here, matching the global reader settings.
    val dualPageSplitPaged by viewModel.preferences.dualPageSplitPaged.collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_dual_page_split),
        pref = viewModel.preferences.dualPageSplitPaged,
        enabled = !spreadEffective,
    )

    if (dualPageSplitPaged) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_dual_page_invert),
            pref = viewModel.preferences.dualPageInvertPaged,
            enabled = !spreadEffective,
        )
    }

    val dualPageRotateToFit by viewModel.preferences.dualPageRotateToFit.collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_page_rotate),
        pref = viewModel.preferences.dualPageRotateToFit,
        enabled = !spreadEffective,
    )

    if (dualPageRotateToFit) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_page_rotate_invert),
            pref = viewModel.preferences.dualPageRotateToFitInvert,
            enabled = !spreadEffective,
        )
    }
}

@Composable
private fun ColumnScope.WebtoonViewerSettings(viewModel: ReaderSettingsViewModel) {
    val numberFormat = remember { NumberFormat.getPercentInstance() }

    HeadingItem(MR.strings.webtoon_viewer)

    val navigationModeWebtoon by viewModel.preferences.navigationModeWebtoon.collectAsState()
    val webtoonNavInverted by viewModel.preferences.webtoonNavInverted.collectAsState()
    TapZonesItems(
        selected = navigationModeWebtoon,
        onSelect = viewModel.preferences.navigationModeWebtoon::set,
        invertMode = webtoonNavInverted,
        onSelectInvertMode = viewModel.preferences.webtoonNavInverted::set,
    )

    val webtoonSidePadding by viewModel.preferences.webtoonSidePadding.collectAsState()
    SliderItem(
        value = webtoonSidePadding,
        valueRange = ReaderPreferences.let { it.WEBTOON_PADDING_MIN..it.WEBTOON_PADDING_MAX },
        label = stringResource(MR.strings.pref_webtoon_side_padding),
        valueString = numberFormat.format(webtoonSidePadding / 100f),
        onChange = {
            viewModel.preferences.webtoonSidePadding.set(it)
        },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_crop_borders),
        pref = viewModel.preferences.cropBordersWebtoon,
    )

    val dualPageSplitWebtoon by viewModel.preferences.dualPageSplitWebtoon.collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_dual_page_split),
        pref = viewModel.preferences.dualPageSplitWebtoon,
    )

    if (dualPageSplitWebtoon) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_dual_page_invert),
            pref = viewModel.preferences.dualPageInvertWebtoon,
        )
    }

    val dualPageRotateToFitWebtoon by viewModel.preferences.dualPageRotateToFitWebtoon.collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_page_rotate),
        pref = viewModel.preferences.dualPageRotateToFitWebtoon,
    )

    if (dualPageRotateToFitWebtoon) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_page_rotate_invert),
            pref = viewModel.preferences.dualPageRotateToFitInvertWebtoon,
        )
    }

    CheckboxItem(
        label = stringResource(MR.strings.pref_double_tap_zoom),
        pref = viewModel.preferences.webtoonDoubleTapZoomEnabled,
    )
    CheckboxItem(
        label = stringResource(MR.strings.pref_webtoon_disable_zoom_out),
        pref = viewModel.preferences.webtoonDisableZoomOut,
    )
}

@Composable
private fun ColumnScope.TapZonesItems(
    selected: Int,
    onSelect: (Int) -> Unit,
    invertMode: ReaderPreferences.TappingInvertMode,
    onSelectInvertMode: (ReaderPreferences.TappingInvertMode) -> Unit,
) {
    SettingsChipRow(MR.strings.pref_viewer_nav) {
        ReaderPreferences.TapZones.mapIndexed { index, it ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text(stringResource(it)) },
            )
        }
    }

    if (selected != 5) {
        SettingsChipRow(MR.strings.pref_read_with_tapping_inverted) {
            ReaderPreferences.TappingInvertMode.entries.map {
                FilterChip(
                    selected = it == invertMode,
                    onClick = { onSelectInvertMode(it) },
                    label = { Text(stringResource(it.titleRes)) },
                )
            }
        }
    }
}
