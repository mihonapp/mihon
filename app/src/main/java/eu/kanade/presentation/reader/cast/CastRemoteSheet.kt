package eu.kanade.presentation.reader.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.ui.reader.cast.CastBackground
import eu.kanade.tachiyomi.ui.reader.cast.CastController
import eu.kanade.tachiyomi.ui.reader.cast.CastLayoutMode
import eu.kanade.tachiyomi.ui.reader.cast.CastOrientation
import eu.kanade.tachiyomi.ui.reader.cast.CastPreferences
import eu.kanade.tachiyomi.ui.reader.cast.CastScaleMode
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.FitScreen
import mihon.icons.materialsymbols.rounded.KeyboardArrowLeft
import mihon.icons.materialsymbols.rounded.KeyboardArrowRight
import mihon.icons.materialsymbols.rounded.SkipNext
import mihon.icons.materialsymbols.rounded.SkipPrevious
import mihon.icons.materialsymbols.roundedfilled.Pause
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.secondaryItemAlpha
import kotlin.math.abs

private val TouchpadHeight = 200.dp

/** Share of the screen height the touchpad takes when expanded. */
private const val EXPANDED_TOUCHPAD_FRACTION = 0.72f
private val SwipeThreshold = 96.dp
private val PlayButtonSize = 56.dp

/** Continuous layouts: how many reader pixels one touchpad pixel scrolls. */
private const val SCROLL_MULTIPLIER = 1.5f

/** Paged layouts: a full touchpad drag pans across the whole overflow (-1..1). */
private const val PAN_MULTIPLIER = 2f

private const val TOUCHPAD_HINT_ALPHA = 0.5f

/**
 * Remote control for an active cast: a touchpad mirroring the reader gestures, page / chapter
 * navigation, auto-scroll and the presentation settings of the cast target.
 */
@Composable
fun CastRemoteSheet(
    onDismissRequest: () -> Unit,
    castController: CastController,
) {
    val state by castController.state.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = MaterialTheme.padding.medium),
        ) {
            RemoteHeader(
                active = state.active,
                targetName = state.targetName,
                onClose = onDismissRequest,
            )

            Touchpad(
                layoutMode = state.layoutMode,
                rtl = state.rtl,
                verticalPaging = state.verticalPaging,
                expanded = expanded,
                onToggleExpanded = { expanded = !expanded },
                castController = castController,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
            )

            ControlRow(
                autoScrollRunning = state.autoScrollRunning,
                castController = castController,
            )

            if (expanded) {
                // Full-size touchpad: only navigation and the stop button stay on screen.
                if (state.active) StopButton(castController)
                return@Column
            }

            SettingsChipRow(MR.strings.cast_orientation) {
                CastOrientation.entries.forEach { orientation ->
                    FilterChip(
                        selected = state.orientation == orientation,
                        onClick = { castController.setOrientation(orientation) },
                        label = { Text(stringResource(orientation.titleRes)) },
                    )
                }
            }

            SizeSection(
                layoutMode = state.layoutMode,
                scaleMode = state.scaleMode,
                zoomPercent = state.zoomPercent,
                stripWidthPercent = state.stripWidthPercent,
                castController = castController,
            )

            SettingsChipRow(MR.strings.cast_background) {
                CastBackground.entries.forEach { background ->
                    FilterChip(
                        selected = state.background == background,
                        onClick = { castController.setBackground(background) },
                        label = { Text(stringResource(background.titleRes)) },
                    )
                }
            }

            AutoScrollSection(
                layoutMode = state.layoutMode,
                preferences = castController.preferences,
            )

            if (state.active) StopButton(castController)
        }
    }
}

@Composable
private fun StopButton(castController: CastController) {
    OutlinedButton(
        onClick = { castController.stopCasting() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.large, vertical = MaterialTheme.padding.small),
    ) {
        Text(text = stringResource(MR.strings.cast_action_stop))
    }
}

@Composable
private fun RemoteHeader(
    active: Boolean,
    targetName: String?,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MaterialTheme.padding.large, end = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(MR.strings.cast_remote_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = if (active && targetName != null) {
                    stringResource(MR.strings.cast_status_casting_to, targetName)
                } else {
                    stringResource(MR.strings.cast_status_not_casting)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.secondaryItemAlpha(),
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Close,
                contentDescription = stringResource(MR.strings.action_close),
            )
        }
    }
}

