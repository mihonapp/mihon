package app.komikita.core.nativecore

/** Optional native batch processing; failure always leaves the Kotlin implementation available. */
object NativeChapterEngine {
    // Match the Rust limit so oversized lists skip the transfer array.
    private const val MAX_NATIVE_CHAPTERS = 1_000_000

    @Volatile
    private var available = try {
        System.loadLibrary("komikita_native")
        true
    } catch (_: LinkageError) {
        false
    } catch (_: SecurityException) {
        false
    }

    /** Avoid allocating a transfer array when this build or device cannot use the native library. */
    fun missingChaptersCount(numbers: List<Double>): Int? {
        if (!available || numbers.size > MAX_NATIVE_CHAPTERS) return null
        return missingChaptersCount(numbers.toDoubleArray())
    }

    fun missingChaptersCount(numbers: DoubleArray): Int? {
        if (!available) return null
        return try {
            nativeMissingChaptersCount(numbers).takeIf { it >= 0 }
        } catch (_: LinkageError) {
            available = false
            null
        }
    }

    @JvmStatic
    private external fun nativeMissingChaptersCount(numbers: DoubleArray): Int
}
