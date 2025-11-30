package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class TrackLoginActivity : BaseOAuthLoginActivity() {

    override fun handleResult(uri: Uri) {
        val data = when {
            !uri.encodedQuery.isNullOrBlank() -> uri.encodedQuery
            !uri.encodedFragment.isNullOrBlank() -> uri.encodedFragment
            else -> null
        }
            ?.split("&")
            ?.filter { it.isNotBlank() }
            ?.associate {
                val parts = it.split("=", limit = 2).map<String, String>(Uri::decode)
                parts[0] to parts.getOrNull(1)
            }
            .orEmpty()

        lifecycleScope.launch {
            when (uri.host) {
                "anilist-auth" -> handleAniList(data["access_token"])
                "bangumi-auth" -> handleBangumi(data["code"])
                "myanimelist-auth" -> handleMyAnimeList(data["code"])
                "shikimori-auth" -> handleShikimori(data["code"])
            }
            returnToSettings()
        }
    }

    private suspend fun handleAniList(accessToken: String?) {
        if (accessToken != null) {
            trackerManager.aniList.login(accessToken)
        } else {
            trackerManager.aniList.logout()
        }
    }

    private suspend fun handleBangumi(code: String?) {
        if (code != null) {
            trackerManager.bangumi.login(code)
        } else {
            trackerManager.bangumi.logout()
        }
    }

    private suspend fun handleMyAnimeList(code: String?) {
        if (code != null) {
            trackerManager.myAnimeList.login(code)
        } else {
            trackerManager.myAnimeList.logout()
        }
    }

    private suspend fun handleShikimori(code: String?) {
        if (code != null) {
            trackerManager.shikimori.login(code)
        } else {
            trackerManager.shikimori.logout()
        }
    }
}
