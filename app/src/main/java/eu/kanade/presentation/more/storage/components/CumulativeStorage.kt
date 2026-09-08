package eu.kanade.presentation.more.storage.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.Layout
import eu.kanade.presentation.more.storage.data.StorageData
import eu.kanade.tachiyomi.util.storage.toSize

@Composable
fun CumulativeStorage(
    items: List<StorageData>,
    modifier: Modifier = Modifier,
    borderWidth: Float = 15f,
) {
    val totalSize = remember(items) {
        items.sumOf { it.size }.toFloat()
    }
    val totalSizeString = remember(totalSize) {
        totalSize.toLong().toSize()
    }
    Layout(
        modifier = modifier,
        content = {
            Canvas(
                modifier = Modifier.aspectRatio(1f),
                onDraw = {
                    val totalAngle = 180f
                    var currentAngle = 0f
                    rotate(180f) {
                        for (item in items) {
                            val itemAngle = (item.size / totalSize) * totalAngle
                            drawArc(
                                color = item.color,
                                startAngle = currentAngle,
                                sweepAngle = itemAngle,
                                useCenter = false,
                                style = Stroke(width = borderWidth, cap = StrokeCap.Round),
                            )
                            currentAngle += itemAngle
                        }
                    }
                },
            )
            Text(
                text = totalSizeString,
                style = MaterialTheme.typography.displaySmall,
            )
        },
        measurePolicy = { measurables, constraints ->
            val placeables = measurables.map { measurable ->
                measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            }
            val canvas = placeables.first()
            val text = placeables.last()
            // use half the height of the canvas to avoid too much extra space
            layout(constraints.maxWidth, canvas.height / 2) {
                canvas.placeRelative(0, 0)
                text.placeRelative(
                    (canvas.width / 2) - (text.width / 2),
                    (canvas.height / 4) - (text.height / 2),
                )
            }
        },
    )
}
