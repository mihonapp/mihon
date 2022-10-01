package eu.kanade.presentation.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.tachiyomi.ui.library.LibraryPresenter

@Stable
interface LibraryState {
    val isLoading: Boolean
    val categories: List<Category>
    var searchQuery: String?
    val selection: List<LibraryManga>
    val selectionMode: Boolean
    var hasActiveFilters: Boolean
    var dialog: LibraryPresenter.Dialog?
}

fun LibraryState(): LibraryState {
    return LibraryStateImpl()
}

class LibraryStateImpl : LibraryState {
    override var isLoading: Boolean by mutableStateOf(true)
    override var categories: List<Category> by mutableStateOf(emptyList())
    override var searchQuery: String? by mutableStateOf(null)
    override var selection: List<LibraryManga> by mutableStateOf(emptyList())
    override val selectionMode: Boolean by derivedStateOf { selection.isNotEmpty() }
    override var hasActiveFilters: Boolean by mutableStateOf(false)
    override var dialog: LibraryPresenter.Dialog? by mutableStateOf(null)
}
