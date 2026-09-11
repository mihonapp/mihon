package mihon.desktop.security

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.util.Base64

class PinHasherTest {

    @Test
    fun `correct pin verifies and wrong pin is rejected`() {
        val salt = PinHasher.generateSalt()
        val saltBase64 = Base64.getEncoder().encodeToString(salt)
        val hashBase64 = Base64.getEncoder().encodeToString(PinHasher.hash("1234", salt, iterations = 5_000))

        PinHasher.verify("1234", hashBase64, saltBase64, iterations = 5_000) shouldBe true
        PinHasher.verify("4321", hashBase64, saltBase64, iterations = 5_000) shouldBe false
    }

    @Test
    fun `same pin uses unique salts and therefore unique hashes`() {
        val firstSalt = PinHasher.generateSalt()
        val secondSalt = PinHasher.generateSalt()

        firstSalt shouldNotBe secondSalt
        PinHasher.hash("1234", firstSalt, iterations = 1_000) shouldNotBe
            PinHasher.hash("1234", secondSalt, iterations = 1_000)
    }

    @Test
    fun `stored iteration count participates in verification`() {
        val salt = PinHasher.generateSalt()
        val saltBase64 = Base64.getEncoder().encodeToString(salt)
        val hashBase64 = Base64.getEncoder().encodeToString(PinHasher.hash("1234", salt, iterations = 2_000))

        PinHasher.verify("1234", hashBase64, saltBase64, iterations = 2_000) shouldBe true
        PinHasher.verify("1234", hashBase64, saltBase64, iterations = 2_001) shouldBe false
    }

    @Test
    fun `invalid or weak credentials are rejected`() {
        val saltBase64 = Base64.getEncoder().encodeToString(PinHasher.generateSalt())
        val hashBase64 = PinHasher.hashBase64("1234", saltBase64, iterations = 1_000)

        PinHasher.isValidCredential("", "", 0) shouldBe false
        PinHasher.isValidCredential("not-base64!", saltBase64, 1_000) shouldBe false
        PinHasher.isValidCredential(hashBase64!!, Base64.getEncoder().encodeToString(ByteArray(4)), 1_000) shouldBe
            false
        PinHasher.isValidCredential(hashBase64, saltBase64, 0) shouldBe false
    }

    @Test
    fun `default iteration count meets the desktop security floor`() {
        PinHasher.DEFAULT_ITERATIONS shouldBeGreaterThan 100_000
    }
}
