package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.comick.ComickMissingScopesException
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class TrackLoginActivity : BaseOAuthLoginActivity() {

    override fun handleResult(uri: Uri) {
        val authority = uri.encodedAuthority
        val scheme = if (authority == "comick-auth") "app.mihon" else "mihon"
        val path = uri.encodedPath.orEmpty()
        if (uri.scheme != scheme || (path.isNotEmpty() && path != "/")) {
            return finish()
        }

        val parameters = uri.encodedQuery?.takeIf { it.isNotEmpty() } ?: uri.encodedFragment.orEmpty()
        val data = buildMap<String, String> {
            for (parameter in parameters.split('&')) {
                if (parameter.isEmpty()) continue
                val key = Uri.decode(parameter.substringBefore('='))
                val value = Uri.decode(parameter.substringAfter('=', ""))
                if (put(key, value) != null) return finish()
            }
        }

        val oauthState = when (authority) {
            "anilist-auth" -> AnilistApi.oauthState
            "bangumi-auth" -> BangumiApi.oauthState
            "shikimori-auth" -> ShikimoriApi.oauthState
            else -> null
        }
        if (oauthState != null && !oauthState.consume(data["state"])) return finish()

        if ("error" in data) return returnToSettings()

        lifecycleScope.launch {
            when (authority) {
                "anilist-auth" -> trackerManager.aniList.run { loginFromCallback(data["access_token"], ::login) }
                "comick-auth" -> handleComick(data["code"], data["state"])
                "bangumi-auth" -> trackerManager.bangumi.run { loginFromCallback(data["code"], ::login) }
                "mangabaka-auth" -> handleMangaBaka(data["code"], data["state"])
                "myanimelist-auth" -> trackerManager.myAnimeList.run { loginFromCallback(data["code"], ::login) }
                "shikimori-auth" -> trackerManager.shikimori.run { loginFromCallback(data["code"], ::login) }
                "hikka-auth" -> trackerManager.hikka.run { loginFromCallback(data["reference"], ::login) }
                else -> return@launch finish()
            }
            returnToSettings()
        }
    }

    private suspend fun Tracker.loginFromCallback(credential: String?, login: suspend (String) -> Unit) {
        if (!credential.isNullOrBlank() && !isLoggedIn) login(credential)
    }

    private suspend fun handleComick(code: String?, state: String?) {
        trackerManager.comick.run {
            loginFromCallback(code) {
                if (state.isNullOrBlank() || !consumeOAuthState(state)) {
                    logcat(LogPriority.WARN) { "Missing or invalid OAuth state from Comick" }
                    return@loginFromCallback
                }
                try {
                    login(it)
                } catch (e: ComickMissingScopesException) {
                    withContext(Dispatchers.Main) {
                        this@TrackLoginActivity.toast(e.message, Toast.LENGTH_LONG)
                    }
                }
            }
        }
    }

    private suspend fun handleMangaBaka(code: String?, state: String?) {
        trackerManager.mangaBaka.run {
            loginFromCallback(code) {
                if (state.isNullOrBlank() || !consumeOAuthState(state)) {
                    logcat(LogPriority.WARN) { "Missing or invalid OAuth state from MangaBaka" }
                    return@loginFromCallback
                }
                login(it)
            }
        }
    }
}
