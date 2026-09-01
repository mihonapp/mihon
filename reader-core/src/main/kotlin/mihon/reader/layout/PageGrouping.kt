package mihon.reader.layout

import mihon.reader.model.PageDescriptor
import mihon.reader.model.ReadingMode
import java.util.Collections

object PageGrouping {
    fun dual(pages: List<PageDescriptor>, reserveCover: Boolean): List<List<PageDescriptor>> {
        if (pages.isEmpty()) return emptyList()

        val groups = buildList {
            var index = 0
            if (reserveCover) {
                add(immutableSnapshot(listOf(pages.first())))
                index = 1
            }
            while (index < pages.size) {
                add(immutableSnapshot(pages.subList(index, minOf(index + 2, pages.size))))
                index += 2
            }
        }
        return immutableSnapshot(groups)
    }

    fun forMode(
        pages: List<PageDescriptor>,
        mode: ReadingMode,
        reserveCover: Boolean,
    ): List<List<PageDescriptor>> {
        val groups = if (mode.isDualPage) dual(pages, reserveCover) else immutableSnapshot(pages.map(::listOf))
        return if (mode.isRightToLeft) {
            immutableSnapshot(groups.map { immutableSnapshot(it.reversed()) })
        } else {
            groups
        }
    }
}

private fun <T> immutableSnapshot(items: Iterable<T>): List<T> = Collections.unmodifiableList(items.toList())
