package mihon.desktop.reader

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mihon.desktop.DesktopRuntimeFactory
import mihon.desktop.cli.DesktopCommandRunner
import mihon.desktop.cli.PackagedReaderVerifier
import mihon.desktop.executeDesktopRuntime
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path

class PackagedReaderScenarioTest {
    @Test
    fun `explicit soak repeatedly decodes real fixtures in one runtime`() {
        val fixture = ReaderFixtureBuilder.build(tempDir.resolve("soak-fixture"), committedRar())
        val samples = tempDir.resolve("soak.jsonl")
        val environment = mapOf(
            "MIHON_W_READER_VERIFY" to "1",
            "MIHON_W_READER_SOAK_SECONDS" to "2",
            "MIHON_W_READER_SOAK_OUTPUT" to samples.toString(),
        )
        DesktopRuntimeFactory.create(
            arrayOf("--verify-reader=${fixture.root}", "--data-dir=${tempDir.resolve("soak-data")}"),
            environment,
            tempDir.resolve("bin"),
        ).use { runtime ->
            val result = kotlinx.coroutines.runBlocking {
                PackagedReaderVerifier(runtime, System::currentTimeMillis, environment).verify(fixture.root)
            }
            val soak = requireNotNull(result.soak)
            (soak.elapsedSeconds >= 2.0) shouldBe true
            (soak.cycles >= 1) shouldBe true
            (soak.decodedTiles >= result.decodedTileCount) shouldBe true
            val rows = Files.readAllLines(samples)
            (rows.size >= 2) shouldBe true
            rows.map {
                Json.parseToJsonElement(it).jsonObject.getValue("processId").jsonPrimitive.content
            }.distinct().size shouldBe
                1
        }
    }

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `fixture builder records the deterministic standalone container matrix and committed RAR provenance`() {
        val requestedRoot = System.getenv("MIHON_W_READER_FIXTURE_DIR")
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?: tempDir.resolve("fixture")
        val fixture = ReaderFixtureBuilder.build(
            root = requestedRoot,
            committedRar = committedRar(),
        )

        fixture.manifestSha256 shouldMatch Regex("[0-9a-f]{64}")
        fixture.committedRarSha256 shouldBe "ccbac45f0afbc1bf543b59cefb22fd22c1f6af243721813fb4ffaf1a0cd4b693"
        fixture.files.map { it.relativePath } shouldContainAll listOf(
            "standalone.png",
            "reader-fixture-manga/01-directory/40-extreme.png",
            "reader-fixture-manga/02-pages.cbz",
            "reader-fixture-manga/03-pages.cbt",
            "reader-fixture-manga/04-pages.cb7",
            "reader-fixture-manga/05-pages.cbr",
            "reader-fixture-manga/06-pages.epub",
        )
        fixture.files.forEach { file ->
            file.sha256 shouldMatch Regex("[0-9a-f]{64}")
            Files.size(fixture.root.resolve(file.relativePath)) shouldBe file.sizeBytes
        }
    }

    @Test
    fun `real verifier decodes the packaged matrix and persists initial continue and completion phases`() {
        val fixture = ReaderFixtureBuilder.build(
            root = tempDir.resolve("scenario-fixture"),
            committedRar = committedRar(),
        )
        val dataRoot = tempDir.resolve("scenario-data")
        val expectedPhases = listOf("initial-open", "reopen-continue", "final-completion")

        expectedPhases.forEach { expectedPhase ->
            val output = ByteArrayOutputStream()
            var verificationFailure: Throwable? = null
            val runtime = DesktopRuntimeFactory.create(
                args = arrayOf("--verify-reader=${fixture.root}", "--data-dir=$dataRoot"),
                environment = mapOf("MIHON_W_READER_VERIFY" to "1"),
                executableDirectory = tempDir.resolve("bin"),
            )
            val exitCode = executeDesktopRuntime(
                runtime = runtime,
                runCommand = { active, command ->
                    DesktopCommandRunner(
                        runtime = active,
                        output = output,
                        readerVerification = { fixtureRoot ->
                            try {
                                PackagedReaderVerifier(active, System::currentTimeMillis).verify(fixtureRoot)
                            } catch (error: Throwable) {
                                verificationFailure = error
                                throw error
                            }
                        },
                    ).run(command)
                },
                launchUi = { error("reader verifier must remain headless") },
            )
            verificationFailure?.let { throw it }
            exitCode shouldBe 0

            val line = output.toString(UTF_8).lineSequence().filter(String::isNotBlank).single()
            val summary = Json.parseToJsonElement(line).jsonObject
            summary.getValue("command").jsonPrimitive.content shouldBe "verify-reader"
            summary.getValue("status").jsonPrimitive.content shouldBe "SUCCEEDED"
            summary.getValue("phase").jsonPrimitive.content shouldBe expectedPhase
            summary.getValue("fixtureManifestSha256").jsonPrimitive.content shouldBe fixture.manifestSha256
            summary.getValue("verifiedAssets").jsonArray.map { it.jsonPrimitive.content } shouldContainAll
                listOf("standalone", "directory", "cbz", "cbt", "cb7", "cbr", "epub")
            summary.getValue("verifiedModes").jsonArray.size shouldBe 6
            (summary.getValue("decodedTileCount").jsonPrimitive.content.toInt() > 0) shouldBe true
            summary.getValue("gifFrameHashes").jsonArray
                .map { it.jsonPrimitive.content }
                .distinct()
                .size shouldBe 2
            val highWater = summary.getValue("coreResidentAndInFlightHighWaterBytes").jsonPrimitive.content.toLong()
            (highWater in 1..(256L * 1024L * 1024L)) shouldBe true
        }

        val database = dataRoot.resolve("database").resolve("library.db")
        Files.isRegularFile(database) shouldBe true
    }

    private fun committedRar(): Path =
        generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(), Path::getParent)
            .first { Files.isDirectory(it.resolve("reader-core")) && Files.isDirectory(it.resolve("desktop-app")) }
            .resolve("reader-core/src/test/resources/mihon/reader/source/fixtures/valid-rar4.rar")
            .toAbsolutePath()
            .normalize()
}
