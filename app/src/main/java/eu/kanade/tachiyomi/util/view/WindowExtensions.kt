package eu.kanade.tachiyomi.util.view

import android.view.Window
import android.view.WindowManager

fun Window.setSecureScreen(enabled: Boolean) {
    if (enabled) {
        setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
