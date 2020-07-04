package eu.kanade.tachiyomi.ui.main

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import it.gmariotti.changelibs.library.view.ChangeLogRecyclerView

class ChangelogDialogController : DialogController() {

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val activity = activity!!
        val view = WhatsNewRecyclerView(activity)
        return MaterialDialog(activity)
            .title(R.string.changelog)
            .customView(view = view)
            .positiveButton(R.string.action_close)
    }

    class WhatsNewRecyclerView(context: Context) : ChangeLogRecyclerView(context) {
        override fun initAttrs(attrs: AttributeSet?, defStyle: Int) {
            mRowLayoutId = R.layout.changelog_row_layout
            mRowHeaderLayoutId = R.layout.changelog_header_layout
            mChangeLogFileResourceId = R.raw.changelog_release
        }
    }
}
