package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

/**
 * Settings both WebGPU viewers use, kept in each of their own settings.
 */
data class WebGpuSettings(
    val doubleTapGesturesDisabled: Boolean,
    /** No tiles or filters: pages draw through the fast path alone. */
    val fastRenderingOnly: Boolean,
    /** See [ca.mpreg.webgpuviewer.viewer.ImageViewerState.holdUntilFinal]. */
    val holdUntilFinal: Boolean,
    val imageFilters: List<WebGpuShader>,
    val tileFilters: List<WebGpuShader>,
    /** Null for [ca.mpreg.webgpuviewer.renderer.Sampler.catmullRom]. */
    val upscaler: WebGpuShader?,
    /** Null for [ca.mpreg.webgpuviewer.renderer.Sampler.box]. */
    val downscaler: WebGpuShader?,
)
