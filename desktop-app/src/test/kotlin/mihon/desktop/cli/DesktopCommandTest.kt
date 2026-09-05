@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package mihon.desktop.cli

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.desktop.DesktopRuntime
import mihon.desktop.executeDesktopRuntime
import mihon.desktop.library.backup.AndroidBackup
import mihon.desktop.library.backup.AndroidBackupCodec
import mihon.desktop.library.backup.AndroidBackupImporter
import mihon.desktop.library.backup.AndroidBackupManga
import mihon.desktop.library.backup.AndroidBackupValidator
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.local.LocalMangaImporter
import mihon.desktop.platform.AppDirectories
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path

class DesktopCommandTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `parses the exact desktop command forms while ignoring runtime options`() {
        val cases = listOf(
            emptyArray<String>() to DesktopCommand.LaunchUi,
            arrayOf("--smoke-test") to DesktopCommand.FoundationSmoke,
            arrayOf("--import-backup=C:\\备份\\a.tachibk") to
                DesktopCommand.ImportBackup(Path.of("C:\\备份\\a.tachibk")),
            arrayOf("--export-backup=C:\\备份\\out.tachibk") to
                DesktopCommand.ExportBackup(Path.of("C:\\备份\\out.tachibk")),
            arrayOf("--import-local=C:\\漫画\\作品") to DesktopCommand.ImportLocal(Path.of("C:\\漫画\\作品")),
            arrayOf("--import-local=C:\\漫画\\作品=a") to DesktopCommand.ImportLocal(Path.of("C:\\漫画\\作品=a")),
            arrayOf("--list-library-json") to DesktopCommand.ListLibraryJson,
            arrayOf("--portable", "--data-dir=C:\\Mihon data") to DesktopCommand.LaunchUi,
        )

