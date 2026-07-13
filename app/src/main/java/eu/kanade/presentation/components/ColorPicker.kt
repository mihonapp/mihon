package eu.kanade.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.components.material.Slider
import tachiyomi.presentation.core.components.material.padding

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(
    initialColor: Color,
    onColorChanged: (Color) -> Unit,
) {
    val hsv = remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        hsv
    }
    var hue by remember { mutableIntStateOf(hsv[0].toInt()) }
    var saturation by remember { mutableIntStateOf((hsv[1] * 100).toInt()) }
    var value by remember { mutableIntStateOf((hsv[2] * 100).toInt()) }

    val currentColor = remember(hue, saturation, value) {
        val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), saturation.toFloat() / 100f, value.toFloat() / 100f)))
        onColorChanged(color)
        color
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(currentColor)
        )

        // Presets
        Text(text = "Presets", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            listOf(
                Color(0xFF0000FF), // Blue
                Color(0xFF00FF00), // Green
                Color(0xFFFF0000), // Red
                Color(0xFFFFFF00), // Yellow
                Color(0xFFFF00FF), // Purple
                Color(0xFF00FFFF), // Aqua
                Color(0xFFFF8000), // Orange
                Color(0xFF000000), // Black
                Color(0xFFFFFFFF), // White
            ).forEach { preset ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(preset)
                        .clickable {
                            val newHsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(preset.toArgb(), newHsv)
                            hue = newHsv[0].toInt()
                            saturation = (newHsv[1] * 100).toInt()
                            value = (newHsv[2] * 100).toInt()
                        }
                )
            }
        }

        Text(text = "Hue: $hue°", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = hue,
            onValueChange = { hue = it },
            valueRange = 0..360,
        )

        Text(text = "Saturation: $saturation%", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = saturation,
            onValueChange = { saturation = it },
            valueRange = 0..100,
        )

        Text(text = "Value: $value%", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value,
            onValueChange = { value = it },
            valueRange = 0..100,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(currentColor)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = String.format("#%08X", currentColor.toArgb()),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
