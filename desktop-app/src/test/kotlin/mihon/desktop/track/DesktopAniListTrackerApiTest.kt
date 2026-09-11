package mihon.desktop.track

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

class DesktopAniListTrackerApiTest {

    private lateinit var server: HttpServer
    private lateinit var tracker: AniListTracker
    private val requests = mutableListOf<TrackerTestRecordedRequest>()
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setUp() {
        requests.clear()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val request = exchange.recordTrackerTestRequest()
            requests += request

            val response = when {
                request.authorization == "Bearer bad-token" -> {
                    exchange.respondTrackerTestJson("""{"errors":[{"message":"Invalid token"}]}""", status = 401)
                    return@createContext
                }
                request.body.contains("SaveMediaListEntry") -> SAVE_ENTRY_RESPONSE
                request.body.contains("Viewer") -> VIEWER_RESPONSE
                request.body.contains("MediaList") -> MEDIA_LIST_RESPONSE
                request.body.contains("Page") -> SEARCH_RESPONSE
                else -> {
                    exchange.respondTrackerTestJson("""{"errors":[{"message":"Unexpected query"}]}""", status = 400)
                    return@createContext
                }
            }
            exchange.respondTrackerTestJson(response)
        }
        server.start()

        tracker = AniListTracker(
            baseUrl = "http://127.0.0.1:${server.address.port}",
            httpClient = trackerTestHttpClient(),
        )
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `login uses viewer query and stores username`() = runBlocking {
        assertTrue(tracker.login(mapOf("token" to "anilist-token")))
        assertTrue(tracker.isLoggedIn)
        assertEquals("TestUser", tracker.username)

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/", request.path)
        assertEquals("Bearer anilist-token", request.authorization)
        assertTrue(queryText(request).contains("Viewer"))
        assertTrue(request.contentType.orEmpty().startsWith("application/json"))
    }

    @Test
    fun `login fails without credentials and on rejected token`() = runBlocking {
        assertFalse(tracker.login(emptyMap()))
        assertTrue(requests.isEmpty())

        assertFalse(tracker.login(mapOf("token" to "bad-token")))
        assertFalse(tracker.isLoggedIn)
        assertEquals(1, requests.size)
    }

    @Test
    fun `search maps Page media results and sends expected query shape`() = runBlocking {
        tracker.login(mapOf("token" to "anilist-token"))
        requests.clear()

        val results = tracker.search("one piece")

        assertEquals(2, results.size)
        val first = results.first()
        assertEquals(2L, first.trackerId)
        assertEquals(123L, first.remoteId)
        assertEquals("One Piece", first.title)
        assertEquals(1200L, first.totalChapters)
        assertEquals("https://cdn.example.com/one-piece.jpg", first.coverUrl)
        assertEquals("https://anilist.co/manga/123", first.trackingUrl)
        assertEquals("A pirate adventure", first.summary)

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("Bearer anilist-token", request.authorization)
        assertTrue(queryText(request).contains("Page(perPage: 50)"))
        assertTrue(queryText(request).contains("media(type: MANGA, search:"))
        assertTrue(queryText(request).contains("format_not_in: [NOVEL]"))
        val variables = variablesOf(request)
        assertEquals("one piece", variables["query"]?.jsonPrimitive?.content)
    }

    @Test
    fun `findRemote maps MediaList entry for bound media id`() = runBlocking {
        tracker.login(mapOf("token" to "anilist-token"))
        requests.clear()

        val local = DesktopTrackRecord(
            mangaId = 7L,
            trackerId = 2L,
            remoteId = 123L,
            title = "Local title",
            totalChapters = 50L,
        )
        val remote = tracker.findRemote(local)

        assertNotNull(remote)
        assertEquals(123L, remote?.remoteId)
        assertEquals(9001L, remote?.libraryId)
        assertEquals("One Piece", remote?.title)
        assertEquals(1200L, remote?.totalChapters)
        assertEquals(12.0, remote?.lastChapterRead)
        assertEquals(85.0, remote?.score)
        assertEquals(TrackStatus.READING.value, remote?.status)
        assertEquals(dateMillis(2024, 1, 15), remote?.startedReadingDate)
        assertEquals(0L, remote?.finishedReadingDate)
        assertTrue(remote?.private == true)
        assertEquals("https://anilist.co/manga/123", remote?.trackingUrl)

        val request = requests.single()
        assertTrue(queryText(request).contains("MediaList(userId:"))
        val variables = variablesOf(request)
        assertEquals(42, variables["userId"]?.jsonPrimitive?.int)
        assertEquals(123, variables["mediaId"]?.jsonPrimitive?.int)
    }

    @Test
    fun `findRemote returns null when media is not on the user list`() = runBlocking {
        tracker.login(mapOf("token" to "anilist-token"))
        requests.clear()
        server.removeContext("/")
        server.createContext("/") { exchange ->
            val request = exchange.recordTrackerTestRequest()
            requests += request
            exchange.respondTrackerTestJson("""{"data":{"MediaList":null}}""")
        }

        val remote = tracker.findRemote(
            DesktopTrackRecord(mangaId = 7L, trackerId = 2L, remoteId = 999L, title = "Missing"),
        )

        assertEquals(null, remote)
    }

