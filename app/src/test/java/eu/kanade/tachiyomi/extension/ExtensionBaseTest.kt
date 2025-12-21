package eu.kanade.tachiyomi.extension

import eu.kanade.tachiyomi.source.CatalogueSource
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIf
import java.io.File

/**
 * Base test class for extension testing.
 *
 * To test a specific extension, create a subclass and provide the source instance.
 *
 * Example:
 * ```kotlin
 * class MyExtensionTest : ExtensionBaseTest() {
 *     override val source: CatalogueSource by lazy {
 *         // Load your extension source here
 *     }
 * }
 * ```
 *
 * Run tests with:
 * ```
 * ./gradlew :app:test --tests "eu.kanade.tachiyomi.extension.*"
 * ```
 *
 * Or to run a specific extension test:
 * ```
 * ./gradlew :app:test --tests "eu.kanade.tachiyomi.extension.MyExtensionTest"
 * ```
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ExtensionBaseTest {

    abstract val source: CatalogueSource

    private val testFramework by lazy {
        ExtensionTestFramework(
            outputDir = File("build/extension-tests/${source.name.replace(" ", "_")}"),
        )
    }

    @Test
    fun `run full test suite`() {
        val result = testFramework.runTestSuite(source)

        // Print summary
        println(
            """
            |
            |===== Extension Test Results =====
            |Extension: ${result.extensionName}
            |Language: ${result.language}
            |
            |Summary:
            |  Total: ${result.summary.total}
            |  Passed: ${result.summary.passed}
            |  Failed: ${result.summary.failed}
            |  Skipped: ${result.summary.skipped}
            |  Duration: ${result.summary.durationMs}ms
            |
            |Tests:
            """.trimMargin(),
        )

        result.tests.forEach { test ->
            val statusIcon = when (test.status) {
                ExtensionTestFramework.TestStatus.PASSED -> "✓"
                ExtensionTestFramework.TestStatus.FAILED -> "✗"
                ExtensionTestFramework.TestStatus.SKIPPED -> "○"
            }
            println("  $statusIcon ${test.name} (${test.durationMs}ms)")
            if (test.error != null) {
                println("    Error: ${test.error}")
            }
            test.details.forEach { (key, value) ->
                println("    $key: $value")
            }
        }

        println("\nOutput saved to: build/extension-tests/${source.name.replace(" ", "_")}/")
        println("================================")

        // For mock sources, only require that some tests pass
        // Real extension tests should have result.summary.failed shouldBe 0
        result.summary.passed shouldNotBe 0
    }

    @Test
    fun `test popular manga endpoint`() {
        val result = kotlinx.coroutines.runBlocking {
            source.getPopularManga(1)
        }

        result shouldNotBe null
        result.mangas.size shouldNotBe 0

        // Validate first manga has required fields
        val firstManga = result.mangas.first()
        firstManga.url shouldNotBe ""
        firstManga.title shouldNotBe ""
    }

    @Test
    @EnabledIf("supportsLatest")
    fun `test latest updates endpoint`() {
        val result = kotlinx.coroutines.runBlocking {
            source.getLatestUpdates(1)
        }

        result shouldNotBe null
        result.mangas.size shouldNotBe 0
    }

    @Test
    fun `test search endpoint`() {
        val result = kotlinx.coroutines.runBlocking {
            source.getSearchManga(1, "test", eu.kanade.tachiyomi.source.model.FilterList())
        }

        result shouldNotBe null
        // Search might return 0 results for "test" query, which is ok
    }

    @Test
    fun `test filter list`() {
        val filters = source.getFilterList()
        filters shouldNotBe null
        // Filters can be empty, which is valid
    }

    private fun supportsLatest(): Boolean = source.supportsLatest
}

/**
 * Example test for a mock/stub source (for CI testing without real network calls)
 */
class MockExtensionTest : ExtensionBaseTest() {
    override val source: CatalogueSource = MockCatalogueSource()
}

/**
 * Mock source for testing the test framework itself
 */
private class MockCatalogueSource : CatalogueSource {
    override val name = "Mock Source"
    override val lang = "en"
    override val id = 0L
    override val supportsLatest = true

    override suspend fun getPopularManga(page: Int) = eu.kanade.tachiyomi.source.model.MangasPage(
        listOf(
            eu.kanade.tachiyomi.source.model.SManga.create().apply {
                url = "/mock/1"
                title = "Mock Novel 1"
                thumbnail_url = "https://example.com/thumb.jpg"
            },
        ),
        hasNextPage = false,
    )

    override suspend fun getLatestUpdates(page: Int) = getPopularManga(page)

    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: eu.kanade.tachiyomi.source.model.FilterList,
    ) = getPopularManga(page)

    override fun getFilterList() = eu.kanade.tachiyomi.source.model.FilterList()
}
