package android.os

/** JVM compatibility surface for Android extensions that use monotonic clocks and rate limiting. */
object SystemClock {

    @JvmStatic
    fun uptimeMillis(): Long = System.nanoTime() / 1_000_000L

    @JvmStatic
    fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000L

    @JvmStatic
    fun elapsedRealtimeNanos(): Long = System.nanoTime()

    @JvmStatic
    fun sleep(milliseconds: Long) {
        if (milliseconds <= 0L) return
        val deadline = uptimeMillis() + milliseconds
        var interrupted = false
        while (true) {
            val remaining = deadline - uptimeMillis()
            if (remaining <= 0L) break
            try {
                Thread.sleep(remaining)
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }
}
