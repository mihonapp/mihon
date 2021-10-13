package eu.kanade.tachiyomi.ui.manga.chapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.MangaChaptersHeaderBinding
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.getResourceColor
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks

class MangaChaptersHeaderAdapter(
    private val controller: MangaController
) :
    RecyclerView.Adapter<MangaChaptersHeaderAdapter.HeaderViewHolder>() {

    private var numChapters: Int? = null
    private var hasActiveFilters: Boolean = false

    private lateinit var binding: MangaChaptersHeaderBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        binding = MangaChaptersHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HeaderViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun getItemId(position: Int): Long = hashCode().toLong()

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind()
    }

    fun setNumChapters(numChapters: Int) {
        this.numChapters = numChapters
        notifyItemChanged(0, this)
    }

    fun setHasActiveFilters(hasActiveFilters: Boolean) {
        this.hasActiveFilters = hasActiveFilters
        notifyItemChanged(0, this)
    }

    inner class HeaderViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            binding.chaptersLabel.text = if (numChapters == null) {
                view.context.getString(R.string.chapters)
            } else {
                view.context.resources.getQuantityString(R.plurals.manga_num_chapters, numChapters!!, numChapters)
            }

            val filterColor = if (hasActiveFilters) {
                view.context.getResourceColor(R.attr.colorFilterActive)
            } else {
                view.context.getResourceColor(R.attr.colorOnBackground)
            }
            binding.btnChaptersFilter.drawable.setTint(filterColor)

            merge(view.clicks(), binding.btnChaptersFilter.clicks())
                .onEach { controller.showSettingsSheet() }
                .launchIn(controller.viewScope)
        }
    }
}
