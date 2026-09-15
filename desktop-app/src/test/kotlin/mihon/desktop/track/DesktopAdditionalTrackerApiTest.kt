package mihon.desktop.track

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class DesktopAdditionalTrackerApiTest {
    @Test fun `mangabaka uses title priorities bearer profile and library put contract`() = runBlocking {
        val requests = mutableListOf<TrackerTestRecordedRequest>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val request = exchange.recordTrackerTestRequest()
            requests += request
            exchange.respondTrackerTestJson(
                when {
                    request.path == "/v1/my/profile" -> """{"data":{"id":"u","preferred_username":"user"}}"""
                    request.path == "/v1/series/search" -> (
"""{"data":[{"id":42,"titles":[{"language":"ja","title":"JP"},{"language":"en","title":"""" +
"""English","is_primary":true}],"cover":{"x250":{"x1":"cover"}}}]}"""
                        )
                    request.method == "GET" ->
                        """{"data":{"state":"paused","progress_chapter":4,"rating":80,"is_private":true}}"""
                    else -> """{"data":true}"""
                },
            )
        }
        server.start()
        try {
            val tracker = MangaBakaTracker("http://127.0.0.1:${server.address.port}")
            assertTrue(tracker.login(mapOf("token" to "secret")))
            assertEquals("English", tracker.search("a b").single().title)
            val track =
                DesktopTrackRecord(
                    mangaId = 1,
                    trackerId = 11,
                    remoteId = 42,
                    title = "English",
                    lastChapterRead = 5.5,
                    status = 3,
                    score = 80.0,
                    private = true,
                )
            assertEquals(4.0, tracker.findRemote(track)!!.lastChapterRead)
            tracker.updateRemote(track)
            val put = requests.last()
            assertEquals("PUT", put.method)
            assertEquals("Bearer secret", put.authorization)
            val payload = Json.parseToJsonElement(put.body).jsonObject
            assertEquals("paused", payload["state"]!!.jsonPrimitive.content)
            assertEquals(5.5, payload["progress_chapter"]!!.jsonPrimitive.double)
        } finally {
            server.stop(0)
        }
    }

    @Test fun `hikka uses auth header slug read endpoint and preserves rereads`() = runBlocking {
        val requests = mutableListOf<TrackerTestRecordedRequest>()
        val auth = mutableListOf<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            auth += exchange.requestHeaders.getFirst("auth")
            val request = exchange.recordTrackerTestRequest()
            requests += request
            exchange.respondTrackerTestJson(
                when (request.path) {
                    "/user/me" -> """{"username":"user"}"""
                    "/manga" -> """{"list":[{"slug":"one","title_ua":"UA","title_en":"EN","chapters":100}]}"""
                    else -> """{"status":"reading","chapters":5,"score":8,"rereads":3,"volumes":2,"note":"keep"}"""
                },
            )
        }
        server.start()
        try {
            val tracker = HikkaTracker("http://127.0.0.1:${server.address.port}")
            assertTrue(tracker.login(mapOf("token" to "secret")))
            val match = tracker.search("one").single()
            assertEquals("UA", match.title)
            val track =
                DesktopTrackRecord(
                    mangaId = 1,
                    trackerId = 10,
                    remoteId = match.remoteId,
                    title = match.title,
                    trackingUrl = match.trackingUrl,
                    lastChapterRead = 6.0,
                    status = 6,
                )
            tracker.updateRemote(track)
            assertTrue(auth.all { it == "secret" })
            val request = requests.last()
            assertEquals("/read/manga/one", request.path)
            assertEquals("PUT", request.method)
            val body = Json.parseToJsonElement(request.body).jsonObject
            assertEquals(3, body["rereads"]!!.jsonPrimitive.int)
            assertEquals("keep", body["note"]!!.jsonPrimitive.content)
            assertEquals("reading", body["status"]!!.jsonPrimitive.content)
        } finally {
            server.stop(0)
        }
    }
}
