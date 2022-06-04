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
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R

enum class MangaCover(private val ratio: Float) {
    Square(1f / 1f),
    Book(2f / 3f);

    @Composable
    operator fun invoke(
        modifier: Modifier = Modifier,
        data: String?,
        contentDescription: String? = null,
        shape: Shape? = null,
    ) {
        AsyncImage(
            model = data,
            placeholder = ColorPainter(CoverPlaceholderColor),
            error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
            contentDescription = contentDescription,
            modifier = modifier
                .aspectRatio(ratio)
                .clip(shape ?: RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

private val CoverPlaceholderColor = Color(0x1F888888)
