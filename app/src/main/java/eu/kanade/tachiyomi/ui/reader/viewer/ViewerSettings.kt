package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView.ZoomStartPosition
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.DisabledNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.EdgeNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.KindlishNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.LNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.RightAndLeftNavigation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * A viewer's tap zones, as the preferences store them.
 */
data class NavigationSettings(
    val mode: ReaderPreferences.NavigationMode,
    val inverted: ReaderPreferences.TappingInvertMode,
) {

    fun navigator(
        isReversed: Boolean,
        default: (ReaderPreferences.TappingInvertMode, Boolean) -> ViewerNavigation,
    ): ViewerNavigation {
        return when (mode) {
            ReaderPreferences.NavigationMode.DEFAULT -> default(inverted, isReversed)
            ReaderPreferences.NavigationMode.L_SHAPED -> LNavigation(inverted, isReversed)
            ReaderPreferences.NavigationMode.KINDLISH -> KindlishNavigation(inverted, isReversed)
            ReaderPreferences.NavigationMode.EDGE -> EdgeNavigation(inverted, isReversed)
            ReaderPreferences.NavigationMode.RIGHT_AND_LEFT -> RightAndLeftNavigation(inverted, isReversed)
            ReaderPreferences.NavigationMode.DISABLED -> DisabledNavigation(inverted, isReversed)
        }
    }
}

/**
 * Where a page starts zoomed for this setting, where [auto] is what automatic means for the viewer.
 */
fun ReaderPreferences.ZoomStartType.position(auto: ZoomStartPosition): ZoomStartPosition {
    return when (this) {
        ReaderPreferences.ZoomStartType.AUTOMATIC -> auto
        ReaderPreferences.ZoomStartType.LEFT -> ZoomStartPosition.LEFT
        ReaderPreferences.ZoomStartType.RIGHT -> ZoomStartPosition.RIGHT
        ReaderPreferences.ZoomStartType.CENTER -> ZoomStartPosition.CENTER
    }
}

/**
 * Runs [action] in [scope] with each value and the one before it, null for the first, so a viewer applies a change
 * in one place by comparing the parts it reacts to.
 */
fun <T : Any> Flow<T>.collectChanges(scope: CoroutineScope, action: (old: T?, new: T) -> Unit) {
    scope.launch {
        var old: T? = null
        collect {
            action(old, it)
            old = it
        }
    }
}
