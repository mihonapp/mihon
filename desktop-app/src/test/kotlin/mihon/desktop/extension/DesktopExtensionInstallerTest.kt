package mihon.desktop.extension

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import mihon.extension.validator.ExtensionValidationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DesktopExtensionInstallerTest {

    private val json = Json { prettyPrint = true }

    private fun createDummyMext(
        file: File,
        manifest: ExtensionManifest,
        includeIcon: Boolean = true,
    ) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            val manifestEntry = ZipEntry("manifest.json")
            zip.putNextEntry(manifestEntry)
            zip.write(json.encodeToString(manifest).toByteArray())
            zip.closeEntry()

            if (includeIcon) {
                val iconEntry = ZipEntry("icon.png")
                zip.putNextEntry(iconEntry)
                zip.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
                zip.closeEntry()
            }
        }
    }

    @Test
    fun `installs valid local package, extracts icon, and records metadata`(@TempDir tempDir: Path) {
        runBlocking {
            val installRoot = tempDir.resolve("installed").toFile()
            val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
            val installer = DesktopExtensionInstaller(installRoot, prefStore)

            val manifest = ExtensionManifest(
                id = "ext.test.sample",
                name = "Sample Source",
                version = "1.0.0",
                versionCode = 1,
                libVersion = 1.4,
                lang = "en",
                sources = listOf(
                    SourceDescriptor(
                        id = 12345L,
                        name = "Sample",
                        lang = "en",
                        className = "ext.test.SampleSource",
                    ),
                ),
                declaredDomains = listOf("api.sample.com"),
            )

            val mextFile = tempDir.resolve("sample.mext").toFile()
            createDummyMext(mextFile, manifest, includeIcon = true)

            val installed = installer.installFromLocalFile(mextFile)
            installed.pkg shouldBe "ext.test.sample"
            installed.manifest.name shouldBe "Sample Source"
            installed.isEnabled shouldBe true
            installed.iconPath.shouldNotBeNull()
            File(installed.iconPath!!).exists() shouldBe true

            val list = installer.getInstalledExtensions()
            list shouldHaveSize 1
            list.first().pkg shouldBe "ext.test.sample"

            // Test disable / enable
            installer.setExtensionEnabled("ext.test.sample", false)
            installer.getInstalledExtensions().first().isEnabled shouldBe false

            // Test uninstall
            val uninstalled = installer.uninstall("ext.test.sample")
            uninstalled shouldBe true
            installer.getInstalledExtensions() shouldHaveSize 0
            File(installed.installDir).exists() shouldBe false
        }
    }

    @Test
    fun `rejects installation if SHA-256 does not match`(@TempDir tempDir: Path) {
        runBlocking {
            val installRoot = tempDir.resolve("installed").toFile()
            val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
            val installer = DesktopExtensionInstaller(installRoot, prefStore)

            val manifest = ExtensionManifest(
                id = "ext.test.sha",
                name = "Sha Test",
                version = "1.0.0",
                versionCode = 1,
                libVersion = 1.4,
                lang = "en",
                sources = listOf(
                    SourceDescriptor(id = 1L, name = "Sha", lang = "en", className = "ext.Sha"),
                ),
            )
            val mextFile = tempDir.resolve("sha.mext").toFile()
            createDummyMext(mextFile, manifest)

            assertThrows<ExtensionValidationException> {
                installer.installFromLocalFile(mextFile, expectedSha256 = "invalid_hash_value")
            }
            installer.getInstalledExtensions() shouldHaveSize 0
        }
    }
}
