package app.komikita.core.nativecore

internal object NativeLibrary {
    val isAvailable = try {
        System.loadLibrary("komikita_native")
        true
    } catch (_: LinkageError) {
        false
    } catch (_: SecurityException) {
        false
    }
}
