package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.provider.Settings

/**
 * Gets the duration multiplier for general animations on the device
 * @see Settings.Global.ANIMATOR_DURATION_SCALE
 */
val Context.animatorDurationScale: Float
    get() = Settings.Global.getFloat(this.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

/**
 * Scales an animation [duration] in milliseconds by the device's [scale].
 *
 * Returns 0 when animations are turned off system-wide, so callers can skip the
 * animation entirely instead of playing a near-instant one.
 */
fun Int.scaledByAnimatorDuration(scale: Float): Int {
    if (this <= 0 || scale <= 0f) return 0
    return (this * scale).toInt().coerceAtLeast(1)
}
