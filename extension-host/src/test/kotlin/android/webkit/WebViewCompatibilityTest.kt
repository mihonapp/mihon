package android.webkit

import android.app.Application
import mihon.extension.host.ExtensionExecutionContext
import mihon.extension.host.WebViewBridge
import mihon.extension.ipc.WebViewRequest
import mihon.extension.ipc.WebViewResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class WebViewCompatibilityTest {
    @Test fun `extension load evaluate cookie destroy routes original identity`() {
        val calls = CopyOnWriteArrayList<WebViewRequest>()
        WebViewBridge.install { request ->
            calls += request
            WebViewResponse(
                true,
                if (request.action ==
                    "evaluate"
                ) {
                    "42"
                } else {
                    "cookie=value"
                },
            )
        }
        val identity = ExtensionExecutionContext.Identity("extension.test", 73, Application())
        val finished = CompletableFuture<Unit>()
        val evaluated = CompletableFuture<String>()
        lateinit var view: WebView
        ExtensionExecutionContext.duringConstruction(identity) {
            view = WebView(identity.application)
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    assertEquals("extension.test", ExtensionExecutionContext.currentPackageId())
                    view!!.evaluateJavascript("6*7") { value -> evaluated.complete(value) }
                    finished.complete(Unit)
                }
            }
            view.loadUrl("https://example.org/", mapOf("X-Test" to "yes"))
        }
        finished.get(5, TimeUnit.SECONDS)
        assertEquals("42", evaluated.get(5, TimeUnit.SECONDS))
        ExtensionExecutionContext.duringConstruction(identity) {
            assertEquals("cookie=value", CookieManager.getInstance().getCookie("https://example.org/"))
            view.destroy()
        }
        assertEquals(listOf("open", "evaluate", "getCookie", "destroy"), calls.map { it.action })
        assertTrue(calls.all { it.sourceId == 73L && it.extensionId == "extension.test" })
        assertEquals("yes", calls.first().headers["X-Test"])
    }

    @Test fun `coroutine main dispatcher retains extension identity`() = kotlinx.coroutines.runBlocking {
        val identity = ExtensionExecutionContext.Identity("extension.main", 84, Application())
        ExtensionExecutionContext.withIdentity(identity) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                assertEquals(84L, ExtensionExecutionContext.currentSourceId())
                assertTrue(Thread.currentThread().name.startsWith("android-main-compat"))
            }
        }
    }
}
