@file:Suppress("ktlint:standard:max-line-length")

package mihon.desktop.track

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class DesktopMangaUpdatesTrackerApiTest {
    private lateinit var server: HttpServer

    @AfterEach
    fun stop() {
        if (::server.isInitialized) server.stop(0)
    }

    @Test
    fun `MangaUpdates exchanges password for session and syncs list`() = runBlocking {
        val requests = mutableListOf<TrackerTestRecordedRequest>()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val req = exchange.recordTrackerTestRequest()
            requests += req
            val body = when (req.path) {
                "/v1/account/login" -> """{"context":{"session_token":"session","uid":7}}"""
                "/v1/account/profile" -> """{"username":"Reader"}"""
                "/v1/series/search" -> """{"results":[{"record":{"series_id":42,"title":"One Piece","url":"https://www.mangaupdates.com/series/42","latest_chapter":100}}]}"""
                "/v1/lists/series/42" -> """{"list_id":0,"status":{"chapter":12}}"""
                "/v1/series/42/rating" -> """{"rating":8.5}"""
                else -> "{}"
            }
            exchange.respondTrackerTestJson(body)
        }
        server.start()
        val tracker = MangaUpdatesTracker("http://127.0.0.1:${server.address.port}", trackerTestHttpClient())
        assertTrue(tracker.login(mapOf("username" to "reader", "password" to "pass")))
        assertEquals("session", tracker.persistenceToken)
        val result = tracker.search("one piece").single()
        val remote = tracker.findRemote(
            DesktopTrackRecord(mangaId = 1, trackerId = 7, remoteId = result.remoteId, title = result.title),
        )!!
        assertEquals(12.0, remote.lastChapterRead)
        assertEquals(8.5, remote.score)
        tracker.updateRemote(remote.copy(lastChapterRead = 20.0))
        assertTrue(
            requests.filter { it.path != "/v1/account/login" && it.path != "/v1/series/search" }.all {
                it.authorization ==
                    "Bearer session"
            },
        )
    }
}
