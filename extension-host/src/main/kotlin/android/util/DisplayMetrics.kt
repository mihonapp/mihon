package android.util

/** Virtual source-WebView metrics, independent of the user's physical monitor DPI. */
class DisplayMetrics {
    @JvmField var widthPixels: Int = 1024

    @JvmField var heightPixels: Int = 768

    @JvmField var density: Float = 1f

    @JvmField var densityDpi: Int = 160

    @JvmField var scaledDensity: Float = 1f

    @JvmField var xdpi: Float = 160f

    @JvmField var ydpi: Float = 160f
}
