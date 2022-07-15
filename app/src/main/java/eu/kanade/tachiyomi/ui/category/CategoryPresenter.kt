package eu.kanade.tachiyomi.ui.category

import android.os.Bundle
import eu.kanade.domain.category.interactor.CreateCategoryWithName
import eu.kanade.domain.category.interactor.DeleteCategory
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.interactor.RenameCategory
import eu.kanade.domain.category.interactor.ReorderCategory
import eu.kanade.domain.category.model.Category
import eu.kanade.presentation.category.CategoryState
import eu.kanade.presentation.category.CategoryStateImpl
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CategoryPresenter(
    private val state: CategoryStateImpl = CategoryState() as CategoryStateImpl,
    private val getCategories: GetCategories = Injekt.get(),
    private val createCategoryWithName: CreateCategoryWithName = Injekt.get(),
    private val renameCategory: RenameCategory = Injekt.get(),
    private val reorderCategory: ReorderCategory = Injekt.get(),
    private val deleteCategory: DeleteCategory = Injekt.get(),
) : BasePresenter<CategoryController>(), CategoryState by state {

    private val _events: Channel<Event> = Channel(Int.MAX_VALUE)
    val events = _events.consumeAsFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        presenterScope.launchIO {
            getCategories.subscribe()
                .collectLatest {
                    state.isLoading = false
                    state.categories = it
                }
        }
    }

    fun createCategory(name: String) {
        presenterScope.launchIO {
            when (createCategoryWithName.await(name)) {
                is CreateCategoryWithName.Result.NameAlreadyExistsError -> _events.send(Event.CategoryWithNameAlreadyExists)
                is CreateCategoryWithName.Result.InternalError -> _events.send(Event.InternalError)
                else -> {}
            }
        }
    }

    fun deleteCategory(category: Category) {
        presenterScope.launchIO {
            when (deleteCategory.await(category.id)) {
                is DeleteCategory.Result.InternalError -> _events.send(Event.InternalError)
                else -> {}
            }
        }
    }

    fun moveUp(category: Category) {
        presenterScope.launchIO {
            when (reorderCategory.await(category, category.order - 1)) {
                is ReorderCategory.Result.InternalError -> _events.send(Event.InternalError)
                else -> {}
            }
        }
    }

    fun moveDown(category: Category) {
        presenterScope.launchIO {
            when (reorderCategory.await(category, category.order + 1)) {
                is ReorderCategory.Result.InternalError -> _events.send(Event.InternalError)
                else -> {}
            }
        }
    }

    fun renameCategory(category: Category, name: String) {
        presenterScope.launchIO {
            when (renameCategory.await(category, name)) {
                RenameCategory.Result.NameAlreadyExistsError -> _events.send(Event.CategoryWithNameAlreadyExists)
                is RenameCategory.Result.InternalError -> _events.send(Event.InternalError)
                else -> {}
            }
        }
    }

    sealed class Dialog {
        object Create : Dialog()
        data class Rename(val category: Category) : Dialog()
        data class Delete(val category: Category) : Dialog()
    }

    sealed class Event {
        object CategoryWithNameAlreadyExists : Event()
        object InternalError : Event()
    }
}
