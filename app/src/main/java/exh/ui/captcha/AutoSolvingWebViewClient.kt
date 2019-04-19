package exh.ui.captcha

import android.os.Build
import android.support.annotation.RequiresApi
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import eu.kanade.tachiyomi.util.asJsoup
import exh.ui.captcha.SolveCaptchaActivity.Companion.CROSS_WINDOW_SCRIPT_INNER
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Element
import java.nio.charset.Charset

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class AutoSolvingWebViewClient(activity: SolveCaptchaActivity,
                               source: CaptchaCompletionVerifier,
                               injectScript: String?,
                               private val headers: Map<String, String>)
    : BasicWebViewClient(activity, source, injectScript) {

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        // Inject our custom script into the recaptcha iframes
        val lastPathSegment = request.url.pathSegments.lastOrNull()
        if(lastPathSegment == "anchor" ||  lastPathSegment == "bframe") {
            val oReq = request.toOkHttpRequest()
            val response = activity.httpClient.newCall(oReq).execute()
            val doc = response.asJsoup()
            doc.body().appendChild(Element("script").appendChild(DataNode(CROSS_WINDOW_SCRIPT_INNER)))
            return WebResourceResponse(
                    "text/html",
                    "UTF-8",
                    doc.toString().byteInputStream(Charset.forName("UTF-8")).buffered()
            )
        }
        if(headers.isNotEmpty()) {
            val response = activity.httpClient.newCall(request.toOkHttpRequest()
                    .newBuilder()
                    .apply {
                        headers.forEach { (n, v) -> addHeader(n, v) }
                    }
                    .build())
                    .execute()

            return WebResourceResponse(
                    response.body()?.contentType()?.let { "${it.type()}/${it.subtype()}" },
                    response.body()?.contentType()?.charset()?.toString(),
                    response.code(),
                    response.message(),
                    response.headers().toMultimap().mapValues { it.value.joinToString(",") },
                    response.body()?.byteStream()
            )
        }
        return super.shouldInterceptRequest(view, request)
    }
}