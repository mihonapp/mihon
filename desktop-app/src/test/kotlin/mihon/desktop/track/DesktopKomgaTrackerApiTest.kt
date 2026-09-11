package mihon.desktop.track

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class DesktopKomgaTrackerApiTest {

    private lateinit var server: HttpServer
    private lateinit var tracker: KomgaTracker
    private val requests = mutableListOf<TrackerTestRecordedRequest>()

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val request = exchange.recordTrackerTestRequest()
            requests += request
            val body = when {
                request.path == "/api/v1/libraries" -> "[]"
                request.path == "/api/v1/series/list" ->
                    """
                    {"content":[{"id":"series-uuid-1","name":"One Piece folder","booksCount":1200,
                    "metadata":{"title":"One Piece","summary":"Pirates"}}]}
                    """.trimIndent()
                request.path == "/api/v1/series/series-uuid-1" ->
                    """{"id":"series-uuid-1","name":"One Piece folder","metadata":{"title":"One Piece"}}"""
                request.path == "/api/v2/series/series-uuid-1/read-progress/tachiyomi" ->
                    """{"booksCount":1200,"booksReadCount":10,"booksUnreadCount":1180,
                    "booksInProgressCount":10,"lastReadContinuousNumberSort":12.5,"maxNumberSort":1200}
                    """.trimIndent()
                else -> "{}"
            }
            exchange.respondTrackerTestJson(body)
        }
        server.start()
        tracker = KomgaTracker(httpClient = trackerTestHttpClient())
    }

    @AfterEach
    fun tearDown() = server.stop(0)

    @Test
    fun `Komga validates API key searches UUID series and syncs Mihon progress`() = runBlocking {
        val serverUrl = "http://127.0.0.1:${server.address.port}"
        assertTrue(tracker.login(mapOf("server_url" to serverUrl, "token" to "komga-api-key")))
        assertEquals("komga-api-key", tracker.persistenceToken)

        val result = tracker.search("one piece").single()
        assertEquals("One Piece", result.title)
        assertEquals("$serverUrl/api/v1/series/series-uuid-1", result.trackingUrl)

        val remote = tracker.findRemote(
            DesktopTrackRecord(
                mangaId = 1L,
                trackerId = tracker.id,
                remoteId = result.remoteId,
                title = result.title,
                trackingUrl = result.trackingUrl,
            ),
        )!!
        assertEquals(12.5, remote.lastChapterRead)
        assertEquals(1200L, remote.totalChapters)
        assertEquals(TrackStatus.READING.value, remote.status)

        val updated = tracker.updateRemote(remote.copy(lastChapterRead = 24.0))
        assertEquals(12.5, updated.lastChapterRead)
        assertTrue(requests.all { it.apiKey == "komga-api-key" })
        assertTrue(requests.any { it.path == "/api/v1/series/list" && it.body.contains("one piece") })
        assertTrue(
            requests.any {
                it.path == "/api/v2/series/series-uuid-1/read-progress/tachiyomi" &&
                    it.body.contains("lastBookNumberSortRead") && it.body.contains("24.0")
            },
        )
    }
}
