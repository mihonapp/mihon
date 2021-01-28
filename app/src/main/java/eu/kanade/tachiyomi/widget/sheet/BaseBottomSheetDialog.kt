package eu.kanade.tachiyomi.widget.sheet

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.kanade.tachiyomi.R

open class BaseBottomSheetDialog(context: Context) : BottomSheetDialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val width = context.resources.getDimensionPixelSize(R.dimen.bottom_sheet_width)
        if (width > 0) {
            window?.setLayout(width, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }
}
