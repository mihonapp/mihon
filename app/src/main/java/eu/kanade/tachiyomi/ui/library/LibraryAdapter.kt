package eu.kanade.tachiyomi.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import eu.kanade.domain.category.model.Category
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.LibraryCategoryBinding
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.widget.RecyclerViewPagerAdapter
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * This adapter stores the categories from the library, used with a ViewPager.
 *
 * @constructor creates an instance of the adapter.
 */
class LibraryAdapter(
    private val controller: LibraryController,
    private val preferences: PreferencesHelper = Injekt.get(),
) : RecyclerViewPagerAdapter() {

    /**
     * The categories to bind in the adapter.
     */
    var categories: List<Category> = emptyList()
        private set

    /**
     * The number of manga in each category.
     * List order must be the same as [categories]
     */
    private var itemsPerCategory: List<Int> = emptyList()

    private var boundViews = arrayListOf<View>()

    private val isPerCategory by lazy { preferences.categorizedDisplaySettings().get() }
    private var currentDisplayMode = preferences.libraryDisplayMode().get()

    init {
        preferences.libraryDisplayMode()
            .asFlow()
            .drop(1)
            .onEach {
                currentDisplayMode = it
            }
            .launchIn(controller.viewScope)
    }

    /**
     * Pair of category and size of category
     */
    fun updateCategories(new: List<Pair<Category, Int>>) {
        var updated = false

        val newCategories = new.map { it.first }
        if (categories != newCategories) {
            categories = newCategories
            updated = true
        }

        val newItemsPerCategory = new.map { it.second }
        if (itemsPerCategory !== newItemsPerCategory) {
            itemsPerCategory = newItemsPerCategory
            updated = true
        }

        if (updated) {
            notifyDataSetChanged()
        }
    }

    /**
     * Creates a new view for this adapter.
     *
     * @return a new view.
     */
    override fun inflateView(container: ViewGroup, viewType: Int): View {
        val binding = LibraryCategoryBinding.inflate(LayoutInflater.from(container.context), container, false)
        val view: LibraryCategoryView = binding.root
        view.onCreate(controller, binding, viewType)
        return view
    }

    /**
     * Binds a view with a position.
     *
     * @param view the view to bind.
     * @param position the position in the adapter.
     */
    override fun bindView(view: View, position: Int) {
        (view as LibraryCategoryView).onBind(categories[position])
        boundViews.add(view)
    }

    /**
     * Recycles a view.
     *
     * @param view the view to recycle.
     * @param position the position in the adapter.
     */
    override fun recycleView(view: View, position: Int) {
        (view as LibraryCategoryView).onRecycle()
        boundViews.remove(view)
    }

    /**
     * Returns the number of categories.
     *
     * @return the number of categories or 0 if the list is null.
     */
    override fun getCount(): Int {
        return categories.size
    }

    /**
     * Returns the title to display for a category.
     *
     * @param position the position of the element.
     * @return the title to display.
     */
    override fun getPageTitle(position: Int): CharSequence {
        return if (!preferences.categoryNumberOfItems().get()) {
            categories[position].name
        } else {
            categories[position].let { "${it.name} (${itemsPerCategory[position]})" }
        }
    }

    /**
     * Returns the position of the view.
     */
    override fun getItemPosition(obj: Any): Int {
        val view = obj as? LibraryCategoryView ?: return POSITION_NONE
        val index = categories.indexOfFirst { it.id == view.category.id }
        return if (index == -1) POSITION_NONE else index
    }

    /**
     * Called when the view of this adapter is being destroyed.
     */
    fun onDestroy() {
        for (view in boundViews) {
            if (view is LibraryCategoryView) {
                view.onDestroy()
            }
        }
    }

    override fun getViewType(position: Int): Int {
        val category = categories.getOrNull(position)
        return if (isPerCategory && category?.id != 0L) {
            if (DisplayModeSetting.fromFlag(category?.displayMode) == DisplayModeSetting.LIST) {
                LIST_DISPLAY_MODE
            } else {
                GRID_DISPLAY_MODE
            }
        } else {
            if (currentDisplayMode == DisplayModeSetting.LIST) {
                LIST_DISPLAY_MODE
            } else {
                GRID_DISPLAY_MODE
            }
        }
    }

    companion object {
        const val LIST_DISPLAY_MODE = 1
        const val GRID_DISPLAY_MODE = 2
    }
}
