package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.util.lang.launchIO

class MyAnimeListLoginActivity : BaseOAuthLoginActivity() {

    override fun handleResult(data: Uri?) {
        val code = data?.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                trackManager.myAnimeList.login(code)
                returnToSettings()
            }
        } else {
            trackManager.myAnimeList.logout()
            returnToSettings()
        }
    }
}
