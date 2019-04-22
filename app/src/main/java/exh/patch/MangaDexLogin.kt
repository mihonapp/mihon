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

val MANGADEX_LOGIN_PATCH: EHInterceptor = { request, response, sourceId ->
    if(request.url().host() == MANGADEX_DOMAIN) {
        response.interceptAsHtml { doc ->
            if (doc.title().trim().equals("Login - MangaDex", true)) {
                BrowserActionActivity.launchAction(
                        Injekt.get<Application>(),
                        ::verifyComplete,
                        HIDE_SCRIPT,
                        "https://mangadex.org/login",
                        "Login",
                        (Injekt.get<SourceManager>().get(sourceId) as? HttpSource)?.headers?.toMultimap()?.mapValues {
                            it.value.joinToString(",")
                        } ?: emptyMap()
                )
            }
        }
    } else response
}

val MANGADEX_SOURCE_IDS = listOf(
        2499283573021220255,
        8033579885162383068,
        1952071260038453057,
        2098905203823335614,
        5098537545549490547,
        4505830566611664829,
        9194073792736219759,
        6400665728063187402,
        4938773340256184018,
        5860541308324630662,
        5189216366882819742,
        2655149515337070132,
        1145824452519314725,
        3846770256925560569,
        3807502156582598786,
        4284949320785450865,
        5463447640980279236,
        8578871918181236609,
        6750440049024086587,
        3339599426223341161,
        5148895169070562838,
        1493666528525752601,
        1713554459881080228,
        4150470519566206911,
        1347402746269051958,
        3578612018159256808,
        425785191804166217,
        8254121249433835847,
        3260701926561129943,
        1411768577036936240,
        3285208643537017688,
        737986167355114438,
        1471784905273036181,
        5967745367608513818,
        3781216447842245147,
        4774459486579224459,
        4710920497926776490,
        5779037855201976894
)
const val MANGADEX_DOMAIN = "mangadex.org"
