package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import tachiyomi.core.common.util.lang.launchIO

class TrackLoginActivity : BaseOAuthLoginActivity() {

    override fun handleResult(data: Uri?) {
        when (data?.host) {
            "anilist-auth" -> handleAnilist(data)
            "bangumi-auth" -> handleBangumi(data)
            "myanimelist-auth" -> handleMyAnimeList(data)
            "shikimori-auth" -> handleShikimori(data)
            "hikka-auth" -> handleHikka(data)
        }
    }

    private fun handleAnilist(data: Uri) {
        val regex = "(?:access_token=)(.*?)(?:&)".toRegex()
        val matchResult = regex.find(data.fragment.toString())
        if (matchResult?.groups?.get(1) != null) {
            lifecycleScope.launchIO {
                trackerManager.aniList.login(matchResult.groups[1]!!.value)
                returnToSettings()
            }
        } else {
            trackerManager.aniList.logout()
            returnToSettings()
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

    private fun handleHikka(data: Uri) {
        val reference = data.getQueryParameter("reference")
        if (reference != null) {
            lifecycleScope.launchIO {
                trackerManager.hikka.login(reference)
                returnToSettings()
            }
        } else {
            trackerManager.hikka.logout()
            returnToSettings()
        }
    }
}
