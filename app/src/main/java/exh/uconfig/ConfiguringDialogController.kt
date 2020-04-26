package exh.uconfig

import android.app.Dialog
import android.os.Bundle
import android.view.View
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.toast
import kotlin.concurrent.thread
import timber.log.Timber

class ConfiguringDialogController : DialogController() {
    private var materialDialog: MaterialDialog? = null

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        if (savedViewState == null)
            thread {
                try {
                    EHConfigurator().configureAll()
                    launchUI {
                        activity?.toast("Settings successfully uploaded!")
                    }
                } catch (e: Exception) {
                    activity?.let {
                        it.runOnUiThread {
                            MaterialDialog(it)
                                    .title(text = "Configuration failed!")
                                    .message(text = "An error occurred during the configuration process: " + e.message)
                                    .positiveButton(android.R.string.ok)
                                    .show()
                        }
                    }
                    Timber.e(e, "Configuration error!")
                }
                launchUI {
                    finish()
                }
            }

        return MaterialDialog(activity!!)
                .title(text = "Uploading settings to server")
                .message(text = "Please wait, this may take some time...")
                .cancelable(false)
                .also {
            materialDialog = it
        }
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        materialDialog = null
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        finish()
    }

    fun finish() {
        router.popController(this)
    }
}
