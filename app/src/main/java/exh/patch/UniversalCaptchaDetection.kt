package exh.patch

import android.app.Application
import exh.ui.captcha.BrowserActionActivity
import exh.util.interceptAsHtml
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val CAPTCHA_DETECTION_PATCH: EHInterceptor = { request, response, sourceId ->
    if(!response.isSuccessful) {
        response.interceptAsHtml { doc ->
            // Find captcha
            if (doc.getElementsByClass("g-recaptcha").isNotEmpty()) {
                // Found it, allow the user to solve this thing
                BrowserActionActivity.launchUniversal(
                        Injekt.get<Application>(),
                        sourceId,
                        request.url().toString()
                )
            }
        }
    } else response
}