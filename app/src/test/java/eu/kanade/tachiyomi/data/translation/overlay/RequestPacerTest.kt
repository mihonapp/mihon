package eu.kanade.tachiyomi.data.translation.overlay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RequestPacerTest {

    private var now = 1_000_000L
    private val pacer = RequestPacer { now }

    @Test
    fun `first request does not wait`() {
        assertEquals(0L, pacer.reserveSlot(6_000))
    }

    @Test
    fun `second request waits for the base interval`() {
        pacer.reserveSlot(6_000)
        assertEquals(6_000L, pacer.reserveSlot(6_000))
    }

    @Test
    fun `no wait after the interval has passed`() {
        pacer.reserveSlot(6_000)
        now += 7_000
        assertEquals(0L, pacer.reserveSlot(6_000))
    }

    @Test
    fun `rate limit adds extra delay and honours retry hint`() {
        pacer.reserveSlot(1_000)
        pacer.onRateLimited(retryAfterMillis = 20_000)

        assertEquals(4_000L, pacer.currentExtraMillis())
        assertTrue(pacer.reserveSlot(1_000) >= 20_000)
    }

    @Test
    fun `extra delay doubles up to the maximum`() {
        repeat(10) { pacer.onRateLimited(null) }
        assertEquals(60_000L, pacer.currentExtraMillis())
    }

    @Test
    fun `successes shrink the extra delay to zero`() {
        pacer.onRateLimited(null)
        repeat(10) { pacer.onSuccess() }
        assertEquals(0L, pacer.currentExtraMillis())
    }
}
