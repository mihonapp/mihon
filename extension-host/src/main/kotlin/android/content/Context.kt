package android.content

import java.io.File

abstract class Context {
    companion object {
        const val MODE_PRIVATE = 0
    }

    abstract fun getPackageName(): String
    abstract fun getSharedPreferences(name: String, mode: Int): SharedPreferences
    abstract fun getCacheDir(): File
    abstract fun getFilesDir(): File
    open fun getAssets(): android.content.res.AssetManager = android.content.res.AssetManager(
        File(getFilesDir(), "assets"),
    )
    open fun getApplicationContext(): Context = this
    open fun getResources(): android.content.res.Resources = android.content.res.Resources.getSystem()
    open fun startActivity(
        intent: Intent,
    ): Unit = throw ActivityNotFoundException(
        "Android activities are unsupported; open the source in the desktop browser",
    )
}
