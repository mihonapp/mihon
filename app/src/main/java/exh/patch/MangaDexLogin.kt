package exh.patch

import android.app.Application
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.ui.captcha.BrowserActionActivity
import exh.util.interceptAsHtml
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private val HIDE_SCRIPT = """
            document.querySelector("#forgot_button").style.visibility = "hidden";
            document.querySelector("#signup_button").style.visibility = "hidden";
            document.querySelector("#announcement").style.visibility = "hidden";
            document.querySelector("nav").style.visibility = "hidden";
            document.querySelector("footer").style.visibility = "hidden";
        """.trimIndent()

private fun verifyComplete(url: String): Boolean {
    return HttpUrl.parse(url)?.let { parsed ->
        parsed.host() == "mangadex.org" && parsed.pathSegments().none { it.isNotBlank() }
    } ?: false
}

fun OkHttpClient.Builder.attachMangaDexLogin() =
        addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if(response.request().url().host() == MANGADEX_DOMAIN) {
                response.interceptAsHtml { doc ->
                    if (doc.title().trim().equals("Login - MangaDex", true)) {
                        BrowserActionActivity.launchAction(
                                Injekt.get<Application>(),
                                ::verifyComplete,
                                HIDE_SCRIPT,
                                "https://mangadex.org/login",
                                "Login",
                                (Injekt.get<SourceManager>().get(MANGADEX_SOURCE_ID) as? HttpSource)?.headers?.toMultimap()?.mapValues {
                                    it.value.joinToString(",")
                                } ?: emptyMap()
                        )
                    }
                }
            } else response
        }

const val MANGADEX_SOURCE_ID = 2499283573021220255
const val MANGADEX_DOMAIN = "mangadex.org"
