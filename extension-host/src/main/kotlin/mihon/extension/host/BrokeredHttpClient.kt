package mihon.extension.host

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.extension.ipc.BrokerHttpRequest
import mihon.extension.ipc.BrokerHttpResponse
import mihon.extension.ipc.IpcCallbacks
import mihon.extension.ipc.IpcException
import mihon.extension.ipc.IpcSession

class BrokeredHttpClient(
    private val sessionProvider: () -> IpcSession?,
) {
    companion object {
        internal fun materialize(
            response: BrokerHttpResponse,
            directory: java.io.File = java.io.File("broker-responses"),
        ): BrokerHttpResponse {
            val name = response.bodyFileName ?: return response
            if (!Regex(
                    "broker-[0-9a-fA-F-]{36}\\.bin",
                ).matches(name)
            ) {
                throw IpcException("Invalid broker response filename")
            }
            val root = directory.toPath().toAbsolutePath().normalize()
            val path = root.resolve(name)
            if (java.nio.file.Files.isSymbolicLink(root) ||
                java.nio.file.Files.isSymbolicLink(path)
            ) {
                throw IpcException("Invalid broker response path")
            }
            try {
                if (java.nio.file.Files.size(path) >
                    64L * 1024 * 1024
                ) {
                    throw IpcException("Broker response exceeds limit")
                }
                val bytes = java.nio.file.Files.newInputStream(path, java.nio.file.LinkOption.NOFOLLOW_LINKS).use {
                    it.readNBytes(64 * 1024 * 1024 + 1)
                }
                if (bytes.size > 64 * 1024 * 1024) throw IpcException("Broker response exceeds limit")
                return response.copy(
                    bodyBase64 = java.util.Base64.getEncoder().encodeToString(bytes),
                    bodyFileName = null,
                )
            } finally {
                java.nio.file.Files.deleteIfExists(path)
            }
        }
    }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun webView(request: mihon.extension.ipc.WebViewRequest): mihon.extension.ipc.WebViewResponse {
        val session = sessionProvider() ?: throw IpcException("IPC session is not active")
        val response = session.sendCallback(mihon.extension.ipc.WebViewIpc.CALLBACK, json.encodeToString(request))
        return json.decodeFromString<mihon.extension.ipc.WebViewResponse>(response).also {
            if (!it.success) throw IpcException(it.error ?: "Browser operation failed")
        }
    }

    suspend fun execute(request: BrokerHttpRequest): BrokerHttpResponse {
        val session = sessionProvider() ?: throw IpcException("IPC session is not active")
        val payload = json.encodeToString(
            request.copy(
                extensionId = request.extensionId ?: ExtensionExecutionContext.currentPackageId(),
                priority = if (request.priority ==
                    mihon.extension.ipc.RequestPriority.NORMAL
                ) {
                    ExtensionExecutionContext.currentPriority()
                } else {
                    request.priority
                },
                sourceId = request.sourceId ?: ExtensionExecutionContext.currentSourceId().takeIf {
                    request.extensionId == null || request.extensionId == ExtensionExecutionContext.currentPackageId()
                },
            ),
        )
        val resJson = session.sendCallback(IpcCallbacks.BROKER_HTTP, payload)
        val response = json.decodeFromString<BrokerHttpResponse>(resJson)
        if (response.error != null) {
            throw IpcException("Brokered HTTP request failed: ${response.error}")
        }
        return materialize(response)
    }

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val res = execute(BrokerHttpRequest(method = "GET", url = url, headers = headers))
        if (res.statusCode !in 200..299) {
            throw IpcException("HTTP GET $url failed with status code ${res.statusCode}")
        }
        return res.bodyBase64?.let { java.util.Base64.getDecoder().decode(it).toString(Charsets.UTF_8) }
            ?: res.body.orEmpty()
    }

    suspend fun post(url: String, headers: Map<String, String> = emptyMap(), body: String? = null): String {
        val res = execute(BrokerHttpRequest(method = "POST", url = url, headers = headers, body = body))
        if (res.statusCode !in 200..299) {
            throw IpcException("HTTP POST $url failed with status code ${res.statusCode}")
        }
        return res.bodyBase64?.let { java.util.Base64.getDecoder().decode(it).toString(Charsets.UTF_8) }
            ?: res.body.orEmpty()
    }
}
