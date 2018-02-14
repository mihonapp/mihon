package exh.uconfig

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.bluelinelabs.conductor.Router
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class WarnConfigureDialogController : DialogController() {
    private val prefs: PreferencesHelper by injectLazy()
    override fun onCreateDialog(savedState: Bundle?): Dialog {
        return MaterialDialog.Builder(activity!!)
                .title("Settings profile note")
                .content("""
                    The app will now add a new settings profile on E-Hentai and ExHentai to optimize app performance. Please ensure that you have less than three profiles on both sites.

                    If you have no idea what settings profiles are, then it probably doesn't matter, just hit 'OK'.
                    """.trimIndent())
                .positiveText(android.R.string.ok)
                .onPositive { _, _ ->
                    prefs.eh_showSettingsUploadWarning().set(false)
                    ConfiguringDialogController().showDialog(router)
                }
                .cancelable(false)
                .build()
    }

    companion object {
        fun uploadSettings(router: Router) {
            if(Injekt.get<PreferencesHelper>().eh_showSettingsUploadWarning().getOrDefault())
                WarnConfigureDialogController().showDialog(router)
            else
                ConfiguringDialogController().showDialog(router)
        }
    }
}