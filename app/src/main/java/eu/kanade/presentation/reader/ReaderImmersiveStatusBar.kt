package eu.kanade.presentation.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.text.format.DateFormat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import java.util.Date

private const val LOW_BATTERY_THRESHOLD = 40
private const val CRITICAL_BATTERY_THRESHOLD = 10

private val normalBatteryColor = Color(0xFF4CAF50)
private val warnBatteryColor = Color(0xFFFFC107)
private val criticalBatteryColor = Color(0xFFFF5252)
private val powerSaveColor = Color(0xFF00C853)

private data class BatteryUiState(
    val percentage: Int = 100,
    val isCharging: Boolean = false,
    val isPowerSaveMode: Boolean = false,
)

/**
 * Overlay shown at the top of the reader while it's in immersive fullscreen (system status bar
 * hidden), so the clock and battery level remain visible while reading.
 */
@Composable
fun ReaderImmersiveStatusBar(
    visible: Boolean,
    showClock: Boolean,
    showBattery: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible || (!showClock && !showBattery)) return

    val context = LocalContext.current

    val textStyle = TextStyle(
        color = Color.White,
        fontSize = MaterialTheme.typography.labelMedium.fontSize,
        fontWeight = FontWeight.SemiBold,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .displayCutoutPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showClock) {
            val time by rememberCurrentTime(context)
            OutlinedText(text = time, style = textStyle)
        } else {
            Spacer(modifier = Modifier)
        }

        if (showBattery) {
            val battery by rememberBatteryState(context)
            val batteryColor = when {
                battery.percentage < CRITICAL_BATTERY_THRESHOLD -> criticalBatteryColor
                battery.percentage < LOW_BATTERY_THRESHOLD -> warnBatteryColor
                else -> normalBatteryColor
            }
            val isCritical = battery.percentage < CRITICAL_BATTERY_THRESHOLD

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (battery.isPowerSaveMode) {
                    PowerSaveGlyph()
                }
                OutlinedText(text = "${battery.percentage}%", style = textStyle)
                BatteryGlyph(
                    percentage = battery.percentage,
                    isCharging = battery.isCharging,
                    blink = isCritical,
                    color = batteryColor,
                )
            }
        }
    }
}

/**
 * Text with a hairline outline in the inverse of [style]'s color, so it stays legible over any
 * page background (white, black, or busy artwork) without needing a heavy stroke or blur.
 */
@Composable
private fun OutlinedText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val outlineWidthPx = with(LocalDensity.current) { 0.5.dp.toPx() }
    val outlineColor = if (style.color.luminance() > 0.5f) Color.Black else Color.White
    Box(modifier = modifier) {
        Text(
            text = text,
            style = style.copy(
                color = outlineColor,
                drawStyle = Stroke(width = outlineWidthPx),
            ),
        )
        Text(text = text, style = style)
    }
}

@Composable
private fun rememberCurrentTime(context: Context) = run {
    val timeState = remember { mutableStateOf(formatCurrentTime(context)) }
    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                timeState.value = formatCurrentTime(context)
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    timeState
}

private fun formatCurrentTime(context: Context): String {
    return DateFormat.getTimeFormat(context).format(Date())
}

@Composable
private fun rememberBatteryState(context: Context) = run {
    val batteryState = remember { mutableStateOf(readBatteryState(context)) }
    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val sticky = intent.takeIf { it.action == Intent.ACTION_BATTERY_CHANGED }
                batteryState.value = readBatteryState(context, sticky)
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    batteryState
}

private fun readBatteryState(context: Context, stickyIntent: Intent? = null): BatteryUiState {
    val intent = stickyIntent
        ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val percentage = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    val isPowerSaveMode = context.getSystemService<PowerManager>()?.isPowerSaveMode == true
    return BatteryUiState(percentage = percentage, isCharging = isCharging, isPowerSaveMode = isPowerSaveMode)
}

@Composable
private fun BatteryGlyph(
    percentage: Int,
    isCharging: Boolean,
    blink: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "battery_blink")
    val blinkAlpha by if (blink) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "battery_blink_alpha",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Canvas(
        modifier = modifier
            .size(width = 22.dp, height = 11.dp)
            .alpha(blinkAlpha),
    ) {
        val strokeWidth = 1.5.dp.toPx()
        val nubWidth = size.height * 0.28f
        val bodyWidth = size.width - nubWidth
        val cornerRadius = CornerRadius(size.height * 0.2f, size.height * 0.2f)

        drawRoundRect(
            color = Color.White,
            topLeft = Offset.Zero,
            size = Size(bodyWidth, size.height),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth),
        )

        val nubHeight = size.height * 0.5f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(bodyWidth, (size.height - nubHeight) / 2f),
            size = Size(nubWidth, nubHeight),
            cornerRadius = CornerRadius(cornerRadius.x / 2, cornerRadius.y / 2),
        )

        val inset = strokeWidth * 1.5f
        val fillMaxWidth = bodyWidth - inset * 2
        val fillWidth = (fillMaxWidth * (percentage.coerceIn(0, 100) / 100f)).coerceAtLeast(0f)
        if (fillWidth > 0f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(inset, inset),
                size = Size(fillWidth, size.height - inset * 2),
                cornerRadius = CornerRadius(cornerRadius.x / 2, cornerRadius.y / 2),
            )
        }

        if (isCharging) {
            val bolt = Path().apply {
                moveTo(bodyWidth * 0.55f, size.height * 0.05f)
                lineTo(bodyWidth * 0.30f, size.height * 0.58f)
                lineTo(bodyWidth * 0.48f, size.height * 0.58f)
                lineTo(bodyWidth * 0.40f, size.height * 0.95f)
                lineTo(bodyWidth * 0.68f, size.height * 0.38f)
                lineTo(bodyWidth * 0.50f, size.height * 0.38f)
                close()
            }
            drawPath(bolt, color = Color.Black.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun PowerSaveGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(12.dp)) {
        val leaf = Path().apply {
            moveTo(size.width * 0.8f, 0f)
            cubicTo(
                size.width * 0.2f, size.height * 0.1f,
                0f, size.height * 0.6f,
                size.width * 0.5f, size.height,
            )
            cubicTo(
                size.width, size.height * 0.6f,
                size.width * 0.85f, size.height * 0.15f,
                size.width * 0.8f, 0f,
            )
            close()
        }
        drawPath(leaf, color = powerSaveColor)
        drawLine(
            color = Color.Black.copy(alpha = 0.4f),
            start = Offset(size.width * 0.15f, size.height * 0.85f),
            end = Offset(size.width * 0.75f, size.height * 0.15f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

@PreviewLightDark
@Composable
private fun ReaderImmersiveStatusBarPreview() {
    TachiyomiPreviewTheme {
        ReaderImmersiveStatusBar(visible = true, showClock = true, showBattery = true)
    }
}
