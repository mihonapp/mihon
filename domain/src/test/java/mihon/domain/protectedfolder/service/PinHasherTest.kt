package mihon.domain.protectedfolder.service

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class PinHasherTest {

    @Test
    fun `Only four ASCII digits are a valid PIN`() {
        PinHasher.isValidPin("0000") shouldBe true
        PinHasher.isValidPin("9731") shouldBe true

        PinHasher.isValidPin("") shouldBe false
        PinHasher.isValidPin("123") shouldBe false
        PinHasher.isValidPin("12345") shouldBe false
        PinHasher.isValidPin("12a4") shouldBe false
        PinHasher.isValidPin("12 4") shouldBe false
        PinHasher.isValidPin("-123") shouldBe false
        // Arabic-Indic digits pass Char.isDigit
        PinHasher.isValidPin("١٢٣٤") shouldBe false
    }

    @Test
    fun `The right PIN verifies against its hash`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("4821", salt)

        PinHasher.verify("4821", salt, hash) shouldBe true
    }

    @Test
    fun `A wrong PIN does not verify`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("4821", salt)

        PinHasher.verify("4822", salt, hash) shouldBe false
        PinHasher.verify("1284", salt, hash) shouldBe false
    }

    @Test
    fun `The same PIN hashes differently under another salt`() {
        val salt = PinHasher.newSalt()
        val otherSalt = PinHasher.newSalt()

        salt.toList() shouldNotBe otherSalt.toList()
        PinHasher.hash("4821", salt).toList() shouldNotBe PinHasher.hash("4821", otherSalt).toList()
        PinHasher.verify("4821", otherSalt, PinHasher.hash("4821", salt)) shouldBe false
    }

    @Test
    fun `The hash is not the PIN`() {
        val hash = PinHasher.hash("4821", PinHasher.newSalt())

        hash.size shouldBe 32
        String(hash, Charsets.ISO_8859_1).contains("4821") shouldBe false
    }
}
