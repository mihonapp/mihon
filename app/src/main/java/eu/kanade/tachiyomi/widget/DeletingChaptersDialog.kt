package eu.kanade.tachiyomi.widget

import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.DialogFragment
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R

class DeletingChaptersDialog : DialogFragment() {

    companion object {
        const val TAG = "deleting_dialog"
    }

    override fun onCreateDialog(savedState: Bundle?): Dialog {
        return MaterialDialog.Builder(activity)
                .progress(true, 0)
                .content(R.string.deleting)
                .build()
    }

}