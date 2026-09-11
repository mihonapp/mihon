package mihon.desktop.extension

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContainIgnoringCase
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtensionSignatureVerifierTest {

    @Test
    fun `extracts signer certificate sha256 fingerprint from a signed jar`(@TempDir tempDir: Path) {
        val fixture = ExtensionSignatureTestFixtures.createSignedJar(tempDir.resolve("signed.jar").toFile())
        assumeTrue(fixture != null, "JDK keytool/jarsigner are not available")

        val verifier = ExtensionSignatureVerifier()
        val result = verifier.verify(fixture!!.file)

        result.valid shouldBe true
        result.signed shouldBe true
        result.hasV1Signature shouldBe true
        result.v2V3Only shouldBe false
        result.fingerprints shouldContain fixture.fingerprint
        verifier.extractSignerFingerprints(fixture.file) shouldContain fixture.fingerprint

        // Companion/object-style entry points remain usable from callers without constructing one.
        ExtensionSignatureVerifier.verify(fixture.file).fingerprints shouldContain fixture.fingerprint
        ExtensionSignatureVerifier.extractFingerprints(fixture.file) shouldContain fixture.fingerprint
    }

    @Test
    fun `rejects a signed jar whose payload was modified after signing`(@TempDir tempDir: Path) {
        val fixture = ExtensionSignatureTestFixtures.createSignedJar(tempDir.resolve("signed.jar").toFile())
        assumeTrue(fixture != null, "JDK keytool/jarsigner are not available")

        val tampered = tempDir.resolve("tampered.jar").toFile()
        ExtensionSignatureTestFixtures.tamperJar(
            source = fixture!!.file,
            target = tampered,
            entryName = "payload.txt",
            replacement = "tampered payload".toByteArray(),
        )

        val verifier = ExtensionSignatureVerifier()
        val result = verifier.verify(tampered)
        result.valid shouldBe false
        result.error shouldNotBe null
        result.error!!.shouldContainIgnoringCase("signature")

        assertThrows<ExtensionSignatureException> {
            verifier.extractSignerFingerprints(tampered)
        }
    }

    @Test
    fun `reports v2 v3 only apk signing as explicitly unsupported`(@TempDir tempDir: Path) {
        val apk = tempDir.resolve("v2-only.apk").toFile()
        ExtensionSignatureTestFixtures.createApkSigningBlockOnlyApk(apk)

        val result = ExtensionSignatureVerifier.verify(apk)
        result.valid shouldBe false
        result.v2V3Only shouldBe true
        result.hasApkSigningBlock shouldBe true
        result.error shouldNotBe null
        result.error!!.shouldContainIgnoringCase("v2/v3")

        val thrown = assertThrows<ExtensionSignatureException> {
            ExtensionSignatureVerifier().extractSignerFingerprints(apk)
        }
        thrown.message!!.shouldContainIgnoringCase("v2/v3")
    }

    @Test
    fun `treats an unsigned zip as valid but without signer identity`(@TempDir tempDir: Path) {
        val zip = tempDir.resolve("unsigned.mext").toFile()
        ZipOutputStream(FileOutputStream(zip)).use { output ->
            output.putNextEntry(ZipEntry("manifest.json"))
            output.write("{}".toByteArray())
            output.closeEntry()
        }

        val result = ExtensionSignatureVerifier().verify(zip)
        result.valid shouldBe true
        result.signed shouldBe false
        result.fingerprints.shouldBeEmpty()
        ExtensionSignatureVerifier().extractSignerFingerprints(zip).shouldBeEmpty()
    }

    @Test
    fun `normalizes fingerprints from hex base64 and separators`() {
        val fingerprint = "aa11bb22cc33dd44ee55ff66aa77bb88cc99dd00ee11ff22aa33bb44cc55dd66"
        ExtensionTrustStore.normalizeFingerprint(fingerprint.uppercase()) shouldBe fingerprint
        ExtensionTrustStore.normalizeFingerprint(
            "aa:11:bb:22:cc:33:dd:44:ee:55:ff:66:aa:77:bb:88:cc:99:dd:00:ee:11:ff:22:aa:33:bb:44:cc:55:dd:66",
        ) shouldBe fingerprint
        ExtensionTrustStore.normalizeFingerprint("NO_SIGNING_KEY") shouldBe ""
        ExtensionTrustStore.normalizeFingerprint(null) shouldBe ""
    }
}
