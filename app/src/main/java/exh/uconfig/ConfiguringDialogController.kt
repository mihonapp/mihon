package exh.uconfig

import android.app.Dialog
import android.os.Bundle
import android.view.View
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.launchUI
import eu.kanade.tachiyomi.util.toast
import timber.log.Timber
import kotlin.concurrent.thread

class ConfiguringDialogController : DialogController() {
    private var materialDialog: MaterialDialog? = null

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        if(savedViewState == null)
            thread {
                try {
                    EHConfigurator().configureAll()
                    launchUI {
                        activity?.toast("Settings successfully uploaded!")
                    }
                } catch (e: Exception) {
                    activity?.let {
                        it.runOnUiThread {
                            MaterialDialog.Builder(it)
                                    .title("Configuration failed!")
                                    .content("An error occurred during the configuration process: " + e.message)
                                    .positiveText("Ok")
                                    .show()
                        }
                    }
                    Timber.e(e, "Configuration error!")
                }
                launchUI {
                    finish()
                }
            }

        return MaterialDialog.Builder(activity!!)
                .title("Uploading settings to server")
                .content("Please wait, this may take some time...")
                .progress(true, 0)
                .cancelable(false)
                .build().also {
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

