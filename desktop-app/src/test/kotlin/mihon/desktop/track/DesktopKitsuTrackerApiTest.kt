package mihon.desktop.track

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Path

class DesktopKitsuTrackerApiTest {

    private lateinit var server: HttpServer
    private lateinit var tracker: KitsuTracker
    private val requests = mutableListOf<TrackerTestRecordedRequest>()

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val request = exchange.recordTrackerTestRequest()
            requests += request
            val body = when {
                request.path == "/oauth" -> """{"access_token":"kitsu-token"}"""
                request.body.contains(
                    "CurrentAccount",
                ) -> """{"data":{"currentAccount":{"id":"8","profile":{"name":"Kitsu User"}}}}"""
                request.body.contains("Search") ->
                    """
                    {"data":{"searchMangaByTitle":{"nodes":[
                      {"id":"42","titles":{"preferred":"One Piece"},"chapterCount":1200,
                       "posterImage":{"original":{"url":"https://img.example/one.jpg"}},
                       "description":{"en":"Pirates"},"slug":"one-piece"}
                    ]}}}
                    """.trimIndent()
                request.body.contains("Find") ->
                    """
                    {"data":{"findMangaById":{"id":"42","titles":{"preferred":"One Piece"},
                      "chapterCount":1200,"slug":"one-piece","myLibraryEntry":{"id":"99",
                      "private":true,"progress":12,"rating":8,"status":"CURRENT",
                      "startedAt":null,"finishedAt":null}}}}
                    """.trimIndent()
                request.body.contains(
                    "Create",
                ) -> """{"data":{"libraryEntry":{"create":{"libraryEntry":{"id":"100"}}}}}"""
                request.body.contains(
                    "Update",
                ) -> """{"data":{"libraryEntry":{"update":{"libraryEntry":{"id":"99"}}}}}"""
                else -> """{"errors":[{"message":"unexpected"}]}"""
            }
            exchange.respondTrackerTestJson(body)
        }
        server.start()
        val base = "http://127.0.0.1:${server.address.port}"
        tracker =
            KitsuTracker(graphQlUrl = "$base/graphql", oauthUrl = "$base/oauth", httpClient = trackerTestHttpClient())
    }

    @AfterEach
    fun tearDown() = server.stop(0)

    @Test
    fun `Kitsu logs in searches reads and writes tracking records`() = runBlocking {
        assertTrue(tracker.login(mapOf("username" to "reader", "password" to "secret")))
        assertEquals("Kitsu User", tracker.username)
        assertEquals("kitsu-token", tracker.persistenceToken)

        val result = tracker.search("one piece").single()
        assertEquals("One Piece", result.title)
        assertEquals(42L, result.remoteId)
        assertEquals("https://kitsu.app/manga/one-piece", result.trackingUrl)

        val remote = tracker.findRemote(
            DesktopTrackRecord(mangaId = 1L, trackerId = tracker.id, remoteId = 42L, title = "Local"),
        )!!
        assertEquals(99L, remote.libraryId)
        assertEquals(12.0, remote.lastChapterRead)
        assertEquals(TrackStatus.READING.value, remote.status)

        assertEquals(100L, tracker.updateRemote(remote.copy(libraryId = 0L)).libraryId)
        assertEquals(99L, tracker.updateRemote(remote).libraryId)
        assertTrue(requests.any { it.path == "/oauth" })
        val oauthForm = parseTrackerTestFormBody(requests.first { it.path == "/oauth" }.body)
        assertEquals("dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd", oauthForm["client_id"])
        assertEquals("54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151", oauthForm["client_secret"])
        assertTrue(requests.filter { it.path == "/graphql" }.all { it.authorization == "Bearer kitsu-token" })
    }

    @Test
    fun `manager persists Kitsu OAuth access token instead of the password`() = runBlocking {
        val store = DesktopTrackerStore(DesktopPreferenceStore(tempDir.resolve("preferences.properties")))
        val manager = DesktopTrackerManager(listOf(tracker), store)

        assertTrue(manager.login(tracker.id, mapOf("username" to "reader", "password" to "secret-password")))
        assertEquals("kitsu-token", store.getAuthToken(tracker.id))
    }
}
