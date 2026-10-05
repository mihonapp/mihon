package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.view.View
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import tachiyomi.core.common.util.system.panel.PanelRect

/**
 * Overlay that paints everything outside [region] (normalised to [ssiv]'s source space) in [color].
 * It draws nothing when [region] is null or [ssiv] isn't ready, and never handles touches.
 */
class PanelMaskView(context: Context) : View(context) {

    var ssiv: SubsamplingScaleImageView? = null

    var region: PanelRect? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var color: Int
        get() = paint.color
        set(value) {
            if (paint.color == value) return
            paint.color = value
            invalidate()
        }

    private val paint = Paint().apply { style = Paint.Style.FILL }

    private val topLeft = PointF()
    private val bottomRight = PointF()

    init {
        isClickable = false
        isFocusable = false
        isLongClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val rect = region ?: return
        val view = ssiv?.takeIf { it.isReady } ?: return
        val sW = view.sWidth.toFloat()
        val sH = view.sHeight.toFloat()
        view.sourceToViewCoord(rect.left * sW, rect.top * sH, topLeft) ?: return
        view.sourceToViewCoord(rect.right * sW, rect.bottom * sH, bottomRight) ?: return

        val w = width.toFloat()
        val h = height.toFloat()
        val left = topLeft.x.coerceIn(0f, w)
        val top = topLeft.y.coerceIn(0f, h)
        val right = bottomRight.x.coerceIn(left, w)
        val bottom = bottomRight.y.coerceIn(top, h)

        canvas.drawRect(0f, 0f, w, top, paint)
        canvas.drawRect(0f, bottom, w, h, paint)
        canvas.drawRect(0f, top, left, bottom, paint)
        canvas.drawRect(right, top, w, bottom, paint)
    }
}
