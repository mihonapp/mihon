package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.graphics.RectF
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

class PanelDetector {

    data class Panel(
        val rect: RectF, // Normalized coordinates (0.0 .. 1.0)
        val centroid: PointF
    )

    fun detectPanels(bitmap: Bitmap, isRightToLeft: Boolean): List<Panel> {
        val w = bitmap.width
        val h = bitmap.height
        val size = w * h
        val pixels = IntArray(size)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 1. Determine background color type by sampling borders
        var totalBrightness = 0L
        var borderCount = 0
        val sampleStep = 5

        // Top/Bottom edges
        for (x in 0 until w step sampleStep) {
            totalBrightness += getBrightness(pixels[x])
            totalBrightness += getBrightness(pixels[(h - 1) * w + x])
            borderCount += 2
        }
        // Left/Right edges
        for (y in 0 until h step sampleStep) {
            totalBrightness += getBrightness(pixels[y * w])
            totalBrightness += getBrightness(pixels[y * w + (w - 1)])
            borderCount += 2
        }

        val avgBorderBrightness = if (borderCount > 0) totalBrightness / borderCount else 255L
        val isDarkBg = avgBorderBrightness < 120

        // 2. Identify gutter/background vs content/border candidates
        val isGutterCandidate = BooleanArray(size)
        if (isDarkBg) {
            val threshold = max(35L, avgBorderBrightness + 20).toInt()
            for (i in 0 until size) {
                isGutterCandidate[i] = getBrightness(pixels[i]) <= threshold
            }
        } else {
            val threshold = min(220L, avgBorderBrightness - 20).toInt()
            for (i in 0 until size) {
                isGutterCandidate[i] = getBrightness(pixels[i]) >= threshold
            }
        }

        // Perform adaptive dilation on content (non-gutter) pixels to close small gaps
        val R = if (isDarkBg) 2 else 1
        val finalGutterCandidate = isGutterCandidate.clone()
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!isGutterCandidate[y * w + x]) { // Content
                    for (dy_val in -R..R) {
                        for (dx_val in -R..R) {
                            val nx = x + dx_val
                            val ny = y + dy_val
                            if (nx in 0 until w && ny in 0 until h) {
                                finalGutterCandidate[ny * w + nx] = false
                            }
                        }
                    }
                }
            }
        }

        // 3. Flood fill from borders to identify outer margins and gutters
        val visited = BooleanArray(size)
        val queueX = IntArray(size)
        val queueY = IntArray(size)
        var head = 0
        var tail = 0

        fun enqueue(x: Int, y: Int) {
            val idx = y * w + x
            if (!visited[idx]) {
                visited[idx] = true
                queueX[tail] = x
                queueY[tail] = y
                tail++
            }
        }

        // Enqueue all border pixels that are gutter candidates
        for (x in 0 until w) {
            if (finalGutterCandidate[x]) enqueue(x, 0)
            if (finalGutterCandidate[(h - 1) * w + x]) enqueue(x, h - 1)
        }
        for (y in 0 until h) {
            if (finalGutterCandidate[y * w]) enqueue(0, y)
            if (finalGutterCandidate[y * w + (w - 1)]) enqueue(w - 1, y)
        }

        val dx = intArrayOf(0, 0, -1, 1)
        val dy = intArrayOf(-1, 1, 0, 0)

        // BFS to propagate gutter pixels
        while (head < tail) {
            val cx = queueX[head]
            val cy = queueY[head]
            head++

            for (i in 0 until 4) {
                val nx = cx + dx[i]
                val ny = cy + dy[i]
                if (nx in 0 until w && ny in 0 until h) {
                    val nIdx = ny * w + nx
                    if (!visited[nIdx] && finalGutterCandidate[nIdx]) {
                        visited[nIdx] = true
                        queueX[tail] = nx
                        queueY[tail] = ny
                        tail++
                    }
                }
            }
        }

        // 4. Group remaining unvisited pixels (panels and content) into connected components
        val componentId = IntArray(size) { -1 }
        var currentCompId = 0
        val panels = mutableListOf<Panel>()

        // Preallocate reuse queue arrays for finding components to avoid gc pressure
        val compQX = IntArray(size)
        val compQY = IntArray(size)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                // If it is content (not gutter) and not yet assigned to any component
                if (!visited[idx] && componentId[idx] == -1) {
                    var minX = x
                    var maxX = x
                    var minY = y
                    var maxY = y
                    var sumX = 0L
                    var sumY = 0L
                    var pixelCount = 0

                    var compHead = 0
                    var compTail = 0

                    compQX[compTail] = x
                    compQY[compTail] = y
                    componentId[idx] = currentCompId
                    compTail++

                    while (compHead < compTail) {
                        val cx = compQX[compHead]
                        val cy = compQY[compHead]
                        compHead++

                        pixelCount++
                        sumX += cx
                        sumY += cy
                        if (cx < minX) minX = cx
                        if (cx > maxX) maxX = cx
                        if (cy < minY) minY = cy
                        if (cy > maxY) maxY = cy

                        for (i in 0 until 4) {
                            val nx = cx + dx[i]
                            val ny = cy + dy[i]
                            if (nx in 0 until w && ny in 0 until h) {
                                val nIdx = ny * w + nx
                                if (!visited[nIdx] && componentId[nIdx] == -1) {
                                    componentId[nIdx] = currentCompId
                                    compQX[compTail] = nx
                                    compQY[compTail] = ny
                                    compTail++
                                }
                            }
                        }
                    }

                    val compWidth = maxX - minX + 1
                    val compHeight = maxY - minY + 1

                    // Validate component dimensions to exclude tiny specks, lone text lines or artifacts
                    // Must be at least 8% of image width/height and contain sufficient pixel volume
                    if (compWidth > w * 0.08 && compHeight > h * 0.08 && pixelCount > (w * h) * 0.005) {
                        val rect = RectF(
                            minX.toFloat() / w,
                            minY.toFloat() / h,
                            (maxX + 1).toFloat() / w,
                            (maxY + 1).toFloat() / h
                        )
                        val centroid = PointF(
                            (sumX.toFloat() / pixelCount) / w,
                            (sumY.toFloat() / pixelCount) / h
                        )
                        panels.add(Panel(rect, centroid))
                    }
                    currentCompId++
                }
            }
        }

        return sortPanels(panels, isRightToLeft)
    }

    private fun getBrightness(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    private fun sortPanels(panels: List<Panel>, isRightToLeft: Boolean): List<Panel> {
        if (panels.isEmpty()) return panels
        return xyCut(panels, isRightToLeft)
    }

    private fun xyCut(panels: List<Panel>, isRightToLeft: Boolean): List<Panel> {
        if (panels.size <= 1) return panels

        // Attempt horizontal cut
        val hCut = findHorizontalCut(panels)
        if (hCut != null) {
            val topGroup = panels.filter { it.rect.bottom <= hCut }
            val bottomGroup = panels.filter { it.rect.top >= hCut }
            if (topGroup.isNotEmpty() && bottomGroup.isNotEmpty()) {
                return xyCut(topGroup, isRightToLeft) + xyCut(bottomGroup, isRightToLeft)
            }
        }

        // Attempt vertical cut
        val vCut = findVerticalCut(panels)
        if (vCut != null) {
            val leftGroup = panels.filter { it.rect.right <= vCut }
            val rightGroup = panels.filter { it.rect.left >= vCut }
            if (leftGroup.isNotEmpty() && rightGroup.isNotEmpty()) {
                return if (isRightToLeft) {
                    xyCut(rightGroup, isRightToLeft) + xyCut(leftGroup, isRightToLeft)
                } else {
                    xyCut(leftGroup, isRightToLeft) + xyCut(rightGroup, isRightToLeft)
                }
            }
        }

        // Fallback to row-based sorting
        return fallbackSort(panels, isRightToLeft)
    }

    private fun findHorizontalCut(panels: List<Panel>): Float? {
        val yCoords = panels.flatMap { listOf(it.rect.top, it.rect.bottom) }.sorted().distinct()
        var bestCut: Float? = null
        var bestGap = -1f

        for (i in 0 until yCoords.size - 1) {
            val y1 = yCoords[i]
            val y2 = yCoords[i + 1]
            if (y2 - y1 < 0.001f) continue
            val midY = (y1 + y2) / 2f
            val hasOverlap = panels.any { it.rect.top < midY && midY < it.rect.bottom }
            if (!hasOverlap) {
                val above = panels.any { it.rect.bottom <= midY }
                val below = panels.any { it.rect.top >= midY }
                if (above && below) {
                    val gap = y2 - y1
                    if (gap > bestGap) {
                        bestGap = gap
                        bestCut = midY
                    }
                }
            }
        }
        return bestCut
    }

    private fun findVerticalCut(panels: List<Panel>): Float? {
        val xCoords = panels.flatMap { listOf(it.rect.left, it.rect.right) }.sorted().distinct()
        var bestCut: Float? = null
        var bestGap = -1f

        for (i in 0 until xCoords.size - 1) {
            val x1 = xCoords[i]
            val x2 = xCoords[i + 1]
            if (x2 - x1 < 0.001f) continue
            val midX = (x1 + x2) / 2f
            val hasOverlap = panels.any { it.rect.left < midX && midX < it.rect.right }
            if (!hasOverlap) {
                val left = panels.any { it.rect.right <= midX }
                val right = panels.any { it.rect.left >= midX }
                if (left && right) {
                    val gap = x2 - x1
                    if (gap > bestGap) {
                        bestGap = gap
                        bestCut = midX
                    }
                }
            }
        }
        return bestCut
    }

    private fun fallbackSort(panels: List<Panel>, isRightToLeft: Boolean): List<Panel> {
        val sortedByTop = panels.sortedBy { it.rect.top }
        val rows = mutableListOf<MutableList<Panel>>()

        for (panel in sortedByTop) {
            var added = false
            for (row in rows) {
                val ref = row.first()
                val overlapTop = max(panel.rect.top, ref.rect.top)
                val overlapBottom = min(panel.rect.bottom, ref.rect.bottom)
                val overlapHeight = overlapBottom - overlapTop
                val minHeight = min(panel.rect.height(), ref.rect.height())

                if (overlapHeight > minHeight * 0.40f) {
                    row.add(panel)
                    added = true
                    break
                }
            }
            if (!added) {
                rows.add(mutableListOf(panel))
            }
        }

        val finalSorted = mutableListOf<Panel>()
        val sortedRows = rows.sortedBy { row -> row.map { it.rect.top }.average() }

        for (row in sortedRows) {
            val sortedRow = if (isRightToLeft) {
                row.sortedByDescending { it.rect.centerX() }
            } else {
                row.sortedBy { it.rect.centerX() }
            }
            finalSorted.addAll(sortedRow)
        }

        return finalSorted
    }
}
