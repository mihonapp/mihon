@file:Suppress("NOTHING_TO_INLINE")

package eu.kanade.tachiyomi.util.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.TooltipCompat
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.children
import androidx.core.view.descendants
import androidx.core.view.forEach
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.snackbar.Snackbar
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.getResourceColor

inline fun ComposeView.setComposeContent(crossinline content: @Composable () -> Unit) {
    consumeWindowInsets = false
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        TachiyomiTheme {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodySmall,
                LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            ) {
                content()
            }
        }
    }
}

inline fun ComponentActivity.setComposeContent(
    parent: CompositionContext? = null,
    crossinline content: @Composable () -> Unit,
) {
    setContent(parent) {
        TachiyomiTheme {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodySmall,
                LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            ) {
                content()
            }
        }
    }
}

/**
 * Shows a snackbar in this view.
 *
 * @param message the message to show.
 * @param length the duration of the snack.
 * @param f a function to execute in the snack, allowing for example to define a custom action.
 */
inline fun View.snack(
    message: String,
    length: Int = 10_000,
    f: Snackbar.() -> Unit = {},
): Snackbar {
    val snack = Snackbar.make(this, message, length)
    snack.f()
    snack.show()
    return snack
}

/**
 * Adds a tooltip shown on long press.
 *
 * @param stringRes String resource for tooltip.
 */
inline fun View.setTooltip(@StringRes stringRes: Int) {
    setTooltip(context.getString(stringRes))
}

/**
 * Adds a tooltip shown on long press.
 *
 * @param text Text for tooltip.
 */
inline fun View.setTooltip(text: String) {
    TooltipCompat.setTooltipText(this, text)
}

/**
 * Shows a popup menu on top of this view.
 *
 * @param menuRes menu items to inflate the menu with.
 * @param initMenu function to execute when the menu after is inflated.
 * @param onMenuItemClick function to execute when a menu item is clicked.
 */
inline fun View.popupMenu(
    @MenuRes menuRes: Int,
    noinline initMenu: (Menu.() -> Unit)? = null,
    noinline onMenuItemClick: MenuItem.() -> Unit,
): PopupMenu {
    val popup = PopupMenu(context, this, Gravity.NO_GRAVITY, R.attr.actionOverflowMenuStyle, 0)
    popup.menuInflater.inflate(menuRes, popup.menu)

    if (initMenu != null) {
        popup.menu.initMenu()
    }
    popup.setOnMenuItemClickListener {
        it.onMenuItemClick()
        true
    }

    popup.show()
    return popup
}

/**
 * Shows a popup menu on top of this view.
 *
 * @param items menu item names to inflate the menu with. List of itemId to stringRes pairs.
 * @param selectedItemId optionally show a checkmark beside an item with this itemId.
 * @param onMenuItemClick function to execute when a menu item is clicked.
 */
@SuppressLint("RestrictedApi")
inline fun View.popupMenu(
    items: List<Pair<Int, Int>>,
    selectedItemId: Int? = null,
    noinline onMenuItemClick: MenuItem.() -> Unit,
): PopupMenu {
    val popup = PopupMenu(context, this, Gravity.NO_GRAVITY, R.attr.actionOverflowMenuStyle, 0)
    items.forEach { (id, stringRes) ->
        popup.menu.add(0, id, 0, stringRes)
    }

    if (selectedItemId != null) {
        (popup.menu as? MenuBuilder)?.setOptionalIconsVisible(true)
        val emptyIcon = AppCompatResources.getDrawable(context, R.drawable.ic_blank_24dp)
        popup.menu.forEach { item ->
            item.icon = when (item.itemId) {
                selectedItemId -> AppCompatResources.getDrawable(context, R.drawable.ic_check_24dp)?.mutate()?.apply {
                    setTint(context.getResourceColor(android.R.attr.textColorPrimary))
                }
                else -> emptyIcon
            }
        }
    }

    popup.setOnMenuItemClickListener {
        it.onMenuItemClick()
        true
    }

    popup.show()
    return popup
}

/**
 * Shrink an ExtendedFloatingActionButton when the associated RecyclerView is scrolled down.
 *
 * @param recycler [RecyclerView] that the FAB should shrink/extend in response to.
 */
inline fun ExtendedFloatingActionButton.shrinkOnScroll(recycler: RecyclerView): RecyclerView.OnScrollListener {
    val listener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy <= 0) {
                extend()
            } else {
                shrink()
            }
        }
    }
    recycler.addOnScrollListener(listener)
    return listener
}

/**
 * Callback will be run immediately when no animation running
 */
fun RecyclerView.onAnimationsFinished(callback: (RecyclerView) -> Unit) = post(
    object : Runnable {
        override fun run() {
            if (isAnimating) {
                itemAnimator?.isRunning {
                    post(this)
                }
            } else {
                callback(this@onAnimationsFinished)
            }
        }
    },
)

/**
 * Returns this ViewGroup's first child of specified class
 */
inline fun <reified T> ViewGroup.findChild(): T? {
    return children.find { it is T } as? T
}

/**
 * Returns this ViewGroup's first descendant of specified class
 */
inline fun <reified T> ViewGroup.findDescendant(): T? {
    return descendants.find { it is T } as? T
}

/**
 * Returns a deep copy of the provided [Drawable]
 */
inline fun <reified T : Drawable> T.copy(context: Context): T? {
    return (constantState?.newDrawable()?.mutate() as? T).apply {
        if (this is MaterialShapeDrawable) {
            initializeElevationOverlay(context)
        }
    }
}

fun View?.isVisibleOnScreen(): Boolean {
    if (this == null) {
        return false
    }
    if (!this.isShown) {
        return false
    }
    val actualPosition = Rect()
    this.getGlobalVisibleRect(actualPosition)
    val screen = Rect(0, 0, Resources.getSystem().displayMetrics.widthPixels, Resources.getSystem().displayMetrics.heightPixels)
    return actualPosition.intersect(screen)
}
