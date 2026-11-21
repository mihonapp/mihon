package eu.kanade.tachiyomi.ui.reader.viewer.compose.webtoon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import eu.kanade.tachiyomi.ui.reader.viewer.LocalPageColorFilter
import eu.kanade.tachiyomi.ui.reader.viewer.compose.PageImageSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

// Each band decodes to about this many bytes, so it uploads to the GPU quickly and never exceeds a texture
private const val BAND_BYTES = 4 * 1024 * 1024

/**
 * Where a page is relative to the screen, in the page's own pixels.
 */
sealed interface PageWindow {
    /** The page is on screen; [top] to [bottom] of it is visible, each of its pixels taking [scale] on screen. */
    data class Visible(val top: Float, val bottom: Float, val scale: Float) : PageWindow

    /** The page is off screen, below it when [isBelow] or else above, so the edge nearest the screen loads ahead. */
    data class Near(val isBelow: Boolean, val screenHeight: Float, val scale: Float) : PageWindow
}

/**
 * A page fitted to the width, drawn from horizontal bands of [source] decoded at the resolution [window]'s zoom
 * needs. Only the bands on screen or within half a screen of it are kept, so a long strip never exists as one
 * bitmap, and coarser bands stay drawn while finer ones load. [onShown] is called once the first band is drawn.
 */
@Composable
fun BandedPageImage(
    source: PageImageSource,
    window: () -> PageWindow?,
    onShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageSize = source.size
    val colorFilter = LocalPageColorFilter.current
    var width by remember { mutableIntStateOf(0) }
    var bands by remember(source) { mutableStateOf(emptyMap<Band, ImageBitmap>()) }

    LaunchedEffect(source) {
        val jobs = mutableMapOf<Band, Job>()
        coroutineScope {
            snapshotFlow { width.takeIf { it > 0 }?.let { neededBands(imageSize, it, window()) } }
                .distinctUntilChanged()
                .collect { needed ->
                    needed ?: return@collect
                    val (wanted, kept) = needed
                    for (band in wanted) {
                        if (band in bands || band in jobs) continue
                        jobs[band] = launch {
                            val bitmap = source.decode(band.region(imageSize), band.sample, band.width(imageSize))
                            jobs.remove(band)
                            if (bitmap != null) {
                                bands = (bands + (band to bitmap.asImageBitmap())).dropOtherLevels(wanted)
                                onShown()
                            }
                        }
                    }
                    jobs.keys.filter { it !in kept }.forEach { jobs.remove(it)?.cancel() }
                    bands = bands.filterKeys { it in kept || !it.isLevelOf(wanted.first()) }.dropOtherLevels(wanted)
                }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(imageSize.width.toFloat() / imageSize.height)
            .onSizeChanged { width = it.width }
            .drawBehind {
                val scale = size.width / imageSize.width
                // Coarser first, so finer bands draw over them
                bands.entries.sortedBy { it.key.width(imageSize) }.forEach { (band, image) ->
                    val region = band.region(imageSize)
                    // Both edges rounded, so the last band ends with the page
                    val top = (region.top * scale).roundToInt()
                    val bottom = (region.bottom * scale).roundToInt()
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(0, top),
                        dstSize = IntSize(size.width.roundToInt(), bottom - top),
                        colorFilter = colorFilter,
                        filterQuality = FilterQuality.Low,
                    )
                }
            },
    )
}

/**
 * The [index]th band of [rows] source rows, decoded shrunk by [sample] and then by a quarter octave per [level], so
 * it's drawn at most slightly shrunk: the GPU's shrinking skips texels and turns screentone into moiré.
 */
private data class Band(val sample: Int, val level: Int, val index: Int, val rows: Int) {
    fun width(imageSize: IntSize) = ceil(imageSize.width / (sample * 2f.pow(level / 4f))).toInt()

    fun isLevelOf(other: Band) = sample == other.sample && level == other.level

    /**
     * The band's rows and a few of its neighbours', so bands overlap and a zoom that puts their edges between
     * screen pixels never shows the background through the seam.
     */
    fun region(imageSize: IntSize): IntRect {
        val overlap = OVERLAP_ROWS * sample
        return IntRect(
            left = 0,
            top = (index * rows - overlap).coerceAtLeast(0),
            right = imageSize.width,
            bottom = minOf((index + 1) * rows + overlap, imageSize.height),
        )
    }
}

// Decoded rows each band shares with the next, at least a screen pixel at any zoom since sample follows zoom
private const val OVERLAP_ROWS = 4

/**
 * The bands to load for [window] and the wider set to keep, all at the sample its zoom needs.
 */
private fun neededBands(imageSize: IntSize, width: Int, window: PageWindow?): Pair<List<Band>, Set<Band>>? {
    window ?: return null
    val pageScale = width.toFloat() / imageSize.width
    val height = imageSize.height * pageScale
    val (top, bottom, scale) = when (window) {
        is PageWindow.Visible -> Triple(window.top, window.bottom, window.scale)
        is PageWindow.Near -> {
            val screen = window.screenHeight / window.scale
            if (window.isBelow) Triple(0f, screen, window.scale) else Triple(height - screen, height, window.scale)
        }
    }
    // The coarsest power of two that still gives every screen pixel a source pixel
    val screenPerSource = pageScale * scale
    var sample = 1
    while (sample * 2 <= 1 / screenPerSource) sample *= 2

    val decodedWidth = ceil(imageSize.width.toFloat() / sample).toInt()
    // The most quarter octaves below the decoded width that still give every screen pixel a pixel
    val level = (log2(decodedWidth / (imageSize.width * screenPerSource)) * 4).toInt().coerceIn(0, 3)
    val rows = (BAND_BYTES / (decodedWidth * 4)).coerceAtLeast(1) * sample
    val margin = (bottom - top) / 2
    fun bandsBetween(from: Float, to: Float): IntRange {
        val first = ((from / pageScale).toInt() / rows).coerceAtLeast(0)
        val last = ((to / pageScale).toInt() / rows).coerceAtMost((imageSize.height - 1) / rows)
        return first..last
    }
    // Nearest the middle of the window first, as each page decodes its bands one at a time
    val middle = (top + bottom) / 2 / pageScale
    val wanted = bandsBetween(top - margin, bottom + margin).map { Band(sample, level, it, rows) }
        .sortedBy { abs((it.index + 0.5f) * rows - middle) }
    if (wanted.isEmpty()) return null
    val kept = bandsBetween(top - margin * 3, bottom + margin * 3).map { Band(sample, level, it, rows) }.toSet()
    return wanted to kept
}

/**
 * Drops the bands of other levels once the [wanted] ones are all loaded, as they're only drawn until then.
 */
private fun Map<Band, ImageBitmap>.dropOtherLevels(wanted: List<Band>): Map<Band, ImageBitmap> {
    val level = wanted.first()
    return if (wanted.all { it in this }) filterKeys { it.isLevelOf(level) } else this
}
