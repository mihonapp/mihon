package android.app

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

open class Application : Context() {

    private val prefs = ConcurrentHashMap<String, SharedPreferences>()
    private val baseDir = File(System.getProperty("java.io.tmpdir"), "mihon_app").apply { mkdirs() }
    private val cacheDir = File(baseDir, "cache").apply { mkdirs() }
    private val filesDir = File(baseDir, "files").apply { mkdirs() }

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return prefs.computeIfAbsent(name) { MemorySharedPreferences() }
    }

    override fun getCacheDir(): File = cacheDir
    override fun getFilesDir(): File = filesDir
    override fun getPackageName(): String = "eu.kanade.tachiyomi"
    open fun onCreate() {}

    private class MemorySharedPreferences : SharedPreferences {
        private val data = ConcurrentHashMap<String, Any>()
        private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): Map<String, *> = HashMap(data)

        override fun getString(key: String, defValue: String?): String? {
            val v = data[key] ?: return defValue
            return v.toString()
        }

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
            val v = data[key] ?: return defValues
            return (v as? Set<String>) ?: defValues
        }

        override fun getInt(key: String, defValue: Int): Int {
            val v = data[key] ?: return defValue
            return when (v) {
                is Number -> v.toInt()
                is String -> v.toIntOrNull() ?: defValue
                else -> defValue
            }
        }

        override fun getLong(key: String, defValue: Long): Long {
            val v = data[key] ?: return defValue
            return when (v) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull() ?: defValue
                else -> defValue
            }
        }

        override fun getFloat(key: String, defValue: Float): Float {
            val v = data[key] ?: return defValue
            return when (v) {
                is Number -> v.toFloat()
                is String -> v.toFloatOrNull() ?: defValue
                else -> defValue
            }
        }

        override fun getBoolean(key: String, defValue: Boolean): Boolean {
            val v = data[key] ?: return defValue
            return when (v) {
                is Boolean -> v
                is String -> v.toBoolean()
                else -> defValue
            }
        }

        override fun contains(key: String): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = EditorImpl()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) {
            listeners.addIfAbsent(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) {
            listeners.remove(listener)
        }

        private inner class EditorImpl : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply {
                pending[key] = value
            }

            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = apply {
                pending[key] = values?.toSet()
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply {
                pending[key] = value
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply {
                pending[key] = value
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply {
                pending[key] = value
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply {
                pending[key] = value
            }

            override fun remove(key: String): SharedPreferences.Editor = apply {
                pending[key] = this
            }

            override fun clear(): SharedPreferences.Editor = apply {
                clearAll = true
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) {
                    data.clear()
                }
                for ((k, v) in pending) {
                    if (v === this) {
                        data.remove(k)
                    } else if (v != null) {
                        data[k] = v
                    } else {
                        data.remove(k)
                    }
                    listeners.forEach { it.onSharedPreferenceChanged(this@MemorySharedPreferences, k) }
                }
                pending.clear()
            }
        }
    }
}
