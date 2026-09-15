package android.content

/** Delegates Android source wrappers to the profile-scoped host context. */
open class ContextWrapper(private val base: Context?) : Context() {
    open fun getBaseContext(): Context = requireNotNull(base)
    override fun getPackageName(): String = requireNotNull(base).getPackageName()
    override fun getSharedPreferences(
        name: String,
        mode: Int,
    ): SharedPreferences = requireNotNull(base).getSharedPreferences(name, mode)
    override fun getCacheDir(): java.io.File = requireNotNull(base).getCacheDir()
    override fun getFilesDir(): java.io.File = requireNotNull(base).getFilesDir()
    override fun getAssets(): android.content.res.AssetManager = requireNotNull(base).getAssets()
    override fun getApplicationContext(): Context = base?.getApplicationContext() ?: this
}
