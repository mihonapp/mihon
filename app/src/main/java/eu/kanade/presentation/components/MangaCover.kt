package eu.kanade.presentation.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

enum class MangaCoverAspect(val ratio: Float) {
    SQUARE(1f / 1f),
    COVER(2f / 3f)
}

@Composable
fun MangaCover(
    modifier: Modifier = Modifier,
    data: String?,
    aspect: MangaCoverAspect,
    contentDescription: String = "",
    shape: Shape = RoundedCornerShape(4.dp)
) {
    AsyncImage(
        model = data,
        placeholder = ColorPainter(CoverPlaceholderColor),
        contentDescription = contentDescription,
        modifier = modifier
            .aspectRatio(aspect.ratio)
            .clip(shape),
        contentScale = ContentScale.Crop
    )
}

private val CoverPlaceholderColor = Color(0x1F888888)
