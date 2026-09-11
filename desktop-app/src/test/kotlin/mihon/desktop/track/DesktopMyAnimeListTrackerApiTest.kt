package mihon.desktop.track

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.LocalDate
import java.time.ZoneId

class DesktopMyAnimeListTrackerApiTest {

    private lateinit var server: HttpServer
    private lateinit var tracker: MyAnimeListTracker
    private val requests = mutableListOf<TrackerTestRecordedRequest>()

    @BeforeEach
    fun setUp() {
        requests.clear()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val request = exchange.recordTrackerTestRequest()
            requests += request

            val status: Int
            val response: String
            when {
                request.path == "/users/@me" -> {
                    if (request.authorization == "Bearer bad-token") {
                        status = 401
                        response = """{"message":"Invalid token"}"""
                    } else {
                        status = 200
                        response = USER_RESPONSE
                    }
                }
                request.path == "/manga" -> {
                    status = 200
                    response = SEARCH_RESPONSE
                }
                request.path.endsWith("/my_list_status") && request.method == "PATCH" -> {
                    status = 200
                    response = UPDATE_RESPONSE
                }
                request.path.endsWith("/my_list_status") -> {
                    if (request.path.contains("/manga/404/")) {
                        status = 404
                        response = """{"message":"Not found"}"""
                    } else {
                        status = 200
                        response = LIST_STATUS_RESPONSE
                    }
                }
                else -> {
                    status = 404
                    response = """{"message":"Unexpected path"}"""
                }
            }
            exchange.respondTrackerTestJson(response, status)
        }
        server.start()

        tracker = MyAnimeListTracker(
            baseUrl = "http://127.0.0.1:${server.address.port}",
            httpClient = trackerTestHttpClient(),
        )
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `login validates token with users me and stores username`() = runBlocking {
        assertTrue(tracker.login(mapOf("token" to "mal-token")))
        assertTrue(tracker.isLoggedIn)
        assertEquals("MALUser", tracker.username)

        val request = requests.single()
        assertEquals("GET", request.method)
        assertEquals("/users/@me", request.path)
        assertEquals("Bearer mal-token", request.authorization)
    }

    @Test
    fun `login fails without credentials and on rejected token`() = runBlocking {
        assertFalse(tracker.login(emptyMap()))
        assertTrue(requests.isEmpty())

        assertFalse(tracker.login(mapOf("password" to "bad-token")))
        assertFalse(tracker.isLoggedIn)
        assertEquals(1, requests.size)
    }

    @Test
    fun `search sends q and limit and parses manga results`() = runBlocking {
        tracker.login(mapOf("token" to "mal-token"))
        requests.clear()

        val results = tracker.search("one piece")

        assertEquals(1, results.size)
        val first = results.single()
        assertEquals(1L, first.trackerId)
        assertEquals(123L, first.remoteId)
        assertEquals("One Piece", first.title)
        assertEquals(1200L, first.totalChapters)
        assertEquals("https://cdn.example.com/one-piece-large.jpg", first.coverUrl)
        assertEquals("https://myanimelist.net/manga/123", first.trackingUrl)
        assertEquals("A pirate adventure", first.summary)

        val request = requests.single()
        assertEquals("GET", request.method)
        assertEquals("/manga", request.path)
        val query = queryParameters(request.query)
        assertEquals("one piece", query["q"])
        assertEquals("50", query["limit"])
        assertTrue(query["fields"].orEmpty().contains("num_chapters"))
    }

    @Test
    fun `findRemote maps my_list_status response`() = runBlocking {
        tracker.login(mapOf("token" to "mal-token"))
        requests.clear()

        val local = DesktopTrackRecord(
            mangaId = 7L,
            trackerId = 1L,
            remoteId = 123L,
            title = "One Piece",
        )
        val remote = tracker.findRemote(local)

        assertNotNull(remote)
        assertEquals(12.0, remote?.lastChapterRead)
        assertEquals(8.0, remote?.score)
        assertEquals(TrackStatus.READING.value, remote?.status)
        assertEquals(dateMillis(2024, 1, 15), remote?.startedReadingDate)
        assertEquals(0L, remote?.finishedReadingDate)
        assertEquals("https://myanimelist.net/manga/123", remote?.trackingUrl)

        val request = requests.single()
        assertEquals("GET", request.method)
        assertEquals("/manga/123/my_list_status", request.path)
        assertEquals("Bearer mal-token", request.authorization)
    }

