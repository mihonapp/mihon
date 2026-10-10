package mihon.sync.drive

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class DriveFailureTest {

    @Test
    fun `a request limit or a server error is retried`() {
        DriveFailure.of(429, emptyList()) shouldBe DriveFailure.Transient
        DriveFailure.of(500, emptyList()) shouldBe DriveFailure.Transient
        DriveFailure.of(503, emptyList()) shouldBe DriveFailure.Transient
        DriveFailure.of(403, listOf("userRateLimitExceeded")) shouldBe DriveFailure.Transient
        DriveFailure.of(403, listOf("rateLimitExceeded")) shouldBe DriveFailure.Transient
    }

    @Test
    fun `a full Drive is reported as such, not as an account to link again`() {
        DriveFailure.of(403, listOf("storageQuotaExceeded")) shouldBe DriveFailure.StorageFull
    }

    @Test
    fun `a daily limit waits for the next round`() {
        DriveFailure.of(403, listOf("dailyLimitExceeded")) shouldBe DriveFailure.Other
    }

    @Test
    fun `a missing or revoked grant asks for the account again`() {
        DriveFailure.of(401, emptyList()) shouldBe DriveFailure.Unauthorized
        DriveFailure.of(403, listOf("insufficientPermissions")) shouldBe DriveFailure.Unauthorized
        DriveFailure.of(403, emptyList()) shouldBe DriveFailure.Unauthorized
    }

    @Test
    fun `anything else is reported and left to the next round`() {
        DriveFailure.of(400, emptyList()) shouldBe DriveFailure.Other
        DriveFailure.of(404, emptyList()) shouldBe DriveFailure.Other
    }
}
