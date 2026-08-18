package eu.kanade.tachiyomi.ui.reader.viewer.panel

/**
 * Letterbox geometry for fitting a [pageW]×[pageH] image into a square `inputSize` model input,
 * preserving aspect with centered gray padding (YOLO's standard preprocessing). Used to build the
 * model input *and* to undo the transform when mapping detections back to the page, so both steps
 * stay consistent.
 */
data class Letterbox(
    val scale: Float,
    val padX: Int,
    val padY: Int,
    val newW: Int,
    val newH: Int,
) {
    companion object {
        fun fit(pageW: Int, pageH: Int, inputSize: Int): Letterbox {
            val scale = minOf(inputSize / pageW.toFloat(), inputSize / pageH.toFloat())
            val newW = (pageW * scale).toInt().coerceAtLeast(1)
            val newH = (pageH * scale).toInt().coerceAtLeast(1)
            return Letterbox(scale, (inputSize - newW) / 2, (inputSize - newH) / 2, newW, newH)
        }
    }
}

/** Detected panels and speech bubbles in normalized page coordinates, plus the page pixel size. */
data class DetectResult(
    val panels: List<PanelRect>,
    val bubbles: List<PanelRect>,
    val pageW: Int,
    val pageH: Int,
)

/**
 * Decodes a YOLO panel/text detector's raw output tensor into [PanelRect]s in normalized page
 * coordinates. Class 0 = Panel, class 1 = Text/speech-balloon.
 *
 * Handles the two common output layouts:
 *  - end-to-end (NMS-free, e.g. YOLO26): `[1, numDet, 6]` rows of `[x1,y1,x2,y2,score,cls]`.
 *  - raw: `[1, 4+nc, anchors]` or `[1, anchors, 4+nc]` of `[cx,cy,w,h,cls0,cls1]` → filter + NMS.
 * Coordinates may be normalized (≤1) or in input pixels; both are detected and handled.
 */
