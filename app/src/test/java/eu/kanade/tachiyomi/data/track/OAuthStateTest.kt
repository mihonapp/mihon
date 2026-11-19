package eu.kanade.tachiyomi.data.track

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

class OAuthStateTest {
    @Test
    fun onlyTheMatchingCallbackConsumesTheState() {
        val session = OAuthState()
        session.consume(null) shouldBe false

        val state = session.begin()
        session.consume("unrelated-state") shouldBe false
        session.consume(state) shouldBe true
        session.consume(state) shouldBe false
    }

    @Test
    fun startingASecondLoginInvalidatesThePreviousState() {
        val session = OAuthState()
        val previous = session.begin()
        val current = session.begin()

        session.consume(previous) shouldBe false
        session.consume(current) shouldBe true
    }

    @Test
    fun anExpiredLoginCannotBeCompleted() {
        val timeSource = TestTimeSource()
        val session = OAuthState(timeSource)
        val state = session.begin()
        timeSource += 10.minutes

        session.consume(state) shouldBe false
    }
}
