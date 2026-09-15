package mihon.extension.ipc

import kotlinx.serialization.Serializable

object WebViewIpc {
    const val CALLBACK = "webview"
}

@Serializable
data class WebViewRequest(
    val action: String,
    val extensionId: String,
    val sourceId: Long,
    val sessionId: String,
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
    val script: String = "",
    val value: String = "",
)

@Serializable
data class WebViewResponse(val success: Boolean, val value: String = "", val error: String? = null)
