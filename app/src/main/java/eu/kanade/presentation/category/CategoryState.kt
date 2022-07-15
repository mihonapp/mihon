package eu.kanade.presentation.category

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.domain.category.model.Category
import eu.kanade.tachiyomi.ui.category.CategoryPresenter

@Stable
interface CategoryState {
    val isLoading: Boolean
    var dialog: CategoryPresenter.Dialog?
    val categories: List<Category>
    val isEmpty: Boolean
}

fun CategoryState(): CategoryState {
    return CategoryStateImpl()
}

class CategoryStateImpl : CategoryState {
    override var isLoading: Boolean by mutableStateOf(true)
    override var dialog: CategoryPresenter.Dialog? by mutableStateOf(null)
    override var categories: List<Category> by mutableStateOf(emptyList())
    override val isEmpty: Boolean by derivedStateOf { categories.isEmpty() }
}
