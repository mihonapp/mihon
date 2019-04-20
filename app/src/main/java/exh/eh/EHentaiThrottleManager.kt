package exh.eh

class EHentaiThrottleManager(private val max: Int = THROTTLE_MAX,
                             private val inc: Int = THROTTLE_INC) {
    private var lastThrottleTime: Long = 0
    var throttleTime: Long = 0
        private set

    fun throttle() {
        //Throttle requests if necessary
        val now = System.currentTimeMillis()
        val timeDiff = now - lastThrottleTime
        if(timeDiff < throttleTime)
            Thread.sleep(throttleTime - timeDiff)

        if(throttleTime < max)
            throttleTime += inc

        lastThrottleTime = System.currentTimeMillis()
    }

    fun resetThrottle() {
        lastThrottleTime = 0
        throttleTime = 0
    }

    companion object {
        const val THROTTLE_MAX = 5500
        const val THROTTLE_INC = 20
    }
}