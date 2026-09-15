package mihon.extension.host

import mihon.extension.ipc.WebViewRequest
import mihon.extension.ipc.WebViewResponse

object WebViewBridge {
    @Volatile private var handler: (suspend (WebViewRequest) -> WebViewResponse)? = null
    fun install(handler: suspend (WebViewRequest) -> WebViewResponse) {
        this.handler = handler
    }
    suspend fun execute(request: WebViewRequest): WebViewResponse =
        (handler ?: error("Desktop browser handler is unavailable"))(request).also {
            check(it.success) { it.error ?: "Browser operation failed" }
        }
    fun identity(): ExtensionExecutionContext.Identity = ExtensionExecutionContext.Identity(
        ExtensionExecutionContext.currentPackageId(),
        ExtensionExecutionContext.currentSourceId(),
        ExtensionExecutionContext.currentApplication(),
        ExtensionExecutionContext.currentPriority(),
    )
    fun request(action: String, sessionId: String = "", url: String = "", value: String = ""): WebViewRequest {
        val identity = identity()
        return WebViewRequest(
            action,
            requireNotNull(identity.packageId) { "WebView requires an extension identity" },
            requireNotNull(identity.sourceId) { "WebView requires a source identity" },
            sessionId,
            url,
            value = value,
        )
    }
}
