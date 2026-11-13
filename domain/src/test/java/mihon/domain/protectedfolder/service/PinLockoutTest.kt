package mihon.domain.protectedfolder.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Execution(ExecutionMode.CONCURRENT)
class PinLockoutTest {

    @Test
    fun `The first four wrong attempts do not lock`() {
        (0..4).forEach { PinLockout.lockoutAfter(it) shouldBe Duration.ZERO }
    }

    @Test
    fun `The fifth wrong attempt locks for 30 seconds`() {
        PinLockout.lockoutAfter(5) shouldBe 30.seconds
    }

    @Test
    fun `Each further wrong attempt doubles the lockout`() {
        PinLockout.lockoutAfter(6) shouldBe 1.minutes
        PinLockout.lockoutAfter(7) shouldBe 2.minutes
        PinLockout.lockoutAfter(8) shouldBe 4.minutes
        PinLockout.lockoutAfter(10) shouldBe 16.minutes
    }

    @Test
    fun `The lockout stops growing instead of overflowing`() {
        val longest = PinLockout.lockoutAfter(17)

        longest shouldBe 30.seconds * 4096
        PinLockout.lockoutAfter(18) shouldBe longest
        PinLockout.lockoutAfter(Int.MAX_VALUE) shouldBe longest
    }
}
