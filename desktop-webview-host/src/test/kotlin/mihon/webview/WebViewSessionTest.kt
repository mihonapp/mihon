package mihon.webview

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebViewSessionTest {
    @Test fun `wire rejects another source session or capability`() {
        val identity = SessionIdentity("one", 7, "secret")
        fun request(session: String, source: Long, token: String) = buildJsonObject {
            put("sessionId", session)
            put("sourceId", source)
            put("token", token)
        }
        assertTrue(identity.accepts(request("one", 7, "secret")))
        assertFalse(identity.accepts(request("two", 7, "secret")))
        assertFalse(identity.accepts(request("one", 8, "secret")))
        assertFalse(identity.accepts(request("one", 7, "wrong")))
    }
}
