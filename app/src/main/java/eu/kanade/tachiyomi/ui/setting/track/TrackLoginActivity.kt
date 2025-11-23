package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import tachiyomi.core.common.util.lang.launchIO
import java.net.URLDecoder

class TrackLoginActivity : BaseOAuthLoginActivity() {

    override fun handleResult(data: Uri?) {
        if (data?.host == "anilist-auth") {
            val queries = data.encodedFragment?.split("&")?.associate { part ->
                val (key, value) = part.split("=")
                URLDecoder.decode(key, "utf-8") to URLDecoder.decode(value, "utf-8")
            }
                .orEmpty()
            lifecycleScope.launchIO {
                trackerManager.onOAuthCallback(queries)
                returnToSettings()
            }
        } else {
            when (data?.host) {
                "bangumi-auth" -> handleBangumi(data)
                "myanimelist-auth" -> handleMyAnimeList(data)
                "shikimori-auth" -> handleShikimori(data)
            }
        }
    }

    private fun handleBangumi(data: Uri) {
        val code = data.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                trackerManager.bangumi.login(code)
                returnToSettings()
            }
        } else {
            trackerManager.bangumi.logout()
            returnToSettings()
        }
    }

    private fun handleMyAnimeList(data: Uri) {
        val code = data.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                trackerManager.myAnimeList.login(code)
                returnToSettings()
            }
        } else {
            trackerManager.myAnimeList.logout()
            returnToSettings()
        }
    }

    private fun handleShikimori(data: Uri) {
        val code = data.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                trackerManager.shikimori.login(code)
                returnToSettings()
            }
        } else {
            trackerManager.shikimori.logout()
            returnToSettings()
        }
    }
}