        cases.map { (args, _) -> DesktopCommandParser.parse(args) } shouldContainExactly cases.map { it.second }
    }

    @Test
    fun `rejects ambiguous missing and unknown command forms with exit code two`() {
        val invalid = listOf(
            arrayOf("--smoke-test", "--list-library-json"),
            arrayOf("--import-backup="),
            arrayOf("--import-local", "C:\\漫画\\作品"),
            arrayOf("--unknown"),
        )

        invalid.forEach { args ->
            assertThrows<CommandLineException> { DesktopCommandParser.parse(args) }.exitCode shouldBe 2
        }
    }

    @Test
    fun `reader verification command requires its exact opt-in environment gate`() {
        val root = tempDir.resolve("reader fixture")

        DesktopCommandParser.parse(
            arrayOf("--verify-reader=$root"),
            mapOf("MIHON_W_READER_VERIFY" to "1"),
        ) shouldBe DesktopCommand.VerifyReader(root)

        listOf(
            emptyMap(),
            mapOf("MIHON_W_READER_VERIFY" to "0"),
            mapOf("MIHON_W_READER_VERIFY" to "true"),
        ).forEach { environment ->
            val error = assertThrows<CommandLineException> {
                DesktopCommandParser.parse(arrayOf("--verify-reader=$root"), environment)
            }
            error.exitCode shouldBe 2
            error.argument shouldBe "--verify-reader"
        }
        assertThrows<CommandLineException> {
            DesktopCommandParser.parse(
                arrayOf("--verify-reader="),
                mapOf("MIHON_W_READER_VERIFY" to "1"),
            )
        }.exitCode shouldBe 2
    }

    @Test
    fun `reader verification emits exactly one stable JSON summary line`() {
        val root = tempDir.resolve("reader fixture")
        val output = ByteArrayOutputStream()
        var verified: Path? = null
        newRuntime(DesktopCommand.VerifyReader(root)).use { fixture ->
            DesktopCommandRunner(
                runtime = fixture.runtime,
                output = output,
                readerVerification = { path ->
                    verified = path
                    ReaderVerificationSummary(
                        phase = "initial-open",
                        fixtureManifestSha256 = "a".repeat(64),
                        verifiedAssets = listOf("standalone", "directory", "cbz"),
                        verifiedModes = listOf("SINGLE_LTR"),
                        decodedTileCount = 3,
                        gifFrameHashes = listOf("b".repeat(64), "c".repeat(64)),
                        cacheResidentHighWaterBytes = 4096,
                        coreResidentAndInFlightHighWaterBytes = 8192,
                        progressRows = listOf(
                            ReaderProgressRow(7, "01-directory", 1, 5, completed = false),
                        ),
                    )
                },
            ).run(fixture.runtime.command) shouldBe 0
        }

        verified shouldBe root
        val lines = output.toString(UTF_8).lineSequence().filter(String::isNotBlank).toList()
        lines.size shouldBe 1
        output.toString(UTF_8) shouldBe
            "{\"command\":\"verify-reader\",\"status\":\"SUCCEEDED\",\"phase\":\"initial-open\"," +
            "\"fixtureManifestSha256\":\"${"a".repeat(
                64,
            )}\",\"verifiedAssets\":[\"standalone\",\"directory\",\"cbz\"]," +
            "\"verifiedModes\":[\"SINGLE_LTR\"],\"decodedTileCount\":3," +
            "\"gifFrameHashes\":[\"${"b".repeat(64)}\",\"${"c".repeat(64)}\"]," +
            "\"cacheResidentHighWaterBytes\":4096,\"coreResidentAndInFlightHighWaterBytes\":8192," +
            "\"progressRows\":[{\"chapterId\":7,\"chapterName\":\"01-directory\"," +
            "\"pageIndex\":1,\"pageCount\":5,\"completed\":false}]}\n"
    }

    @Test
    fun `command-line JSON redacts unknown option values`() {
        val secret = "SECRET_PREFERENCE_VALUE"
        val error = assertThrows<CommandLineException> {
            DesktopCommandParser.parse(arrayOf("--preference=$secret"))
        }
        val output = ByteArrayOutputStream()

        DesktopCommandRunner.writeCommandLineError(output, error)

        output.toString(UTF_8) shouldBe
            "{\"command\":\"command-line\",\"status\":\"REJECTED\",\"category\":\"COMMAND_LINE\",\"path\":\"--preference\"}\n"
        output.toString(UTF_8) shouldNotContain secret
    }

    @Test
    fun `foundation smoke and empty library output remain stable one-line UTF-8`() {
        val smokeOutput = ByteArrayOutputStream()
        newRuntime(DesktopCommand.FoundationSmoke).use { fixture ->
            DesktopCommandRunner(fixture.runtime, smokeOutput, nowMillis = { 123L })
                .run(fixture.runtime.command) shouldBe 0
            smokeOutput.toString(UTF_8) shouldBe "MIHON_DESKTOP_SMOKE_OK ${fixture.runtime.directories.root}\n"
        }

        val listOutput = ByteArrayOutputStream()
        newRuntime(DesktopCommand.ListLibraryJson).use { fixture ->
            DesktopCommandRunner(fixture.runtime, listOutput, nowMillis = { 123L })
                .run(fixture.runtime.command) shouldBe 0
        }
        listOutput.toString(UTF_8) shouldBe "{\"command\":\"list-library\",\"items\":[]}\n"
    }

    @Test
    fun `successful backup import emits a stable report without report items`() {
        val backup = tempDir.resolve("备份 secret.tachibk")
        Files.write(
            backup,
            ProtoBuf.encodeToByteArray(
                AndroidBackup.serializer(),
                AndroidBackup(backupManga = listOf(AndroidBackupManga(source = 7, url = "/作品", title = "作品"))),
            ),
        )
        val output = ByteArrayOutputStream()

        newRuntime(DesktopCommand.ImportBackup(backup)).use { fixture ->
            DesktopCommandRunner(fixture.runtime, output, nowMillis = { 123L })
                .run(fixture.runtime.command) shouldBe 0
        }

        output.toString(UTF_8) shouldBe
            "{\"command\":\"import-backup\",\"status\":\"SUCCEEDED\",\"reportId\":1," +
            "\"path\":${jsonString(backup.toString())},\"counts\":$ONE_MANGA_COUNTS}\n"
    }

    @Test
    fun `successful backup export emits stable JSON`() {
        val exportFile = tempDir.resolve("export.tachibk")
        val output = ByteArrayOutputStream()

        newRuntime(DesktopCommand.ExportBackup(exportFile)).use { fixture ->
            DesktopCommandRunner(fixture.runtime, output, nowMillis = { 123L })
                .run(fixture.runtime.command) shouldBe 0
        }

        output.toString(UTF_8) shouldBe
            "{\"command\":\"export-backup\",\"status\":\"SUCCEEDED\",\"path\":${jsonString(exportFile.toString())}}\n"
        Files.exists(exportFile) shouldBe true
    }

    @Test
    fun `successful local import and non-empty Unicode library emit stable JSON`() {
        val source = tempDir.resolve("本地 作品")
        val chapter = source.resolve("第 1 话")
        Files.createDirectories(chapter)
        Files.write(chapter.resolve("001.jpg"), byteArrayOf(1, 2, 3))
        val importOutput = ByteArrayOutputStream()

        newRuntime(DesktopCommand.ImportLocal(source)).use { fixture ->
            DesktopCommandRunner(fixture.runtime, importOutput, nowMillis = { 456L })
                .run(fixture.runtime.command) shouldBe 0
            importOutput.toString(UTF_8) shouldBe
                "{\"command\":\"import-local\",\"status\":\"SUCCEEDED\",\"reportId\":1," +
                "\"path\":${jsonString(source.toString())},\"counts\":$ONE_LOCAL_MANGA_COUNTS}\n"

            val listOutput = ByteArrayOutputStream()
            DesktopCommandRunner(fixture.runtime, listOutput).run(DesktopCommand.ListLibraryJson) shouldBe 0
            val manga = fixture.runtime.library.librarySnapshot().single()
            listOutput.toString(UTF_8) shouldBe
                "{\"command\":\"list-library\",\"items\":[{" +
                "\"id\":${manga.id},\"sourceId\":0,\"url\":${jsonString(manga.url)}," +
                "\"title\":\"本地 作品\",\"thumbnailUrl\":null,\"chapterCount\":1,\"unreadCount\":1}]}\n"
        }
    }

    @Test
    fun `typed import failures return two with redacted category and path`() {
        val secret = "SECRET_PREFERENCE_VALUE"
        val invalidBackup = tempDir.resolve("invalid.tachibk")
        Files.writeString(invalidBackup, secret, UTF_8)
        val backupOutput = ByteArrayOutputStream()

        newRuntime(DesktopCommand.ImportBackup(invalidBackup)).use { fixture ->
            DesktopCommandRunner(fixture.runtime, backupOutput).run(fixture.runtime.command) shouldBe 2
        }
        backupOutput.toString(UTF_8).run {
            shouldContain("\"category\":\"INVALID_PROTOBUF\"")
            shouldContain("\"path\":")
            shouldNotContain(secret)
            shouldNotContain("SerializationException")
        }

        val missingLocal = tempDir.resolve("不存在 漫画")
        val localOutput = ByteArrayOutputStream()
        newRuntime(DesktopCommand.ImportLocal(missingLocal)).use { fixture ->
            DesktopCommandRunner(fixture.runtime, localOutput).run(fixture.runtime.command) shouldBe 2
        }
        localOutput.toString(UTF_8).run {
            shouldContain("\"category\":\"LOCAL_IMPORT_REJECTED\"")
            shouldContain(missingLocal.fileName.toString())
            shouldNotContain("LocalImportRejected")
        }
    }

    @Test
    fun `unexpected failures return one without exception details`() {
        val output = ByteArrayOutputStream()
        val fixture = newRuntime(DesktopCommand.ListLibraryJson)
        fixture.close()

        DesktopCommandRunner(fixture.runtime, output).run(fixture.runtime.command) shouldBe 1
        output.toString(UTF_8) shouldBe
            "{\"command\":\"list-library\",\"status\":\"FAILED\",\"category\":\"UNEXPECTED\",\"path\":null}\n"
    }

    @Test
    fun `runtime closes exactly once after command success and exceptions`() {
        val success = newRuntime(DesktopCommand.FoundationSmoke)
        executeDesktopRuntime(
            runtime = success.runtime,
            runCommand = { _, _ -> 0 },
            launchUi = { error("UI must not launch") },
        ) shouldBe 0
        success.closeCount shouldBe 1
        success.close()
        success.closeCount shouldBe 1

        val failure = newRuntime(DesktopCommand.ListLibraryJson)
        assertThrows<IllegalStateException> {
            executeDesktopRuntime(
                runtime = failure.runtime,
                runCommand = { _, _ -> error("command failure") },
                launchUi = { error("UI must not launch") },
            )
        }
        failure.closeCount shouldBe 1

        val ui = newRuntime(DesktopCommand.LaunchUi)
        executeDesktopRuntime(
            runtime = ui.runtime,
            runCommand = { _, _ -> error("Headless runner must not launch") },
            launchUi = { activeRuntime ->
                ui.closeCount shouldBe 0
                activeRuntime.library.librarySnapshot() shouldBe emptyList()
            },
        ) shouldBe 0
        ui.closeCount shouldBe 1
    }

    private fun newRuntime(command: DesktopCommand): RuntimeFixture {
        val root = Files.createTempDirectory(tempDir, "runtime-")
        val directories = AppDirectories(root).create()
        val library = DesktopLibraryDatabaseFactory.open(directories.database.resolve("library.db"))
        val localLibraryRoot = root.resolve("media").resolve("local")
        val backupImporter = AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), library)
        val localImporter = LocalMangaImporter(library)
        localImporter.cleanupOrphans(localLibraryRoot)
        var closes = 0
        val runtime = DesktopRuntime(
            directories = directories,
            preferences = DesktopPreferenceStore(root.resolve("preferences.properties")),
            command = command,
            library = library,
            backupImporter = backupImporter,
            localImporter = localImporter,
            localLibraryRoot = localLibraryRoot,
            closeLibrary = {
                closes++
                library.close()
            },
        )
        return RuntimeFixture(runtime) { closes }
    }
}

private class RuntimeFixture(
    val runtime: DesktopRuntime,
    private val closes: () -> Int,
) : AutoCloseable {
    val closeCount: Int get() = closes()

    override fun close() = runtime.close()
}

private fun jsonString(value: String): String = JsonPrimitive(value).toString()

private const val ONE_MANGA_COUNTS =
    "{\"mangaInserted\":1,\"mangaMerged\":0,\"chaptersInserted\":0,\"chaptersMerged\":0," +
        "\"categoriesLinked\":0,\"preferencesImported\":0,\"preferencesSkipped\":0}"

private const val ONE_LOCAL_MANGA_COUNTS =
    "{\"mangaInserted\":1,\"mangaMerged\":0,\"chaptersInserted\":1,\"chaptersMerged\":0," +
        "\"categoriesLinked\":0,\"preferencesImported\":0,\"preferencesSkipped\":0}"
