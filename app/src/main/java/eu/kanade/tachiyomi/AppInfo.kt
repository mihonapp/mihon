package eu.kanade.tachiyomi

/**
 * Used by extensions.
 *
 * @since extension-lib 1.3
 */
object AppInfo {
    fun getVersionCode() = BuildConfig.VERSION_CODE
    fun getVersionName() = BuildConfig.VERSION_NAME
}
