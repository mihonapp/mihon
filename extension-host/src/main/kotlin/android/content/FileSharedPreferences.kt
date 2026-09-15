package android.content

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CopyOnWriteArrayList

/** Private, typed preferences. apply is deliberately synchronous in the desktop host. */
internal class FileSharedPreferences(private val file: File) : SharedPreferences {
    private val lock = Any()
    private val data = linkedMapOf<String, Any>()
    private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()
    init {
        if (file.isFile) {
            java.io.DataInputStream(file.inputStream().buffered()).use { input ->
                require(input.readInt() == 0x4D505246) { "Invalid preferences file: $file" }
                repeat(input.readInt()) {
                    val key = input.readTextValue()
                    data[key] = when (input.readByte().toInt()) {
                        1 -> input.readTextValue()
                        2 -> input.readInt()
                        3 -> input.readLong()
                        4 -> input.readFloat()
                        5 -> input.readBoolean()
                        6 -> LinkedHashSet<String>().apply { repeat(input.readInt()) { add(input.readTextValue()) } }
                        else -> error("Invalid preference type")
                    }
                }
            }
        }
    }
    private fun java.io.DataInputStream.readTextValue(): String {
        val size = readInt()
        require(size in 0..(64 * 1024 * 1024)) { "Invalid preference string length" }
        return ByteArray(size).also { readFully(it) }.toString(Charsets.UTF_8)
    }
    private fun java.io.DataOutputStream.writeTextValue(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }
    private fun copy(value: Any): Any = if (value is Set<*>) LinkedHashSet(value) else value
    override fun getAll(): Map<String, *> = synchronized(lock) { data.mapValues { copy(it.value) } }
    override fun getString(
        key: String,
        defValue: String?,
    ): String? = synchronized(lock) {
        data[key]?.let { it as String }
            ?: defValue
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = synchronized(lock) {
        (data[key]?.let { it as Set<String> } ?: defValues)?.let { LinkedHashSet(it) }
    }
    override fun getInt(
        key: String,
        defValue: Int,
    ): Int = synchronized(lock) { data[key]?.let { it as Int } ?: defValue }
    override fun getLong(key: String, defValue: Long): Long = synchronized(lock) {
        data[key]?.let { it as Long }
            ?: defValue
    }
    override fun getFloat(key: String, defValue: Float): Float = synchronized(lock) {
        data[key]?.let { it as Float }
            ?: defValue
    }
    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = synchronized(lock) {
        data[key]?.let { it as Boolean }
            ?: defValue
    }
    override fun contains(key: String): Boolean = synchronized(lock) { data.containsKey(key) }
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
    override fun edit(): SharedPreferences.Editor = Editor()

    private fun persist(): Boolean {
        var temporary: File? = null
        return try {
            file.parentFile.mkdirs()
            temporary = Files.createTempFile(file.parentFile.toPath(), "prefs-", ".tmp").toFile()
            java.io.FileOutputStream(temporary).use { stream ->
                val output = java.io.DataOutputStream(stream)
                output.writeInt(0x4D505246)
                output.writeInt(data.size)
                data.forEach { (key, value) ->
                    output.writeTextValue(key)
                    when (value) {
                        is String -> {
                            output.writeByte(1)
                            output.writeTextValue(value)
                        }
                        is Int -> {
                            output.writeByte(2)
                            output.writeInt(value)
                        }
                        is Long -> {
                            output.writeByte(3)
                            output.writeLong(value)
                        }
                        is Float -> {
                            output.writeByte(4)
                            output.writeFloat(value)
                        }
                        is Boolean -> {
                            output.writeByte(5)
                            output.writeBoolean(value)
                        }
                        is Set<*> -> {
                            output.writeByte(6)
                            output.writeInt(value.size)
                            value.forEach { output.writeTextValue(it as String) }
                        }
                    }
                }
                output.flush()
                stream.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (_: java.io.IOException) {
            false
        } finally {
            temporary?.delete()
        }
    }

    private inner class Editor : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private var clearAll = false
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply {
            pending[key] =
                values?.let { LinkedHashSet(it) }
        }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { pending[key] = null }
        override fun clear() = apply { clearAll = true }
        override fun commit(): Boolean {
            val changed = linkedSetOf<String>()
            val success = synchronized(lock) {
                if (clearAll) {
                    changed.addAll(data.keys)
                    data.clear()
                }
                pending.forEach { (key, value) ->
                    if (data[key] != value) changed.add(key)
                    if (value == null) data.remove(key) else data[key] = copy(value)
                }
                pending.clear()
                clearAll = false
                persist()
            }
            changed.forEach { key ->
                listeners.forEach { it.onSharedPreferenceChanged(this@FileSharedPreferences, key) }
            }
            return success
        }
        override fun apply() {
            commit()
        }
    }
}