/**
 * Tap the sides to turn pages, the center to toggle auto-scroll; drag to scroll (continuous) or to
 * pan / swipe between pages (paged). Gestures are keyed on the layout so the handlers never read
 * stale state, without recomposing the sheet on every pointer event.
 */
@Composable
private fun Touchpad(
    layoutMode: CastLayoutMode,
    rtl: Boolean,
    verticalPaging: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    castController: CastController,
    modifier: Modifier = Modifier,
) {
    val swipeThresholdPx = with(LocalDensity.current) { SwipeThreshold.toPx() }
    val height = if (expanded) {
        (LocalConfiguration.current.screenHeightDp * EXPANDED_TOUCHPAD_FRACTION).dp
    } else {
        TouchpadHeight
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(rtl) {
                detectTapGestures { offset ->
                    val zone = offset.x / size.width
                    when {
                        zone < 1f / 3f -> {
                            if (rtl) castController.remoteNextPage() else castController.remotePreviousPage()
                        }
                        zone > 2f / 3f -> {
                            if (rtl) castController.remotePreviousPage() else castController.remoteNextPage()
                        }
                        else -> castController.remoteToggleAutoScroll()
                    }
                }
            }
            .pointerInput(layoutMode, rtl, verticalPaging) {
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                    },
                    onDragEnd = {
                        if (layoutMode == CastLayoutMode.PAGED) {
                            val horizontal = abs(totalX) > swipeThresholdPx && abs(totalX) > abs(totalY)
                            val vertical = abs(totalY) > swipeThresholdPx && abs(totalY) > abs(totalX)
                            if (verticalPaging && vertical) {
                                // Vertical pager: swiping up goes forward.
                                if (totalY <
                                    0f
                                ) {
                                    castController.remoteNextPage()
                                } else {
                                    castController.remotePreviousPage()
                                }
                            } else if (horizontal) {
                                // Swiping left goes forward, unless the reader is right-to-left.
                                val forward = (totalX < 0f) != rtl
                                if (forward) castController.remoteNextPage() else castController.remotePreviousPage()
                            }
                            castController.resetPan()
                        }
                    },
                    onDragCancel = {
                        if (layoutMode == CastLayoutMode.PAGED) castController.resetPan()
                    },
                ) { change, dragAmount ->
                    change.consume()
                    totalX += dragAmount.x
                    totalY += dragAmount.y
                    when (layoutMode) {
                        CastLayoutMode.CONTINUOUS -> castController.remoteScrollBy(-dragAmount.y * SCROLL_MULTIPLIER)
                        CastLayoutMode.PAGED -> castController.panBy(
                            dx = -dragAmount.x / size.width * PAN_MULTIPLIER,
                            dy = -dragAmount.y / size.height * PAN_MULTIPLIER,
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(MR.strings.cast_remote_touchpad_hint),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(MaterialTheme.padding.large)
                .alpha(TOUCHPAD_HINT_ALPHA),
        )
        // A child consumes its own taps, so the touchpad never sees them.
        IconButton(
            onClick = onToggleExpanded,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.FitScreen,
                contentDescription = stringResource(
                    if (expanded) MR.strings.cast_touchpad_collapse else MR.strings.cast_touchpad_expand,
                ),
                tint = if (expanded) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
    }
}

@Composable
private fun ControlRow(
    autoScrollRunning: Boolean,
    castController: CastController,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { castController.remotePreviousChapter() }) {
            Icon(
                imageVector = MaterialSymbols.Rounded.SkipPrevious,
                contentDescription = stringResource(MR.strings.cast_remote_prev_chapter),
            )
        }
        IconButton(onClick = { castController.remotePreviousPage() }) {
            Icon(
                imageVector = MaterialSymbols.Rounded.KeyboardArrowLeft,
                contentDescription = stringResource(MR.strings.cast_remote_prev_page),
            )
        }
        FilledIconButton(
            onClick = { castController.remoteToggleAutoScroll() },
            modifier = Modifier.size(PlayButtonSize),
        ) {
            Icon(
                imageVector = if (autoScrollRunning) {
                    MaterialSymbols.RoundedFilled.Pause
                } else {
                    MaterialSymbols.RoundedFilled.PlayArrow
                },
                contentDescription = stringResource(MR.strings.cast_auto_scroll),
            )
        }
        IconButton(onClick = { castController.remoteNextPage() }) {
            Icon(
                imageVector = MaterialSymbols.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(MR.strings.cast_remote_next_page),
            )
        }
        IconButton(onClick = { castController.remoteNextChapter() }) {
            Icon(
                imageVector = MaterialSymbols.Rounded.SkipNext,
                contentDescription = stringResource(MR.strings.cast_remote_next_chapter),
            )
        }
    }
}

