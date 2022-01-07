@file:Suppress("PackageDirectoryMismatch")

package com.google.android.material.bottomsheet

import android.view.View

/**
 * Returns package-private elevation value
 */
fun <T : View> BottomSheetBehavior<T>.getElevation(): Float {
    return elevation.takeIf { it >= 0F } ?: 0F
}
