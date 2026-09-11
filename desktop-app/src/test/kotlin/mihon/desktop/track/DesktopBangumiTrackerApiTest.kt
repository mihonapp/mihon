package mihon.desktop.track

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class DesktopBangumiTrackerApiTest {
    private lateinit var server: HttpServer
    private lateinit var tracker: BangumiTracker
    private val requests = mutableListOf<TrackerTestRecordedRequest>()

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val request = exchange.recordTrackerTestRequest()
            requests += request
            val body = when {
                request.path == "/v0/me" -> """{"username":"reader","nickname":"Reader"}"""
                request.path == "/v0/search/subjects" ->
                    """{"data":[{"id":42,"name_cn":"海贼王","name":"One Piece","summary":"Pirates","eps":100,"platform":"漫画","images":{"common":"https://img/42.jpg"}}]}"""
                request.path == "/v0/users/reader/collections/42" ->
                    """{"rate":8,"type":3,"ep_status":12,"private":false,"subject":{"eps":100}}"""
                else -> "{}"
            }
            exchange.respondTrackerTestJson(
                body,
                if (request.method == "POST" &&
                    request.path.contains("collections")
                ) {
                    202
                } else {
                    200
                },
            )
        }
        server.start()
        tracker = BangumiTracker(
            baseUrl = "http://127.0.0.1:${server.address.port}",
            httpClient = trackerTestHttpClient(),
        )
    }

    @AfterEach
    fun tearDown() = server.stop(0)

    @Test
    fun `Bangumi validates token searches refreshes and writes collections`() = runBlocking {
        assertTrue(tracker.login(mapOf("token" to "access-token")))
        val result = tracker.search("one piece").single()
        assertEquals(42L, result.remoteId)
        val local = DesktopTrackRecord(mangaId = 1L, trackerId = 5L, remoteId = 42L, title = result.title)
        val remote = tracker.findRemote(local)!!
        assertEquals(12.0, remote.lastChapterRead)
        assertEquals(TrackStatus.READING.value, remote.status)
        tracker.updateRemote(local.copy(lastChapterRead = 3.0))
        tracker.updateRemote(remote.copy(libraryId = 42L, lastChapterRead = 20.0))
        assertTrue(requests.all { it.authorization == "Bearer access-token" })
        assertTrue(requests.any { it.path.endsWith("collections/42") && it.method == "POST" })
        assertTrue(requests.any { it.path.endsWith("collections/42") && it.method == "PATCH" })
    }
}
