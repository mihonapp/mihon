package eu.kanade.tachiyomi.ui.reader.viewer.panel

object PanelTextClustering {

    private const val CLUSTER_GAP_FRACTION = 0.15f

    /** Groups [rects] (e.g. OCR text-block boxes) into clusters by horizontal center gap. */
    fun clusterByGap(rects: List<PanelRect>, panelWidth: Float): List<List<PanelRect>> {
        if (rects.isEmpty()) return emptyList()

        val sorted = rects.sortedBy { centerX(it) }
        val clusters = mutableListOf(mutableListOf(sorted.first()))
        for (i in 1 until sorted.size) {
            val prevCenter = centerX(clusters.last().last())
            val center = centerX(sorted[i])
            if (center - prevCenter > CLUSTER_GAP_FRACTION * panelWidth) {
                clusters += mutableListOf(sorted[i])
            } else {
                clusters.last() += sorted[i]
            }
        }
        return clusters
    }

    private fun centerX(rect: PanelRect): Float = (rect.left + rect.right) / 2f
}
