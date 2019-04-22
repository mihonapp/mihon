package exh.patch

import android.content.Context
import exh.ui.captcha.BrowserActionActivity
import exh.util.interceptAsHtml
import okhttp3.OkHttpClient

fun OkHttpClient.Builder.detectCaptchas(context: Context, sourceId: Long, domains: List<String>? = null): OkHttpClient.Builder {
    return addInterceptor { chain ->
        // Automatic captcha detection
        val response = chain.proceed(chain.request())
        if(!response.isSuccessful) {
            if(domains != null && response.request().url().host() !in domains)
                return@addInterceptor response

            response.interceptAsHtml { doc ->
                if (doc.getElementsByClass("g-recaptcha").isNotEmpty()) {
                    // Found it, allow the user to solve this thing
                    BrowserActionActivity.launchUniversal(
                            context,
                            sourceId,
                            chain.request().url().toString()
                    )
                }
            }
        } else response
    }
}