package eu.kanade.tachiyomi.ui.reader.setting

import android.content.pm.ActivityInfo
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R

enum class OrientationType(val prefValue: Int, val flag: Int, @StringRes val stringRes: Int, @DrawableRes val iconRes: Int) {
    FREE(1, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, R.string.rotation_free, R.drawable.ic_screen_rotation_24dp),
    PORTRAIT(2, ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, R.string.rotation_portrait, R.drawable.ic_stay_current_portrait_24dp),
    LANDSCAPE(3, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, R.string.rotation_landscape, R.drawable.ic_stay_current_landscape_24dp),
    LOCKED_PORTRAIT(4, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, R.string.rotation_force_portrait, R.drawable.ic_screen_lock_portrait_24dp),
    LOCKED_LANDSCAPE(5, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, R.string.rotation_force_landscape, R.drawable.ic_screen_lock_landscape_24dp),
    ;

    companion object {
        fun fromPreference(preference: Int): OrientationType =
            values().find { it.prefValue == preference } ?: FREE
    }
}
