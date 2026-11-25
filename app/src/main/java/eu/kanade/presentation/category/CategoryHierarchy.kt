// Adapted from https://github.com/komikku-app/komikku/pull/1356
// Copyright Komikku contributors, Apache-2.0

package eu.kanade.presentation.category

import tachiyomi.domain.category.model.Category

data class CategoryHierarchyEntry(
    val category: Category,
    val depth: Int,
)

fun buildCategoryHierarchy(categories: List<Category>): List<CategoryHierarchyEntry> {
    if (categories.isEmpty()) return emptyList()

    val childrenByParent = categories
        .groupBy { it.parentId }
        .mapValues { (_, children) -> children.sortedBy { it.order } }
    val visited = mutableSetOf<Long>()
    val result = mutableListOf<CategoryHierarchyEntry>()

    fun traverse(category: Category, depth: Int) {
        if (!visited.add(category.id)) return

        result += CategoryHierarchyEntry(category, depth)
        childrenByParent[category.id].orEmpty().forEach { child ->
            traverse(child, depth + 1)
        }
    }

    childrenByParent[null].orEmpty().forEach { category ->
        traverse(category, 0)
    }

    // Invalid parents and cycles are promoted to roots instead of disappearing or being duplicated.
    categories.sortedBy { it.order }.forEach { category ->
        traverse(category, 0)
    }

    return result
}

fun visibleCategoryHierarchy(
    entries: List<CategoryHierarchyEntry>,
    expandedCategoryIds: Set<Long>,
): List<CategoryHierarchyEntry> {
    val visibleCategoryIds = mutableSetOf<Long>()

    return entries.filter { entry ->
        val isVisible = entry.depth == 0 || entry.category.parentId?.let { parentId ->
            parentId in visibleCategoryIds && parentId in expandedCategoryIds
        } == true

        if (isVisible) visibleCategoryIds += entry.category.id
        isVisible
    }
}

fun buildCategoryDescendants(categories: List<Category>): Map<Long, List<Category>> {
    val childrenByParent = categories
        .filter { it.parentId != null }
        .groupBy { it.parentId!! }
        .mapValues { (_, children) -> children.sortedBy { it.order } }

    return categories.associate { category ->
        val descendants = mutableListOf<Category>()
        val visited = mutableSetOf(category.id)

        fun collect(parentId: Long) {
            childrenByParent[parentId].orEmpty().forEach { child ->
                if (visited.add(child.id)) {
                    descendants += child
                    collect(child.id)
                }
            }
        }

        collect(category.id)
        category.id to descendants
    }
}

fun buildCategoryAncestorIds(
    categories: List<Category>,
    categoryIds: Collection<Long>,
): Set<Long> {
    val categoriesById = categories.associateBy { it.id }
    return buildSet {
        categoryIds.forEach { categoryId ->
            val visited = mutableSetOf(categoryId)
            var parentId = categoriesById[categoryId]?.parentId
            while (parentId != null && visited.add(parentId)) {
                add(parentId)
                parentId = categoriesById[parentId]?.parentId
            }
        }
    }
}

fun moveCategoryHierarchy(
    entries: List<CategoryHierarchyEntry>,
    fromCategoryId: Long,
    toCategoryId: Long,
): List<CategoryHierarchyEntry> {
    val fromIndex = entries.indexOfFirst { it.category.id == fromCategoryId }
    val toIndex = entries.indexOfFirst { it.category.id == toCategoryId }
    if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) return entries

    val fromEntry = entries[fromIndex]
    val targetEntry = entries
        .subList(0, toIndex + 1)
        .lastOrNull { it.depth == fromEntry.depth }
        ?: return entries
    if (
        targetEntry.category.id == fromCategoryId ||
        (fromEntry.depth > 0 && targetEntry.category.parentId != fromEntry.category.parentId)
    ) {
        return entries
    }

    val reordered = entries.toMutableList()
    var subtreeEnd = fromIndex + 1
    while (subtreeEnd < reordered.size && reordered[subtreeEnd].depth > fromEntry.depth) {
        subtreeEnd++
    }
    val subtree = reordered.subList(fromIndex, subtreeEnd).toList()
    reordered.subList(fromIndex, subtreeEnd).clear()

    val targetIndex = reordered.indexOfFirst { it.category.id == targetEntry.category.id }
    if (targetIndex == -1) return entries
    val insertionIndex = if (fromIndex < entries.indexOf(targetEntry)) {
        var targetSubtreeEnd = targetIndex + 1
        while (targetSubtreeEnd < reordered.size && reordered[targetSubtreeEnd].depth > targetEntry.depth) {
            targetSubtreeEnd++
        }
        targetSubtreeEnd
    } else {
        targetIndex
    }
    reordered.addAll(insertionIndex, subtree)

    return reordered
}
