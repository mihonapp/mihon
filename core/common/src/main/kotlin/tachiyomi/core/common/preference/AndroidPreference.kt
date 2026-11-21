package tachiyomi.core.common.preference

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.util.system.logcat

sealed class AndroidPreference<T>(
    private val preferences: SharedPreferences,
    private val keyFlow: Flow<String?>,
    private val key: String,
    defaultValue: () -> T,
) : Preference<T> {

    protected val defaultValue: T by lazy(defaultValue)

    abstract fun read(preferences: SharedPreferences, key: String): T

    abstract fun write(key: String, value: T): Editor.() -> Unit

    override fun key(): String {
        return key
    }

    override fun get(): T {
        return try {
            read(preferences, key)
        } catch (e: ClassCastException) {
            logcat { "Invalid value for $key; deleting" }
            delete()
            defaultValue
        }
    }

    override fun set(value: T) {
        preferences.edit(action = write(key, value))
    }

    override fun isSet(): Boolean {
        return preferences.contains(key)
    }

    override fun delete() {
        preferences.edit {
            remove(key)
        }
    }

    override fun defaultValue(): T {
        return defaultValue
    }

    override fun changes(): Flow<T> {
        return keyFlow
            .filter { it == key || it == null }
            .onStart { emit("ignition") }
            .map { get() }
            .conflate()
    }

    override fun stateIn(scope: CoroutineScope): StateFlow<T> {
        return changes().stateIn(scope, SharingStarted.Eagerly, get())
    }

    class StringPrimitive(
        preferences: SharedPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: () -> String,
    ) : AndroidPreference<String>(preferences, keyFlow, key, defaultValue) {
        override fun read(preferences: SharedPreferences, key: String): String {
            return preferences.getString(key, null) ?: defaultValue
        }

        override fun write(key: String, value: String): Editor.() -> Unit = {
            putString(key, value)
        }
    }

    class LongPrimitive(
        preferences: SharedPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: Long,
    ) : AndroidPreference<Long>(preferences, keyFlow, key, { defaultValue }) {
        override fun read(preferences: SharedPreferences, key: String): Long {
            return preferences.getLong(key, defaultValue)
        }

        override fun write(key: String, value: Long): Editor.() -> Unit = {
            putLong(key, value)
        }
    }

    class IntPrimitive(
        preferences: SharedPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: Int,
    ) : AndroidPreference<Int>(preferences, keyFlow, key, { defaultValue }) {
        override fun read(preferences: SharedPreferences, key: String): Int {
            return preferences.getInt(key, defaultValue)
        }

        override fun write(key: String, value: Int): Editor.() -> Unit = {
            putInt(key, value)
        }
    }

    class FloatPrimitive(
        preferences: SharedPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: Float,
    ) : AndroidPreference<Float>(preferences, keyFlow, key, { defaultValue }) {
        override fun read(preferences: SharedPreferences, key: String): Float {
            return preferences.getFloat(key, defaultValue)
        }

        override fun write(key: String, value: Float): Editor.() -> Unit = {
            putFloat(key, value)
        }
    }

    class BooleanPrimitive(
        preferences: SharedPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: Boolean,
    ) : AndroidPreference<Boolean>(preferences, keyFlow, key, { defaultValue }) {
        override fun read(preferences: SharedPreferences, key: String): Boolean {
            return preferences.getBoolean(key, defaultValue)
        }

        override fun write(key: String, value: Boolean): Editor.() -> Unit = {
            putBoolean(key, value)
        }
    }

    class StringSetPrimitive(
        preferences: SharedPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: Set<String>,
    ) : AndroidPreference<Set<String>>(preferences, keyFlow, key, { defaultValue }) {
        override fun read(preferences: SharedPreferences, key: String): Set<String> {
            return preferences.getStringSet(key, null) ?: defaultValue
        }

        override fun write(key: String, value: Set<String>): Editor.() -> Unit = {
            putStringSet(key, value)
        }
    }

    class ObjectAsString<T>(
        preferences: SharedPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: T,
        private val serializer: (T) -> String,
        private val deserializer: (String) -> T,
    ) : AndroidPreference<T>(preferences, keyFlow, key, { defaultValue }) {
        override fun read(preferences: SharedPreferences, key: String): T {
            return try {
                preferences.getString(key, null)?.let(deserializer) ?: defaultValue
            } catch (e: Exception) {
                defaultValue
            }
        }

        override fun write(key: String, value: T): Editor.() -> Unit = {
            putString(key, serializer(value))
        }
    }

    class ObjectAsInt<T>(
        preferences: SharedPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: T,
        private val serializer: (T) -> Int,
        private val deserializer: (Int) -> T,
    ) : AndroidPreference<T>(preferences, keyFlow, key, { defaultValue }) {
        override fun read(preferences: SharedPreferences, key: String): T {
            return try {
                if (preferences.contains(key)) preferences.getInt(key, 0).let(deserializer) else defaultValue
            } catch (e: Exception) {
                defaultValue
            }
        }

        override fun write(key: String, value: T): Editor.() -> Unit = {
            putInt(key, serializer(value))
        }
    }

    class ObjectSetAsStringSet<T>(
        preferences: SharedPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: Set<T>,
        private val serializer: (T) -> String,
        private val deserializer: (String) -> T?,
    ) : AndroidPreference<Set<T>>(preferences, keyFlow, key, { defaultValue }) {
        override fun read(preferences: SharedPreferences, key: String): Set<T> {
            return try {
                preferences.getStringSet(key, null)?.mapNotNull(deserializer)?.toSet() ?: defaultValue
            } catch (_: Exception) {
                defaultValue
            }
        }

        override fun write(key: String, value: Set<T>): Editor.() -> Unit = {
            putStringSet(key, value.map(serializer).toSet())
        }
    }
}
