package eu.kanade.tachiyomi.util.system

import android.app.Activity
import android.os.Build
import androidx.annotation.AnimRes

fun Activity.overridePendingTransitionCompat(@AnimRes enterAnim: Int, @AnimRes exitAnim: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, enterAnim, exitAnim)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(enterAnim, exitAnim)
    }
}
