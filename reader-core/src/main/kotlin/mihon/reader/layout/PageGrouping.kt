package mihon.reader.layout

import mihon.reader.model.PageDescriptor
import mihon.reader.model.ReadingMode

object PageGrouping {
    fun dual(pages: List<PageDescriptor>, reserveCover: Boolean): List<List<PageDescriptor>> {
        if (pages.isEmpty()) return emptyList()

        val groups = buildList {
            var index = 0
            if (reserveCover) {
                add(listOf(pages.first()))
                index = 1
            }
            while (index < pages.size) {
                add(pages.subList(index, minOf(index + 2, pages.size)))
                index += 2
            }
        }
        return groups
    }

    fun forMode(
        pages: List<PageDescriptor>,
        mode: ReadingMode,
        reserveCover: Boolean,
    ): List<List<PageDescriptor>> {
        val groups = if (mode.isDualPage) dual(pages, reserveCover) else pages.map(::listOf)
        return if (mode.isRightToLeft) groups.map(List<PageDescriptor>::reversed) else groups
    }
}
