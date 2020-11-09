package eu.kanade.tachiyomi.util.view

import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.P)
fun Window.edgeToEdge(lightSystemUi: Boolean = false) {
    decorView.systemUiVisibility = when {
        // Handle light status and navigation bars programmatically to avoid duplicate themes
        lightSystemUi -> {
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        else -> {
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    navigationBarColor = Color.TRANSPARENT
}

fun Window.showBar() {
    decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
}

fun Window.hideBar() {
    decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
}

fun Window.defaultBar() {
    decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
}

fun Window.isDefaultBar() = decorView.systemUiVisibility == View.SYSTEM_UI_FLAG_VISIBLE
