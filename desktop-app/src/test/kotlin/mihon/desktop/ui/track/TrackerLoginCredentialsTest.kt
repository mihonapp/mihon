package mihon.desktop.ui.track

import mihon.desktop.track.TrackerAuthType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class TrackerLoginCredentialsTest {
    @Test
    fun `credentials login does not mislabel password as an access token`() {
        val values = trackerLoginCredentials(TrackerAuthType.CREDENTIALS, " reader ", " secret ", "")

        assertEquals("reader", values["username"])
        assertEquals(" secret ", values["password"])
        assertFalse(values.containsKey("token"))
    }

    @Test
    fun `server API key and password modes remain distinguishable`() {
        val apiKey = trackerLoginCredentials(TrackerAuthType.SERVER, "", " key ", " http://server ")
        val password = trackerLoginCredentials(TrackerAuthType.SERVER, "user", " pass ", "http://server")

        assertEquals("key", apiKey["token"])
        assertFalse(apiKey.containsKey("password"))
        assertEquals(" pass ", password["password"])
        assertFalse(password.containsKey("token"))
    }
}