    @Test
    fun `updateRemote sends SaveMediaListEntry mutation with tracking fields`() = runBlocking {
        tracker.login(mapOf("token" to "anilist-token"))
        requests.clear()

        val updated = tracker.updateRemote(
            DesktopTrackRecord(
                mangaId = 7L,
                trackerId = 2L,
                remoteId = 123L,
                libraryId = 9001L,
                title = "One Piece",
                lastChapterRead = 100.0,
                totalChapters = 1200L,
                score = 85.0,
                status = TrackStatus.COMPLETED.value,
                startedReadingDate = dateMillis(2024, 1, 15),
                finishedReadingDate = dateMillis(2024, 2, 20),
                private = true,
            ),
        )

        assertEquals(777L, updated.libraryId)
        assertEquals(100.0, updated.lastChapterRead)

        val request = requests.single()
        val query = queryText(request)
        assertTrue(query.contains("mutation UpdateMediaListEntry"))
        assertTrue(query.contains("SaveMediaListEntry"))
        assertTrue(query.contains("scoreRaw: "))

        val variables = variablesOf(request)
        assertEquals(9001, variables["id"]?.jsonPrimitive?.int)
        assertFalse(variables.containsKey("mediaId"))
        assertEquals("COMPLETED", variables["status"]?.jsonPrimitive?.content)
        assertEquals(85, variables["score"]?.jsonPrimitive?.int)
        assertEquals(100, variables["progress"]?.jsonPrimitive?.int)
        assertTrue(variables["private"]?.jsonPrimitive?.content?.toBoolean() == true)
        assertEquals(2024, variables["startedAt"]?.jsonObject?.get("year")?.jsonPrimitive?.int)
        assertEquals(1, variables["startedAt"]?.jsonObject?.get("month")?.jsonPrimitive?.int)
        assertEquals(15, variables["startedAt"]?.jsonObject?.get("day")?.jsonPrimitive?.int)
        assertEquals(2, variables["completedAt"]?.jsonObject?.get("month")?.jsonPrimitive?.int)
        assertEquals(20, variables["completedAt"]?.jsonObject?.get("day")?.jsonPrimitive?.int)
    }

    @Test
    fun `updateRemote creates entry by media id when no library id is bound`() = runBlocking {
        tracker.login(mapOf("token" to "anilist-token"))
        requests.clear()

        tracker.updateRemote(
            DesktopTrackRecord(
                mangaId = 7L,
                trackerId = 2L,
                remoteId = 123L,
                title = "One Piece",
                lastChapterRead = 1.0,
                score = 70.0,
                status = TrackStatus.READING.value,
            ),
        )

        val request = requests.single()
        assertTrue(queryText(request).contains("mutation AddMediaListEntry"))
        val variables = variablesOf(request)
        assertFalse(variables.containsKey("id"))
        assertEquals(123, variables["mediaId"]?.jsonPrimitive?.int)
        assertEquals("CURRENT", variables["status"]?.jsonPrimitive?.content)
    }

    private fun variablesOf(request: TrackerTestRecordedRequest): JsonObject =
        bodyJson(request)["variables"]?.jsonObject ?: JsonObject(emptyMap())

    private fun queryText(request: TrackerTestRecordedRequest): String =
        bodyJson(request)["query"]?.jsonPrimitive?.content.orEmpty()

    private fun bodyJson(request: TrackerTestRecordedRequest): JsonObject =
        json.parseToJsonElement(request.body).jsonObject

    private fun dateMillis(year: Int, month: Int, day: Int): Long = LocalDate.of(year, month, day)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private companion object {
        private val VIEWER_RESPONSE = """
            {"data":{"Viewer":{"id":42,"name":"TestUser"}}}
        """.trimIndent()

        private val SEARCH_RESPONSE = """
            {
              "data": {
                "Page": {
                  "media": [
                    {
                      "id": 123,
                      "title": {"userPreferred": "One Piece"},
                      "coverImage": {"large": "https://cdn.example.com/one-piece.jpg"},
                      "chapters": 1200,
                      "description": "A pirate adventure"
                    },
                    {
                      "id": 456,
                      "title": {"userPreferred": "Berserk"},
                      "coverImage": null,
                      "chapters": null,
                      "description": null
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        private val MEDIA_LIST_RESPONSE = """
            {
              "data": {
                "MediaList": {
                  "id": 9001,
                  "status": "CURRENT",
                  "scoreRaw": 85,
                  "progress": 12,
                  "private": true,
                  "startedAt": {"year": 2024, "month": 1, "day": 15},
                  "completedAt": {"year": null, "month": null, "day": null},
                  "media": {
                    "id": 123,
                    "title": {"userPreferred": "One Piece"},
                    "coverImage": {"large": "https://cdn.example.com/one-piece.jpg"},
                    "chapters": 1200,
                    "description": "A pirate adventure"
                  }
                }
              }
            }
        """.trimIndent()

        private val SAVE_ENTRY_RESPONSE = """
            {"data":{"SaveMediaListEntry":{"id":777,"status":"COMPLETED","progress":100}}}
        """.trimIndent()
    }
}
