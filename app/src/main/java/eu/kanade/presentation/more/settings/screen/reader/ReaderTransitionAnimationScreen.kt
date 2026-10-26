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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.ReaderTransitionAnimation
import eu.kanade.tachiyomi.ui.reader.viewer.CubicBezier
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderTransitionAnimations
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Info
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.secondaryItemAlpha
import kotlin.math.roundToInt

class ReaderTransitionAnimationScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val prefs = remember { context.appGraph.readerPreferences }

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
                    optionPref = prefs.pagerTransitionAnimation,
                    customDurationPref = prefs.pagerTransitionCustomDuration,
                    customCurvePref = prefs.pagerTransitionCustomCurve,
                )
                HorizontalDivider()
                ViewerSection(
                    title = stringResource(MR.strings.webtoon_viewer),
                    longStrip = true,
                    optionPref = prefs.webtoonTransitionAnimation,
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
        customDurationPref: Preference<Int>,
        customCurvePref: Preference<CubicBezier>,
    ) {
        val option by optionPref.collectAsState()

        val preset = if (option == ReaderTransitionAnimation.GENTLE) {
            ReaderTransitionAnimations.GENTLE
        } else {
            ReaderTransitionAnimations.SMOOTH
        }

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

            AnimatedVisibility(
                visible = option == ReaderTransitionAnimation.SMOOTH || option == ReaderTransitionAnimation.GENTLE,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PageTransitionPreview(Modifier, longStrip, preset.curve, preset.durationMs)
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
    private fun CustomCurveEditor(
        modifier: Modifier,
        longStrip: Boolean,
        curvePref: Preference<CubicBezier>,
        durationPref: Preference<Int>,
    ) {
        val dense = LocalConfiguration.current.smallestScreenWidthDp >= TABLET_MIN_SW
        val fieldHeight = if (dense) 32.dp else 36.dp
        val fieldGap = if (dense) 5.dp else 7.dp
        val duration = durationPref.collectAsState().value
            .coerceIn(ReaderTransitionAnimations.CUSTOM_DURATION_MIN, ReaderTransitionAnimations.CUSTOM_DURATION_MAX)
        var curve by remember { mutableStateOf(curvePref.get()) }
        fun update(c: CubicBezier) {
            curve = c
            curvePref.set(c)
        }
        fun edit(transform: (CubicBezier) -> CubicBezier) = update(transform(curve))

        Column(modifier = modifier) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val wide = maxWidth >= WIDE_LAYOUT_MIN_WIDTH
                val editor: @Composable (RowScope.() -> Modifier) -> Unit = { gridModifier ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(GRID_NUM_GAP),
                        verticalAlignment = Alignment.Top,
                    ) {
                        BezierCurveEditor(gridModifier(), curve, ::edit)
                        CurveFieldsColumn(
                            Modifier.width(NUMERIC_COL_WIDTH).padding(top = GRID_PADDING),
                            curve,
                            duration,
                            fieldHeight,
                            fieldGap,
                            ::edit,
                            { durationPref.set(it) },
                        )
                    }
                }
                if (wide) {
                    val colHeight = GRID_PADDING + fieldHeight * 6 + fieldGap * 5 + 2.dp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        editor { Modifier.size(colHeight) }
                        PageTransitionPreview(Modifier, longStrip, curve, duration)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        editor { Modifier.weight(1f).padding(start = EDGE_GESTURE_INSET).aspectRatio(1f) }
                        PageTransitionPreview(Modifier, longStrip, curve, duration)
                    }
                }
            }

            CurveInfo()
        }
    }

    @Composable
    private fun BezierCurveEditor(
        modifier: Modifier,
        curve: CubicBezier,
        onEdit: ((CubicBezier) -> CubicBezier) -> Unit,
    ) {
        val current by rememberUpdatedState(curve)
        val edit by rememberUpdatedState(onEdit)

        val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
        val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        val handleFillColor = MaterialTheme.colorScheme.surface

        val canvasColor = containerColor.compositeOver(MaterialTheme.colorScheme.surface)
        val startColor = MaterialTheme.colorScheme.primary
        val endColor = pickAccent(
            candidates = listOf(
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.primary,
            ),
            canvas = canvasColor,
        )

        Canvas(
            modifier = modifier
                .pointerInput(Unit) {
                    var firstHandle = true
                    fun emitHandle(pos: Offset) {
                        val pad = GRID_PADDING.toPx()
                        val nx = (pos.x - pad) / (size.width - pad * 2)
                        val ny = 1f - (pos.y - pad) / (size.height - pad * 2)
                        edit { if (firstHandle) it.with(x1 = nx, y1 = ny) else it.with(x2 = nx, y2 = ny) }
                    }
                    detectDragGestures(
                        onDragStart = { pos ->
                            val pad = GRID_PADDING.toPx()
                            val gw = size.width - pad * 2
                            val gh = size.height - pad * 2
                            val p1 = Offset(pad + current.x1 * gw, pad + (1f - current.y1) * gh)
                            val p2 = Offset(pad + current.x2 * gw, pad + (1f - current.y2) * gh)
                            firstHandle = (pos - p1).getDistanceSquared() <= (pos - p2).getDistanceSquared()
                            emitHandle(pos)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            emitHandle(change.position)
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
            val c1 = Offset(gl + curve.x1 * gw, gt + (1f - curve.y1) * gh)
            val c2 = Offset(gl + curve.x2 * gw, gt + (1f - curve.y2) * gh)
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
            drawCircle(handleFillColor, radius = radius, center = c1)
            drawCircle(startColor, radius = radius, center = c1, style = Stroke(3.dp.toPx()))
            drawCircle(startColor, radius = dotRadius, center = c1)
            drawCircle(handleFillColor, radius = radius, center = c2)
            drawCircle(endColor, radius = radius, center = c2, style = Stroke(3.dp.toPx()))
            drawCircle(endColor, radius = dotRadius, center = c2)
        }
    }

    @Composable
    private fun EditableNumberBox(
        text: String,
        onTextChange: (String) -> Unit,
        onCommit: () -> Unit,
        modifier: Modifier,
        height: Dp,
        keyboardType: KeyboardType = KeyboardType.Decimal,
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
        curve: CubicBezier,
        duration: Int,
        fieldHeight: Dp,
        fieldGap: Dp,
        onEdit: ((CubicBezier) -> CubicBezier) -> Unit,
        onDuration: (Int) -> Unit,
    ) {
        val onPreset: (CubicBezier) -> Unit = { preset -> onEdit { preset } }
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(fieldGap)) {
            CurveNumberField("X1", curve.x1, fieldHeight) { v -> onEdit { it.with(x1 = v) } }
            CurveNumberField("Y1", curve.y1, fieldHeight) { v -> onEdit { it.with(y1 = v) } }
            CurveNumberField("X2", curve.x2, fieldHeight) { v -> onEdit { it.with(x2 = v) } }
            CurveNumberField("Y2", curve.y2, fieldHeight) { v -> onEdit { it.with(y2 = v) } }
            DurationField(duration, fieldHeight, onDuration)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PresetGlyphButton(
                    CubicBezier.EASE_OUT,
                    stringResource(MR.strings.transition_curve_ease_out),
                    Modifier.weight(1f),
                    fieldHeight,
                    onPreset,
                )
                PresetGlyphButton(
                    CubicBezier.EASE_IN_OUT,
                    stringResource(MR.strings.transition_curve_ease_in_out),
                    Modifier.weight(1f),
                    fieldHeight,
                    onPreset,
                )
                PresetGlyphButton(
                    CubicBezier.EASE_IN,
                    stringResource(MR.strings.transition_curve_ease_in),
                    Modifier.weight(1f),
                    fieldHeight,
                    onPreset,
                )
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
                    val parsed = text.toFloatOrNull()?.takeIf { it.isFinite() }
                    if (parsed != null) onValue(parsed) else text = formatCurveValue(value)
                },
                modifier = Modifier.weight(1f),
                height = fieldHeight,
            )
        }
    }

    @Composable
    private fun DurationField(value: Int, fieldHeight: Dp, onValue: (Int) -> Unit) {
        val min = ReaderTransitionAnimations.CUSTOM_DURATION_MIN
        val max = ReaderTransitionAnimations.CUSTOM_DURATION_MAX
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
                height = fieldHeight,
                keyboardType = KeyboardType.Number,
            )
        }
    }

    @Composable
    private fun PresetGlyphButton(
        curve: CubicBezier,
        contentDescription: String,
        modifier: Modifier,
        height: Dp,
        onPick: (CubicBezier) -> Unit,
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
    private fun CurveGlyph(curve: CubicBezier, modifier: Modifier) {
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(modifier) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(0f, h)
                cubicTo(curve.x1 * w, (1f - curve.y1) * h, curve.x2 * w, (1f - curve.y2) * h, w, 0f)
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
            Icon(imageVector = MaterialSymbols.Rounded.Info, contentDescription = null)
            Text(
                text = stringResource(MR.strings.pref_reader_transition_curve_help),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    @Composable
    private fun rememberCurveAnimatable(curve: CubicBezier, durationMs: Int): Animatable<Float, AnimationVector1D> {
        val cCurve by rememberUpdatedState(curve)
        val cDuration by rememberUpdatedState(durationMs)
        val anim = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            while (isActive) {
                val easing = CubicBezierEasing(cCurve.x1, cCurve.y1, cCurve.x2, cCurve.y2)
                anim.animateTo(1f, tween(durationMillis = cDuration, easing = easing))
                delay(PREVIEW_PAUSE_MS)
                anim.animateTo(0f, tween(durationMillis = cDuration, easing = easing))
                delay(PREVIEW_PAUSE_MS)
            }
        }
        return anim
    }

    @Composable
    private fun PageTransitionPreview(modifier: Modifier, longStrip: Boolean, curve: CubicBezier, durationMs: Int) {
        if (longStrip) {
            LongStripPreview(modifier, curve, durationMs)
        } else {
            PagedPreview(modifier, curve, durationMs)
        }
    }

    @Composable
    private fun PagedPreview(modifier: Modifier, curve: CubicBezier, durationMs: Int) {
        val anim = rememberCurveAnimatable(curve, durationMs)
        val frame = frameColors()
        val gutter = MaterialTheme.colorScheme.surfaceVariant
        val page = MaterialTheme.colorScheme.surface
        val panel = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        Canvas(modifier = modifier.size(EREADER_BODY_W, EREADER_BODY_H)) {
            val screen =
                screenRectIn(size, EREADER_INSET_LEFT, EREADER_INSET_TOP, EREADER_INSET_RIGHT, EREADER_INSET_BOTTOM)
            val screenRadius = 3.dp.toPx()
            drawEReaderFrame(screen, screenRadius, frame)
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
    private fun LongStripPreview(modifier: Modifier, curve: CubicBezier, durationMs: Int) {
        val anim = rememberCurveAnimatable(curve, durationMs)
        val frame = frameColors()
        val page = MaterialTheme.colorScheme.surface
        val ink = MaterialTheme.colorScheme.onSurfaceVariant
        val fill = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
        val accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        Canvas(modifier = modifier.size(PHONE_BODY_W, PHONE_BODY_H)) {
            val screen = screenRectIn(size, PHONE_INSET_SIDE, PHONE_INSET_TOP, PHONE_INSET_SIDE, PHONE_INSET_BOTTOM)
            val screenRadius = 6.dp.toPx()
            drawPhoneFrame(screen, screenRadius, frame)
            clipPath(roundedRectPath(screen, screenRadius)) {
                drawRect(page, topLeft = Offset(screen.left, screen.top), size = Size(screen.width, screen.height))
                val scroll = anim.value * (WEBTOON_SCROLL_FRACTION * screen.height)
                drawWebtoonContent(screen.left, screen.top - scroll, screen.width, screen.height, ink, fill, accent)
            }
        }
    }

    private fun screenRectIn(size: Size, left: Float, top: Float, right: Float, bottom: Float): Rect =
        Rect(size.width * left, size.height * top, size.width * (1f - right), size.height * (1f - bottom))

    private fun roundedRectPath(rect: Rect, radiusPx: Float): Path =
        Path().apply { addRoundRect(RoundRect(rect.left, rect.top, rect.right, rect.bottom, CornerRadius(radiusPx))) }

    @Composable
    private fun frameColors() = FrameColors(
        body = MaterialTheme.colorScheme.secondaryContainer,
        bezel = MaterialTheme.colorScheme.onSurfaceVariant,
        outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        detail = MaterialTheme.colorScheme.primary,
    )

    private fun DrawScope.drawBezel(screen: Rect, screenRadiusPx: Float, color: Color) {
        val lip = 2.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(screen.left - lip, screen.top - lip),
            size = Size(screen.width + lip * 2, screen.height + lip * 2),
            cornerRadius = CornerRadius(screenRadiusPx + lip),
        )
    }

    private fun DrawScope.drawEReaderFrame(screen: Rect, screenRadiusPx: Float, colors: FrameColors) {
        val bodyRadius = CornerRadius(size.width * 0.06f)
        drawRoundRect(colors.body, size = size, cornerRadius = bodyRadius)
        drawRoundRect(colors.outline, size = size, cornerRadius = bodyRadius, style = Stroke(1.dp.toPx()))

        drawBezel(screen, screenRadiusPx, colors.bezel)

        val gripCx = size.width * EREADER_INSET_LEFT * 0.5f
        val bw = size.width * 0.018f
        val bh = size.height * 0.11f
        drawRoundRect(
            colors.detail,
            topLeft = Offset(gripCx - bw / 2f, size.height * 0.32f),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(bw),
        )
        drawRoundRect(
            colors.detail,
            topLeft = Offset(gripCx - bw / 2f, size.height * 0.50f),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(bw),
        )
    }

    private fun DrawScope.drawPhoneFrame(screen: Rect, screenRadiusPx: Float, colors: FrameColors) {
        val path = phoneBodyPath(size)
        drawPath(path, colors.body)
        drawPath(path, colors.outline, style = Stroke(1.dp.toPx()))

        drawBezel(screen, screenRadiusPx, colors.bezel)

        val slitW = size.width * 0.20f
        val slitH = 3.dp.toPx()
        drawRoundRect(
            color = colors.detail,
            topLeft = Offset((size.width - slitW) / 2f, size.height * 0.085f - slitH / 2f),
            size = Size(slitW, slitH),
            cornerRadius = CornerRadius(slitH / 2f),
        )
        drawCircle(
            colors.detail,
            radius = size.width * 0.05f,
            center = Offset(size.width / 2f, size.height * 0.915f),
            style = Stroke(1.5.dp.toPx()),
        )
    }

    private fun phoneBodyPath(size: Size): Path {
        val w = size.width
        val h = size.height
        val cap = h * 0.11f
        return Path().apply {
            moveTo(0f, cap)
            cubicTo(0f, 0f, w, 0f, w, cap)
            lineTo(w, h - cap)
            cubicTo(w, h, 0f, h, 0f, h - cap)
            close()
        }
    }

    private fun DrawScope.drawMangaPage(
        left: Float,
        top: Float,
        w: Float,
        h: Float,
        pageColor: Color,
        panelColor: Color,
        alt: Boolean,
    ) {
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

    private fun DrawScope.drawWebtoonContent(
        ox: Float,
        baseY: Float,
        w: Float,
        h: Float,
        ink: Color,
        fill: Color,
        accent: Color,
    ) {
        val sw = 1.5.dp.toPx()
        fun px(fx: Float) = ox + w * fx
        fun py(fy: Float) = baseY + h * fy
        fun seg(fx0: Float, fy0: Float, fx1: Float, fy1: Float, width: Float = sw, color: Color = ink) =
            drawLine(color, Offset(px(fx0), py(fy0)), Offset(px(fx1), py(fy1)), width, cap = StrokeCap.Round)

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
        drawLine(
            ink,
            Offset(catCx, catCy + rr * 0.28f),
            Offset(catCx - rr * 0.22f, catCy + rr * 0.5f),
            sw,
            cap = StrokeCap.Round,
        )
        drawLine(
            ink,
            Offset(catCx, catCy + rr * 0.28f),
            Offset(catCx + rr * 0.22f, catCy + rr * 0.5f),
            sw,
            cap = StrokeCap.Round,
        )
        for (k in 0..2) {
            val yk = catCy + rr * (0.05f + k * 0.12f)
            drawLine(
                ink,
                Offset(catCx - rr * 0.5f, yk),
                Offset(catCx - rr * 1.4f, yk - rr * 0.2f),
                sw * 0.8f,
                cap = StrokeCap.Round,
            )
            drawLine(
                ink,
                Offset(catCx + rr * 0.5f, yk),
                Offset(catCx + rr * 1.4f, yk - rr * 0.2f),
                sw * 0.8f,
                cap = StrokeCap.Round,
            )
        }

        for (k in 0..6) {
            val t0 = 0.08f + k * 0.13f
            seg(t0, 0.58f, t0 + 0.18f, 0.66f, sw * 0.8f, accent)
        }

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

        seg(0.16f, 1.16f, 0.96f, 1.154f, sw * 0.9f)
        seg(0.16f, 1.21f, 0.78f, 1.204f, sw * 0.9f)
        seg(0.24f, 1.26f, 0.94f, 1.254f, sw * 0.9f)
        seg(0.16f, 1.31f, 0.61f, 1.304f, sw * 0.9f)

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

    private fun formatCurveValue(value: Float): String {
        val r = (value * 1000).roundToInt() / 1000f
        return if (r == r.toLong().toFloat()) r.toLong().toString() else r.toString()
    }

    private fun pickAccent(candidates: List<Color>, canvas: Color): Color =
        candidates.firstOrNull { ColorUtils.calculateContrast(it.toArgb(), canvas.toArgb()) >= MIN_ACCENT_CONTRAST }
            ?: candidates.last()

    private class FrameColors(val body: Color, val bezel: Color, val outline: Color, val detail: Color)

    companion object {
        private const val PREVIEW_PAUSE_MS = 400L
        private const val WEBTOON_SCROLL_FRACTION = 0.75f

        private val GRID_PADDING = 12.dp

        private val CONTENT_HORIZONTAL_PADDING = 16.dp

        private val WIDE_LAYOUT_MIN_WIDTH = 560.dp
        private const val TABLET_MIN_SW = 600
        private val NUMERIC_COL_WIDTH = 80.dp

        private val EDGE_GESTURE_INSET = 12.dp

        private val GRID_NUM_GAP = 8.dp

        private val EREADER_BODY_W = 138.dp
        private val EREADER_BODY_H = 168.dp
        private const val EREADER_INSET_LEFT = 0.109f
        private const val EREADER_INSET_RIGHT = 0.038f
        private const val EREADER_INSET_TOP = 0.031f
        private const val EREADER_INSET_BOTTOM = 0.031f

        private val PHONE_BODY_W = 104.dp
        private val PHONE_BODY_H = 188.dp
        private const val PHONE_INSET_SIDE = 0.075f
        private const val PHONE_INSET_TOP = 0.15f
        private const val PHONE_INSET_BOTTOM = 0.15f

        private const val MIN_ACCENT_CONTRAST = 2.0
    }
}
