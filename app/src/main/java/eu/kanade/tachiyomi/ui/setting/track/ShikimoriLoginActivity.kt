package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.util.lang.launchIO

class ShikimoriLoginActivity : BaseOAuthLoginActivity() {

    override fun handleResult(data: Uri?) {
        val code = data?.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                trackManager.shikimori.login(code)
                returnToSettings()
            }
        } else {
            trackManager.shikimori.logout()
            returnToSettings()
        }
    }
}
