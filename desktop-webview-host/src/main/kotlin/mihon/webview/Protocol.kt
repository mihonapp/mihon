package mihon.webview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.PrintStream

internal val wireJson = Json { ignoreUnknownKeys = true }
internal fun JsonObject.string(name: String): String = get(name)?.jsonPrimitive?.contentOrNull.orEmpty()
internal class SessionIdentity(val sessionId: String, val sourceId: Long, val token: String) {
    fun accepts(message: JsonObject): Boolean = message.string("sessionId") == sessionId &&
        message.string("sourceId") == sourceId.toString() && message.string("token") == token
}
internal class Events(private val identity: SessionIdentity, private val output: PrintStream = System.out) {
    @Synchronized fun send(type: String, id: String = "", value: String = "") {
        output.println(
            "MIHON_WEBVIEW " + buildJsonObject {
                put("sessionId", identity.sessionId)
                put("sourceId", identity.sourceId)
                put("type", type)
                put("id", id)
                put("value", value)
            },
        )
        output.flush()
    }
}
