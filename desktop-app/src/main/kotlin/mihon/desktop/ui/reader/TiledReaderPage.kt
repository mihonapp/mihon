package mihon.desktop.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import mihon.desktop.reader.DesktopReaderContent
import mihon.desktop.reader.DesktopReaderPageFrame
import mihon.desktop.reader.DesktopReaderTileSet
import mihon.reader.model.PageId
import kotlin.math.roundToInt

@Composable
internal fun TiledReaderPage(
    content: DesktopReaderContent,
    pageId: PageId,
    fallback: DesktopReaderPageFrame,
    context: ReaderPageRenderContext,
    colorFilter: ColorFilter?,
    modifier: Modifier,
) {
    var tileSet by remember(pageId) { mutableStateOf<DesktopReaderTileSet?>(null) }
    LaunchedEffect(content, pageId, context.visibleImageBounds, context.sampleSize) {
        val replacement = content.loadRegionTiles(pageId, context.visibleImageBounds, context.sampleSize)
        val previous = tileSet
        tileSet = replacement
        previous?.close()
    }
    DisposableEffect(content, pageId) {
        onDispose {
            tileSet?.close()
            tileSet = null
            content.releaseRegionTiles(pageId)
        }
    }

    Canvas(modifier) {
        drawImage(
            image = fallback.tile.image,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            colorFilter = colorFilter,
        )
        tileSet?.tiles?.forEach { region ->
            drawRegion(
                image = region.tile.image,
                left = region.bounds.left,
                top = region.bounds.top,
                width = region.bounds.width,
                height = region.bounds.height,
                context = context,
                colorFilter = colorFilter,
            )
        }
    }
}

private fun DrawScope.drawRegion(
    image: androidx.compose.ui.graphics.ImageBitmap,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    context: ReaderPageRenderContext,
    colorFilter: ColorFilter?,
) {
    val scaleX = size.width / context.imageWidth
    val scaleY = size.height / context.imageHeight
    drawImage(
        image = image,
        dstOffset = IntOffset((left * scaleX).roundToInt(), (top * scaleY).roundToInt()),
        dstSize = IntSize((width * scaleX).roundToInt(), (height * scaleY).roundToInt()),
        colorFilter = colorFilter,
    )
}
