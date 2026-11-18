package mihon.domain.protectedfolder.service

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ProtectedFolderPinTest {

    private lateinit var preferences: ProtectedFolderPreferences
    private lateinit var pin: ProtectedFolderPin
    private var now = Instant.fromEpochMilliseconds(1_000_000)

    @BeforeEach
    fun beforeEach() {
        preferences = ProtectedFolderPreferences(FakePreferenceStore())
        pin = ProtectedFolderPin(preferences)
        pin.clock = object : Clock {
            override fun now() = now
        }
    }

    @Test
    fun `No PIN is set at first`() {
        pin.isSet shouldBe false
        pin.verify("0000") shouldBe ProtectedFolderPin.Result.Wrong(Duration.ZERO)
        preferences.failedAttempts.get() shouldBe 0
    }

    @Test
    fun `A PIN that is not four digits is rejected`() {
        pin.set("12a4") shouldBe false
        pin.set("123") shouldBe false
        pin.set("12345") shouldBe false

        pin.isSet shouldBe false
    }

    @Test
    fun `Only a salted hash of the PIN is stored`() {
        pin.set("4821") shouldBe true

        pin.isSet shouldBe true
        preferences.pinHash.get() shouldNotContain "4821"
        preferences.pinSalt.get() shouldNotContain "4821"
        preferences.pinSalt.get().isNotEmpty() shouldBe true
        Preference.isAppState(preferences.pinHash.key()) shouldBe true
        Preference.isAppState(preferences.pinSalt.key()) shouldBe true
    }

    @Test
    fun `The right PIN verifies and a wrong one does not`() {
        pin.set("4821")

        pin.verify("4821") shouldBe ProtectedFolderPin.Result.Success
        pin.verify("4820") shouldBe ProtectedFolderPin.Result.Wrong(Duration.ZERO)
        pin.verify("48 1") shouldBe ProtectedFolderPin.Result.Wrong(Duration.ZERO)
    }

    @Test
    fun `The fifth wrong PIN in a row locks entry for 30 seconds`() {
        pin.set("4821")

        repeat(4) { pin.verify("0000") shouldBe ProtectedFolderPin.Result.Wrong(Duration.ZERO) }
        pin.verify("0000") shouldBe ProtectedFolderPin.Result.Wrong(30.seconds)

        pin.lockoutRemaining() shouldBe 30.seconds
    }

    @Test
    fun `Not even the right PIN is accepted during a lockout`() {
        pin.set("4821")
        repeat(5) { pin.verify("0000") }

        now += 10.seconds

        pin.verify("4821") shouldBe ProtectedFolderPin.Result.LockedOut(20.seconds)
        // An attempt during the lockout is not counted either
        preferences.failedAttempts.get() shouldBe 5
    }

    @Test
    fun `Each wrong PIN after a lockout doubles the next one`() {
        pin.set("4821")
        repeat(5) { pin.verify("0000") }

        now += 30.seconds
        pin.verify("0000") shouldBe ProtectedFolderPin.Result.Wrong(1.minutes)

        now += 1.minutes
        pin.verify("0000") shouldBe ProtectedFolderPin.Result.Wrong(2.minutes)
    }

    @Test
    fun `The right PIN resets the failed attempts`() {
        pin.set("4821")
        repeat(5) { pin.verify("0000") }
        now += 30.seconds

        pin.verify("4821") shouldBe ProtectedFolderPin.Result.Success

        preferences.failedAttempts.get() shouldBe 0
        pin.lockoutRemaining() shouldBe Duration.ZERO
        repeat(4) { pin.verify("0000") shouldBe ProtectedFolderPin.Result.Wrong(Duration.ZERO) }
    }

    @Test
    fun `Setting the clock back does not stretch a lockout`() {
        pin.set("4821")
        repeat(5) { pin.verify("0000") }

        now -= 10.minutes

        pin.lockoutRemaining() shouldBe 30.seconds
    }

    @Test
    fun `Clearing removes the PIN and the lockout`() {
        pin.set("4821")
        repeat(5) { pin.verify("0000") }

        pin.clear()

        pin.isSet shouldBe false
        preferences.pinHash.isSet() shouldBe false
        preferences.pinSalt.isSet() shouldBe false
        preferences.failedAttempts.get() shouldBe 0
        pin.lockoutRemaining() shouldBe Duration.ZERO
    }

    @Test
    fun `Changing the PIN invalidates the old one`() {
        pin.set("4821")
        pin.set("1357")

        pin.verify("4821").shouldBeInstanceOf<ProtectedFolderPin.Result.Wrong>()
        pin.verify("1357") shouldBe ProtectedFolderPin.Result.Success
    }
}
