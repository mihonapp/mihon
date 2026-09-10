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
    open fun getApplicationContext(): Context = this
}
