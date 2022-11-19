package eu.kanade.core.util

import androidx.compose.ui.util.fastForEach
import java.util.concurrent.ConcurrentHashMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun <T : R, R : Any> List<T>.insertSeparators(
    generator: (T?, T?) -> R?,
): List<R> {
    if (isEmpty()) return emptyList()
    val newList = mutableListOf<R>()
    for (i in -1..lastIndex) {
        val before = getOrNull(i)
        before?.let { newList.add(it) }
        val after = getOrNull(i + 1)
        val separator = generator.invoke(before, after)
        separator?.let { newList.add(it) }
    }
    return newList
}

/**
 * Returns a new map containing only the key entries of [transform] that are not null.
 */
inline fun <K, V, R> Map<out K, V>.mapNotNullKeys(transform: (Map.Entry<K?, V>) -> R?): ConcurrentHashMap<R, V> {
    val mutableMap = ConcurrentHashMap<R, V>()
    forEach { element -> transform(element)?.let { mutableMap[it] = element.value } }
    return mutableMap
}

fun <E> HashSet<E>.addOrRemove(value: E, shouldAdd: Boolean) {
    if (shouldAdd) {
        add(value)
    } else {
        remove(value)
    }
}

/**
 * Returns a list containing only elements matching the given [predicate].
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
@Suppress("BanInlineOptIn")
@OptIn(ExperimentalContracts::class)
inline fun <T> List<T>.fastFilter(predicate: (T) -> Boolean): List<T> {
    contract { callsInPlace(predicate) }
    val destination = ArrayList<T>()
    fastForEach { if (predicate(it)) destination.add(it) }
    return destination
}

/**
 * Returns a list containing all elements not matching the given [predicate].
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
@Suppress("BanInlineOptIn")
@OptIn(ExperimentalContracts::class)
inline fun <T> List<T>.fastFilterNot(predicate: (T) -> Boolean): List<T> {
    contract { callsInPlace(predicate) }
    val destination = ArrayList<T>()
    fastForEach { if (!predicate(it)) destination.add(it) }
    return destination
}

/**
 * Returns a list containing only the non-null results of applying the
 * given [transform] function to each element in the original collection.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
@Suppress("BanInlineOptIn")
@OptIn(ExperimentalContracts::class)
inline fun <T, R> List<T>.fastMapNotNull(transform: (T) -> R?): List<R> {
    contract { callsInPlace(transform) }
    val destination = ArrayList<R>()
    fastForEach { element ->
        transform(element)?.let { destination.add(it) }
    }
    return destination
}

/**
 * Splits the original collection into pair of lists,
 * where *first* list contains elements for which [predicate] yielded `true`,
 * while *second* list contains elements for which [predicate] yielded `false`.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
@Suppress("BanInlineOptIn")
@OptIn(ExperimentalContracts::class)
inline fun <T> List<T>.fastPartition(predicate: (T) -> Boolean): Pair<List<T>, List<T>> {
    contract { callsInPlace(predicate) }
    val first = ArrayList<T>()
    val second = ArrayList<T>()
    fastForEach {
        if (predicate(it)) {
            first.add(it)
        } else {
            second.add(it)
        }
    }
    return Pair(first, second)
}
