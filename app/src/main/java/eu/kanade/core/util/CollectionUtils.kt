package eu.kanade.core.util

import androidx.compose.ui.util.fastForEach
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun <T : R, R : Any> List<T>.insertSeparators(
    generator: (T?, T?) -> R?,
): List<R> {
    if (isEmpty()) return emptyList()
    val newList = mutableListOf<R>()
    for (i in -1..lastIndex) {
        val before = getOrNull(i)
        before?.let(newList::add)
        val after = getOrNull(i + 1)
        val separator = generator.invoke(before, after)
        separator?.let(newList::add)
    }
    return newList
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
@OptIn(ExperimentalContracts::class)
inline fun <T, R> List<T>.fastMapNotNull(transform: (T) -> R?): List<R> {
    contract { callsInPlace(transform) }
    val destination = ArrayList<R>()
    fastForEach { element ->
        transform(element)?.let(destination::add)
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

/**
 * Returns the number of entries not matching the given [predicate].
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> List<T>.fastCountNot(predicate: (T) -> Boolean): Int {
    contract { callsInPlace(predicate) }
    var count = size
    fastForEach { if (predicate(it)) --count }
    return count
}

/**
 * Returns a list containing only elements from the given collection
 * having distinct keys returned by the given [selector] function.
 *
 * Among elements of the given collection with equal keys, only the first one will be present in the resulting list.
 * The elements in the resulting list are in the same order as they were in the source collection.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T, K> List<T>.fastDistinctBy(selector: (T) -> K): List<T> {
    contract { callsInPlace(selector) }
    val set = HashSet<K>()
    val list = ArrayList<T>()
    fastForEach {
        val key = selector(it)
        if (set.add(key)) list.add(it)
    }
    return list
}
