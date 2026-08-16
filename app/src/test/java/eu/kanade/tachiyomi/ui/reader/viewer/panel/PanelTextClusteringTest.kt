package eu.kanade.tachiyomi.ui.reader.viewer.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PanelTextClusteringTest {

    @Test
    fun `text blocks close together form one cluster`() {
        val blocks = listOf(
            PanelRect(0.1f, 0f, 0.2f, 0.1f),
            PanelRect(0.22f, 0f, 0.32f, 0.1f),
        )

        val clusters = PanelTextClustering.clusterByGap(blocks, panelWidth = 1f)

        assertEquals(1, clusters.size)
        assertEquals(2, clusters.single().size)
    }

    @Test
    fun `text blocks far apart form separate clusters`() {
        val blocks = listOf(
            PanelRect(0.05f, 0f, 0.15f, 0.1f),
            PanelRect(0.8f, 0f, 0.9f, 0.1f),
        )

        val clusters = PanelTextClustering.clusterByGap(blocks, panelWidth = 1f)

        assertEquals(2, clusters.size)
    }

    @Test
    fun `empty input produces no clusters`() {
        assertEquals(emptyList<List<PanelRect>>(), PanelTextClustering.clusterByGap(emptyList(), panelWidth = 1f))
    }
}
