package eu.kanade.core.util

import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun <T : R, R : Any> List<T>.insertSeparators(
    generator: (before: T?, after: T?) -> R?,
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

/**
 * Similar to [eu.kanade.core.util.insertSeparators] but iterates from last to first element
 */
fun <T : R, R : Any> List<T>.insertSeparatorsReversed(
    generator: (before: T?, after: T?) -> R?,
): List<R> {
    if (isEmpty()) return emptyList()
    val newList = mutableListOf<R>()
    for (i in size downTo 0) {
        val after = getOrNull(i)
        after?.let(newList::add)
        val before = getOrNull(i - 1)
        val separator = generator.invoke(before, after)
        separator?.let(newList::add)
    }
    return newList.asReversed()
}

fun <E> HashSet<E>.addOrRemove(value: E, shouldAdd: Boolean) {
    if (shouldAdd) {
        add(value)
    } else {
        remove(value)
    }
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
    return fastFilter { !predicate(it) }
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
