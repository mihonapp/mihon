package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import com.google.android.material.button.MaterialButton
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer

/**
 * A button class to be used by child views of the pager viewer. All tap gestures are handled by
 * the pager, but this class disables that behavior to allow clickable buttons.
 */
class ReaderButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.materialButtonStyle,
) : MaterialButton(context, attrs, defStyleAttr) {

    var viewer: PagerViewer? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        viewer?.pager?.setGestureDetectorEnabled(false)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            viewer?.pager?.setGestureDetectorEnabled(true)
        }
        return super.onTouchEvent(event)
    }
}
