package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

/**
 * Settings both WebGPU viewers use, kept in each of their own settings.
 */
data class WebGpuSettings(
    val doubleTapGesturesDisabled: Boolean,
    val imageFilters: List<WebGpuShader>,
    val tileFilters: List<WebGpuShader>,
)
