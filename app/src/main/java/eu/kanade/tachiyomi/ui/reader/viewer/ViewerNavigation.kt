package eu.kanade.tachiyomi.ui.reader.viewer

import android.graphics.PointF
import android.graphics.RectF
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.lang.invert

abstract class ViewerNavigation {

    sealed class NavigationRegion(@StringRes val nameRes: Int, val colorRes: Int) {
        data object MENU : NavigationRegion(R.string.action_menu, R.color.navigation_menu)
        data object PREV : NavigationRegion(R.string.nav_zone_prev, R.color.navigation_prev)
        data object NEXT : NavigationRegion(R.string.nav_zone_next, R.color.navigation_next)
        data object LEFT : NavigationRegion(R.string.nav_zone_left, R.color.navigation_left)
        data object RIGHT : NavigationRegion(R.string.nav_zone_right, R.color.navigation_right)
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

    abstract var regions: List<Region>

    var invertMode: ReaderPreferences.TappingInvertMode = ReaderPreferences.TappingInvertMode.NONE

    fun getAction(pos: PointF): NavigationRegion {
        val x = pos.x
        val y = pos.y
        val region = regions.map { it.invert(invertMode) }
            .find { it.rectF.contains(x, y) }
        return when {
            region != null -> region.type
            constantMenuRegion.contains(x, y) -> NavigationRegion.MENU
            else -> NavigationRegion.MENU
        }
    }
}
