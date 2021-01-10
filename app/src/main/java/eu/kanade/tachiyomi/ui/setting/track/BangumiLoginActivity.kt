package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.util.lang.launchIO

class BangumiLoginActivity : BaseOAuthLoginActivity() {

    override fun handleResult(data: Uri?) {
        val code = data?.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                trackManager.bangumi.login(code)
                returnToSettings()
            }
        } else {
            trackManager.bangumi.logout()
            returnToSettings()
        }
    }
}
