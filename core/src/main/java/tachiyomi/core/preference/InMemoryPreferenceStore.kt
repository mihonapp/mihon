package tachiyomi.core.preference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Local-copy implementation of PreferenceStore mostly for test and preview purposes
 */
class InMemoryPreferenceStore(
    initialPreferences: Sequence<InMemoryPreference<*>> = sequenceOf(),
) : PreferenceStore {

    private val preferences: Map<String, Preference<*>> =
        initialPreferences.toList().associateBy { it.key() }

    override fun getString(key: String, defaultValue: String): Preference<String> {
        val default = InMemoryPreference(key, null, defaultValue)
        val data: String? = preferences[key]?.get() as? String
        return if (data == null) default else InMemoryPreference(key, data, defaultValue)
    }

    override fun getLong(key: String, defaultValue: Long): Preference<Long> {
        val default = InMemoryPreference(key, null, defaultValue)
        val data: Long? = preferences[key]?.get() as? Long
        return if (data == null) default else InMemoryPreference(key, data, defaultValue)
    }

    override fun getInt(key: String, defaultValue: Int): Preference<Int> {
        val default = InMemoryPreference(key, null, defaultValue)
        val data: Int? = preferences[key]?.get() as? Int
        return if (data == null) default else InMemoryPreference(key, data, defaultValue)
    }

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> {
        val default = InMemoryPreference(key, null, defaultValue)
        val data: Float? = preferences[key]?.get() as? Float
        return if (data == null) default else InMemoryPreference(key, data, defaultValue)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> {
        val default = InMemoryPreference(key, null, defaultValue)
        val data: Boolean? = preferences[key]?.get() as? Boolean
        return if (data == null) default else InMemoryPreference(key, data, defaultValue)
    }

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> {
        TODO("Not yet implemented")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> {
        val default = InMemoryPreference(key, null, defaultValue)
        val data: T? = preferences[key]?.get() as? T
        return if (data == null) default else InMemoryPreference(key, data, defaultValue)
    }

    override fun getAll(): Map<String, *> {
        return preferences
    }

    class InMemoryPreference<T>(
        private val key: String,
        private var data: T?,
        private val defaultValue: T,
    ) : Preference<T> {
        override fun key(): String = key

        override fun get(): T = data ?: defaultValue()

        override fun isSet(): Boolean = data != null

        override fun delete() {
            data = null
        }

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> = flow { data }

        override fun stateIn(scope: CoroutineScope): StateFlow<T> {
            return changes().stateIn(scope, SharingStarted.Eagerly, get())
        }

        override fun set(value: T) {
            data = value
        }
    }
}
