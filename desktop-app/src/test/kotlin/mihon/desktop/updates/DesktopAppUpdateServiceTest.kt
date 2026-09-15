package mihon.desktop.updates

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import mihon.desktop.platform.DistributionMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class DesktopAppUpdateServiceTest {

    @Test
    fun `updates resolve renamed repository and distribution assets`(): Unit = runBlocking {
        var requestedUrl = ""
        val service = DesktopAppUpdateService(
            currentVersion = "0.2.4",
            fetchText = {
                requestedUrl = it
                """{"tag_name":"v0.2.5","assets":[
                    {"name":"mihondesk-0.2.5.exe","browser_download_url":"https://example.com/app.exe"},
                    {"name":"mihondesk-0.2.5-windows-x64-portable.zip","browser_download_url":"https://example.com/app.zip"}
                ]}"""
            },
        )

        val result = service.checkForUpdates().shouldBeInstanceOf<UpdateCheckResult.UpdateAvailable>()
        requestedUrl shouldBe "https://api.github.com/repos/1873412297-art/mihondesk/releases/latest"
        result.matchedAsset?.name shouldBe "mihondesk-0.2.5.exe"
        DesktopAppUpdateService.findBestAsset(result.release.assets, DistributionMode.Portable)?.name shouldBe
            "mihondesk-0.2.5-windows-x64-portable.zip"
    }

    @Test
    fun `current version matches the Windows distribution version`() {
        DesktopAppUpdateService.CURRENT_VERSION shouldBe System.getProperty("mihon.desktop.expectedVersion")
    }

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `version comparison correctly detects newer versions`() {
        DesktopAppUpdateService.isNewerVersion("0.2.0", "0.1.0") shouldBe true
        DesktopAppUpdateService.isNewerVersion("1.0.0", "0.9.9") shouldBe true
        DesktopAppUpdateService.isNewerVersion("v0.1.1", "0.1.0") shouldBe true
        DesktopAppUpdateService.isNewerVersion("0.1.0", "0.1.0") shouldBe false
        DesktopAppUpdateService.isNewerVersion("0.0.9", "0.1.0") shouldBe false
        DesktopAppUpdateService.isNewerVersion("v0.1.0", "0.1.0") shouldBe false
    }

    @Test
    fun `parseReleaseJson parses GitHub release payload`() {
        val json = """
        {
          "tag_name": "v0.2.0",
          "body": "New release features and fixes",
          "html_url": "https://github.com/mihonapp/mihon-w/releases/tag/v0.2.0",
          "published_at": "2026-09-06T00:00:00Z",
          "assets": [
            {
              "name": "MihonW-0.2.0.exe",
              "browser_download_url": "https://github.com/mihonapp/mihon-w/releases/download/v0.2.0/MihonW-0.2.0.exe",
              "size": 85000000,
              "content_type": "application/x-msdownload"
            },
            {
              "name": "MihonW-0.2.0-windows-x64-portable.zip",
              "browser_download_url": "https://github.com/mihonapp/mihon-w/releases/download/v0.2.0/MihonW-0.2.0-windows-x64-portable.zip",
              "size": 83000000,
              "content_type": "application/zip"
            }
          ]
        }
        """.trimIndent()

        val release = DesktopAppUpdateService.parseReleaseJson(json)
        release.version shouldBe "0.2.0"
        release.tagName shouldBe "v0.2.0"
        release.releaseNotes shouldBe "New release features and fixes"
        release.assets.size shouldBe 2

        val portable = DesktopAppUpdateService.findBestAsset(release.assets, DistributionMode.Portable)
        portable?.name shouldBe "MihonW-0.2.0-windows-x64-portable.zip"

        val installer = DesktopAppUpdateService.findBestAsset(release.assets, DistributionMode.Installed)
        installer?.name shouldBe "MihonW-0.2.0.exe"
    }

    @Test
    fun `sha256 verification succeeds for matching file and fails on mismatch`() {
        val file = tempDir.resolve("test-file.bin")
        val content = "Mihon Desktop Port SHA256 Test Content".toByteArray()
        Files.write(file, content)

        val digest = MessageDigest.getInstance("SHA-256")
        val expectedHash = digest.digest(content).joinToString("") { "%02x".format(it) }

        DesktopAppUpdateService.verifySha256(file, expectedHash) shouldBe true
        DesktopAppUpdateService.verifySha256(
            file,
            "0000000000000000000000000000000000000000000000000000000000000000",
        ) shouldBe
            false
    }

    @Test
    fun `checkForUpdates yields UpdateAvailable when remote is newer`(): Unit = runBlocking {
        val json = """
        {
          "tag_name": "v0.3.0",
          "body": "Release notes",
          "html_url": "https://github.com/mihonapp/mihon-w/releases/tag/v0.3.0",
          "assets": [
            {
              "name": "MihonW-0.3.0-windows-x64-portable.zip",
              "browser_download_url": "https://example.com/download.zip"
            }
          ]
        }
        """.trimIndent()

        val service = DesktopAppUpdateService(
            currentVersion = "0.1.0",
            distributionMode = DistributionMode.Portable,
            fetchText = { json },
        )

        val result = service.checkForUpdates()
        result.shouldBeInstanceOf<UpdateCheckResult.UpdateAvailable>()
        result.release.version shouldBe "0.3.0"
        result.matchedAsset?.name shouldBe "MihonW-0.3.0-windows-x64-portable.zip"
    }

    @Test
    fun `downloadAsset saves file and validates checksum`(): Unit = runBlocking {
        val content = "Binary update package payload".toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content).joinToString("") { "%02x".format(it) }

        val service = DesktopAppUpdateService(
            downloadStream = { ByteArrayInputStream(content) },
        )

        val destination = tempDir.resolve("update.zip")
        val asset = AppReleaseAsset(
            name = "update.zip",
            downloadUrl = "https://example.com/update.zip",
        )

        val success = service.downloadAsset(asset, destination, expectedSha256 = hash)
        success shouldBe true
        Files.exists(destination) shouldBe true
        Files.readAllBytes(destination) shouldBe content

        // Invalid checksum should fail and delete destination
        val failDest = tempDir.resolve("fail.zip")
        val failed = service.downloadAsset(asset, failDest, expectedSha256 = "invalidhash")
        failed shouldBe false
        Files.exists(failDest) shouldBe false
    }
}
