package eu.kanade.tachiyomi.widget

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.widget.EditText
import android.widget.NumberPicker
import androidx.core.view.doOnLayout
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.view.findDescendant

class MinMaxNumberPicker @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    NumberPicker(context, attrs) {

    override fun setDisplayedValues(displayedValues: Array<out String>?) {
        super.setDisplayedValues(displayedValues)

        // Disable keyboard input when a value that can't be auto-filled with number exists
        val notNumberValue = displayedValues?.find { it.getOrNull(0)?.digitToIntOrNull() == null }
        if (notNumberValue != null) {
            descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        }
    }

    init {
        if (attrs != null) {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.MinMaxNumberPicker, 0, 0)
            try {
                minValue = ta.getInt(R.styleable.MinMaxNumberPicker_min, 0)
                maxValue = ta.getInt(R.styleable.MinMaxNumberPicker_max, 0)
            } finally {
                ta.recycle()
            }
        }

        doOnLayout {
            findDescendant<EditText>()?.setRawInputType(InputType.TYPE_CLASS_NUMBER)
        }
    }
}
