package mihon.desktop.track

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class DesktopShikimoriTrackerApiTest {
    private lateinit var server: HttpServer
    private lateinit var tracker: ShikimoriTracker
    private val requests = mutableListOf<TrackerTestRecordedRequest>()

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val request = exchange.recordTrackerTestRequest()
            requests += request
            val body = when {
                request.path == "/api/graphql" && request.body.contains("currentUser") ->
                    """{"data":{"currentUser":{"id":"7","nickname":"Reader"}}}"""
                request.path == "/api/graphql" && request.body.contains("userRate") ->
                    """{"data":{"mangas":[{"id":"42","url":"/mangas/42-one-piece","name":"One Piece","chapters":100,"userRate":{"id":"91","chapters":12,"status":"watching","score":8}}]}}"""
                request.path == "/api/graphql" ->
                    """{"data":{"mangas":[{"id":"42","name":"One Piece","chapters":100,"poster":{"mainUrl":"https://img/42.jpg"},"url":"/mangas/42-one-piece","description":"Pirates"}]}}"""
                request.path == "/api/v2/user_rates" -> """{"id":91}"""
                request.path == "/api/v2/user_rates/91" -> """{"id":91}"""
                else -> "{}"
            }
            exchange.respondTrackerTestJson(body)
        }
        server.start()
        tracker = ShikimoriTracker(
            baseUrl = "http://127.0.0.1:${server.address.port}",
            httpClient = trackerTestHttpClient(),
        )
    }

    @AfterEach
    fun tearDown() = server.stop(0)

    @Test
    fun `Shikimori validates token and supports search refresh add and update`() = runBlocking {
        assertTrue(tracker.login(mapOf("token" to "access-token")))
        assertEquals("Reader", tracker.username)
        val found = tracker.search("one piece").single()
        assertEquals(42L, found.remoteId)

        val local = DesktopTrackRecord(1L, 1L, 4L, 42L, title = found.title, trackingUrl = found.trackingUrl)
        val remote = tracker.findRemote(local)!!
        assertEquals(91L, remote.libraryId)
        assertEquals(12.0, remote.lastChapterRead)
        assertEquals(TrackStatus.READING.value, remote.status)

        assertEquals(91L, tracker.updateRemote(local.copy(lastChapterRead = 4.0)).libraryId)
        assertEquals(91L, tracker.updateRemote(remote.copy(lastChapterRead = 20.0)).libraryId)
        assertTrue(requests.all { it.authorization == "Bearer access-token" })
        assertTrue(requests.any { it.path == "/api/v2/user_rates" && it.body.contains("\"user_id\":\"7\"") })
        assertTrue(requests.any { it.path == "/api/v2/user_rates/91" && it.method == "PUT" })
    }
}
