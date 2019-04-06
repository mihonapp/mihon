package exh.ui.captcha

import android.os.Build
import android.support.annotation.RequiresApi
import android.webkit.WebResourceRequest
import okhttp3.Request

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun WebResourceRequest.toOkHttpRequest(): Request {
    val request = Request.Builder()
            .url(url.toString())
            .method(method, null)

    requestHeaders.entries.forEach { (t, u) ->
        request.addHeader(t, u)
    }

    return request.build()
}