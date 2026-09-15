package android.content

class ActivityNotFoundException @JvmOverloads constructor(message: String? = null) : RuntimeException(message)

/** Intent values may be inspected; launching Android activities is unsupported in the host. */
class Intent @JvmOverloads constructor(val action: String? = null, val data: android.net.Uri? = null) {
    var flags: Int = 0
        private set
    fun addFlags(value: Int): Intent = apply { flags = flags or value }
    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
    }
}
