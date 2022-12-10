package eu.kanade.tachiyomi.util.view

import android.content.Context
import android.graphics.Color
import android.view.Window
import android.view.WindowManager
import com.google.android.material.elevation.ElevationOverlayProvider
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isNavigationBarNeedsScrim

/**
 * Sets navigation bar color to transparent if system's config_navBarNeedsScrim is false,
 * otherwise it will use the theme navigationBarColor with 70% opacity.
 *
 * @see isNavigationBarNeedsScrim
 */
fun Window.setNavigationBarTransparentCompat(context: Context, elevation: Float = 0F) {
    navigationBarColor = if (context.isNavigationBarNeedsScrim()) {
        // Set navbar scrim 70% of navigationBarColor
        ElevationOverlayProvider(context).compositeOverlayIfNeeded(
            context.getResourceColor(android.R.attr.navigationBarColor, 0.7F),
            elevation,
        )
    } else {
        Color.TRANSPARENT
    }
}

fun Window.setSecureScreen(enabled: Boolean) {
    if (enabled) {
        setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
