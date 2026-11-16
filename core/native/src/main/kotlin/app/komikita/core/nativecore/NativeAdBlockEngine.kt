package app.komikita.core.nativecore

object NativeAdBlockEngine {
    @Volatile
    var isAvailable = NativeLibrary.isAvailable
        private set

    fun replaceRules(rules: String): Boolean = call { nativeReplaceRules(rules) } == true

    fun validateRules(rules: String, strict: Boolean): Boolean? = call { nativeValidateRules(rules, strict) }

    fun shouldBlock(url: String, sourceUrl: String, resourceType: String, method: String): Boolean {
        return call { nativeShouldBlock(url, sourceUrl, resourceType, method) } == true
    }

    private inline fun call(action: () -> Boolean): Boolean? {
        if (!isAvailable) return null
        return try {
            action()
        } catch (_: LinkageError) {
            isAvailable = false
            null
        }
    }

    @JvmStatic
    private external fun nativeReplaceRules(rules: String): Boolean

    @JvmStatic
    private external fun nativeValidateRules(rules: String, strict: Boolean): Boolean

    @JvmStatic
    private external fun nativeShouldBlock(
        url: String,
        sourceUrl: String,
        resourceType: String,
        method: String,
    ): Boolean
}
