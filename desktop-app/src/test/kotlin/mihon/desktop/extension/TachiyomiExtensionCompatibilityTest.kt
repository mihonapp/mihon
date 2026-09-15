package mihon.desktop.extension

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.extension.compat.AxmlManifestParser
import mihon.desktop.extension.compat.TachiyomiExtensionConverter
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.extension.model.SourceDescriptor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class TachiyomiExtensionCompatibilityTest {
    @Test
    fun `conversion versions and source changes invalidate cache`(@TempDir tempDir: Path) {
        val source = tempDir.resolve("cache.apk").toFile()
        val target = tempDir.resolve("cache.mext").toFile()
        createSampleTachiyomiJar(source, "ext.cache", "ext.cache.Source", "Cache")
        TachiyomiExtensionConverter.convertToMext(source, target)
        target.setLastModified(123_000L)
        TachiyomiExtensionConverter.convertToMext(source, target, converterVersion = "next")
        (target.lastModified() > 123_000L) shouldBe true
        target.setLastModified(123_000L)
        TachiyomiExtensionConverter.convertToMext(
            source,
            target,
            converterVersion = "next",
            compatibilityVersion = "next",
        )
        (target.lastModified() > 123_000L) shouldBe true
        createSampleTachiyomiJar(source, "ext.cache", "ext.cache.Source", "Changed")
        TachiyomiExtensionConverter.convertToMext(
            source,
            target,
            converterVersion = "next",
            compatibilityVersion = "next",
        ).name shouldBe
            "Changed"
    }

    @Test
    fun `conversion translates primary and secondary dex files`(@TempDir tempDir: Path) {
        fun dex(className: String): ByteArray {
            val writer = com.googlecode.d2j.dex.writer.DexFileWriter()
            writer.visit(1, "L$className;", "Ljava/lang/Object;", emptyArray()).visitEnd()
            writer.visitEnd()
            return writer.toByteArray()
        }
        val source = tempDir.resolve("multidex.apk").toFile()
        ZipOutputStream(source.outputStream()).use { zip ->
            val entries = mapOf(
                "AndroidManifest.xml" to (
"""<manifest package="ext.multidex" android:versionName="1.4.1"><application android:lab""" +
"""el="Multi"><meta-data android:name="tachiyomi.extension.class" android:value="ext.Sec""" +
"""ondary"/></application></manifest>"""
                    ).toByteArray(),
                "classes.dex" to dex("ext/Primary"),
                "classes2.dex" to dex("ext/Secondary"),
            )
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        val target = tempDir.resolve("multidex.mext").toFile()
        TachiyomiExtensionConverter.convertToMext(source, target)
        ZipFile(target).use { zip ->
            mapOf(
                "classes.jar" to "ext/Primary.class",
                "classes2.jar" to "ext/Secondary.class",
            ).forEach { (name, expectedClass) ->
                val entry = zip.getEntry(name).shouldNotBeNull()
                java.util.zip.ZipInputStream(zip.getInputStream(entry)).use { nested ->
                    generateSequence { nested.nextEntry }.map { it.name }.toList().contains(expectedClass) shouldBe true
                }
            }
        }
    }

    @Test
    fun `unchanged source reuses validated conversion cache`(@TempDir tempDir: Path) {
        val source = tempDir.resolve("cache.apk").toFile()
        val target = tempDir.resolve("cache.mext").toFile()
        createSampleTachiyomiJar(source, "ext.cache", "ext.cache.Source", "Cache")
        TachiyomiExtensionConverter.convertToMext(source, target)
        target.setLastModified(123_000L)
        TachiyomiExtensionConverter.convertToMext(source, target)
        target.lastModified() shouldBe 123_000L
    }

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
            zos.putNextEntry(ZipEntry("assets/config.json"))
            zos.write("{\"enabled\":true}".toByteArray())
            zos.closeEntry()

            // Dummy class entry
            val classPath = className.replace('.', '/') + ".class"
            zos.putNextEntry(ZipEntry(classPath))
            zos.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
            zos.closeEntry()
        }
    }

    @Test
    fun `conversion preserves source assets`(@TempDir tempDir: Path) {
        val source = tempDir.resolve("assets.jar").toFile()
        val target = tempDir.resolve("assets.mext").toFile()
        createSampleTachiyomiJar(source, "ext.assets", "ext.assets.Source", "Assets")
        TachiyomiExtensionConverter.convertToMext(source, target)
        ZipFile(target).use { zip ->
            val entry = zip.getEntry("assets/config.json").shouldNotBeNull()
            zip.getInputStream(entry).use { it.readBytes().decodeToString() } shouldBe "{\"enabled\":true}"
        }
    }

    @Test
    fun `conversion rejects unsupported standard APK version API`(@TempDir tempDir: Path) {
        val source = tempDir.resolve("future-standard.apk").toFile()
        ZipOutputStream(source.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(
                (
"""<manifest package="ext.future" android:versionName="1.7.1"><application android:label""" +
"""="Future"><meta-data android:name="tachiyomi.extension.class" android:value="ext.Sour""" +
"""ce"/></application></manifest>"""
                    ).toByteArray(),
            )
            zip.closeEntry()
        }
        assertThrows<IllegalArgumentException> {
            TachiyomiExtensionConverter.convertToMext(source, tempDir.resolve("future-standard.mext").toFile())
        }
    }

    @Test
    fun `conversion rejects unsupported API before publishing package`(@TempDir tempDir: Path) {
        val source = tempDir.resolve("future.jar").toFile()
        ZipOutputStream(source.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(
                (
"""<manifest package="ext.future"><application android:label="Future"><meta-data android""" +
""":name="tachiyomi.extension.class" android:value="ext.Source"/><meta-data android:name""" +
"""="tachiyomix.extensionLib" android:value="99.0"/></application></manifest>"""
                    ).toByteArray(),
            )
            zip.closeEntry()
        }
        assertThrows<IllegalArgumentException> {
            TachiyomiExtensionConverter.convertToMext(source, tempDir.resolve("future.mext").toFile())
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
            val process = object : WindowsExtensionProcessManager(tempDir.resolve("probe").toFile()) {
                override suspend fun loadExtension(packageFile: File): List<SourceDescriptor> =
                    listOf(SourceDescriptor(987654321L, "CopyManga", "zh", "runtime.GeneratedSource"))
            }
            val sourceManager = DesktopSourceManager(installer, process, prefStore)

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
            installed.manifest.sources.single().id shouldBe 987654321L
            installed.manifest.sources.first().className shouldBe "eu.kanade.tachiyomi.extension.zh.copymanga.CopyManga"
            installed.isEnabled shouldBe true
            installed.iconPath.shouldNotBeNull()
            File(installed.iconPath!!).exists() shouldBe true

            installer.getInstalledExtensions() shouldHaveSize 1
            sourceManager.close()
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
