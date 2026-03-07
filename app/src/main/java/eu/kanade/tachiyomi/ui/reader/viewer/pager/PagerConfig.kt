package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.res.Configuration
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerConfig
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.DisabledNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.EdgeNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.KindlishNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.LNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.RightAndLeftNavigation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Configuration used by pager viewers.
 */
class PagerConfig(
    private val viewer: PagerViewer,
    scope: CoroutineScope,
    readerPreferences: ReaderPreferences = Injekt.get(),
) : ViewerConfig(readerPreferences, scope) {

    var theme = readerPreferences.readerTheme().get()
        private set

    var automaticBackground = false
        private set

    var dualPageMode = false
        private set

    val isLandscape: Boolean
        get() = viewer.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var dualPageFirstPageCover = readerPreferences.dualPageFirstPageCover().get()
        private set

    var dualPageFoldable = readerPreferences.dualPageFoldable().get()
        private set

    val isWideScreen: Boolean
        get() = viewer.activity.resources.configuration.screenWidthDp >= 600

    var dualPageSplitChangedListener: ((Boolean) -> Unit)? = null

    var imageScaleType = readerPreferences.imageScaleType().get()
        private set

    var imageZoomType = ReaderPageImageView.ZoomStartPosition.LEFT
        private set

    var imageCropBorders = readerPreferences.cropBorders().get()
        private set

    var navigateToPan = readerPreferences.navigateToPan().get()
        private set

    var landscapeZoom = readerPreferences.landscapeZoom().get()
        private set

    init {
        zoomTypeFromPreference(readerPreferences.zoomStart().get())
        navigationMode = readerPreferences.navigationModePager().get()
        tappingInverted = readerPreferences.pagerNavInverted().get()
        dualPageSplit = readerPreferences.dualPageSplitPaged().get()
        dualPageInvert = readerPreferences.dualPageInvertPaged().get()
        dualPageRotateToFit = readerPreferences.dualPageRotateToFit().get()
        dualPageRotateToFitInvert = readerPreferences.dualPageRotateToFitInvert().get()
        dualPageFoldable = readerPreferences.dualPageFoldable().get()

        updateDualPageMode(readerPreferences.dualPageMode().get())

        readerPreferences.readerTheme()
            .register(
                {
                    theme = it
                    automaticBackground = it == 3
                },
                { imagePropertyChangedListener?.invoke() },
            )

        readerPreferences.imageScaleType()
            .register({ imageScaleType = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.zoomStart()
            .register({ zoomTypeFromPreference(it) }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.cropBorders()
            .register({ imageCropBorders = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.navigateToPan()
            .register({ navigateToPan = it })

        readerPreferences.landscapeZoom()
            .register({ landscapeZoom = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.navigationModePager()
            .register({ navigationMode = it }, { updateNavigation(navigationMode) })

        readerPreferences.pagerNavInverted()
            .register({ tappingInverted = it }, { navigator.invertMode = it })

        readerPreferences.pagerNavInverted().changes()
            .drop(1)
            .onEach { navigationModeChangedListener?.invoke() }
            .launchIn(scope)

        readerPreferences.dualPageMode()
            .register(
                { updateDualPageMode(it) },
                { imagePropertyChangedListener?.invoke() },
            )

        readerPreferences.dualPageFirstPageCover()
            .register({ dualPageFirstPageCover = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.dualPageFoldable()
            .register({ dualPageFoldable = it; updateDualPageMode(readerPreferences.dualPageMode().get()) }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.dualPageSplitPaged()
            .register(
                { dualPageSplit = it },
                {
                    imagePropertyChangedListener?.invoke()
                    dualPageSplitChangedListener?.invoke(it)
                },
            )

        readerPreferences.dualPageInvertPaged()
            .register({ dualPageInvert = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.dualPageRotateToFit()
            .register(
                { dualPageRotateToFit = it },
                { imagePropertyChangedListener?.invoke() },
            )

        readerPreferences.dualPageRotateToFitInvert()
            .register(
                { dualPageRotateToFitInvert = it },
                { imagePropertyChangedListener?.invoke() },
            )
    }

    private fun updateDualPageMode(enabled: Boolean) {
        val manga = viewer.activity.viewModel.state.value.manga
        val readingMode = ReadingMode.fromPreference(manga?.readingMode?.toInt())
        val isDualPageReadingMode = readingMode == ReadingMode.DUAL_PAGE_LTR || readingMode == ReadingMode.DUAL_PAGE_RTL
        val shouldEnableDual = enabled || isDualPageReadingMode
        // Enable dual-page mode if:
        // 1. Landscape orientation, OR
        // 2. Foldable option is enabled AND screen is wide enough AND using dual-page reading mode
        dualPageMode = shouldEnableDual && (isLandscape || (dualPageFoldable && isWideScreen && isDualPageReadingMode))
    }

    private fun zoomTypeFromPreference(value: Int) {
        imageZoomType = when (value) {
            // Auto
            1 -> when (viewer) {
                is L2RPagerViewer -> ReaderPageImageView.ZoomStartPosition.LEFT
                is R2LPagerViewer -> ReaderPageImageView.ZoomStartPosition.RIGHT
                else -> ReaderPageImageView.ZoomStartPosition.CENTER
            }
            // Left
            2 -> ReaderPageImageView.ZoomStartPosition.LEFT
            // Right
            3 -> ReaderPageImageView.ZoomStartPosition.RIGHT
            // Center
            else -> ReaderPageImageView.ZoomStartPosition.CENTER
        }
    }

    override var navigator: ViewerNavigation = defaultNavigation()
        set(value) {
            field = value.also { it.invertMode = this.tappingInverted }
        }

    override fun defaultNavigation(): ViewerNavigation {
        return when (viewer) {
            is VerticalPagerViewer -> LNavigation()
            else -> RightAndLeftNavigation()
        }
    }

    override fun updateNavigation(navigationMode: Int) {
        navigator = when (navigationMode) {
            0 -> defaultNavigation()
            1 -> LNavigation()
            2 -> KindlishNavigation()
            3 -> EdgeNavigation()
            4 -> RightAndLeftNavigation()
            5 -> DisabledNavigation()
            else -> defaultNavigation()
        }
        navigationModeChangedListener?.invoke()
    }
}
