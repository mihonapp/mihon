package eu.kanade.tachiyomi.ui.reader.viewer

import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.lang.invert
import tachiyomi.i18n.MR

abstract class ViewerNavigation {

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

    var invertMode: ReaderPreferences.TappingInvertMode = ReaderPreferences.TappingInvertMode.NONE

    protected abstract var regionList: List<Region>

    /** Returns regions with applied inversion. */
    fun getRegions(): List<Region> {
        return regionList.map { it.invert(invertMode) }
    }

    fun getAction(pos: PointF): NavigationRegion {
        val x = pos.x
        val y = pos.y
        val region = getRegions().find { it.rectF.contains(x, y) }
        return when {
            region != null -> region.type
            constantMenuRegion.contains(x, y) -> NavigationRegion.MENU
            else -> NavigationRegion.MENU
        }
    }
}
