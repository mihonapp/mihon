package mihon.domain.protectedfolder.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * InMemoryPreferenceStore hands out a detached preference on every call, so a value set through one is never
 * seen through another. This one keeps a single value per key.
 */
class FakePreferenceStore : PreferenceStore {

    private val values = MutableStateFlow(emptyMap<String, Any?>())

    private inner class FakePreference<T>(
        private val key: String,
        private val defaultValue: T,
    ) : Preference<T> {
        override fun key() = key

        @Suppress("UNCHECKED_CAST")
        override fun get(): T = values.value[key] as T? ?: defaultValue

        override fun set(value: T) {
            values.value += key to value
        }

        override fun isSet() = key in values.value

        override fun delete() {
            values.value -= key
        }

        override fun defaultValue() = defaultValue

        override fun changes(): Flow<T> = values.map { get() }

        override fun stateIn(scope: CoroutineScope): StateFlow<T> {
            return changes().stateIn(scope, SharingStarted.Eagerly, get())
        }
    }

    override fun getString(key: String, defaultValue: String): Preference<String> = FakePreference(key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> = FakePreference(key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> = FakePreference(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> = FakePreference(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
        FakePreference(key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
        FakePreference(key, defaultValue)

    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> = FakePreference(key, defaultValue)

    override fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ): Preference<T> = FakePreference(key, defaultValue)

    override fun <T> getObjectSetFromStringSet(
        key: String,
        defaultValue: Set<T>,
        serializer: (T) -> String,
        deserializer: (String) -> T?,
    ): Preference<Set<T>> = FakePreference(key, defaultValue)

    override fun getAll(): Map<String, *> = values.value
}
