package eu.kanade.tachiyomi.ui.reader.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import tachiyomi.i18n.MR

/**
 * Tap zones of a viewer. [isReversed] is set for right to left books, where the right side of the
 * screen goes back.
 */
abstract class ViewerNavigation(
    val invertMode: ReaderPreferences.TappingInvertMode,
    val isReversed: Boolean,
) {

    enum class Action { MENU, FORWARD, BACKWARD }

    sealed class NavigationRegion(val nameRes: StringResource, val color: Color) {
        data object MENU : NavigationRegion(MR.strings.action_menu, Color(0xCC95818D))
        data object PREV : NavigationRegion(MR.strings.nav_zone_prev, Color(0xCCFF7733))
        data object NEXT : NavigationRegion(MR.strings.nav_zone_next, Color(0xCC84E296))
        data object LEFT : NavigationRegion(MR.strings.nav_zone_left, Color(0xCC7D1128))
        data object RIGHT : NavigationRegion(MR.strings.nav_zone_right, Color(0xCCA6CFD5))
    }

    data class Region(
        val rect: Rect,
        val type: NavigationRegion,
    ) {
        fun invert(invertMode: ReaderPreferences.TappingInvertMode): Region {
            val horizontal = invertMode.shouldInvertHorizontal
            val vertical = invertMode.shouldInvertVertical
            if (!horizontal && !vertical) return this
            return copy(
                rect = Rect(
                    left = if (horizontal) 1f - rect.right else rect.left,
                    top = if (vertical) 1f - rect.bottom else rect.top,
                    right = if (horizontal) 1f - rect.left else rect.right,
                    bottom = if (vertical) 1f - rect.top else rect.bottom,
                ),
            )
        }
    }

    protected abstract val regionList: List<Region>

    /** Returns regions with applied inversion. */
    fun getRegions(): List<Region> {
        return regionList.map { it.invert(invertMode) }
    }

    fun getAction(pos: Offset): Action {
        val region = getRegions().find { pos in it.rect } ?: return Action.MENU
        return actionOf(region.type)
    }

    fun actionOf(region: NavigationRegion): Action = when (region) {
        NavigationRegion.MENU -> Action.MENU
        NavigationRegion.NEXT -> Action.FORWARD
        NavigationRegion.PREV -> Action.BACKWARD
        NavigationRegion.RIGHT -> if (isReversed) Action.BACKWARD else Action.FORWARD
        NavigationRegion.LEFT -> if (isReversed) Action.FORWARD else Action.BACKWARD
    }

    override fun equals(other: Any?): Boolean =
        other is ViewerNavigation &&
            other.javaClass == javaClass &&
            other.invertMode == invertMode &&
            other.isReversed == isReversed

    override fun hashCode(): Int = (31 * javaClass.hashCode() + invertMode.hashCode()) * 31 + isReversed.hashCode()
}
