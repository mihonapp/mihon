package android.content.res

/** Host resources expose virtual display metrics; Android resource IDs are not desktop assets. */
class Resources {
    private val metrics = android.util.DisplayMetrics()
    fun getDisplayMetrics(): android.util.DisplayMetrics = metrics
    fun getString(
        id: Int,
    ): String = throw NotFoundException("Android string resource $id is unavailable; use extension assets")
    class NotFoundException(message: String) : RuntimeException(message)
    companion object {
        private val system = Resources()

        @JvmStatic fun getSystem(): Resources = system
    }
}
