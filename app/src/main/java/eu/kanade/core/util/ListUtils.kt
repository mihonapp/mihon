package eu.kanade.core.util

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
