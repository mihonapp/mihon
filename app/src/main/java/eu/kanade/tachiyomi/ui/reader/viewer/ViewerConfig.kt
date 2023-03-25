package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import tachiyomi.core.preference.Preference

/**
 * Common configuration for all viewers.
 */
abstract class ViewerConfig(readerPreferences: ReaderPreferences, private val scope: CoroutineScope) {

    var imagePropertyChangedListener: (() -> Unit)? = null

    var navigationModeChangedListener: (() -> Unit)? = null

    var tappingInverted = ReaderPreferences.TappingInvertMode.NONE
    var longTapEnabled = true
    var usePageTransitions = false
    var doubleTapAnimDuration = 500
    var volumeKeysEnabled = false
    var volumeKeysInverted = false
    var trueColor = false
    var alwaysShowChapterTransition = true
    var navigationMode = 0
        protected set

    var forceNavigationOverlay = false

    var navigationOverlayOnStart = false

    var dualPageSplit = false
        protected set

    var dualPageInvert = false
        protected set

    var dualPageRotateToFit = false
        protected set

    var dualPageRotateToFitInvert = false
        protected set

    abstract var navigator: ViewerNavigation
        protected set

    init {
        readerPreferences.readWithLongTap()
            .register({ longTapEnabled = it })

        readerPreferences.pageTransitions()
            .register({ usePageTransitions = it })

        readerPreferences.doubleTapAnimSpeed()
            .register({ doubleTapAnimDuration = it })

        readerPreferences.readWithVolumeKeys()
            .register({ volumeKeysEnabled = it })

        readerPreferences.readWithVolumeKeysInverted()
            .register({ volumeKeysInverted = it })

        readerPreferences.trueColor()
            .register({ trueColor = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.alwaysShowChapterTransition()
            .register({ alwaysShowChapterTransition = it })

        forceNavigationOverlay = readerPreferences.showNavigationOverlayNewUser().get()
        if (forceNavigationOverlay) {
            readerPreferences.showNavigationOverlayNewUser().set(false)
        }

        readerPreferences.showNavigationOverlayOnStart()
            .register({ navigationOverlayOnStart = it })
    }

    protected abstract fun defaultNavigation(): ViewerNavigation

    abstract fun updateNavigation(navigationMode: Int)

    fun <T> Preference<T>.register(
        valueAssignment: (T) -> Unit,
        onChanged: (T) -> Unit = {},
    ) {
        changes()
            .onEach { valueAssignment(it) }
            .distinctUntilChanged()
            .onEach { onChanged(it) }
            .launchIn(scope)
    }
}
