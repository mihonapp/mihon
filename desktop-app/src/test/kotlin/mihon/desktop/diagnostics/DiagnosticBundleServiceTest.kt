package mihon.desktop.diagnostics

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.platform.AppDirectories
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

class DiagnosticBundleServiceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `creates diagnostic summary and exports sanitized zip bundle`() {
        val root = tempDir.resolve("app-root")
        val directories = AppDirectories(root).create()
        val logFile = directories.logs.resolve("test.log")
        Files.writeString(
            logFile,
            "2026-09-06 INFO Login request with token: my-secret-token-12345 and password: secret_password\n" +
                "Authorization: Bearer confidential_oauth_token\n",
        )

        val dbFile = directories.database.resolve("library.db")
        DesktopLibraryDatabaseFactory.open(dbFile).use { repository ->
            val service = DiagnosticBundleService(directories, repository)
            val summary = service.createSummary()

            summary.databaseIntegrity shouldContain "ok"
            summary.logFileCount shouldBe 1

            val targetZip = tempDir.resolve("diagnostics.zip")
            service.exportBundle(targetZip)

            Files.exists(targetZip) shouldBe true

            ZipFile(targetZip.toFile()).use { zip ->
                val summaryEntry = zip.getEntry("diagnostics/summary.txt")
                summaryEntry shouldBe summaryEntry // Not null
                val summaryText = zip.getInputStream(summaryEntry).bufferedReader().readText()
                summaryText shouldContain "Mihon W Diagnostic Summary"
                summaryText shouldContain "DB Integrity: ok"

                val logEntry = zip.getEntry("diagnostics/logs/test.log")
                val logText = zip.getInputStream(logEntry).bufferedReader().readText()
                logText shouldNotContain "my-secret-token-12345"
                logText shouldNotContain "secret_password"
                logText shouldNotContain "confidential_oauth_token"
                logText shouldContain "[REDACTED]"
            }
        }
    }
}
