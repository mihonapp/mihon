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
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun execute(request: BrokerHttpRequest): BrokerHttpResponse {
        val session = sessionProvider() ?: throw IpcException("IPC session is not active")
        val payload = json.encodeToString(request)
        val resJson = session.sendCallback(IpcCallbacks.BROKER_HTTP, payload)
        val response = json.decodeFromString<BrokerHttpResponse>(resJson)
        if (response.error != null) {
            throw IpcException("Brokered HTTP request failed: ${response.error}")
        }
        return response
    }

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val res = execute(BrokerHttpRequest(method = "GET", url = url, headers = headers))
        if (res.statusCode !in 200..299) {
            throw IpcException("HTTP GET $url failed with status code ${res.statusCode}")
        }
        return res.body ?: ""
    }

    suspend fun post(url: String, headers: Map<String, String> = emptyMap(), body: String? = null): String {
        val res = execute(BrokerHttpRequest(method = "POST", url = url, headers = headers, body = body))
        if (res.statusCode !in 200..299) {
            throw IpcException("HTTP POST $url failed with status code ${res.statusCode}")
        }
        return res.body ?: ""
    }
}
