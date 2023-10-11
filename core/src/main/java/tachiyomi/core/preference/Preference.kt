package tachiyomi.core.preference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface Preference<T> {

    fun key(): String

    fun get(): T

    fun set(value: T)

    fun isSet(): Boolean

    fun delete()

    fun defaultValue(): T

    fun changes(): Flow<T>

    fun stateIn(scope: CoroutineScope): StateFlow<T>

    val isPrivate: Boolean
        get() = key().startsWith(PRIVATE_PREFIX)

    companion object {
        /**
         * A preference that should not be exposed in places like backups.
         */
        fun isPrivate(key: String): Boolean {
            return key.startsWith(PRIVATE_PREFIX)
        }

        fun privateKey(key: String): String {
            return "${PRIVATE_PREFIX}$key"
        }

        private const val PRIVATE_PREFIX = "__PRIVATE_"
    }
}

inline fun <reified T, R : T> Preference<T>.getAndSet(crossinline block: (T) -> R) = set(
    block(get()),
)

operator fun <T> Preference<Set<T>>.plusAssign(item: T) {
    set(get() + item)
}

operator fun <T> Preference<Set<T>>.minusAssign(item: T) {
    set(get() - item)
}

fun Preference<Boolean>.toggle(): Boolean {
    set(!get())
    return get()
}
