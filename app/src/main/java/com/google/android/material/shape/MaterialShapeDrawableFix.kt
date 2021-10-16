package com.google.android.material.shape

/**
 * Use this instead of [MaterialShapeDrawable.getAlpha].
 *
 * https://github.com/material-components/material-components-android/issues/1796
 */
fun MaterialShapeDrawable.getStateAlpha(): Int {
    return (constantState as? MaterialShapeDrawable.MaterialShapeDrawableState)?.alpha ?: alpha
}
