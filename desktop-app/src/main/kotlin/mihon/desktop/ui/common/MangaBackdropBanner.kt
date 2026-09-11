package mihon.desktop.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mihon.desktop.image.ImageRequest
import mihon.desktop.image.LocalImageLoader
import java.nio.file.Path

/**
 * Pixel-level recreation of Mihon Android's signature Manga backdrop banner.
 * Uses the manga cover with Skia hardware Gaussian blur and a multi-stop vertical gradient scrim
 * that fades smoothly into the active Material 3 surface/background color.
 */
@Composable
fun MangaBackdropBanner(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    mangaId: Long? = null,
    localMangaPath: Path? = null,
    bannerHeight: Dp = 280.dp,
) {
    val imageLoader = LocalImageLoader.current
    var bitmap by remember(thumbnailUrl, mangaId, localMangaPath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(thumbnailUrl, mangaId, localMangaPath, imageLoader) {
        if (imageLoader != null) {
            bitmap = imageLoader.load(
                ImageRequest(
                    uri = thumbnailUrl,
                    mangaId = mangaId,
                    localMangaPath = localMangaPath,
                ),
            )
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bannerHeight)
            .clipToBounds(),
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(16.dp)
                    .alpha(0.28f),
            )
        }
        // Multi-stop gradient scrim to blend into the theme surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            surfaceColor.copy(alpha = 0.45f),
                            surfaceColor.copy(alpha = 0.85f),
                            surfaceColor,
                        ),
                    ),
                ),
        )
    }
}
