package mihon.desktop.extension

import io.kotest.matchers.collections.shouldContain
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
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DesktopExtensionInstallerTest {

    @Test
    fun `reinstalling installed package stages bytes before replacing directory`(
        @TempDir tempDir: Path,
    ): Unit = runBlocking {
        val installer =
            DesktopExtensionInstaller(
                tempDir.resolve("installed").toFile(),
                DesktopPreferenceStore(tempDir.resolve("prefs")),
            )
        val manifest = ExtensionManifest(
            id = "ext.reinstall",
            name = "Reinstall",
            version = "1.0",
            versionCode = 1,
            libVersion = 1.4,
            lang = "en",
            sources = listOf(SourceDescriptor(99L, "Source", "en", "ext.Source")),
        )
        val source = tempDir.resolve("source.mext").toFile()
        createDummyMext(source, manifest)
        val installed = installer.installFromLocalFile(source, trustOnInstall = true)
        val bytes = File(installed.packageFile).readBytes().toList()
        val replacement = installer.installFromLocalFile(File(installed.packageFile), trustOnInstall = true)
        File(replacement.packageFile).readBytes().toList() shouldBe bytes
        replacement.manifest.sources.single().id shouldBe 99L
    }

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

            val installed = installer.installFromLocalFile(mextFile, trustOnInstall = true)
            installed.pkg shouldBe "ext.test.sample"
            installed.manifest.name shouldBe "Sample Source"
            installed.manifest.declaredDomains shouldBe listOf("api.sample.com")
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

    @Test
    fun `unknown package requires explicit trust before installation`(@TempDir tempDir: Path) {
        runBlocking {
            val installer = DesktopExtensionInstaller(
                installRoot = tempDir.resolve("installed").toFile(),
                preferenceStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties")),
            )
            val manifest = sampleManifest("ext.test.trust.required")
            val mextFile = tempDir.resolve("unknown.mext").toFile()
            createDummyMext(mextFile, manifest)

            val error = assertThrows<ExtensionTrustRequiredException> {
                installer.installFromLocalFile(mextFile)
            }
            error.pkg shouldBe manifest.id
            installer.trustStore.pendingTrustRequest.value?.pkg shouldBe manifest.id
            installer.getInstalledExtensions() shouldHaveSize 0

            installer.installFromLocalFile(mextFile, trustOnInstall = true)
            installer.getInstalledExtensions() shouldHaveSize 1
            installer.trustStore.status(manifest.id, emptyList()) shouldBe ExtensionTrustStatus.TRUSTED
        }
    }

    @Test
    fun `revoke blocks installation until explicitly trusted again`(@TempDir tempDir: Path) {
        runBlocking {
            val installer = DesktopExtensionInstaller(
                installRoot = tempDir.resolve("installed").toFile(),
                preferenceStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties")),
            )
            val manifest = sampleManifest("ext.test.revoke")
            val mextFile = tempDir.resolve("revoke.mext").toFile()
            createDummyMext(mextFile, manifest)

            installer.installFromLocalFile(mextFile, trustOnInstall = true)
            installer.revokeExtension(manifest.id)
            installer.trustStore.isRevoked(manifest.id) shouldBe true

            assertThrows<ExtensionTrustRequiredException> {
                installer.installFromLocalFile(mextFile)
            }

            installer.trustUnsignedExtension(manifest.id)
            installer.installFromLocalFile(mextFile)
            installer.trustStore.status(manifest.id, emptyList()) shouldBe ExtensionTrustStatus.TRUSTED
        }
    }

    @Test
    fun `known repository signing key must match the package signer`(@TempDir tempDir: Path) {
        val signedJar = tempDir.resolve("signed-extension.jar").toFile()
        val manifest = sampleManifest("ext.test.signed")
        val fixture = ExtensionSignatureTestFixtures.createSignedJar(
            target = signedJar,
            extraEntries = mapOf("manifest.json" to json.encodeToString(manifest).toByteArray()),
        )
        assumeTrue(fixture != null, "JDK keytool/jarsigner are not available")

        runBlocking {
            val installer = DesktopExtensionInstaller(
                installRoot = tempDir.resolve("installed").toFile(),
                preferenceStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties")),
            )
            val storeItem = ExtensionStoreItem(
                pkg = manifest.id,
                name = manifest.name,
                version = manifest.version,
                versionCode = manifest.versionCode,
                signingKey = fixture!!.fingerprint,
            )

            val installed = installer.installFromLocalFile(signedJar, storeItem = storeItem)
            installed.signatureFingerprint shouldBe fixture.fingerprint
            installed.signatureFingerprints shouldContain fixture.fingerprint
            installed.signingKey shouldBe fixture.fingerprint
            installed.trustStatus shouldBe ExtensionTrustStatus.TRUSTED

            val wrongKey = "00".repeat(32)
            assertThrows<ExtensionSignatureMismatchException> {
                installer.installFromLocalFile(
                    signedJar,
                    storeItem = storeItem.copy(signingKey = wrongKey),
                )
            }
        }
    }

    @Test
    fun `v2 v3 only apk signature is rejected explicitly`(@TempDir tempDir: Path) {
        val apk = tempDir.resolve("v2-only.apk").toFile()
        ExtensionSignatureTestFixtures.createApkSigningBlockOnlyApk(apk)

        runBlocking {
            val installer = DesktopExtensionInstaller(
                installRoot = tempDir.resolve("installed").toFile(),
                preferenceStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties")),
            )

            val error = assertThrows<ExtensionSignatureException> {
                installer.installFromLocalFile(apk, trustOnInstall = true)
            }
            (error.message ?: "").contains("v2/v3") shouldBe true
        }
    }

    @Test
    fun `store item sha256 is enforced for local installs`(@TempDir tempDir: Path) {
        runBlocking {
            val installer = DesktopExtensionInstaller(
                installRoot = tempDir.resolve("installed").toFile(),
                preferenceStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties")),
            )
            val manifest = sampleManifest("ext.test.store.sha")
            val mextFile = tempDir.resolve("store-sha.mext").toFile()
            createDummyMext(mextFile, manifest)

            assertThrows<ExtensionValidationException> {
                installer.installFromLocalFile(
                    mextFile,
                    trustOnInstall = true,
                    storeItem = ExtensionStoreItem(
                        pkg = manifest.id,
                        name = manifest.name,
                        version = manifest.version,
                        versionCode = manifest.versionCode,
                        sha256 = "deadbeef",
                    ),
                )
            }
        }
    }

    @Test
    fun `installer accepts an injected fake verifier`(@TempDir tempDir: Path) {
        val fakeFingerprint = "11".repeat(32)
        val fakeVerifier = object : ExtensionSignatureVerifier() {
            override fun verify(file: File): ExtensionSignatureVerification = ExtensionSignatureVerification(
                fingerprints = listOf(fakeFingerprint),
                hasV1Signature = true,
                valid = true,
            )
        }

        runBlocking {
            val installer = DesktopExtensionInstaller(
                installRoot = tempDir.resolve("installed").toFile(),
                preferenceStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties")),
                signatureVerifier = fakeVerifier,
            )
            val manifest = sampleManifest("ext.test.fake.verifier")
            val mextFile = tempDir.resolve("fake.mext").toFile()
            createDummyMext(mextFile, manifest)

            val installed = installer.installFromLocalFile(
                mextFile,
                storeItem = ExtensionStoreItem(
                    pkg = manifest.id,
                    name = manifest.name,
                    version = manifest.version,
                    versionCode = manifest.versionCode,
                    signingKey = fakeFingerprint,
                ),
            )
            installed.signatureFingerprint shouldBe fakeFingerprint
            installed.trustStatus shouldBe ExtensionTrustStatus.TRUSTED
        }
    }

    private fun sampleManifest(id: String): ExtensionManifest = ExtensionManifest(
        id = id,
        name = "Sample Source",
        version = "1.0.0",
        versionCode = 1,
        libVersion = 1.4,
        lang = "en",
        sources = listOf(
            SourceDescriptor(id = 12345L, name = "Sample", lang = "en", className = "ext.test.SampleSource"),
        ),
        declaredDomains = listOf("api.sample.com"),
    )

    @Test
    fun `update cannot silently change the installed signer`(@TempDir tempDir: Path) {
        val manifest = sampleManifest("ext.test.signer.change")
        val manifestBytes = json.encodeToString(manifest).toByteArray()
        val firstJar = tempDir.resolve("first.jar").toFile()
        val secondJar = tempDir.resolve("second.jar").toFile()
        val firstFixture = ExtensionSignatureTestFixtures.createSignedJar(
            target = firstJar,
            extraEntries = mapOf("manifest.json" to manifestBytes),
        )
        val secondFixture = ExtensionSignatureTestFixtures.createSignedJar(
            target = secondJar,
            extraEntries = mapOf("manifest.json" to manifestBytes),
        )
        assumeTrue(firstFixture != null && secondFixture != null, "JDK keytool/jarsigner are not available")

        runBlocking {
            val installer = DesktopExtensionInstaller(
                installRoot = tempDir.resolve("installed").toFile(),
                preferenceStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties")),
            )
            installer.installFromLocalFile(
                firstJar,
                storeItem = ExtensionStoreItem(
                    pkg = manifest.id,
                    name = manifest.name,
                    version = manifest.version,
                    versionCode = manifest.versionCode,
                    signingKey = firstFixture!!.fingerprint,
                ),
            )
            installer.getInstalledExtensions().single().signingKey shouldBe firstFixture.fingerprint

            assertThrows<ExtensionSignatureMismatchException> {
                installer.installFromLocalFile(
                    secondJar,
                    trustOnInstall = true,
                    storeItem = ExtensionStoreItem(
                        pkg = manifest.id,
                        name = manifest.name,
                        version = "2.0.0",
                        versionCode = 2,
                        signingKey = secondFixture!!.fingerprint,
                    ),
                )
            }

            assertThrows<ExtensionSignatureMismatchException> {
                installer.installFromLocalFile(secondJar, trustOnInstall = true)
            }
        }
    }
}
