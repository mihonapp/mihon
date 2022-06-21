package eu.kanade.tachiyomi.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.style.ScaleXSpan
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.set
import eu.kanade.tachiyomi.widget.OutlineSpan

/**
 * Page indicator found at the bottom of the reader
 */
class PageIndicatorTextView(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatTextView(context, attrs) {

    init {
        setTextColor(fillColor)
    }

    @SuppressLint("SetTextI18n")
    override fun setText(text: CharSequence?, type: BufferType?) {
        // Add spaces at the start & end of the text, otherwise the stroke is cut-off because it's
        // not taken into account when measuring the text (view's padding doesn't help).
        val currText = " $text "

        // Also add a bit of spacing between each character, as the stroke overlaps them
        val finalText = SpannableString(currText.asIterable().joinToString("\u00A0")).apply {
            // Apply text outline
            set(1, length - 1, spanOutline)

            for (i in 1..lastIndex step 2) {
                set(i, i + 1, ScaleXSpan(0.2f))
            }
        }

        super.setText(finalText, BufferType.SPANNABLE)
    }
}

private val fillColor = Color.rgb(235, 235, 235)
private val strokeColor = Color.rgb(45, 45, 45)

// A span object with text outlining properties
private val spanOutline = OutlineSpan(
    strokeColor = strokeColor,
    strokeWidth = 4f,
)
