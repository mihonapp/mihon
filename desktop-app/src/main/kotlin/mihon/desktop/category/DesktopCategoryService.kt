package mihon.desktop.category

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository

class DesktopCategoryService(
    private val repository: LibraryRepository,
    private val mutationPort: LibraryMutationPort,
    private val scope: CoroutineScope,
) {
    val categories: StateFlow<List<DesktopCategory>> = repository.observeCategories()
        .map { records ->
            records.map { it.toDesktopCategory() }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    fun createCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        scope.launch(Dispatchers.IO) {
            val maxOrder = categories.value.maxOfOrNull { it.order } ?: 0L
            mutationPort.upsertCategory(
                CategoryRecord(
                    name = trimmed,
                    sortOrder = maxOrder + 1,
                ),
            )
        }
    }

    fun renameCategory(categoryId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        scope.launch(Dispatchers.IO) {
            mutationPort.updateCategoryName(categoryId, trimmed)
        }
    }

    fun reorderCategory(categoryId: Long, newOrder: Long) {
        scope.launch(Dispatchers.IO) {
            mutationPort.updateCategoryOrder(categoryId, newOrder)
        }
    }

    fun deleteCategory(categoryId: Long) {
        scope.launch(Dispatchers.IO) {
            mutationPort.deleteCategory(categoryId)
        }
    }

    fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        scope.launch(Dispatchers.IO) {
            mutationPort.setMangaCategories(mangaId, categoryIds)
        }
    }
}

private fun CategoryRecord.toDesktopCategory() = DesktopCategory(
    id = id,
    name = name,
    order = sortOrder,
    flags = flags,
)
