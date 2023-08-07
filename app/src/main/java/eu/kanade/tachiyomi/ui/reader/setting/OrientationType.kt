package eu.kanade.tachiyomi.ui.reader.setting

import android.content.pm.ActivityInfo
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R

enum class OrientationType(val flag: Int, @StringRes val stringRes: Int, @DrawableRes val iconRes: Int, val flagValue: Int) {
    DEFAULT(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, R.string.label_default, R.drawable.ic_screen_rotation_24dp, 0x00000000),
    FREE(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, R.string.rotation_free, R.drawable.ic_screen_rotation_24dp, 0x00000008),
    PORTRAIT(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, R.string.rotation_portrait, R.drawable.ic_stay_current_portrait_24dp, 0x00000010),
    LANDSCAPE(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, R.string.rotation_landscape, R.drawable.ic_stay_current_landscape_24dp, 0x00000018),
    LOCKED_PORTRAIT(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, R.string.rotation_force_portrait, R.drawable.ic_screen_lock_portrait_24dp, 0x00000020),
    LOCKED_LANDSCAPE(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, R.string.rotation_force_landscape, R.drawable.ic_screen_lock_landscape_24dp, 0x00000028),
    REVERSE_PORTRAIT(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, R.string.rotation_reverse_portrait, R.drawable.ic_stay_current_portrait_24dp, 0x00000030),
    ;

    companion object {
        const val MASK = 0x00000038

        fun fromPreference(preference: Int?): OrientationType = entries.find { it.flagValue == preference } ?: DEFAULT
    }
}