@Composable
private fun SizeSection(
    layoutMode: CastLayoutMode,
    scaleMode: CastScaleMode,
    zoomPercent: Int,
    stripWidthPercent: Int,
    castController: CastController,
) {
    when (layoutMode) {
        CastLayoutMode.PAGED -> {
            SettingsChipRow(MR.strings.cast_scale_mode) {
                CastScaleMode.entries.forEach { mode ->
                    FilterChip(
                        selected = scaleMode == mode,
                        onClick = { castController.setScaleMode(mode) },
                        label = { Text(stringResource(mode.titleRes)) },
                    )
                }
            }
            SliderItem(
                label = stringResource(MR.strings.cast_zoom),
                value = zoomPercent,
                valueRange = CastPreferences.ZOOM_MIN..CastPreferences.ZOOM_MAX step 5,
                steps = 74,
                valueString = "$zoomPercent%",
                onChange = { castController.setZoomPercent(it) },
                pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
        CastLayoutMode.CONTINUOUS -> {
            SliderItem(
                label = stringResource(MR.strings.cast_strip_width),
                value = stripWidthPercent,
                valueRange = CastPreferences.STRIP_WIDTH_MIN..CastPreferences.STRIP_WIDTH_MAX step 5,
                steps = 16,
                valueString = "$stripWidthPercent%",
                onChange = { castController.setStripWidthPercent(it) },
                pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

@Composable
private fun AutoScrollSection(
    layoutMode: CastLayoutMode,
    preferences: CastPreferences,
) {
    HeadingItem(MR.strings.cast_auto_scroll)
    when (layoutMode) {
        CastLayoutMode.CONTINUOUS -> {
            val speedPref = preferences.autoScrollSpeed
            val speed by speedPref.collectAsState()
            val stepMsPref = preferences.autoScrollStepMs
            val stepMs by stepMsPref.collectAsState()

            SliderItem(
                label = stringResource(MR.strings.cast_auto_scroll_speed),
                value = speed,
                valueRange = CastPreferences.AUTO_SCROLL_SPEED_MIN..CastPreferences.AUTO_SCROLL_SPEED_MAX step 5,
                steps = 98,
                valueString = stringResource(MR.strings.cast_auto_scroll_speed_value, speed),
                onChange = { speedPref.set(it) },
                pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            SettingsChipRow(MR.strings.cast_auto_scroll_step) {
                CastPreferences.AutoScrollSteps.forEach { ms ->
                    FilterChip(
                        selected = stepMs == ms,
                        onClick = { stepMsPref.set(ms) },
                        label = {
                            Text(
                                text = if (ms == CastPreferences.AutoScrollSteps.first()) {
                                    stringResource(MR.strings.cast_auto_scroll_step_smooth)
                                } else {
                                    stringResource(MR.strings.cast_auto_scroll_step_value, ms)
                                },
                            )
                        },
                    )
                }
            }
        }
        CastLayoutMode.PAGED -> {
            val intervalPref = preferences.autoScrollPageIntervalSec
            val interval by intervalPref.collectAsState()
            val intervalRange = CastPreferences.let {
                it.AUTO_SCROLL_PAGE_INTERVAL_MIN..it.AUTO_SCROLL_PAGE_INTERVAL_MAX
            }

            SliderItem(
                label = stringResource(MR.strings.cast_auto_scroll_page_interval),
                value = interval,
                valueRange = intervalRange,
                steps = 118,
                valueString = stringResource(MR.strings.cast_auto_scroll_page_interval_value, interval),
                onChange = { intervalPref.set(it) },
                pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}