    @Test
    fun `findRemote returns null when manga is not on the user list`() = runBlocking {
        tracker.login(mapOf("token" to "mal-token"))
        requests.clear()

        val remote = tracker.findRemote(
            DesktopTrackRecord(mangaId = 7L, trackerId = 1L, remoteId = 404L, title = "Missing"),
        )

        assertEquals(null, remote)
        assertEquals("/manga/404/my_list_status", requests.single().path)
    }

    @Test
    fun `updateRemote patches my_list_status with required fields`() = runBlocking {
        tracker.login(mapOf("token" to "mal-token"))
        requests.clear()

        val updated = tracker.updateRemote(
            DesktopTrackRecord(
                mangaId = 7L,
                trackerId = 1L,
                remoteId = 123L,
                title = "One Piece",
                lastChapterRead = 100.0,
                totalChapters = 1200L,
                score = 8.0,
                status = TrackStatus.COMPLETED.value,
                startedReadingDate = dateMillis(2024, 1, 15),
                finishedReadingDate = dateMillis(2024, 2, 20),
            ),
        )

        assertEquals(100.0, updated.lastChapterRead)
        assertEquals(8.0, updated.score)
        assertEquals(TrackStatus.COMPLETED.value, updated.status)
        assertEquals(dateMillis(2024, 2, 20), updated.finishedReadingDate)

        val request = requests.single()
        assertEquals("PATCH", request.method)
        assertEquals("/manga/123/my_list_status", request.path)
        assertTrue(request.contentType.orEmpty().startsWith("application/x-www-form-urlencoded"))

        val form = parseTrackerTestFormBody(request.body)
        assertEquals("completed", form["status"])
        assertEquals("8", form["score"])
        assertEquals("100", form["num_chapters_read"])
        assertEquals("2024-01-15", form["start_date"])
        assertEquals("2024-02-20", form["finish_date"])
        assertEquals("false", form["is_rereading"])
    }

    private fun queryParameters(query: String?): Map<String, String> = parseTrackerTestFormBody(query.orEmpty())

    private fun dateMillis(year: Int, month: Int, day: Int): Long = LocalDate.of(year, month, day)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private companion object {
        private val USER_RESPONSE = """{"id":42,"name":"MALUser"}"""

        private val SEARCH_RESPONSE = """
            {
              "data": [
                {
                  "node": {
                    "id": 123,
                    "title": "One Piece",
                    "synopsis": "A pirate adventure",
                    "num_chapters": 1200,
                    "mean": 8.7,
                    "main_picture": {
                      "medium": "https://cdn.example.com/one-piece-medium.jpg",
                      "large": "https://cdn.example.com/one-piece-large.jpg"
                    },
                    "media_type": "manga"
                  }
                },
                {
                  "node": {
                    "id": 456,
                    "title": "A Light Novel",
                    "num_chapters": 0,
                    "media_type": "novel"
                  }
                }
              ],
              "paging": {"next": null}
            }
        """.trimIndent()

        private val LIST_STATUS_RESPONSE = """
            {
              "status": "reading",
              "score": 8,
              "num_chapters_read": 12,
              "start_date": "2024-01-15",
              "finish_date": null,
              "is_rereading": false
            }
        """.trimIndent()

        private val UPDATE_RESPONSE = """
            {
              "status": "completed",
              "score": 8,
              "num_chapters_read": 100,
              "start_date": "2024-01-15",
              "finish_date": "2024-02-20",
              "is_rereading": false
            }
        """.trimIndent()
    }
}
