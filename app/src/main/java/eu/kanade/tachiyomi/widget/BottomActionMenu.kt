package eu.kanade.tachiyomi.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MenuInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.MenuRes
import androidx.appcompat.widget.ActionMenuView
import eu.kanade.tachiyomi.R
import kotlinx.android.synthetic.main.common_bottom_action_menu.view.bottom_menu
import kotlinx.android.synthetic.main.common_bottom_action_menu.view.bottom_menu_bar

class BottomActionMenu @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        FrameLayout(context, attrs) {

    init {
        inflate(context, R.layout.common_bottom_action_menu, this)
    }

    fun show(menuInflater: MenuInflater, @MenuRes menuRes: Int, listener: ActionMenuView.OnMenuItemClickListener) {
        // Avoid re-inflating the menu
        if (bottom_menu.menu.size() == 0) {
            menuInflater.inflate(menuRes, bottom_menu.menu)
            bottom_menu.setOnMenuItemClickListener(listener)
        }

        bottom_menu_bar.visibility = View.VISIBLE
    }

    fun hide() {
        bottom_menu_bar.visibility = View.GONE

        bottom_menu.setOnMenuItemClickListener(null)
        bottom_menu.menu.clear()
    }
}
