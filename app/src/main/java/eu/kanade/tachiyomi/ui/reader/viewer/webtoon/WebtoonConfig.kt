package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerConfig
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.DisabledNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.EdgeNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.KindlishNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.LNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.RightAndLeftNavigation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Configuration used by webtoon viewers.
 */
class WebtoonConfig(
    scope: CoroutineScope,
    private val isLandscape: Boolean = false,
    readerPreferences: ReaderPreferences = Injekt.get(),
) : ViewerConfig(readerPreferences, scope) {

    var themeChangedListener: (() -> Unit)? = null

    var imageCropBorders = false
        private set

    var zoomOutDisabled = false
        private set

    var zoomPropertyChangedListener: ((Boolean) -> Unit)? = null

    private var portraitSidePadding = 0
    private var landscapeSidePadding = ReaderPreferences.WEBTOON_PADDING_FOLLOW_PORTRAIT

    /**
     * Side padding, in percent of the viewer width, for the current screen orientation.
     */
    val sidePadding: Int
        get() = if (isLandscape && landscapeSidePadding >= 0) landscapeSidePadding else portraitSidePadding

    var sidePaddingChangedListener: ((Int) -> Unit)? = null

    /**
     * Auto-scroll speed in dp per second.
     */
    var autoScrollSpeed = ReaderPreferences.AUTO_SCROLL_SPEED_DEFAULT * ReaderPreferences.AUTO_SCROLL_DP_PER_STEP
        private set

    var doubleTapZoom = true
        private set

    var doubleTapZoomChangedListener: ((Boolean) -> Unit)? = null

    val theme = readerPreferences.readerTheme.get()

    init {
        readerPreferences.cropBordersWebtoon
            .register({ imageCropBorders = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.webtoonSidePadding
            .register({ portraitSidePadding = it }, { sidePaddingChangedListener?.invoke(sidePadding) })

        readerPreferences.webtoonSidePaddingLandscape
            .register({ landscapeSidePadding = it }, { sidePaddingChangedListener?.invoke(sidePadding) })

        readerPreferences.webtoonAutoScrollSpeed
            .register({ autoScrollSpeed = it * ReaderPreferences.AUTO_SCROLL_DP_PER_STEP })

        readerPreferences.navigationModeWebtoon
            .register({ navigationMode = it }, { updateNavigation(it) })

        readerPreferences.webtoonNavInverted
            .register({ tappingInverted = it }, { navigator.invertMode = it })
        readerPreferences.webtoonNavInverted.changes()
            .drop(1)
            .onEach { navigationModeChangedListener?.invoke() }
            .launchIn(scope)

        readerPreferences.dualPageSplitWebtoon
            .register({ dualPageSplit = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.dualPageInvertWebtoon
            .register({ dualPageInvert = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.dualPageRotateToFitWebtoon
            .register(
                { dualPageRotateToFit = it },
                { imagePropertyChangedListener?.invoke() },
            )

        readerPreferences.dualPageRotateToFitInvertWebtoon
            .register(
                { dualPageRotateToFitInvert = it },
                { imagePropertyChangedListener?.invoke() },
            )

        readerPreferences.webtoonDisableZoomOut
            .register(
                { zoomOutDisabled = it },
                { zoomPropertyChangedListener?.invoke(it) },
            )

        readerPreferences.webtoonDoubleTapZoomEnabled
            .register(
                { doubleTapZoom = it },
                { doubleTapZoomChangedListener?.invoke(it) },
            )

        readerPreferences.readerTheme.changes()
            .drop(1)
            .distinctUntilChanged()
            .onEach { themeChangedListener?.invoke() }
            .launchIn(scope)
    }

    override var navigator: ViewerNavigation = defaultNavigation()
        set(value) {
            field = value.also { it.invertMode = tappingInverted }
        }

    override fun defaultNavigation(): ViewerNavigation {
        return LNavigation()
    }

    override fun updateNavigation(navigationMode: Int) {
        this.navigator = when (navigationMode) {
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
