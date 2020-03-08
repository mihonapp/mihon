package eu.kanade.tachiyomi.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.annotation.MenuRes
import eu.kanade.tachiyomi.R
import kotlinx.android.synthetic.main.common_bottom_action_menu.view.bottom_menu
import kotlinx.android.synthetic.main.common_bottom_action_menu.view.bottom_menu_bar

class BottomActionMenu @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        FrameLayout(context, attrs) {

    @MenuRes
    private var menuRes: Int = 0

    init {
        inflate(context, R.layout.common_bottom_action_menu, this)
    }

    fun configure(@MenuRes menuRes: Int, listener: (item: MenuItem?) -> Boolean) {
        this.menuRes = menuRes
        bottom_menu.setOnMenuItemClickListener { listener(it) }
    }

    fun cleanup() {
        bottom_menu.menu.clear()
        bottom_menu.setOnMenuItemClickListener(null)
    }

    fun findItem(@IdRes itemId: Int): MenuItem? {
        return bottom_menu.menu.findItem(itemId)
    }

    fun show(menuInflater: MenuInflater) {
        // Avoid re-inflating the menu
        if (bottom_menu.menu.size() == 0) {
            menuInflater.inflate(menuRes, bottom_menu.menu)
        }

        bottom_menu_bar.visibility = View.VISIBLE
    }

    fun hide() {
        bottom_menu_bar.visibility = View.GONE
    }
}
