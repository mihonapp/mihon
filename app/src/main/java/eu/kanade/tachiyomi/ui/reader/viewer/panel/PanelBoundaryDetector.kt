package eu.kanade.tachiyomi.ui.reader.viewer.panel

import kotlin.math.abs

object PanelBoundaryDetector {

    private const val INK_LUMINANCE_DELTA = 40
    private const val MIN_PANEL_AREA_FRACTION = 0.02f
    private const val NOISE_AREA_FRACTION = 0.0005f

    fun detect(buffer: PixelBuffer): List<PanelRect> {
        if (buffer.width < 3 || buffer.height < 3) return emptyList()

        val background = estimateBackgroundLuminance(buffer)
        val isInk = BooleanArray(buffer.width * buffer.height) { i ->
            val x = i % buffer.width
            val y = i / buffer.width
            abs(buffer.luminanceAt(x, y) - background) > INK_LUMINANCE_DELTA
        }

        val eroded = erode(isInk, buffer.width, buffer.height)
        val pageArea = buffer.width * buffer.height
        val noiseFloor = NOISE_AREA_FRACTION * pageArea
        val minArea = MIN_PANEL_AREA_FRACTION * pageArea

        return connectedComponentBoxes(eroded, buffer.width, buffer.height)
            .filter { box -> boxArea(box) >= noiseFloor }
            .filter { box -> boxArea(box) >= minArea }
            .map { box ->
                PanelRect(
                    left = box.left / buffer.width.toFloat(),
                    top = box.top / buffer.height.toFloat(),
                    right = (box.right + 1) / buffer.width.toFloat(),
                    bottom = (box.bottom + 1) / buffer.height.toFloat(),
                )
            }
    }

    private fun estimateBackgroundLuminance(buffer: PixelBuffer): Int {
        val samples = mutableListOf<Int>()
        for (x in 0 until buffer.width) {
            samples += buffer.luminanceAt(x, 0)
            samples += buffer.luminanceAt(x, buffer.height - 1)
        }
        for (y in 0 until buffer.height) {
            samples += buffer.luminanceAt(0, y)
            samples += buffer.luminanceAt(buffer.width - 1, y)
        }
        samples.sort()
        return samples[samples.size / 2]
    }

    private fun erode(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val out = BooleanArray(mask.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                if (!mask[i]) continue
                var allInk = true
                loop@ for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height || !mask[ny * width + nx]) {
                            allInk = false
                            break@loop
                        }
                    }
                }
                out[i] = allInk
            }
        }
        return out
    }

    private class MutableBox(var left: Int, var top: Int, var right: Int, var bottom: Int)

    private fun boxArea(box: MutableBox): Int = (box.right - box.left + 1) * (box.bottom - box.top + 1)

    private fun connectedComponentBoxes(mask: BooleanArray, width: Int, height: Int): List<MutableBox> {
        val visited = BooleanArray(mask.size)
        val boxes = mutableListOf<MutableBox>()
        val stack = ArrayDeque<Int>()

        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            visited[start] = true
            stack.addLast(start)
            val box = MutableBox(start % width, start / width, start % width, start / width)

            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                val x = i % width
                val y = i / width
                if (x < box.left) box.left = x
                if (x > box.right) box.right = x
                if (y < box.top) box.top = y
                if (y > box.bottom) box.bottom = y

                if (x > 0) tryVisit(i - 1, mask, visited, stack)
                if (x < width - 1) tryVisit(i + 1, mask, visited, stack)
                if (y > 0) tryVisit(i - width, mask, visited, stack)
                if (y < height - 1) tryVisit(i + width, mask, visited, stack)
            }
            boxes += box
        }
        return boxes
    }

    private fun tryVisit(i: Int, mask: BooleanArray, visited: BooleanArray, stack: ArrayDeque<Int>) {
        if (mask[i] && !visited[i]) {
            visited[i] = true
            stack.addLast(i)
        }
    }
}
