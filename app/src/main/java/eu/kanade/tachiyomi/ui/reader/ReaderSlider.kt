package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.slider.Slider

/**
 * Slider to show current chapter progress.
 */
class ReaderSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Slider(context, attrs) {

    init {
        stepSize = 1f
        setLabelFormatter { value ->
            (value.toInt() + 1).toString()
        }
    }

    /**
     * Whether the slider should draw from right to left.
     */
    var isRTL: Boolean
        set(value) {
            layoutDirection = if (value) LAYOUT_DIRECTION_RTL else LAYOUT_DIRECTION_LTR
        }
        get() = layoutDirection == LAYOUT_DIRECTION_RTL
}
