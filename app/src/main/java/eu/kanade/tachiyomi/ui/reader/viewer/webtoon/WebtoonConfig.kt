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
    readerPreferences: ReaderPreferences = Injekt.get(),
) : ViewerConfig(readerPreferences, scope) {

    var themeChangedListener: (() -> Unit)? = null

    var imageCropBorders = false
        private set

    var zoomOutDisabled = false
        private set

    var zoomPropertyChangedListener: ((Boolean) -> Unit)? = null

    var sidePadding = 0
        private set

    var doubleTapZoom = true
        private set

    var doubleTapZoomChangedListener: ((Boolean) -> Unit)? = null

    var stripFilter = false
        private set

    var stripFilterColor = -0x1000000
        private set

    var stripFilterWhiteThreshold = 230
        private set

    var stripFilterGutterWidth = 90
        private set

    var stripFilterTallMarginHeight = 30
        private set

    var stripFilterBigChunkArea = 20
        private set

    val theme = readerPreferences.readerTheme().get()

    init {
        readerPreferences.cropBordersWebtoon()
            .register({ imageCropBorders = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.webtoonSidePadding()
            .register({ sidePadding = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.navigationModeWebtoon()
            .register({ navigationMode = it }, { updateNavigation(it) })

        readerPreferences.webtoonNavInverted()
            .register({ tappingInverted = it }, { navigator.invertMode = it })
        readerPreferences.webtoonNavInverted().changes()
            .drop(1)
            .onEach { navigationModeChangedListener?.invoke() }
            .launchIn(scope)

        readerPreferences.dualPageSplitWebtoon()
            .register({ dualPageSplit = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.dualPageInvertWebtoon()
            .register({ dualPageInvert = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.dualPageRotateToFitWebtoon()
            .register(
                { dualPageRotateToFit = it },
                { imagePropertyChangedListener?.invoke() },
            )

        readerPreferences.dualPageRotateToFitInvertWebtoon()
            .register(
                { dualPageRotateToFitInvert = it },
                { imagePropertyChangedListener?.invoke() },
            )

        readerPreferences.webtoonDisableZoomOut()
            .register(
                { zoomOutDisabled = it },
                { zoomPropertyChangedListener?.invoke(it) },
            )

        readerPreferences.webtoonDoubleTapZoomEnabled()
            .register(
                { doubleTapZoom = it },
                { doubleTapZoomChangedListener?.invoke(it) },
            )

        readerPreferences.stripFilter()
            .register({ stripFilter = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.stripFilterColor()
            .register({ stripFilterColor = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.stripFilterWhiteThreshold()
            .register({ stripFilterWhiteThreshold = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.stripFilterGutterWidth()
            .register({ stripFilterGutterWidth = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.stripFilterTallMarginHeight()
            .register({ stripFilterTallMarginHeight = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.stripFilterBigChunkArea()
            .register({ stripFilterBigChunkArea = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.readerTheme().changes()
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
