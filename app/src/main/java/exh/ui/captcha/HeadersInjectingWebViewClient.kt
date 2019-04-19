package exh.ui.captcha

import android.os.Build
import android.support.annotation.RequiresApi
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
open class HeadersInjectingWebViewClient(activity: BrowserActionActivity,
                                         verifyComplete: (String) -> Boolean,
                                         injectScript: String?,
                                         private val headers: Map<String, String>)
    : BasicWebViewClient(activity, verifyComplete, injectScript) {

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        // Temp disabled as it's unreliable
        /*if(headers.isNotEmpty()) {
            val response = activity.httpClient.newCall(request.toOkHttpRequest()
                    .newBuilder()
                    .apply {
                        headers.forEach { (n, v) -> header(n, v) }
                    }
                    .build())
                    .execute()

            return WebResourceResponse(
                    response.body()?.contentType()?.let { "${it.type()}/${it.subtype()}" },
                    response.body()?.contentType()?.charset()?.toString(),
                    response.code(),
                    response.message().nullIfBlank() ?: FALLBACK_REASON_PHRASES[response.code()] ?: "Unknown status",
                    response.headers().toMultimap().mapValues { it.value.joinToString(",") },
                    response.body()?.byteStream()
            )
        }*/
        return super.shouldInterceptRequest(view, request)
    }

    companion object {
        private val FALLBACK_REASON_PHRASES = mapOf(
                100 to "Continue",
                101 to "Switching Protocols",
                200 to "OK",
                201 to "Created",
                202 to "Accepted",
                203 to "Non-Authoritative Information",
                204 to "No Content",
                205 to "Reset Content",
                206 to "Partial Content",
                300 to "Multiple Choices",
                301 to "Moved Permanently",
                302 to "Moved Temporarily",
                303 to "See Other",
                304 to "Not Modified",
                305 to "Use Proxy",
                400 to "Bad Request",
                401 to "Unauthorized",
                402 to "Payment Required",
                403 to "Forbidden",
                404 to "Not Found",
                405 to "Method Not Allowed",
                406 to "Not Acceptable",
                407 to "Proxy Authentication Required",
                408 to "Request Time-out",
                409 to "Conflict",
                410 to "Gone",
                411 to "Length Required",
                412 to "Precondition Failed",
                413 to "Request Entity Too Large",
                414 to "Request-URI Too Large",
                415 to "Unsupported Media Type",
                500 to "Internal Server Error",
                501 to "Not Implemented",
                502 to "Bad Gateway",
                503 to "Service Unavailable",
                504 to "Gateway Time-out",
                505 to "HTTP Version not supported"
        )
    }
}
