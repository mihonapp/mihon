package android.os

import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import org.junit.jupiter.api.Test

class SystemClockTest {

    @Test
    fun `elapsed realtime and uptime are monotonic for extension rate limiting`() {
        val elapsedStart = SystemClock.elapsedRealtime()
        val uptimeStart = SystemClock.uptimeMillis()

        SystemClock.sleep(2)

        SystemClock.elapsedRealtime() shouldBeGreaterThanOrEqual elapsedStart
        SystemClock.uptimeMillis() shouldBeGreaterThanOrEqual uptimeStart
    }
}
