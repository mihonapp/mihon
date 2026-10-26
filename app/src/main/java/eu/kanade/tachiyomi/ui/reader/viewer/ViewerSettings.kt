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

    fun navigator(default: () -> ViewerNavigation): ViewerNavigation {
        val navigation = when (mode) {
            ReaderPreferences.NavigationMode.DEFAULT -> default()
            ReaderPreferences.NavigationMode.L_SHAPED -> LNavigation()
            ReaderPreferences.NavigationMode.KINDLISH -> KindlishNavigation()
            ReaderPreferences.NavigationMode.EDGE -> EdgeNavigation()
            ReaderPreferences.NavigationMode.RIGHT_AND_LEFT -> RightAndLeftNavigation()
            ReaderPreferences.NavigationMode.DISABLED -> DisabledNavigation()
        }
        return navigation.also { it.invertMode = inverted }
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