class YoloPanelDecoder(
    val inputSize: Int = DEFAULT_INPUT_SIZE,
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE,
    private val nmsIoU: Float = DEFAULT_NMS_IOU,
    private val containmentThreshold: Float = DEFAULT_CONTAINMENT,
    private val minAreaFraction: Float = DEFAULT_MIN_AREA_FRACTION,
) {

    fun decode(raw: FloatArray, shape: IntArray, lb: Letterbox, pageW: Int, pageH: Int): DetectResult {
        if (shape.size != 3) return DetectResult(emptyList(), emptyList(), pageW, pageH)
        val d1 = shape[1]
        val d2 = shape[2]
        val transposed = d1 < d2 // [1, attrs, anchors]
        val attrs = if (transposed) d1 else d2
        val preds = if (transposed) d2 else d1
        fun at(pred: Int, attr: Int) = if (transposed) raw[attr * preds + pred] else raw[pred * attrs + attr]

        if (attrs < 6) return DetectResult(emptyList(), emptyList(), pageW, pageH)

        val endToEnd = preds <= 1000

        // Detect coordinate normalization by peeking at a few values.
        var maxCoord = 0f
        var sampled = 0
        var p = 0
        while (p < preds && sampled < 64) {
            val v = maxOf(at(p, 0), at(p, 1), at(p, 2), at(p, 3))
            if (v.isFinite()) { maxCoord = maxOf(maxCoord, v); sampled++ }
            p++
        }
        val coordScale = if (maxCoord <= 1.5f) inputSize.toFloat() else 1f

        val panelBoxes = ArrayList<FloatArray>() // x1,y1,x2,y2,score (input-pixel space)
        val bubbleBoxes = ArrayList<FloatArray>()

        for (i in 0 until preds) {
            val cls: Int
            val score: Float
            if (endToEnd) {
                score = at(i, 4)
                cls = at(i, 5).toInt()
            } else {
                val cls0 = at(i, 4); val cls1 = at(i, 5)
                if (cls0 >= cls1) { cls = PANEL_CLASS; score = cls0 } else { cls = TEXT_CLASS; score = cls1 }
            }
            if (score < confidenceThreshold || (cls != PANEL_CLASS && cls != TEXT_CLASS)) continue

            val a = at(i, 0) * coordScale
            val b = at(i, 1) * coordScale
            val c = at(i, 2) * coordScale
            val d = at(i, 3) * coordScale
            val x1: Float; val y1: Float; val x2: Float; val y2: Float
            if (endToEnd) { x1 = a; y1 = b; x2 = c; y2 = d } // xyxy
            else { x1 = a - c / 2f; y1 = b - d / 2f; x2 = a + c / 2f; y2 = b + d / 2f } // cxcywh
            val box = floatArrayOf(x1, y1, x2, y2, score)
            if (cls == PANEL_CLASS) panelBoxes.add(box) else bubbleBoxes.add(box)
        }

        // Suppress overlapping/nested duplicates within each class.
        val panels = toPanels(suppress(panelBoxes), lb, pageW, pageH, minAreaFraction)
        val bubbles = toPanels(suppress(bubbleBoxes), lb, pageW, pageH, 0f)
        return DetectResult(panels, bubbles, pageW, pageH)
    }

    /** Filters by min area, undoes the letterbox, and normalizes boxes to [0,1] page coordinates. */
    private fun toPanels(
        boxes: List<FloatArray>,
        lb: Letterbox,
        pageW: Int,
        pageH: Int,
        minAreaFrac: Float,
    ): List<PanelRect> {
        val minArea = minAreaFrac * inputSize * inputSize
        return boxes.mapNotNull { box ->
            val w = (box[2] - box[0]).coerceAtLeast(0f)
            val h = (box[3] - box[1]).coerceAtLeast(0f)
            if (w * h < minArea) return@mapNotNull null
            val l = ((box[0] - lb.padX) / lb.scale / pageW).coerceIn(0f, 1f)
            val t = ((box[1] - lb.padY) / lb.scale / pageH).coerceIn(0f, 1f)
            val r = ((box[2] - lb.padX) / lb.scale / pageW).coerceIn(0f, 1f)
            val bo = ((box[3] - lb.padY) / lb.scale / pageH).coerceIn(0f, 1f)
            if (r > l && bo > t) PanelRect(l, t, r, bo) else null
        }
    }

    /**
     * Greedy suppression by confidence: a box is dropped if it overlaps an already-kept box too
     * much (IoU) or is largely contained within one. Removes duplicate detections and panels nested
     * inside a larger panel.
     */
    private fun suppress(boxes: List<FloatArray>): List<FloatArray> {
        val sorted = boxes.sortedByDescending { it[4] }
        val kept = ArrayList<FloatArray>()
        for (box in sorted) {
            val redundant = kept.any { iou(it, box) > nmsIoU || containedFraction(box, it) > containmentThreshold }
            if (redundant) continue
            // A larger box can arrive after a smaller one it engulfs (the smaller scored higher), so
            // also evict any already-kept boxes now nested inside this one — we never keep a panel
            // inside another panel, whatever order they're processed in.
            kept.removeAll { containedFraction(it, box) > containmentThreshold }
            kept.add(box)
        }
        return kept
    }

    /** Fraction of [inner]'s area that lies inside [outer]. */
    private fun containedFraction(inner: FloatArray, outer: FloatArray): Float {
        val ix = (minOf(inner[2], outer[2]) - maxOf(inner[0], outer[0])).coerceAtLeast(0f)
        val iy = (minOf(inner[3], outer[3]) - maxOf(inner[1], outer[1])).coerceAtLeast(0f)
        val inter = ix * iy
        val innerArea = (inner[2] - inner[0]) * (inner[3] - inner[1])
        return if (innerArea <= 0f) 0f else inter / innerArea
    }

    private fun iou(a: FloatArray, b: FloatArray): Float {
        val ix = (minOf(a[2], b[2]) - maxOf(a[0], b[0])).coerceAtLeast(0f)
        val iy = (minOf(a[3], b[3]) - maxOf(a[1], b[1])).coerceAtLeast(0f)
        val inter = ix * iy
        val areaA = (a[2] - a[0]) * (a[3] - a[1])
        val areaB = (b[2] - b[0]) * (b[3] - b[1])
        val union = areaA + areaB - inter
        return if (union <= 0f) 0f else inter / union
    }

    companion object {
        const val PANEL_CLASS = 0
        const val TEXT_CLASS = 1

        const val DEFAULT_INPUT_SIZE = 640
        const val DEFAULT_CONFIDENCE = 0.25f
        const val DEFAULT_NMS_IOU = 0.45f
        const val DEFAULT_CONTAINMENT = 0.6f
        const val DEFAULT_MIN_AREA_FRACTION = 0.008f
    }
}
