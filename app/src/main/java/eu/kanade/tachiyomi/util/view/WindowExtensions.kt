package eu.kanade.tachiyomi.util.view

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.Window
import android.view.WindowManager
import com.google.android.material.elevation.ElevationOverlayProvider
import eu.kanade.tachiyomi.util.system.InternalResourceHelper
import eu.kanade.tachiyomi.util.system.getResourceColor

/**
 * Sets navigation bar color to transparent if system's config_navBarNeedsScrim is false,
 * otherwise it will use the theme navigationBarColor with 70% opacity.
 */
fun Window.setNavigationBarTransparentCompat(context: Context, elevation: Float = 0F) {
    navigationBarColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        !InternalResourceHelper.getBoolean(context, "config_navBarNeedsScrim", true)
    ) {
        Color.TRANSPARENT
    } else {
        // Set navbar scrim 70% of navigationBarColor
        ElevationOverlayProvider(context).compositeOverlayIfNeeded(
            context.getResourceColor(android.R.attr.navigationBarColor, 0.7F),
            elevation,
        )
    }
}

fun Window.setSecureScreen(enabled: Boolean) {
    if (enabled) {
        setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
