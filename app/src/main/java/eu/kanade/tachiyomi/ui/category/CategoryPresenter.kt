package eu.kanade.tachiyomi.ui.category

import android.os.Bundle
import eu.kanade.domain.category.interactor.DeleteCategory
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.interactor.InsertCategory
import eu.kanade.domain.category.interactor.UpdateCategory
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.category.model.CategoryUpdate
import eu.kanade.domain.category.repository.DuplicateNameException
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [CategoryController]. Used to manage the categories of the library.
 */
class CategoryPresenter(
    private val getCategories: GetCategories = Injekt.get(),
    private val insertCategory: InsertCategory = Injekt.get(),
    private val updateCategory: UpdateCategory = Injekt.get(),
    private val deleteCategory: DeleteCategory = Injekt.get(),
) : BasePresenter<CategoryController>() {

    private val _categories: MutableStateFlow<List<Category>> = MutableStateFlow(listOf())
    val categories = _categories.asStateFlow()

    /**
     * Called when the presenter is created.
     *
     * @param savedState The saved state of this presenter.
     */
    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        presenterScope.launchIO {
            getCategories.subscribe()
                .collectLatest { list ->
                    _categories.value = list
                }
        }
    }

    /**
     * Creates and adds a new category to the database.
     *
     * @param name The name of the category to create.
     */
    fun createCategory(name: String) {
        presenterScope.launchIO {
            val result = insertCategory.await(
                name = name,
                order = categories.value.map { it.order + 1L }.maxOrNull() ?: 0L,
            )
            when (result) {
                is InsertCategory.Result.Success -> {}
                is InsertCategory.Result.Error -> {
                    logcat(LogPriority.ERROR, result.error)
                    if (result.error is DuplicateNameException) {
                        launchUI { view?.onCategoryExistsError() }
                    }
                }
            }
        }
    }

    /**
     * Deletes the given categories from the database.
     *
     * @param categories The list of categories to delete.
     */
    fun deleteCategories(categories: List<Category>) {
        presenterScope.launchIO {
            categories.forEach { category ->
                deleteCategory.await(category.id)
            }
        }
    }

    /**
     * Reorders the given categories in the database.
     *
     * @param categories The list of categories to reorder.
     */
    fun reorderCategories(categories: List<Category>) {
        presenterScope.launchIO {
            categories.forEachIndexed { order, category ->
                updateCategory.await(
                    payload = CategoryUpdate(
                        id = category.id,
                        order = order.toLong(),
                    ),
                )
            }
        }
    }

    /**
     * Renames a category.
     *
     * @param category The category to rename.
     * @param name The new name of the category.
     */
    fun renameCategory(category: Category, name: String) {
        presenterScope.launchIO {
            val result = updateCategory.await(
                payload = CategoryUpdate(
                    id = category.id,
                    name = name,
                ),
            )
            when (result) {
                is UpdateCategory.Result.Success -> {}
                is UpdateCategory.Result.Error -> {
                    logcat(LogPriority.ERROR, result.error)
                    if (result.error is DuplicateNameException) {
                        launchUI { view?.onCategoryExistsError() }
                    }
                }
            }
        }
    }
}
