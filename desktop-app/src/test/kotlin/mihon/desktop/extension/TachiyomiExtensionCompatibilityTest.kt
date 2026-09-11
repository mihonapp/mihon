package mihon.desktop.extension

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.extension.compat.AxmlManifestParser
import mihon.desktop.extension.compat.TachiyomiExtensionConverter
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class TachiyomiExtensionCompatibilityTest {

    private fun createSampleTachiyomiJar(file: File, packageId: String, className: String, name: String) {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="$packageId"
                android:versionCode="104"
                android:versionName="1.4.12">
                <application android:label="Tachiyomi: $name">
                    <meta-data android:name="tachiyomi.extension.class" android:value="$className" />
                    <meta-data android:name="tachiyomi.extension.nsfw" android:value="1" />
                    <meta-data android:name="tachiyomix.extensionLib" android:value="1.4" />
                </application>
            </manifest>
        """.trimIndent()

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(xml.toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("res/mipmap-xhdpi/ic_launcher.png"))
            zos.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
            zos.closeEntry()

            // Dummy class entry
            val classPath = className.replace('.', '/') + ".class"
            zos.putNextEntry(ZipEntry(classPath))
            zos.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
            zos.closeEntry()
        }
    }

    @Test
    fun `parses plain xml manifest accurately`() {
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="eu.kanade.tachiyomi.extension.all.everiaclub"
                android:versionCode="10412"
                android:versionName="1.4.12">
                <application android:label="Tachiyomi: Everia.club">
                    <meta-data android:name="tachiyomi.extension.class" android:value="keiyoushi.source.Generated" />
                    <meta-data android:name="tachiyomi.extension.nsfw" android:value="1" />
                    <meta-data android:name="tachiyomix.extensionLib" android:value="1.4" />
                </application>
            </manifest>
        """.trimIndent()

        val info = AxmlManifestParser.parsePlainXml(xml)
        info.packageId shouldBe "eu.kanade.tachiyomi.extension.all.everiaclub"
        info.versionCode shouldBe 10412L
        info.versionName shouldBe "1.4.12"
        info.name shouldBe "Everia.club"
        info.className shouldBe "keiyoushi.source.Generated"
        info.isNsfw shouldBe true
        info.libVersion shouldBe 1.4
    }

    @Test
    fun `resolves relative class name starting with dot`() {
        val xml = """
            <manifest package="eu.kanade.tachiyomi.extension.all.mangadex"
                android:versionCode="1"
                android:versionName="1.0">
                <application android:label="MangaDex">
                    <meta-data android:name="tachiyomi.extension.class" android:value=".MangaDex" />
                </application>
            </manifest>
        """.trimIndent()

        val info = AxmlManifestParser.parsePlainXml(xml)
        info.className shouldBe "eu.kanade.tachiyomi.extension.all.mangadex.MangaDex"
    }

    @Test
    fun `converts Tachiyomi jar package to mext with synthesized manifest`(@TempDir tempDir: Path) {
        val jarFile = tempDir.resolve("tachiyomi-all.everiaclub-v1.4.12.jar").toFile()
        createSampleTachiyomiJar(
            jarFile,
            packageId = "eu.kanade.tachiyomi.extension.all.everiaclub",
            className = "keiyoushi.source.Generated",
            name = "Everia.club",
        )

        TachiyomiExtensionConverter.isTachiyomiPackage(jarFile) shouldBe true

        val mextFile = tempDir.resolve("everiaclub.mext").toFile()
        val manifest = TachiyomiExtensionConverter.convertToMext(jarFile, mextFile)

        manifest.id shouldBe "eu.kanade.tachiyomi.extension.all.everiaclub"
        manifest.name shouldBe "Everia.club"
        manifest.version shouldBe "1.4.12"
        manifest.versionCode shouldBe 104L
        manifest.isNsfw shouldBe true
        manifest.sources shouldHaveSize 1
        manifest.sources.first().className shouldBe "keiyoushi.source.Generated"
        // Direct APK/JAR installs have no repository metadata, so the converter must not invent a
        // wildcard network permission.
        manifest.declaredDomains shouldBe emptyList()

        // Verify mext contents
        ZipFile(mextFile).use { zip ->
            zip.getEntry("manifest.json").shouldNotBeNull()
            zip.getEntry("icon.png").shouldNotBeNull()
            zip.getEntry("classes.jar").shouldNotBeNull()
        }
    }

    @Test
    fun `installer transparently accepts and installs Tachiyomi package`(@TempDir tempDir: Path) {
        runBlocking {
            val installRoot = tempDir.resolve("installed").toFile()
            val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
            val installer = DesktopExtensionInstaller(installRoot, prefStore)

            val jarFile = tempDir.resolve("sample-extension.jar").toFile()
            createSampleTachiyomiJar(
                jarFile,
                packageId = "eu.kanade.tachiyomi.extension.zh.copymanga",
                className = ".CopyManga",
                name = "CopyManga",
            )

            val installed = installer.installFromLocalFile(jarFile, trustOnInstall = true)
            installed.pkg shouldBe "eu.kanade.tachiyomi.extension.zh.copymanga"
            installed.manifest.name shouldBe "CopyManga"
            installed.manifest.sources.first().className shouldBe "eu.kanade.tachiyomi.extension.zh.copymanga.CopyManga"
            installed.isEnabled shouldBe true
            installed.iconPath.shouldNotBeNull()
            File(installed.iconPath!!).exists() shouldBe true

            installer.getInstalledExtensions() shouldHaveSize 1
        }
    }

    @Test
    fun `converter preserves store declared domains without wildcard fallback`(@TempDir tempDir: Path) {
        val jarFile = tempDir.resolve("store-extension.jar").toFile()
        createSampleTachiyomiJar(
            jarFile,
            packageId = "eu.kanade.tachiyomi.extension.all.storetest",
            className = ".StoreTest",
            name = "Store Test",
        )
        val mextFile = tempDir.resolve("storetest.mext").toFile()
        val manifest = TachiyomiExtensionConverter.convertToMext(
            sourceFile = jarFile,
            targetMextFile = mextFile,
            storeItem = ExtensionStoreItem(
                pkg = "eu.kanade.tachiyomi.extension.all.storetest",
                name = "Store Test",
                version = "1.4.12",
                versionCode = 104,
                declaredDomains = listOf("api.store.example", "cdn.store.example"),
            ),
        )

        manifest.declaredDomains shouldBe listOf("api.store.example", "cdn.store.example")
    }
}
