package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.manga.model.readerOrientation
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.presentation.reader.settings.TapZonesItems
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsViewModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Add
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.DragHandle
import sh.calvin.reorderable.ReorderableColumn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.text.NumberFormat

@Composable
internal fun WebGpuViewerSettings(viewModel: ReaderSettingsViewModel) {
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

    val default = LocalContext.current.appGraph.readerPreferences.defaultReadingMode.get()
    val resolved = ReadingMode.fromPreference(
        when {
            readingMode == ReadingMode.DEFAULT -> default
            else -> manga?.readingMode?.toInt() ?: default
        },
    )
    if (resolved == ReadingMode.LEFT_TO_RIGHT || resolved == ReadingMode.RIGHT_TO_LEFT) {
        val dualPageView by viewModel.preferences.dualPageView.collectAsState()
        SettingsChipRow(MR.strings.pref_dual_page_view) {
            ReaderPreferences.DualPageView.entries.map {
                FilterChip(
                    selected = it == dualPageView,
                    onClick = { viewModel.preferences.dualPageView.set(it) },
                    label = { Text(stringResource(it.titleRes)) },
                )
            }
        }
    }

    if (resolved == ReadingMode.WEBTOON || resolved == ReadingMode.CONTINUOUS_VERTICAL) {
        val numberFormat = remember { NumberFormat.getPercentInstance() }
        val continuousMinWidth by viewModel.preferences.continuousMinWidth.collectAsState()
        SliderItem(
            value = continuousMinWidth,
            valueRange = ReaderPreferences.let { 1..100 },
            label = stringResource(MR.strings.pref_continuous_minwidth),
            valueString = numberFormat.format(continuousMinWidth / 100f),
            onChange = {
                viewModel.preferences.continuousMinWidth.set(it)
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )

        if (resolved == ReadingMode.CONTINUOUS_VERTICAL) {
            val continuousGap by viewModel.preferences.continuousGap.collectAsState()
            SliderItem(
                value = continuousGap,
                valueRange = ReaderPreferences.let { 1..100 },
                label = stringResource(MR.strings.pref_continuous_gap),
                valueString = numberFormat.format(continuousGap / 100f),
                onChange = {
                    viewModel.preferences.continuousGap.set(it)
                },
                pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }

        CheckboxItem(
            label = stringResource(MR.strings.pref_webtoon_disable_zoom_out),
            pref = viewModel.preferences.webtoonDisableZoomOut,
        )
    }

    val orientation = remember(manga) { ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt()) }
    SettingsChipRow(MR.strings.rotation_type) {
        ReaderOrientation.entries.forEach {
            FilterChip(
                selected = it == orientation,
                onClick = { viewModel.onChangeOrientation(it) },
                label = { Text(stringResource(it.stringRes)) },
            )
        }
    }

    HeadingItem(MR.strings.webgpu_viewer)

    val isDual = (viewer as? WebGpuViewer)?.isDualPageMode() == true

    val navigationModePager by viewModel.preferences.navigationModePager.collectAsState()
    val pagerNavInverted by viewModel.preferences.pagerNavInverted.collectAsState()
    TapZonesItems(
        selected = navigationModePager,
        onSelect = viewModel.preferences.navigationModePager::set,
        invertMode = pagerNavInverted,
        onSelectInvertMode = viewModel.preferences.pagerNavInverted::set,
    )

    if (isDual) {
        val transitionAnimation by viewModel.preferences.transitionAnimationDual.collectAsState()
        SettingsChipRow(MR.strings.pref_transition_animation_dual) {
            (
                ReaderPreferences.TransitionAnimation.entries - ReaderPreferences.TransitionAnimation.FLIP_LEFT -
                    ReaderPreferences.TransitionAnimation.FLIP_RIGHT
                ).forEach {
                FilterChip(
                    selected = it == transitionAnimation,
                    onClick = { viewModel.preferences.transitionAnimationDual.set(it) },
                    label = { Text(stringResource(it.titleRes)) },
                )
            }
        }
        val cutoutMode by viewModel.preferences.cutoutModeDual.collectAsState()
        SettingsChipRow(MR.strings.pref_cutout_mode_dual) {
            ReaderPreferences.CutoutMode.entries.forEach {
                FilterChip(
                    selected = it == cutoutMode,
                    onClick = { viewModel.preferences.cutoutModeDual.set(it) },
                    label = { Text(stringResource(it.titleRes)) },
                )
            }
        }
        return
    }

    if (resolved != ReadingMode.WEBTOON && resolved != ReadingMode.CONTINUOUS_VERTICAL) {
        val imageScaleType by viewModel.preferences.imageScaleType.collectAsState()
        SettingsChipRow(MR.strings.pref_image_scale_type) {
            ReaderPreferences.ImageScaleTypeWebGpuViewer.forEach {
                FilterChip(
                    selected = ReaderPreferences.ImageScaleType[imageScaleType - 1] == it,
                    onClick = {
                        viewModel.preferences.imageScaleType.set(ReaderPreferences.ImageScaleType.indexOf(it) + 1)
                    },
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

        CheckboxItem(
            label = stringResource(MR.strings.pref_disable_double_tap_gestures),
            pref = viewModel.preferences.disableDoubleTapGestures,
        )

        val transitionAnimation by viewModel.preferences.transitionAnimation.collectAsState()
        SettingsChipRow(MR.strings.pref_transition_animation) {
            ReaderPreferences.TransitionAnimation.entries.forEach {
                FilterChip(
                    selected = it == transitionAnimation,
                    onClick = { viewModel.preferences.transitionAnimation.set(it) },
                    label = { Text(stringResource(it.titleRes)) },
                )
            }
        }

        val cutoutMode by viewModel.preferences.cutoutMode.collectAsState()
        SettingsChipRow(MR.strings.pref_cutout_mode) {
            ReaderPreferences.CutoutMode.entries.forEach {
                FilterChip(
                    selected = it == cutoutMode,
                    onClick = { viewModel.preferences.cutoutMode.set(it) },
                    label = { Text(stringResource(it.titleRes)) },
                )
            }
        }
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        WebGpuShaders.prune(context, listOf(viewModel.preferences.imageFilters, viewModel.preferences.tileFilters))
    }
    ShaderList(MR.strings.pref_image_filters, viewModel.preferences.imageFilters)
    ShaderList(MR.strings.pref_tile_filters, viewModel.preferences.tileFilters)
}

@Composable
private fun ShaderList(labelRes: StringResource, pref: Preference<List<WebGpuShader>>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filters by pref.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }

    val pickShader = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                WebGpuShaders.import(context, uri, pref)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e)
                error = e.message ?: e.toString()
            }
        }
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            confirmButton = {
                TextButton(onClick = { error = null }) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            title = { Text(stringResource(MR.strings.image_filter_invalid)) },
            text = {
                SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = message,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        )
    }

    HeadingItem(labelRes)
    ReorderableColumn(
        list = filters,
        onSettle = { from, to ->
            // The indices are into the list as drawn, which the preference may have moved past.
            val moved = filters.toMutableList().apply { add(to, removeAt(from)) }
            val current = pref.get()
            val files = current.mapTo(HashSet()) { it.file }
            val kept = moved.filter { it.file in files }
            val keptFiles = kept.mapTo(HashSet()) { it.file }
            pref.set(kept + current.filter { it.file !in keptFiles })
        },
    ) { _, shader, _ ->
        key(shader.file) {
            ReorderableItem {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = SettingsItemsPaddings.Horizontal, end = MaterialTheme.padding.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.DragHandle,
                        contentDescription = null,
                        modifier = Modifier.draggableHandle(),
                    )
                    Text(
                        text = shader.name,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = MaterialTheme.padding.medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = { WebGpuShaders.remove(context, pref, shader) },
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Delete,
                            contentDescription = stringResource(MR.strings.action_delete),
                        )
                    }
                }
            }
        }
    }
    TextButton(
        onClick = {
            try {
                pickShader.launch(arrayOf("*/*"))
            } catch (e: ActivityNotFoundException) {
                error = e.message ?: e.toString()
            }
        },
        modifier = Modifier.padding(horizontal = SettingsItemsPaddings.Horizontal - MaterialTheme.padding.small),
    ) {
        Icon(imageVector = MaterialSymbols.Rounded.Add, contentDescription = null)
        Text(
            text = stringResource(MR.strings.action_add_shader),
            modifier = Modifier.padding(start = MaterialTheme.padding.small),
        )
    }
}
