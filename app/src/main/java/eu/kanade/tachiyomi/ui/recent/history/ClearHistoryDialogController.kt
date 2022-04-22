package eu.kanade.tachiyomi.ui.recent.history

import android.app.Dialog
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.DialogController

class ClearHistoryDialogController : DialogController() {
    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(activity!!)
            .setMessage(R.string.clear_history_confirmation)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                (targetController as? HistoryController)
                    ?.presenter
                    ?.deleteAllHistory()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }
}
