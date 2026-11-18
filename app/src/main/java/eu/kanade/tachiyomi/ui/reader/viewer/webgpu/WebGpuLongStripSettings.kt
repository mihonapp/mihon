package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

/**
 * Settings only the long strip WebGPU viewer uses.
 */
data class WebGpuLongStripSettings(
    val page: Page,
    val common: WebGpuSettings,
) {

    /**
     * What the pages are made with, so a change remakes them.
     */
    data class Page(
        val continuousMinWidth: Int,
        val continuousGap: Int,
    )
}
