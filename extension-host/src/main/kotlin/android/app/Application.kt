package android.app

import android.content.Context
import android.content.FileSharedPreferences
import android.content.SharedPreferences
import java.io.File
import java.util.concurrent.ConcurrentHashMap

open class Application @JvmOverloads constructor(
    private val baseDir: File = File(System.getProperty("user.dir"), "host-data/default"),
    private val packageName: String = "eu.kanade.tachiyomi",
    private val assetDirectory: File = File(baseDir, "assets"),
) : android.content.ContextWrapper(null) {
    private val prefs = ConcurrentHashMap<String, SharedPreferences>()
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        require(mode == MODE_PRIVATE) { "Only private preferences are supported" }
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(name.toByteArray(Charsets.UTF_8))
        return prefs.computeIfAbsent(name) { FileSharedPreferences(File(baseDir, "shared_prefs/$encoded.bin")) }
    }
    override fun getCacheDir(): File = File(baseDir, "cache").apply { mkdirs() }
    override fun getFilesDir(): File = File(baseDir, "files").apply { mkdirs() }
    override fun getPackageName(): String = packageName
    override fun getAssets(): android.content.res.AssetManager = android.content.res.AssetManager(assetDirectory)
    open fun onCreate() {}
}
