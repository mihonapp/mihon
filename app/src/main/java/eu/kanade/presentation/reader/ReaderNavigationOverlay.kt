package eu.kanade.presentation.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.DisabledNavigation
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderNavigationOverlay(
    state: ReaderNavigationOverlayState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.visible,
        modifier = modifier,
        enter = fadeIn(tween(FADE_DURATION)),
        exit = fadeOut(tween(FADE_DURATION)),
    ) {
        val regions = state.regions
        val labels = regions.map { stringResource(it.type.nameRes) }
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val textStyle = remember(density) {
            TextStyle(color = Color.White, fontSize = with(density) { 64f.toSp() })
        }
        val borderStyle = remember(textStyle) {
            textStyle.copy(color = Color.Black, drawStyle = Stroke(width = 8f))
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            regions.forEachIndexed { index, region ->
                val rect = region.rect
                val topLeft = Offset(size.width * rect.left, size.height * rect.top)
                val regionSize = Size(size.width * rect.width, size.height * rect.height)
                drawRect(color = region.type.color, topLeft = topLeft, size = regionSize)

                val text = textMeasurer.measure(labels[index], textStyle)
                val textTopLeft = Offset(
                    x = topLeft.x + (regionSize.width - text.size.width) / 2,
                    y = topLeft.y + (regionSize.height - text.size.height) / 2,
                )
                drawText(textMeasurer, labels[index], textTopLeft, borderStyle)
                drawText(text, topLeft = textTopLeft)
            }
        }
    }
}

@Stable
class ReaderNavigationOverlayState {

    internal var regions by mutableStateOf(emptyList<ViewerNavigation.Region>())
    internal var visible by mutableStateOf(false)

    private var navigation: ViewerNavigation? = null

    fun setNavigation(navigation: ViewerNavigation, showOnStart: Boolean) {
        val firstLaunch = this.navigation == null
        this.navigation = navigation
        regions = navigation.getRegions()

        if (visible || (!showOnStart && firstLaunch) || navigation is DisabledNavigation) {
            return
        }
        visible = true
    }

    fun hide() {
        visible = false
    }
}

private const val FADE_DURATION = 1000
