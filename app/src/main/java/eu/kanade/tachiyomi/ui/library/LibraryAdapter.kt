package eu.kanade.tachiyomi.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import eu.kanade.domain.category.model.Category
import eu.kanade.presentation.components.SwipeRefreshIndicator
import eu.kanade.presentation.library.components.LibraryComfortableGrid
import eu.kanade.presentation.library.components.LibraryCompactGrid
import eu.kanade.presentation.library.components.LibraryCoverOnlyGrid
import eu.kanade.presentation.library.components.LibraryList
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.ComposeControllerBinding
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.RecyclerViewPagerAdapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * This adapter stores the categories from the library, used with a ViewPager.
 *
 * @constructor creates an instance of the adapter.
 */
class LibraryAdapter(
    private val presenter: LibraryPresenter,
    private val onClickManga: (LibraryManga) -> Unit,
    private val preferences: PreferencesHelper = Injekt.get(),
) : RecyclerViewPagerAdapter() {

    /**
     * The categories to bind in the adapter.
     */
    var categories: List<Category> = mutableStateListOf()
        private set

    /**
     * The number of manga in each category.
     * List order must be the same as [categories]
     */
    private var itemsPerCategory: List<Int> = emptyList()

    private var boundViews = arrayListOf<View>()

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
        val binding = ComposeControllerBinding.inflate(LayoutInflater.from(container.context), container, false)
        return binding.root
    }

    /**
     * Binds a view with a position.
     *
     * @param view the view to bind.
     * @param position the position in the adapter.
     */
    override fun bindView(view: View, position: Int) {
        (view as ComposeView).apply {
            consumeWindowInsets = false
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TachiyomiTheme {
                    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodySmall) {
                        val nestedScrollInterop = rememberNestedScrollInteropConnection()

                        val category = presenter.categories[position]
                        val displayMode = presenter.getDisplayMode(index = position)
                        val mangaList by presenter.getMangaForCategory(categoryId = category.id)

                        val onClickManga = { manga: LibraryManga ->
                            if (presenter.hasSelection().not()) {
                                onClickManga(manga)
                            } else {
                                presenter.toggleSelection(manga)
                            }
                        }
                        val onLongClickManga = { manga: LibraryManga ->
                            presenter.toggleSelection(manga)
                        }

                        SwipeRefresh(
                            modifier = Modifier.nestedScroll(nestedScrollInterop),
                            state = rememberSwipeRefreshState(isRefreshing = false),
                            onRefresh = {
                                if (LibraryUpdateService.start(context, category)) {
                                    context.toast(R.string.updating_category)
                                }
                            },
                            indicator = { s, trigger ->
                                SwipeRefreshIndicator(
                                    state = s,
                                    refreshTriggerDistance = trigger,
                                )
                            },
                        ) {
                            when (displayMode) {
                                DisplayModeSetting.LIST -> {
                                    LibraryList(
                                        items = mangaList,
                                        selection = presenter.selection,
                                        onClick = onClickManga,
                                        onLongClick = {
                                            presenter.toggleSelection(it)
                                        },
                                    )
                                }
                                DisplayModeSetting.COMPACT_GRID -> {
                                    LibraryCompactGrid(
                                        items = mangaList,
                                        columns = presenter.columns,
                                        selection = presenter.selection,
                                        onClick = onClickManga,
                                        onLongClick = onLongClickManga,
                                    )
                                }
                                DisplayModeSetting.COMFORTABLE_GRID -> {
                                    LibraryComfortableGrid(
                                        items = mangaList,
                                        columns = presenter.columns,
                                        selection = presenter.selection,
                                        onClick = onClickManga,
                                        onLongClick = onLongClickManga,
                                    )
                                }
                                DisplayModeSetting.COVER_ONLY_GRID -> {
                                    LibraryCoverOnlyGrid(
                                        items = mangaList,
                                        columns = presenter.columns,
                                        selection = presenter.selection,
                                        onClick = onClickManga,
                                        onLongClick = onLongClickManga,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        boundViews.add(view)
    }

    /**
     * Recycles a view.
     *
     * @param view the view to recycle.
     * @param position the position in the adapter.
     */
    override fun recycleView(view: View, position: Int) {
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

    override fun getViewType(position: Int): Int = -1
}
