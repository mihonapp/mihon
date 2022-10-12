package eu.kanade.tachiyomi.core.preference

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
}

inline fun <reified T, R : T> Preference<T>.getAndSet(crossinline block: (T) -> R) = set(block(get()))
