package eu.kanade.tachiyomi.ui.reader.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.ui.reader.viewer.LongStripSettings
import eu.kanade.tachiyomi.ui.reader.viewer.NavigationSettings
import eu.kanade.tachiyomi.ui.reader.viewer.PagedSettings
import eu.kanade.tachiyomi.ui.reader.viewer.pager.SimplePagedSettings
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuLongStripSettings
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuPagedSettings
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuSettings
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.SimpleLongStripSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference
import kotlin.time.Duration.Companion.seconds

/**
 * The reader's settings, each as one value re-read whenever a preference it's made of changes, so the activity and
 * viewers apply a change in one place instead of each preference calling back.
 */
class ReaderSettingsViewModel(
    private val readerViewModel: ReaderViewModel,
    val preferences: ReaderPreferences,
    private val basePreferences: BasePreferences,
) : ViewModel() {

    val mangaFlow = readerViewModel.state
        .map { it.manga }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /**
     * The reading mode the viewer is made for, with the default resolved.
     */
    val readingMode = combine(mangaFlow, preferences.defaultReadingMode.changes()) { manga, default ->
        ReadingMode.fromPreference(manga?.readingMode?.toInt())
            .takeIf { it != ReadingMode.DEFAULT }
            ?: ReadingMode.fromPreference(default)
    }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ReadingMode.fromPreference(readerViewModel.getMangaReadingMode()),
        )

    /**
     * Whether the viewers are the WebGPU ones.
     */
    val isWebGpu = basePreferences.highQualityRenderer

    val general = settings(
        preferences.readerTheme,
        preferences.keepScreenOn,
        preferences.grayscale,
        preferences.invertedColors,
        preferences.fullscreen,
        preferences.drawUnderCutout,
        preferences.readWithLongTap,
        preferences.pageTransitions,
        preferences.doubleTapAnimSpeed,
        preferences.readWithVolumeKeys,
        preferences.readWithVolumeKeysInverted,
        preferences.alwaysShowChapterTransition,
        preferences.showNavigationOverlayOnStart,
    ) {
        GeneralSettings(
            theme = ReaderPreferences.ReaderTheme.fromValue(readerTheme.get()),
            keepScreenOn = keepScreenOn.get(),
            grayscale = grayscale.get(),
            invertedColors = invertedColors.get(),
            fullscreen = fullscreen.get(),
            drawUnderCutout = drawUnderCutout.get(),
            longTapEnabled = readWithLongTap.get(),
            usePageTransitions = pageTransitions.get(),
            doubleTapAnimDuration = doubleTapAnimSpeed.get(),
            volumeKeysEnabled = readWithVolumeKeys.get(),
            volumeKeysInverted = readWithVolumeKeysInverted.get(),
            alwaysShowChapterTransition = alwaysShowChapterTransition.get(),
            navigationOverlayOnStart = showNavigationOverlayOnStart.get(),
        )
    }

    /**
     * The custom brightness, 0 when it's off.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val brightness: StateFlow<Int> = preferences.customBrightness.changes()
        .flatMapLatest { enabled ->
            if (enabled) preferences.customBrightnessValue.changes().sample(0.1.seconds) else flowOf(0)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            if (preferences.customBrightness.get()) preferences.customBrightnessValue.get() else 0,
        )

    /**
     * Settings every paged viewer uses.
     */
    val paged = settings(
        preferences.imageScaleType,
        preferences.zoomStart,
        preferences.cropBorders,
        preferences.landscapeZoom,
        preferences.navigateToPan,
        preferences.navigationModePager,
        preferences.pagerNavInverted,
    ) {
        PagedSettings(
            page = PagedSettings.Page(
                imageScaleType = ReaderPreferences.ScaleType.fromValue(imageScaleType.get()),
                zoomStart = ReaderPreferences.ZoomStartType.fromValue(zoomStart.get()),
                cropBorders = cropBorders.get(),
                landscapeZoom = landscapeZoom.get(),
            ),
            navigateToPan = navigateToPan.get(),
            navigation = NavigationSettings(
                ReaderPreferences.NavigationMode.fromValue(navigationModePager.get()),
                pagerNavInverted.get(),
            ),
        )
    }

    /**
     * Settings every long strip viewer uses.
     */
    val longStrip = settings(
        preferences.cropBordersWebtoon,
        preferences.webtoonDisableZoomOut,
    ) {
        LongStripSettings(
            cropBorders = cropBordersWebtoon.get(),
            zoomOutDisabled = webtoonDisableZoomOut.get(),
        )
    }

    /**
     * Settings only the pager viewers use.
     */
    val simplePaged = settings(
        preferences.dualPageSplitPaged,
        preferences.dualPageInvertPaged,
        preferences.dualPageRotateToFit,
        preferences.dualPageRotateToFitInvert,
    ) {
        SimplePagedSettings(
            dualPageSplit = dualPageSplitPaged.get(),
            dualPageInvert = dualPageInvertPaged.get(),
            dualPageRotateToFit = dualPageRotateToFit.get(),
            dualPageRotateToFitInvert = dualPageRotateToFitInvert.get(),
        )
    }

    /**
     * Settings only the webtoon viewers use.
     */
    val simpleLongStrip = settings(
        preferences.webtoonSidePadding,
        preferences.dualPageSplitWebtoon,
        preferences.dualPageInvertWebtoon,
        preferences.dualPageRotateToFitWebtoon,
        preferences.dualPageRotateToFitInvertWebtoon,
        preferences.webtoonDoubleTapZoomEnabled,
        preferences.navigationModeWebtoon,
        preferences.webtoonNavInverted,
    ) {
        SimpleLongStripSettings(
            page = SimpleLongStripSettings.Page(
                sidePadding = webtoonSidePadding.get(),
                dualPageSplit = dualPageSplitWebtoon.get(),
                dualPageInvert = dualPageInvertWebtoon.get(),
                dualPageRotateToFit = dualPageRotateToFitWebtoon.get(),
                dualPageRotateToFitInvert = dualPageRotateToFitInvertWebtoon.get(),
            ),
            doubleTapZoom = webtoonDoubleTapZoomEnabled.get(),
            navigation = NavigationSettings(
                ReaderPreferences.NavigationMode.fromValue(navigationModeWebtoon.get()),
                webtoonNavInverted.get(),
            ),
        )
    }

    /**
     * Settings only the paged WebGPU viewers use.
     */
    val webgpuPaged = settings(
        preferences.dualPageView,
        preferences.transitionAnimation,
        preferences.transitionAnimationDual,
        preferences.cutoutMode,
        preferences.cutoutModeDual,
        preferences.disableDoubleTapGestures,
        preferences.imageFilters,
        preferences.tileFilters,
    ) {
        WebGpuPagedSettings(
            page = WebGpuPagedSettings.Page(
                dualPageView = dualPageView.get(),
                transitionAnimation = transitionAnimation.get(),
                transitionAnimationDual = transitionAnimationDual.get(),
                cutoutMode = cutoutMode.get(),
                cutoutModeDual = cutoutModeDual.get(),
            ),
            common = WebGpuSettings(
                doubleTapGesturesDisabled = disableDoubleTapGestures.get(),
                imageFilters = imageFilters.get(),
                tileFilters = tileFilters.get(),
            ),
        )
    }

    /**
     * Settings only the long strip WebGPU viewer uses.
     */
    val webgpuLongStrip = settings(
        preferences.continuousMinWidth,
        preferences.continuousGap,
        preferences.disableDoubleTapGestures,
        preferences.imageFilters,
        preferences.tileFilters,
    ) {
        WebGpuLongStripSettings(
            page = WebGpuLongStripSettings.Page(
                continuousMinWidth = continuousMinWidth.get(),
                continuousGap = continuousGap.get(),
            ),
            common = WebGpuSettings(
                doubleTapGesturesDisabled = disableDoubleTapGestures.get(),
                imageFilters = imageFilters.get(),
                tileFilters = tileFilters.get(),
            ),
        )
    }

    fun setReadingMode(readingMode: ReadingMode) = readerViewModel.setMangaReadingMode(readingMode)

    fun setOrientation(orientation: ReaderOrientation) = readerViewModel.setMangaOrientationType(orientation)

    /**
     * Whether a viewer must show its tap zones because the reader is new to the user, which it is only once.
     */
    fun takeNavigationOverlayForNewUser(): Boolean {
        val show = preferences.showNavigationOverlayNewUser.get()
        if (show) {
            preferences.showNavigationOverlayNewUser.set(false)
        }
        return show
    }

    /**
     * A value [read] from [sources], kept current as any of them changes. Started eagerly, so a viewer reading it
     * before collecting gets what the preferences hold now.
     */
    private fun <T> settings(vararg sources: Preference<*>, read: ReaderPreferences.() -> T): StateFlow<T> {
        return sources.map { it.changes() }
            .merge()
            .map { preferences.read() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, preferences.read())
    }
}
