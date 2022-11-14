package eu.kanade.core.util

import java.util.concurrent.ConcurrentHashMap

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
