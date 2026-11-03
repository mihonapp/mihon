package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences

/**
 * Settings only the paged WebGPU viewers use.
 */
data class WebGpuPagedSettings(
    val page: Page,
    val common: WebGpuSettings,
) {

    /**
     * What the pages are made with, so a change remakes them.
     */
    data class Page(
        val dualPageView: ReaderPreferences.DualPageView,
        val transitionAnimation: ReaderPreferences.TransitionAnimation,
        val transitionAnimationDual: ReaderPreferences.TransitionAnimation,
        val cutoutMode: ReaderPreferences.CutoutMode,
        val cutoutModeDual: ReaderPreferences.CutoutMode,
    )
}
