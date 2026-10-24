package eu.kanade.tachiyomi.ui.reader.viewer

import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.lang.invert
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

    sealed class NavigationRegion(val nameRes: StringResource, val color: Int) {
        data object MENU : NavigationRegion(MR.strings.action_menu, Color.argb(0xCC, 0x95, 0x81, 0x8D))
        data object PREV : NavigationRegion(MR.strings.nav_zone_prev, Color.argb(0xCC, 0xFF, 0x77, 0x33))
        data object NEXT : NavigationRegion(MR.strings.nav_zone_next, Color.argb(0xCC, 0x84, 0xE2, 0x96))
        data object LEFT : NavigationRegion(MR.strings.nav_zone_left, Color.argb(0xCC, 0x7D, 0x11, 0x28))
        data object RIGHT : NavigationRegion(MR.strings.nav_zone_right, Color.argb(0xCC, 0xA6, 0xCF, 0xD5))
    }

    data class Region(
        val rectF: RectF,
        val type: NavigationRegion,
    ) {
        fun invert(invertMode: ReaderPreferences.TappingInvertMode): Region {
            if (invertMode == ReaderPreferences.TappingInvertMode.NONE) return this
            return this.copy(
                rectF = this.rectF.invert(invertMode),
            )
        }
    }

    private var constantMenuRegion: RectF = RectF(0f, 0f, 1f, 0.05f)

    protected abstract val regionList: List<Region>

    /** Returns regions with applied inversion. */
    fun getRegions(): List<Region> {
        return regionList.map { it.invert(invertMode) }
    }

    fun getAction(pos: PointF): Action {
        val x = pos.x
        val y = pos.y
        val region = getRegions().find { it.rectF.contains(x, y) }
        return when {
            region != null -> actionOf(region.type)
            constantMenuRegion.contains(x, y) -> Action.MENU
            else -> Action.MENU
        }
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
