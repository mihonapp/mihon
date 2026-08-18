package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.ui.reader.viewer.panel.PanelRect

/**
 * Dims everything outside [targetRect] on top of [sourceView], so the panel currently being read
 * in panel-by-panel mode stands out even when neighboring content bleeds into view (a small merged
 * panel, the breathing-room padding around a panel's edges, or a stop that isn't zoomed all the
 * way in). This view draws nothing on its own — the whole page still renders on [sourceView]
 * underneath; this only adds the scrim + cutout on top.
 *
 * [targetRect] is normalized page coordinates. It's mapped to the live on-screen rect via
 * [SubsamplingScaleImageView.sourceToViewCoord] fresh on every draw, so the cutout tracks pan/zoom
 * — including a guided animation still in flight — without needing any animation logic of its own.
 * The view's own [alpha] (driven externally) is what fades the whole scrim in and out; [opacityPercent]
 * is the separate, user-configured strength of the scrim itself once shown.
 */
class PanelSpotlightOverlay(context: Context) : View(context) {

    var sourceView: SubsamplingScaleImageView? = null

    var targetRect: PanelRect? = null
        set(value) {
            field = value
            invalidate()
        }

    /** User-configured scrim strength, 0 (transparent, effectively off) to 100 (opaque black). */
    var opacityPercent: Int = DEFAULT_OPACITY_PERCENT
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        val view = sourceView ?: return
        val rect = targetRect ?: return
        // A stop covering (close to) the whole page has nothing meaningful to dim.
        if (rect.width >= FULL_PAGE_THRESHOLD && rect.height >= FULL_PAGE_THRESHOLD) return
        if (opacityPercent <= 0) return

        val topLeft = view.sourceToViewCoord(rect.left * view.sWidth, rect.top * view.sHeight) ?: return
        val bottomRight = view.sourceToViewCoord(rect.right * view.sWidth, rect.bottom * view.sHeight) ?: return

        val alpha = (opacityPercent * 255 / 100).coerceIn(0, 255)
        canvas.save()
        canvas.clipOutRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
        canvas.drawColor(alpha shl 24)
        canvas.restore()
    }

    companion object {
        private const val FULL_PAGE_THRESHOLD = 0.98f
        const val DEFAULT_OPACITY_PERCENT = 65
    }
}
