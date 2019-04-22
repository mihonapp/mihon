package exh.util

import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import okhttp3.ResponseBody
import org.jsoup.nodes.Document

fun Response.interceptAsHtml(block: (Document) -> Unit): Response {
    val body = body()
    if (body?.contentType()?.type() == "text"
            && body.contentType()?.subtype() == "html") {
        val bodyString = body.string()
        val rebuiltResponse = newBuilder()
                .body(ResponseBody.create(body.contentType(), bodyString))
                .build()
        try {
            // Search for captcha
            val parsed = asJsoup(html = bodyString)
            block(parsed)
        } catch (t: Throwable) {
            // Ignore all errors
            XLog.w("Interception error!", t)
        } finally {
            close()
        }

        return rebuiltResponse
    }
    return this
}