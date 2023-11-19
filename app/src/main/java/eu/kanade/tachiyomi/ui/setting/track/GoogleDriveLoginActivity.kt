package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveService
import tachiyomi.core.i18n.stringResource
import tachiyomi.core.util.lang.launchIO
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GoogleDriveLoginActivity : BaseOAuthLoginActivity() {
    private val googleDriveService = Injekt.get<GoogleDriveService>()
    override fun handleResult(data: Uri?) {
        val code = data?.getQueryParameter("code")
        val error = data?.getQueryParameter("error")
        if (code != null) {
            lifecycleScope.launchIO {
                googleDriveService.handleAuthorizationCode(
                    code,
                    this@GoogleDriveLoginActivity,
                    onSuccess = {
                        Toast.makeText(
                            this@GoogleDriveLoginActivity,
                            stringResource(MR.strings.google_drive_login_success),
                            Toast.LENGTH_LONG,
                        ).show()

                        returnToSettings()
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this@GoogleDriveLoginActivity,
                            stringResource(MR.strings.google_drive_login_failed, error),
                            Toast.LENGTH_LONG,
                        ).show()
                        returnToSettings()
                    },
                )
            }
        } else if (error != null) {
            Toast.makeText(
                this@GoogleDriveLoginActivity,
                stringResource(MR.strings.google_drive_login_failed, error),
                Toast.LENGTH_LONG,
            ).show()

            returnToSettings()
        } else {
            returnToSettings()
        }
    }
}
