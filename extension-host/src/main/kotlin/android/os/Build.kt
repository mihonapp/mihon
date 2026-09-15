package android.os

/** This host is Windows, not an Android SDK installation; callers must use their fallback paths. */
object Build {
    const val MANUFACTURER = "MihonW"
    const val MODEL = "Windows"
    object VERSION {
        const val SDK_INT = 0
        const val RELEASE = "Windows"
    }
}
