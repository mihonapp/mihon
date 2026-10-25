package eu.kanade.tachiyomi.ui.manga

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Selection belongs to one list surface and is always bounded by its displayed rows. */
internal class MangaChapterSelection {
    private val selectedIds = MutableStateFlow(emptySet<Long>())
    val ids = selectedIds.asStateFlow()

    fun retain(chapters: List<ChapterList.Item>) {
        val displayedIds = chapters.mapTo(HashSet()) { it.id }
        selectedIds.update { it.intersect(displayedIds) }
    }

    fun toggle(chapters: List<ChapterList.Item>, item: ChapterList.Item, selected: Boolean, fromLongPress: Boolean) {
        val index = chapters.indexOfFirst { it.id == item.id }
        if (index < 0) return
        val displayedIds = chapters.mapTo(HashSet()) { it.id }
        selectedIds.update { previous ->
            val ids = previous.intersect(displayedIds).toMutableSet()
            if ((item.id in ids) == selected) return@update ids
            val first = chapters.indexOfFirst { it.id in ids }
            val last = chapters.indexOfLast { it.id in ids }
            if (selected) ids.add(item.id) else ids.remove(item.id)
            if (selected && fromLongPress && first >= 0) {
                val range = when {
                    index < first -> index + 1..<first
                    index > last -> last + 1..<index
                    else -> IntRange.EMPTY
                }
                range.forEach { ids.add(chapters[it].id) }
            }
            ids
        }
    }

    fun selectAll(chapters: List<ChapterList.Item>, selected: Boolean) {
        selectedIds.value = if (selected) chapters.mapTo(HashSet()) { it.id } else emptySet()
    }

    fun invert(chapters: List<ChapterList.Item>) {
        selectedIds.update { previous -> chapters.filterNot { it.id in previous }.mapTo(HashSet()) { it.id } }
    }
}
