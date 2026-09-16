package mihon.desktop.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.reader.image.TileKey
import mihon.reader.model.FrameId
import java.awt.image.BufferedImage

data class ReaderAnimatedFrame(
    val key: TileKey,
    val image: BufferedImage,
    val lease: AutoCloseable? = null,
) {
    init {
        require(key.frameId != null) { "animated frames require a frame TileKey" }
    }
}

interface AnimationVisibilityReporter {
    fun setContentVisible(visible: Boolean)

    fun setForeground(foreground: Boolean)
}

/**
 * Displays the frame selected by reader-core. This composable never advances time or chooses a
 * frame; it only converts a changed [selectedFrame], atomically publishes the ready replacement,
 * and reports UI visibility back to the core-owned coordinator.
 */
@Composable
fun AnimatedPage(
    selectedFrame: FrameId,
    loadFrame: suspend (FrameId) -> ReaderAnimatedFrame,
    bridge: ComposeTileBridge,
    contentVisible: Boolean,
    foreground: Boolean,
    visibilityReporter: AnimationVisibilityReporter,
    modifier: Modifier = Modifier,
) {
    var current by remember(bridge) { mutableStateOf<ComposeTileBridge.BridgeTile?>(null) }
    val strings = LocalStrings.current
    var renderedFrame by remember(bridge) { mutableStateOf<FrameId?>(null) }

    LaunchedEffect(visibilityReporter, contentVisible) {
        visibilityReporter.setContentVisible(contentVisible)
    }
    LaunchedEffect(visibilityReporter, foreground) {
        visibilityReporter.setForeground(foreground)
    }
    LaunchedEffect(selectedFrame, contentVisible, bridge) {
        if (!contentVisible) {
            current?.close()
            current = null
            renderedFrame = null
            return@LaunchedEffect
        }
        val decoded = loadFrame(selectedFrame)
        val replacement = try {
            bridge.acquire(decoded.key, decoded.image)
        } finally {
            decoded.lease?.close()
        }
        val previous = current
        current = replacement
        renderedFrame = selectedFrame
        previous?.close()
    }
    DisposableEffect(bridge, visibilityReporter) {
        onDispose {
            current?.close()
            current = null
            visibilityReporter.setContentVisible(false)
        }
    }

    Box(
        modifier = modifier
            .testTag("reader-animated-page")
            .semantics {
                contentDescription = strings.text(UiText.AnimatedFrame, renderedFrame?.frameIndex ?: 0)
                readerFrameIndex = renderedFrame?.frameIndex ?: 0
            },
        contentAlignment = Alignment.Center,
    ) {
        current?.let { frame ->
            Image(
                bitmap = frame.image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
