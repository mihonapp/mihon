package mihon.desktop.extension

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ExtensionTrustStoreTest {

    private val fingerprintA = "aa11bb22cc33dd44ee55ff66aa77bb88cc99dd00ee11ff22aa33bb44cc55dd66"
    private val fingerprintB = "bb22cc33dd44ee55ff66aa77bb88cc99dd00ee11ff22aa33bb44cc55dd66ee77ff88"

    @Test
    fun `trusts package fingerprint and persists across store instances`(@TempDir tempDir: Path) {
        val preferences = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val store = ExtensionTrustStore(preferences)

        store.status("ext.foo", listOf(fingerprintA)) shouldBe ExtensionTrustStatus.UNTRUSTED
        store.trust("ext.foo", fingerprintA)
        store.status("ext.foo", listOf(fingerprintA)) shouldBe ExtensionTrustStatus.TRUSTED
        store.getTrustedFingerprints("ext.foo") shouldContain fingerprintA

        val reopened = ExtensionTrustStore(preferences)
        reopened.status("ext.foo", listOf(fingerprintA)) shouldBe ExtensionTrustStatus.TRUSTED
        reopened.isTrusted("ext.foo", fingerprintA) shouldBe true
        reopened.status("ext.foo", listOf(fingerprintB)) shouldBe ExtensionTrustStatus.UNTRUSTED
    }

    @Test
    fun `revoke removes user trust and blocks a matching repository key until trusted again`(@TempDir tempDir: Path) {
        val preferences = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val store = ExtensionTrustStore(preferences)

        store.status("ext.foo", listOf(fingerprintA), listOf(fingerprintA)) shouldBe ExtensionTrustStatus.TRUSTED
        store.trust("ext.foo", fingerprintA)
        store.revoke("ext.foo")

        store.status("ext.foo", listOf(fingerprintA), listOf(fingerprintA)) shouldBe ExtensionTrustStatus.UNTRUSTED
        store.isRevoked("ext.foo") shouldBe true
        store.getTrustedFingerprints("ext.foo").shouldBe(emptySet<String>())

        val reopened = ExtensionTrustStore(preferences)
        reopened.isRevoked("ext.foo") shouldBe true
        reopened.status("ext.foo", listOf(fingerprintA), listOf(fingerprintA)) shouldBe
            ExtensionTrustStatus.UNTRUSTED

        reopened.trust("ext.foo", fingerprintA)
        reopened.isRevoked("ext.foo") shouldBe false
        reopened.status("ext.foo", listOf(fingerprintA), listOf(fingerprintA)) shouldBe
            ExtensionTrustStatus.TRUSTED
    }

    @Test
    fun `unsigned packages are unknown until explicitly trusted`(@TempDir tempDir: Path) {
        val preferences = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val store = ExtensionTrustStore(preferences)

        store.status("ext.unsigned", emptyList()) shouldBe ExtensionTrustStatus.UNKNOWN
        store.trustUnsigned("ext.unsigned")
        store.status("ext.unsigned", emptyList()) shouldBe ExtensionTrustStatus.TRUSTED
        store.isTrusted("ext.unsigned", "") shouldBe true

        store.revoke("ext.unsigned")
        store.status("ext.unsigned", emptyList()) shouldBe ExtensionTrustStatus.UNTRUSTED
    }

    @Test
    fun `invalid signature is always reported invalid`(@TempDir tempDir: Path) {
        val store = ExtensionTrustStore(DesktopPreferenceStore(tempDir.resolve("prefs.properties")))
        store.trust("ext.foo", fingerprintA)

        store.status(
            pkg = "ext.foo",
            fingerprints = listOf(fingerprintA),
            signatureValid = false,
        ) shouldBe ExtensionTrustStatus.INVALID
    }

    @Test
    fun `revokeAll clears explicit trust and revocations`(@TempDir tempDir: Path) {
        val store = ExtensionTrustStore(DesktopPreferenceStore(tempDir.resolve("prefs.properties")))
        store.trust("ext.one", fingerprintA)
        store.trust("ext.two", fingerprintB)
        store.revoke("ext.one")

        store.revokeAll()

        store.getTrustedPackages().shouldBe(emptySet<String>())
        store.isRevoked("ext.one") shouldBe false
        store.status("ext.one", listOf(fingerprintA)) shouldBe ExtensionTrustStatus.UNTRUSTED
    }

    @Test
    fun `pending trust request can be created consumed and cleared`(@TempDir tempDir: Path) {
        val store = ExtensionTrustStore(DesktopPreferenceStore(tempDir.resolve("prefs.properties")))
        store.requestTrust(
            pkg = "ext.foo",
            fingerprints = listOf(fingerprintA),
            filePath = "C:/tmp/ext.foo.mext",
            reason = "unknown signer",
        )

        val request = store.pendingTrustRequest.value
        request shouldNotBe null
        request!!.pkg shouldBe "ext.foo"
        request.fingerprints shouldContain fingerprintA

        val consumed = store.consumePendingTrustRequest()
        consumed?.pkg shouldBe "ext.foo"
        store.pendingTrustRequest.value shouldBe null

        store.requestTrust("ext.bar", listOf(fingerprintB))
        store.clearPendingTrustRequest()
        store.pendingTrustRequest.value shouldBe null
    }
}
