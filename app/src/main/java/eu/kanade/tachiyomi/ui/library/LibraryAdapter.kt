package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.databinding.LibraryCategoryBinding

/**
 * This adapter stores the categories from the library, used with a ViewPager.
 */
class LibraryAdapter(
    private val context: Context,
    private val controller: LibraryController
) : RecyclerView.Adapter<LibraryAdapter.ViewHolder>() {

    /**
     * The categories to bind in the adapter.
     */
    var categories: List<Category> = emptyList()
        // This setter helps to not refresh the adapter if the reference to the list doesn't change.
        set(value) {
            if (field !== value) {
                field = value
                notifyDataSetChanged()
            }
        }

    private var boundViews = arrayListOf<View>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = LibraryCategoryBinding.inflate(LayoutInflater.from(context), parent, false)
        binding.root.onCreate(controller)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val view = (holder.itemView as LibraryCategoryView)
        view.onBind(categories[position])
        boundViews.add(view)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        val view = (holder.itemView as LibraryCategoryView)
        view.onRecycle()
        boundViews.remove(view)
    }

    override fun getItemCount(): Int {
        return categories.size
    }

    /**
     * Returns the title to display for a category.
     *
     * @param position the position of the element.
     * @return the title to display.
     */
    fun getPageTitle(position: Int): CharSequence {
        return categories[position].name
    }

    /**
     * Called when the view of this adapter is being destroyed.
     */
    fun onDestroy() {
        for (view in boundViews) {
            if (view is LibraryCategoryView) {
                view.unsubscribe()
            }
        }
    }

    class ViewHolder(binding: LibraryCategoryBinding) : RecyclerView.ViewHolder(binding.root)
}
