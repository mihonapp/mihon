package eu.kanade.presentation.more.settings.screen.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.ReaderTransitionAnimation
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderTransitionAnimations
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

class ReaderTransitionAnimationScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val prefs = remember { Injekt.get<ReaderPreferences>() }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.pref_page_transition_animation),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState()),
            ) {
                ViewerSection(
                    title = stringResource(MR.strings.pager_viewer),
                    longStrip = false,
                    optionPref = prefs.pagerPageTransitionAnimation,
                    smoothDurationPref = prefs.pagerTransitionSmoothDuration,
                    gentleDurationPref = prefs.pagerTransitionGentleDuration,
                    customDurationPref = prefs.pagerTransitionCustomDuration,
                    customCurvePref = prefs.pagerTransitionCustomCurve,
                )
                HorizontalDivider()
                ViewerSection(
                    title = stringResource(MR.strings.webtoon_viewer),
                    longStrip = true,
                    optionPref = prefs.webtoonPageTransitionAnimation,
                    smoothDurationPref = prefs.webtoonTransitionSmoothDuration,
                    gentleDurationPref = prefs.webtoonTransitionGentleDuration,
                    customDurationPref = prefs.webtoonTransitionCustomDuration,
                    customCurvePref = prefs.webtoonTransitionCustomCurve,
                )
            }
        }
    }

    @Composable
    private fun ViewerSection(
        title: String,
        longStrip: Boolean,
        optionPref: Preference<ReaderTransitionAnimation>,
        smoothDurationPref: Preference<Int>,
        gentleDurationPref: Preference<Int>,
        customDurationPref: Preference<Int>,
        customCurvePref: Preference<String>,
    ) {
        val option by optionPref.collectAsState()
        val customCurve by customCurvePref.collectAsState()
        val durationPref = when (option) {
            ReaderTransitionAnimation.GENTLE -> gentleDurationPref
            ReaderTransitionAnimation.CUSTOM -> customDurationPref
            else -> smoothDurationPref
        }
        val durationValue by durationPref.collectAsState()

        // Curve + duration the top preview animates with for non-custom options.
        val previewCurve = when (option) {
            ReaderTransitionAnimation.GENTLE -> ReaderTransitionAnimations.GENTLE_CURVE
            ReaderTransitionAnimation.CUSTOM -> ReaderTransitionAnimations.parseCurve(customCurve)
            else -> ReaderTransitionAnimations.SMOOTH_CURVE
        }
        val previewDuration = if (option == ReaderTransitionAnimation.DEFAULT) DEFAULT_PREVIEW_DURATION else durationValue

        Column(modifier = Modifier.padding(horizontal = CONTENT_HORIZONTAL_PADDING, vertical = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            val entries = ReaderTransitionAnimation.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = option == entry,
                        onClick = { optionPref.set(entry) },
                        shape = SegmentedButtonDefaults.itemShape(index, entries.size),
                    ) {
                        Text(stringResource(entry.titleRes))
                    }
                }
            }

            // Preview + duration slider for the Smooth/Gentle presets, animated in/out with the
            // app's standard expand+fade pattern. DEFAULT shows no preview (its transition is the
            // viewer's native one, which the preview can't faithfully represent); CUSTOM has its own
            // editor below. Responsive: stacked on phones; preview | slider side-by-side when wide.
            AnimatedVisibility(
                visible = option == ReaderTransitionAnimation.SMOOTH || option == ReaderTransitionAnimation.GENTLE,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    if (maxWidth >= WIDE_LAYOUT_MIN_WIDTH) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PageTransitionPreview(Modifier, longStrip, previewCurve, previewDuration)
                            DurationSlider(Modifier.width(PRESET_SLIDER_WIDTH), durationPref)
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                PageTransitionPreview(Modifier, longStrip, previewCurve, previewDuration)
                            }
                            // Inset both ends so the whole slider thumb clears the system back-gesture zone.
                            DurationSlider(
                                Modifier.fillMaxWidth().padding(top = 12.dp, start = SLIDER_EDGE_INSET, end = SLIDER_EDGE_INSET),
                                durationPref,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = option == ReaderTransitionAnimation.CUSTOM,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                CustomCurveEditor(
                    modifier = Modifier.padding(top = 12.dp),
                    longStrip = longStrip,
                    curvePref = customCurvePref,
                    durationPref = customDurationPref,
                )
            }
        }
    }

    @Composable
    private fun DurationSlider(modifier: Modifier, pref: Preference<Int>) {
        val value by pref.collectAsState()
        val min = ReaderPreferences.TRANSITION_DURATION_MIN
        val max = ReaderPreferences.TRANSITION_DURATION_MAX
        val step = ReaderPreferences.TRANSITION_DURATION_STEP
        Column(modifier = modifier) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(MR.strings.pref_reader_transition_duration), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(MR.strings.transition_duration_value, value.coerceIn(min, max)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Slider(
                value = value.coerceIn(min, max).toFloat(),
                onValueChange = { pref.set(((it / step).roundToInt() * step).coerceIn(min, max)) },
                valueRange = min.toFloat()..max.toFloat(),
                steps = ((max - min) / step) - 1,
            )
        }
    }

    @Composable
    private fun CustomCurveEditor(
        modifier: Modifier,
        longStrip: Boolean,
        curvePref: Preference<String>,
        durationPref: Preference<Int>,
    ) {
        // Tablets (and any wide window) get a denser, two-column layout; phones a stacked one.
        val dense = LocalConfiguration.current.smallestScreenWidthDp >= TABLET_MIN_SW
        val fieldHeight = if (dense) 32.dp else 36.dp
        val fieldGap = if (dense) 5.dp else 7.dp
        val duration by durationPref.collectAsState()
        val initial = remember { ReaderTransitionAnimations.parseCurve(curvePref.get()) }
        var x1 by remember { mutableFloatStateOf(initial[0]) }
        var y1 by remember { mutableFloatStateOf(initial[1]) }
        var x2 by remember { mutableFloatStateOf(initial[2]) }
        var y2 by remember { mutableFloatStateOf(initial[3]) }
        fun persist() = curvePref.set(ReaderTransitionAnimations.formatCurve(floatArrayOf(x1, y1, x2, y2)))
        fun applyPreset(c: FloatArray) {
            x1 = sanitize(c[0]); y1 = sanitize(c[1]); x2 = sanitize(c[2]); y2 = sanitize(c[3]); persist()
        }
        val onChange: (Float, Float, Float, Float) -> Unit = { nx1, ny1, nx2, ny2 ->
            x1 = sanitize(nx1); y1 = sanitize(ny1); x2 = sanitize(nx2); y2 = sanitize(ny2); persist()
        }

        Column(modifier = modifier) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val wide = maxWidth >= WIDE_LAYOUT_MIN_WIDTH
                val curve = floatArrayOf(x1, y1, x2, y2)
                if (wide) {
                    // Grid + numbers grouped side-by-side (as in portrait); animation to the right; the
                    // two blocks spread with even space. The grid is a square sized to the numeric
                    // column's own height, and the animation fills that height (preview top / slider
                    // bottom via SpaceBetween). Top offsets align the three visible tops.
                    val colHeight = GRID_PADDING + fieldHeight * 6 + fieldGap * 5 + 2.dp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(GRID_NUM_GAP),
                            verticalAlignment = Alignment.Top,
                        ) {
                            BezierCurveEditor(Modifier.size(colHeight), x1, y1, x2, y2, onChange)
                            CurveFieldsColumn(
                                Modifier.width(NUMERIC_COL_WIDTH).padding(top = GRID_PADDING),
                                x1, y1, x2, y2, duration, fieldHeight, fieldGap,
                                { x1 = it; persist() }, { y1 = it; persist() },
                                { x2 = it; persist() }, { y2 = it; persist() }, { durationPref.set(it) },
                                { applyPreset(it) },
                            )
                        }
                        Column(
                            modifier = Modifier
                                .width(ANIM_COL_WIDTH)
                                .heightIn(min = colHeight)
                                .padding(top = GRID_PADDING),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            PageTransitionPreview(Modifier, longStrip, curve, duration)
                            DurationSliderBare(Modifier.fillMaxWidth(), durationPref)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(GRID_NUM_GAP),
                            verticalAlignment = Alignment.Top,
                        ) {
                            BezierCurveEditor(
                                Modifier.weight(1f).padding(start = EDGE_GESTURE_INSET).aspectRatio(1f),
                                x1, y1, x2, y2, onChange,
                            )
                            CurveFieldsColumn(
                                Modifier.width(NUMERIC_COL_WIDTH).padding(top = GRID_PADDING),
                                x1, y1, x2, y2, duration, fieldHeight, fieldGap,
                                { x1 = it; persist() }, { y1 = it; persist() },
                                { x2 = it; persist() }, { y2 = it; persist() }, { durationPref.set(it) },
                                { applyPreset(it) },
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Align the preview's left edge with the grid's *visible* border above (the grid
                            // insets its drawn container by GRID_PADDING for handle room), and keep the slider
                            // thumb clear of the back-gesture edge on the right.
                            PageTransitionPreview(
                                Modifier.padding(start = EDGE_GESTURE_INSET + GRID_PADDING),
                                longStrip, curve, duration,
                            )
                            DurationSliderBare(Modifier.weight(1f).padding(end = SLIDER_EDGE_INSET), durationPref)
                        }
                    }
                }
            }

            // The abstract motion square is hidden behind a flag (kept for quick curve debugging).
            if (SHOW_DEBUG_SQUARE_PREVIEW) {
                SquarePreview(Modifier.padding(top = 12.dp), floatArrayOf(x1, y1, x2, y2), duration)
            }

            CurveInfo()
        }
    }

    @Composable
    private fun BezierCurveEditor(
        modifier: Modifier,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        onChange: (Float, Float, Float, Float) -> Unit,
    ) {
        val cx1 by rememberUpdatedState(x1)
        val cy1 by rememberUpdatedState(y1)
        val cx2 by rememberUpdatedState(x2)
        val cy2 by rememberUpdatedState(y2)
        val updated by rememberUpdatedState(onChange)

        val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
        val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        val handleFillColor = MaterialTheme.colorScheme.surface

        // The effective canvas the curve and handles sit on (grid container over the screen surface).
        val canvasColor = containerColor.compositeOver(MaterialTheme.colorScheme.surface)
        // Start is always primary. For the far end prefer tertiary — it's the genuinely distinct second
        // accent on most themes, giving a two-tone curve — but fall back to secondary/primary when it
        // lacks contrast against the canvas (e.g. Lavender/Yin & Yang, whose tertiary matches the bg).
        val startColor = MaterialTheme.colorScheme.primary
        val endColor = ReaderTransitionAnimations.pickAccentWithContrast(
            candidates = listOf(
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.primary,
            ),
            canvas = canvasColor,
        )

        // The visible grid is inset by GRID_PADDING so handles and the curve glow near an edge
        // overflow into transparent padding instead of being clipped at the component border.
        Canvas(
            modifier = modifier
                .pointerInput(Unit) {
                    val pad = GRID_PADDING.toPx()
                    val gw = size.width - pad * 2
                    val gh = size.height - pad * 2
                    var handle = -1
                    detectDragGestures(
                        onDragStart = { pos ->
                            val p1 = Offset(pad + cx1 * gw, pad + (1f - cy1) * gh)
                            val p2 = Offset(pad + cx2 * gw, pad + (1f - cy2) * gh)
                            handle = if ((pos - p1).getDistanceSquared() <= (pos - p2).getDistanceSquared()) 1 else 2
                            emitHandle(handle, pos, pad, pad, gw, gh, cx1, cy1, cx2, cy2, updated)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            emitHandle(handle, change.position, pad, pad, gw, gh, cx1, cy1, cx2, cy2, updated)
                        },
                    )
                },
        ) {
            val pad = GRID_PADDING.toPx()
            val gl = pad
            val gt = pad
            val gw = size.width - pad * 2
            val gh = size.height - pad * 2
            drawRoundRect(
                color = containerColor,
                topLeft = Offset(gl, gt),
                size = Size(gw, gh),
                cornerRadius = CornerRadius(12.dp.toPx()),
            )
            val cells = 8
            val stroke = 1.dp.toPx()
            for (i in 1 until cells) {
                val gx = gl + gw * i / cells
                drawLine(gridColor, Offset(gx, gt), Offset(gx, gt + gh), stroke)
                val gy = gt + gh * i / cells
                drawLine(gridColor, Offset(gl, gy), Offset(gl + gw, gy), stroke)
            }

            val start = Offset(gl, gt + gh)
            val end = Offset(gl + gw, gt)
            val c1 = Offset(gl + cx1 * gw, gt + (1f - cy1) * gh)
            val c2 = Offset(gl + cx2 * gw, gt + (1f - cy2) * gh)
            val path = Path().apply {
                moveTo(start.x, start.y)
                cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
            }
            val gradient = Brush.linearGradient(listOf(startColor, endColor), start = start, end = end)
            drawPath(path, brush = gradient, alpha = 0.18f, style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, brush = gradient, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))

            drawLine(startColor, start, c1, 2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(endColor, end, c2, 2.dp.toPx(), cap = StrokeCap.Round)

            val radius = 9.dp.toPx()
            val dotRadius = 3.5.dp.toPx()
            // Opaque fill + accent ring, plus a filled center dot so the two handles read as distinct
            // targets even on themes where start and end resolve to the same color.
            drawCircle(handleFillColor, radius = radius, center = c1)
            drawCircle(startColor, radius = radius, center = c1, style = Stroke(3.dp.toPx()))
            drawCircle(startColor, radius = dotRadius, center = c1)
            drawCircle(handleFillColor, radius = radius, center = c2)
            drawCircle(endColor, radius = radius, center = c2, style = Stroke(3.dp.toPx()))
            drawCircle(endColor, radius = dotRadius, center = c2)
        }
    }

    /** A compact bordered text box used by the numeric inputs; commits on IME-done and focus loss. */
    @Composable
    private fun EditableNumberBox(
        text: String,
        onTextChange: (String) -> Unit,
        onCommit: () -> Unit,
        modifier: Modifier = Modifier,
        keyboardType: KeyboardType = KeyboardType.Decimal,
        height: Dp = 38.dp,
    ) {
        val shape = RoundedCornerShape(8.dp)
        Box(
            modifier = modifier
                .height(height)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCommit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) onCommit() },
            )
        }
    }

    @Composable
    private fun CurveFieldsColumn(
        modifier: Modifier,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        duration: Int,
        fieldHeight: Dp,
        fieldGap: Dp,
        onX1: (Float) -> Unit,
        onY1: (Float) -> Unit,
        onX2: (Float) -> Unit,
        onY2: (Float) -> Unit,
        onDuration: (Int) -> Unit,
        onPreset: (FloatArray) -> Unit,
    ) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(fieldGap)) {
            CurveNumberField("X1", x1, fieldHeight, onX1)
            CurveNumberField("Y1", y1, fieldHeight, onY1)
            CurveNumberField("X2", x2, fieldHeight, onX2)
            CurveNumberField("Y2", y2, fieldHeight, onY2)
            DurationField(duration, fieldHeight, onDuration)
            // Preset shapes fill the height the bigger grid frees up; the glyph itself teaches the curve.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PresetGlyphButton(EASE_OUT, stringResource(MR.strings.transition_curve_ease_out), Modifier.weight(1f), fieldHeight, onPreset)
                PresetGlyphButton(EASE_IN_OUT, stringResource(MR.strings.transition_curve_ease_in_out), Modifier.weight(1f), fieldHeight, onPreset)
                PresetGlyphButton(EASE_IN, stringResource(MR.strings.transition_curve_ease_in), Modifier.weight(1f), fieldHeight, onPreset)
            }
        }
    }

    @Composable
    private fun CurveNumberField(label: String, value: Float, fieldHeight: Dp, onValue: (Float) -> Unit) {
        var text by remember(value) { mutableStateOf(formatCurveValue(value)) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(22.dp),
            )
            EditableNumberBox(
                text = text,
                onTextChange = { text = it },
                onCommit = {
                    val parsed = text.toFloatOrNull()
                    if (parsed != null) onValue(sanitize(parsed)) else text = formatCurveValue(value)
                },
                modifier = Modifier.weight(1f),
                height = fieldHeight,
            )
        }
    }

    @Composable
    private fun DurationField(value: Int, fieldHeight: Dp, onValue: (Int) -> Unit) {
        val min = ReaderPreferences.TRANSITION_DURATION_CUSTOM_MIN
        val max = ReaderPreferences.TRANSITION_DURATION_CUSTOM_MAX
        var text by remember(value) { mutableStateOf(value.toString()) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(MR.strings.transition_duration_unit),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(22.dp),
            )
            EditableNumberBox(
                text = text,
                onTextChange = { text = it },
                onCommit = {
                    val parsed = text.toIntOrNull()
                    if (parsed != null) onValue(parsed.coerceIn(min, max)) else text = value.toString()
                },
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                height = fieldHeight,
            )
        }
    }

    /** Coarse duration control shown beside the animation; exact value is typed in the fields column. */
    @Composable
    private fun DurationSliderBare(modifier: Modifier, pref: Preference<Int>) {
        val value by pref.collectAsState()
        val min = ReaderPreferences.TRANSITION_DURATION_CUSTOM_MIN
        val max = ReaderPreferences.TRANSITION_DURATION_CUSTOM_MAX
        Column(modifier = modifier) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(MR.strings.pref_reader_transition_duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(MR.strings.transition_duration_value, value),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The slider max grows to fit values typed past the preset cap.
            val sliderMax = maxOf(ReaderPreferences.TRANSITION_DURATION_MAX, ((value + 499) / 500) * 500)
            Slider(
                value = value.coerceIn(0, sliderMax).toFloat(),
                onValueChange = { pref.set(((it / 10f).roundToInt() * 10).coerceIn(min, max)) },
                valueRange = 0f..sliderMax.toFloat(),
            )
        }
    }

    /** Icon-only preset: the glyph shows the curve shape; the name is exposed for accessibility only. */
    @Composable
    private fun PresetGlyphButton(
        curve: FloatArray,
        contentDescription: String,
        modifier: Modifier,
        height: Dp,
        onPick: (FloatArray) -> Unit,
    ) {
        val shape = RoundedCornerShape(6.dp)
        Box(
            modifier = modifier
                .height(height)
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable(onClickLabel = contentDescription) { onPick(curve) },
            contentAlignment = Alignment.Center,
        ) {
            CurveGlyph(curve, Modifier.size(16.dp))
        }
    }

    @Composable
    private fun CurveGlyph(curve: FloatArray, modifier: Modifier) {
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(modifier) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(0f, h)
                cubicTo(curve[0] * w, (1f - curve[1]) * h, curve[2] * w, (1f - curve[3]) * h, w, 0f)
            }
            drawPath(path, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }

    @Composable
    private fun CurveInfo() {
        Column(
            modifier = Modifier
                .padding(top = 12.dp)
                .secondaryItemAlpha(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(imageVector = Icons.Outlined.Info, contentDescription = null)
            Text(
                text = stringResource(MR.strings.pref_reader_transition_curve_help),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    // region Previews

    /** Drives a 0..1 fraction that eases forward, pauses, eases back, and never restarts on edits. */
    @Composable
    private fun rememberCurveAnimatable(curve: FloatArray, durationMs: Int): Animatable<Float, AnimationVector1D> {
        val cx1 by rememberUpdatedState(curve[0])
        val cy1 by rememberUpdatedState(curve[1])
        val cx2 by rememberUpdatedState(curve[2])
        val cy2 by rememberUpdatedState(curve[3])
        val cDuration by rememberUpdatedState(durationMs)
        val anim = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            while (isActive) {
                val easing = CubicBezierEasing(cx1.coerceIn(0f, 1f), cy1, cx2.coerceIn(0f, 1f), cy2)
                val d = cDuration.coerceIn(
                    ReaderPreferences.TRANSITION_DURATION_CUSTOM_MIN,
                    ReaderPreferences.TRANSITION_DURATION_CUSTOM_MAX,
                )
                anim.animateTo(1f, tween(durationMillis = d, easing = easing))
                delay(PREVIEW_PAUSE_MS)
                anim.animateTo(0f, tween(durationMillis = d, easing = easing))
                delay(PREVIEW_PAUSE_MS)
            }
        }
        return anim
    }

    @Composable
    private fun PageTransitionPreview(modifier: Modifier, longStrip: Boolean, curve: FloatArray, durationMs: Int) {
        if (longStrip) {
            LongStripPreview(modifier, curve, durationMs)
        } else {
            PagedPreview(modifier, curve, durationMs)
        }
    }

    @Composable
    private fun PagedPreview(modifier: Modifier, curve: FloatArray, durationMs: Int) {
        val anim = rememberCurveAnimatable(curve, durationMs)
        // Body uses the selected-option background (segmented button's active container) so the device
        // reads as tinted rather than plain, and stays distinct from the page on themes where the two
        // neutrals coincide (e.g. Lavender). Buttons/speaker/home use primary for a clear accent on it.
        val body = MaterialTheme.colorScheme.secondaryContainer
        val bezel = MaterialTheme.colorScheme.onSurfaceVariant
        val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        val detail = MaterialTheme.colorScheme.primary
        val gutter = MaterialTheme.colorScheme.surfaceVariant
        val page = MaterialTheme.colorScheme.surface
        val panel = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        Canvas(modifier = modifier.size(EREADER_BODY_W, EREADER_BODY_H)) {
            val screen = screenRectIn(size, EREADER_INSET_LEFT, EREADER_INSET_TOP, EREADER_INSET_RIGHT, EREADER_INSET_BOTTOM)
            val screenRadius = 3.dp.toPx()
            drawEReaderFrame(screen, screenRadius, body, bezel, outline, detail)
            clipPath(roundedRectPath(screen, screenRadius)) {
                drawRect(gutter, topLeft = Offset(screen.left, screen.top), size = Size(screen.width, screen.height))
                val f = anim.value
                val w = screen.width
                drawMangaPage(screen.left - f * w, screen.top, w, screen.height, page, panel, alt = false)
                drawMangaPage(screen.left + (1f - f) * w, screen.top, w, screen.height, page, panel, alt = true)
            }
        }
    }

    @Composable
    private fun LongStripPreview(modifier: Modifier, curve: FloatArray, durationMs: Int) {
        val anim = rememberCurveAnimatable(curve, durationMs)
        // See PagedPreview: tinted body (selected-option background) + primary details for contrast.
        val body = MaterialTheme.colorScheme.secondaryContainer
        val bezel = MaterialTheme.colorScheme.onSurfaceVariant
        val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        val detail = MaterialTheme.colorScheme.primary
        val page = MaterialTheme.colorScheme.surface
        val ink = MaterialTheme.colorScheme.onSurfaceVariant
        val fill = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
        val accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        Canvas(modifier = modifier.size(PHONE_BODY_W, PHONE_BODY_H)) {
            val screen = screenRectIn(size, PHONE_INSET_SIDE, PHONE_INSET_TOP, PHONE_INSET_SIDE, PHONE_INSET_BOTTOM)
            val screenRadius = 6.dp.toPx()
            drawPhoneFrame(screen, screenRadius, body, bezel, outline, detail)
            clipPath(roundedRectPath(screen, screenRadius)) {
                drawRect(page, topLeft = Offset(screen.left, screen.top), size = Size(screen.width, screen.height))
                // A tap scrolls 75% of the viewport (mirrors WebtoonViewer's heightPixels * 3 / 4),
                // so ~25% of the old content stays visible at the top.
                val scroll = anim.value * (WEBTOON_SCROLL_FRACTION * screen.height)
                drawWebtoonContent(screen.left, screen.top - scroll, screen.width, screen.height, ink, fill, accent)
            }
        }
    }

    @Composable
    private fun SquarePreview(modifier: Modifier, curve: FloatArray, durationMs: Int) {
        val anim = rememberCurveAnimatable(curve, durationMs)
        val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
        val squareColor = MaterialTheme.colorScheme.primary
        val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor),
        ) {
            val cell = size.height / 4f
            val stroke = 1.dp.toPx()
            var gx = cell
            while (gx < size.width) {
                drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), stroke)
                gx += cell
            }
            var gy = cell
            while (gy < size.height) {
                drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), stroke)
                gy += cell
            }
            val square = cell * 2
            val travel = (size.width - square - cell * 2).coerceAtLeast(0f)
            drawRoundRect(
                color = squareColor,
                topLeft = Offset(cell + anim.value * travel, (size.height - square) / 2f),
                size = Size(square, square),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
        }
    }

    // endregion

    // region Device frames

    private fun screenRectIn(size: Size, left: Float, top: Float, right: Float, bottom: Float): Rect =
        Rect(size.width * left, size.height * top, size.width * (1f - right), size.height * (1f - bottom))

    private fun roundedRectPath(rect: Rect, radiusPx: Float): Path =
        Path().apply { addRoundRect(RoundRect(rect.left, rect.top, rect.right, rect.bottom, CornerRadius(radiusPx))) }

    /**
     * A 10.3" e-reader (4:3 screen, 7 mm bezels, ~20 mm left grip → body ≈ 18.4 × 22.3 cm). The wide
     * left grip with two page-turn buttons is the recognisable cue.
     */
    private fun DrawScope.drawEReaderFrame(
        screen: Rect,
        screenRadiusPx: Float,
        body: Color,
        bezel: Color,
        outline: Color,
        detail: Color,
    ) {
        val bodyRadius = CornerRadius(size.width * 0.06f)
        drawRoundRect(body, size = size, cornerRadius = bodyRadius)
        drawRoundRect(outline, size = size, cornerRadius = bodyRadius, style = Stroke(1.dp.toPx()))

        val lip = 2.dp.toPx()
        drawRoundRect(
            color = bezel,
            topLeft = Offset(screen.left - lip, screen.top - lip),
            size = Size(screen.width + lip * 2, screen.height + lip * 2),
            cornerRadius = CornerRadius(screenRadiusPx + lip),
        )

        val gripCx = size.width * EREADER_INSET_LEFT * 0.5f
        val bw = size.width * 0.018f
        val bh = size.height * 0.11f
        drawRoundRect(detail, topLeft = Offset(gripCx - bw / 2f, size.height * 0.32f), size = Size(bw, bh), cornerRadius = CornerRadius(bw))
        drawRoundRect(detail, topLeft = Offset(gripCx - bw / 2f, size.height * 0.50f), size = Size(bw, bh), cornerRadius = CornerRadius(bw))
    }

    /**
     * An older symmetric phone: big bezels, convex (bowed) top and bottom caps, an earpiece slit and a
     * physical home button. The body does not wrap the screen tightly, so the bezels read clearly.
     */
    private fun DrawScope.drawPhoneFrame(
        screen: Rect,
        screenRadiusPx: Float,
        body: Color,
        bezel: Color,
        outline: Color,
        detail: Color,
    ) {
        val path = phoneBodyPath(size)
        drawPath(path, body)
        drawPath(path, outline, style = Stroke(1.dp.toPx()))

        val lip = 2.dp.toPx()
        drawRoundRect(
            color = bezel,
            topLeft = Offset(screen.left - lip, screen.top - lip),
            size = Size(screen.width + lip * 2, screen.height + lip * 2),
            cornerRadius = CornerRadius(screenRadiusPx + lip),
        )

        val slitW = size.width * 0.20f
        val slitH = 3.dp.toPx()
        drawRoundRect(
            color = detail,
            topLeft = Offset((size.width - slitW) / 2f, size.height * 0.085f - slitH / 2f),
            size = Size(slitW, slitH),
            cornerRadius = CornerRadius(slitH / 2f),
        )
        drawCircle(detail, radius = size.width * 0.05f, center = Offset(size.width / 2f, size.height * 0.915f), style = Stroke(1.5.dp.toPx()))
    }

    private fun phoneBodyPath(size: Size): Path {
        val w = size.width
        val h = size.height
        val cap = h * 0.11f
        return Path().apply {
            moveTo(0f, cap)
            cubicTo(0f, 0f, w, 0f, w, cap) // convex top cap
            lineTo(w, h - cap) // right edge
            cubicTo(w, h, 0f, h, 0f, h - cap) // convex bottom cap
            close() // left edge
        }
    }

    // endregion

    // region Content drawing

    private fun DrawScope.drawMangaPage(left: Float, top: Float, w: Float, h: Float, pageColor: Color, panelColor: Color, alt: Boolean) {
        drawRect(pageColor, topLeft = Offset(left, top), size = Size(w, h))
        val m = w * 0.10f
        val gap = w * 0.05f
        val radius = CornerRadius(w * 0.03f)
        val l = left + m
        val t = top + m
        val r = left + w - m
        val b = top + h - m
        val innerW = r - l
        val innerH = b - t
        fun panel(pl: Float, pt: Float, pr: Float, pb: Float) =
            drawRoundRect(panelColor, topLeft = Offset(pl, pt), size = Size(pr - pl, pb - pt), cornerRadius = radius)
        if (!alt) {
            val splitY = t + innerH * 0.42f
            panel(l, t, r, splitY - gap / 2)
            val midX = l + innerW / 2
            panel(l, splitY + gap / 2, midX - gap / 2, b)
            panel(midX + gap / 2, splitY + gap / 2, r, b)
        } else {
            val splitY = t + innerH * 0.58f
            val midX = l + innerW / 2
            panel(l, t, midX - gap / 2, splitY - gap / 2)
            panel(midX + gap / 2, t, r, splitY - gap / 2)
            panel(l, splitY + gap / 2, r, b)
        }
    }

    /**
     * Line-art webtoon content laid out down a strip ~1.7× the viewport, intentionally diagonal-heavy
     * and non-uniform (tilted panels, a cat face, speed lines, a figure, slanted text, diamonds) so
     * edges don't all clip at once as it scrolls. [baseY] already includes the scroll offset.
     */
    private fun DrawScope.drawWebtoonContent(ox: Float, baseY: Float, w: Float, h: Float, ink: Color, fill: Color, accent: Color) {
        val sw = 1.5.dp.toPx()
        fun px(fx: Float) = ox + w * fx
        fun py(fy: Float) = baseY + h * fy
        fun seg(fx0: Float, fy0: Float, fx1: Float, fy1: Float, width: Float = sw, color: Color = ink) =
            drawLine(color, Offset(px(fx0), py(fy0)), Offset(px(fx1), py(fy1)), width, cap = StrokeCap.Round)

        // A — tilted panel with diagonal hatching.
        val panelA = Path().apply {
            moveTo(px(0.10f), py(0.05f))
            lineTo(px(0.90f), py(0.02f))
            lineTo(px(0.90f), py(0.22f))
            lineTo(px(0.10f), py(0.25f))
            close()
        }
        drawPath(panelA, fill)
        drawPath(panelA, ink, style = Stroke(sw))
        seg(0.18f, 0.21f, 0.40f, 0.07f)
        seg(0.30f, 0.235f, 0.55f, 0.075f)
        seg(0.45f, 0.235f, 0.70f, 0.075f)

        // B — cat face (ears, whiskers, mouth = lots of diagonals).
        val catCx = px(0.5f)
        val catCy = py(0.40f)
        val rr = w * 0.20f
        drawCircle(ink, rr, Offset(catCx, catCy), style = Stroke(sw))
        val leftEar = Path().apply {
            moveTo(catCx - rr * 0.75f, catCy - rr * 0.62f)
            lineTo(catCx - rr * 1.05f, catCy - rr * 1.45f)
            lineTo(catCx - rr * 0.10f, catCy - rr * 1.0f)
            close()
        }
        val rightEar = Path().apply {
            moveTo(catCx + rr * 0.75f, catCy - rr * 0.62f)
            lineTo(catCx + rr * 1.05f, catCy - rr * 1.45f)
            lineTo(catCx + rr * 0.10f, catCy - rr * 1.0f)
            close()
        }
        drawPath(leftEar, ink, style = Stroke(sw))
        drawPath(rightEar, ink, style = Stroke(sw))
        drawCircle(ink, rr * 0.10f, Offset(catCx - rr * 0.38f, catCy - rr * 0.10f))
        drawCircle(ink, rr * 0.10f, Offset(catCx + rr * 0.38f, catCy - rr * 0.10f))
        val nose = Path().apply {
            moveTo(catCx, catCy + rr * 0.28f)
            lineTo(catCx - rr * 0.12f, catCy + rr * 0.12f)
            lineTo(catCx + rr * 0.12f, catCy + rr * 0.12f)
            close()
        }
        drawPath(nose, ink)
        drawLine(ink, Offset(catCx, catCy + rr * 0.28f), Offset(catCx - rr * 0.22f, catCy + rr * 0.5f), sw, cap = StrokeCap.Round)
        drawLine(ink, Offset(catCx, catCy + rr * 0.28f), Offset(catCx + rr * 0.22f, catCy + rr * 0.5f), sw, cap = StrokeCap.Round)
        for (k in 0..2) {
            val yk = catCy + rr * (0.05f + k * 0.12f)
            drawLine(ink, Offset(catCx - rr * 0.5f, yk), Offset(catCx - rr * 1.4f, yk - rr * 0.2f), sw * 0.8f, cap = StrokeCap.Round)
            drawLine(ink, Offset(catCx + rr * 0.5f, yk), Offset(catCx + rr * 1.4f, yk - rr * 0.2f), sw * 0.8f, cap = StrokeCap.Round)
        }

        // C — diagonal speed lines.
        for (k in 0..6) {
            val t0 = 0.08f + k * 0.13f
            seg(t0, 0.58f, t0 + 0.18f, 0.66f, sw * 0.8f, accent)
        }

        // D — a figure inside an inclined panel.
        val panelD = Path().apply {
            moveTo(px(0.18f), py(0.72f))
            lineTo(px(0.84f), py(0.69f))
            lineTo(px(0.82f), py(1.04f))
            lineTo(px(0.16f), py(1.07f))
            close()
        }
        drawPath(panelD, fill)
        drawPath(panelD, ink, style = Stroke(sw))
        drawCircle(ink, w * 0.085f, Offset(px(0.5f), py(0.80f)), style = Stroke(sw))
        seg(0.5f, 0.845f, 0.34f, 0.99f)
        seg(0.5f, 0.845f, 0.66f, 0.99f)
        seg(0.34f, 0.99f, 0.66f, 0.99f)
        seg(0.45f, 0.88f, 0.30f, 0.95f)
        seg(0.55f, 0.88f, 0.70f, 0.95f)

        // E — slanted text lines of varied length and indent.
        seg(0.16f, 1.16f, 0.96f, 1.154f, sw * 0.9f)
        seg(0.16f, 1.21f, 0.78f, 1.204f, sw * 0.9f)
        seg(0.24f, 1.26f, 0.94f, 1.254f, sw * 0.9f)
        seg(0.16f, 1.31f, 0.61f, 1.304f, sw * 0.9f)

        // F — diamond cluster (rotated squares) crossed by diagonals.
        fun diamond(cxf: Float, cyf: Float, rf: Float) {
            val p = Path().apply {
                moveTo(px(cxf), py(cyf - rf))
                lineTo(px(cxf + rf * 0.62f), py(cyf))
                lineTo(px(cxf), py(cyf + rf))
                lineTo(px(cxf - rf * 0.62f), py(cyf))
                close()
            }
            drawPath(p, ink, style = Stroke(sw))
        }
        diamond(0.32f, 1.48f, 0.10f)
        diamond(0.58f, 1.55f, 0.14f)
        diamond(0.74f, 1.44f, 0.08f)
        seg(0.20f, 1.40f, 0.80f, 1.64f, sw * 0.8f, accent)
        seg(0.20f, 1.64f, 0.80f, 1.40f, sw * 0.8f, accent)
    }

    // endregion

    private fun sanitize(value: Float): Float = ((value * 1000).roundToInt() / 1000f).coerceIn(0f, 1f)

    private fun formatCurveValue(value: Float): String {
        val r = sanitize(value)
        return if (r == r.toLong().toFloat()) r.toLong().toString() else r.toString()
    }

    companion object {
        private const val PREVIEW_PAUSE_MS = 400L
        private const val DEFAULT_PREVIEW_DURATION = 300
        private const val WEBTOON_SCROLL_FRACTION = 0.75f

        // The abstract motion square is kept (useful while shaping a curve) but hidden from the UI.
        private const val SHOW_DEBUG_SQUARE_PREVIEW = false

        // Inset reserved inside the Bézier grid so edge handles / curve glow aren't clipped.
        internal val GRID_PADDING = 12.dp

        // Horizontal padding of each ViewerSection — the base clearance every child already has from the
        // screen edge before its own inset is added.
        internal val CONTENT_HORIZONTAL_PADDING = 16.dp

        // Approx. half the Material3 slider thumb; the part of the thumb that reaches past its track end
        // toward the screen edge and must still clear the gesture zone.
        internal val SLIDER_THUMB_RADIUS = 10.dp

        // Minimum clearance a draggable element must keep from the screen edge to stay out of the system
        // back-gesture zone. Enforced by ReaderTransitionAnimationLayoutTest.
        internal val MIN_EDGE_CLEARANCE = 16.dp

        // Custom-editor layout sizing.
        private val WIDE_LAYOUT_MIN_WIDTH = 560.dp // landscape phones / tablets → 3-column layout
        private const val TABLET_MIN_SW = 600 // Android's standard tablet smallest-width breakpoint
        private val NUMERIC_COL_WIDTH = 80.dp // fixed-width X/Y/duration column (same in both layouts)
        // Inset applied to the grid (and matched by the preview) in the narrow layout so an x=0 handle
        // clears the system back-gesture edge zone.
        internal val EDGE_GESTURE_INSET = 12.dp

        // Sliders need a larger inset than the grid: the thumb has width, so clearing its *center* isn't
        // enough — its outer edge (~half the ~20dp thumb) must also clear the gesture zone. On top of the
        // section's own padding this keeps the whole thumb out of the edge.
        internal val SLIDER_EDGE_INSET = 24.dp
        private val GRID_NUM_GAP = 8.dp
        private val ANIM_COL_WIDTH = 184.dp // preview + slider column in the wide layout
        private val PRESET_SLIDER_WIDTH = 280.dp // duration slider beside the preview (wide presets)

        // Quick-fill presets for the custom curve. These are the "cubic" easings: the cubic-bézier
        // control points approximate the t^3 power curve (quad = t^2, quart = t^4, … — higher exponent
        // bends harder). out-in is omitted: its mid-pause is useless for a reader.
        private val EASE_OUT = floatArrayOf(0.33f, 1f, 0.68f, 1f)
        private val EASE_IN_OUT = floatArrayOf(0.66f, 0f, 0.34f, 1f)
        private val EASE_IN = floatArrayOf(0.32f, 0f, 0.67f, 0f)

        // 10.3" e-reader body proportions (see drawEReaderFrame).
        private val EREADER_BODY_W = 138.dp
        private val EREADER_BODY_H = 168.dp
        private const val EREADER_INSET_LEFT = 0.109f
        private const val EREADER_INSET_RIGHT = 0.038f
        private const val EREADER_INSET_TOP = 0.031f
        private const val EREADER_INSET_BOTTOM = 0.031f

        // Older-phone body proportions (see drawPhoneFrame).
        private val PHONE_BODY_W = 104.dp
        private val PHONE_BODY_H = 188.dp
        private const val PHONE_INSET_SIDE = 0.075f
        private const val PHONE_INSET_TOP = 0.15f
        private const val PHONE_INSET_BOTTOM = 0.15f

        private fun emitHandle(
            handle: Int,
            pos: Offset,
            gridLeft: Float,
            gridTop: Float,
            gridWidth: Float,
            gridHeight: Float,
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            onChange: (Float, Float, Float, Float) -> Unit,
        ) {
            if (handle != 1 && handle != 2) return
            val nx = ((pos.x - gridLeft) / gridWidth).coerceIn(0f, 1f)
            val ny = (1f - (pos.y - gridTop) / gridHeight).coerceIn(0f, 1f)
            if (handle == 1) onChange(nx, ny, x2, y2) else onChange(x1, y1, nx, ny)
        }
    }
}
