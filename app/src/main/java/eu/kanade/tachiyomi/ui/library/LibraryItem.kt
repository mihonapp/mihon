package eu.kanade.tachiyomi.ui.library

import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.tfcporciuncula.flow.Preference
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import exh.util.updateLayoutParams
import kotlinx.android.synthetic.main.source_comfortable_grid_item.view.constraint_layout
import kotlinx.android.synthetic.main.source_grid_item.view.card
import kotlinx.android.synthetic.main.source_grid_item.view.gradient
import kotlinx.android.synthetic.main.source_grid_item.view.thumbnail
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LibraryItem(val manga: LibraryManga, private val libraryViewSetting: Preference<Int>) :
    AbstractFlexibleItem<LibraryHolder>(), IFilterable<String> {

    private val sourceManager: SourceManager = Injekt.get()
    private val trackManager: TrackManager = Injekt.get()
    private val db: DatabaseHelper = Injekt.get()

    var downloadCount = -1
    var unreadCount = -1

    override fun getLayoutRes(): Int {
        return when (libraryViewSetting.get()) {
            0 -> R.layout.source_grid_item
            1 -> R.layout.source_list_item
            else -> R.layout.source_comfortable_grid_item
        }
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): LibraryHolder {
        val parent = adapter.recyclerView
        return if (parent is AutofitRecyclerView) {
            if (libraryViewSetting.get() == 0) {
                view.apply {
                    val coverHeight = parent.itemWidth / 3 * 4
                    card.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, coverHeight)
                    gradient.layoutParams = FrameLayout.LayoutParams(
                        MATCH_PARENT, coverHeight / 2, Gravity.BOTTOM
                    )
                }
                LibraryGridHolder(view, adapter)
            } else {
                view.apply {
                    val coverHeight = parent.itemWidth / 3 * 4

                    gradient.layoutParams = FrameLayout.LayoutParams(
                        MATCH_PARENT,
                        coverHeight * (66 / 100),
                        Gravity.BOTTOM
                    )
                    card.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        bottomMargin = 6.dpToPx
                    }

                    constraint_layout.layoutParams = FrameLayout.LayoutParams(
                        MATCH_PARENT, WRAP_CONTENT
                    )
                    thumbnail.maxHeight = Int.MAX_VALUE
                    thumbnail.minimumHeight = 0
                    constraint_layout.minHeight = 0
                    thumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
                    thumbnail.adjustViewBounds = false
                    thumbnail.layoutParams = FrameLayout.LayoutParams(
                        MATCH_PARENT,
                        (parent.itemWidth / 3f * 3.7f).toInt()
                    )
                    // .layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, coverHeight)

                    // gradient.layoutParams = FrameLayout.LayoutParams(
                    // MATCH_PARENT, coverHeight / 2, Gravity.BOTTOM
                    // )
                }
                LibraryComfortableGridHolder(view, adapter)
            }
        } else {
            LibraryListHolder(view, adapter)
        }
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: LibraryHolder,
        position: Int,
        payloads: List<Any?>?
    ) {
        holder.onSetValues(this)
    }

    /**
     * Returns true if this item is draggable.
     */
    override fun isDraggable(): Boolean {
        return true
    }

    /**
     * Filters a manga depending on a query.
     *
     * @param constraint the query to apply.
     * @return true if the manga should be included, false otherwise.
     */
    override fun filter(constraint: String): Boolean {
        return manga.title.contains(constraint, true) ||
            (manga.author?.contains(constraint, true) ?: false) ||
            (manga.artist?.contains(constraint, true) ?: false) ||
            sourceManager.getOrStub(manga.source).name.contains(constraint, true) ||
            (Injekt.get<TrackManager>().hasLoggedServices() && filterTracks(constraint, db.getTracks(manga).executeAsBlocking())) ||
            if (constraint.contains(" ") || constraint.contains("\"")) {
                val genres = manga.genre?.split(", ")?.map {
                    it.drop(it.indexOfFirst { it == ':' } + 1).toLowerCase().trim() // tachiEH tag namespaces
                }
                var clean_constraint = ""
                var ignorespace = false
                for (i in constraint.trim().toLowerCase()) {
                    if (i == ' ') {
                        if (!ignorespace) {
                            clean_constraint = clean_constraint + ","
                        } else {
                            clean_constraint = clean_constraint + " "
                        }
                    } else if (i == '"') {
                        ignorespace = !ignorespace
                    } else {
                        clean_constraint = clean_constraint + Character.toString(i)
                    }
                }
                clean_constraint.split(",").all { containsGenre(it.trim(), genres) }
            } else containsGenre(
                constraint,
                manga.genre?.split(", ")?.map {
                    it.drop(it.indexOfFirst { it == ':' } + 1).toLowerCase().trim() // tachiEH tag namespaces
                }
            )
    }

    private fun filterTracks(constraint: String, tracks: List<Track>): Boolean {
        return tracks.any {
            val trackService = trackManager.getService(it.sync_id)
            if (trackService != null) {
                val status = trackService.getStatus(it.status)
                return@any status.contains(constraint, true)
            }
            return@any false
        }
    }

    private fun containsGenre(tag: String, genres: List<String>?): Boolean {
        return if (tag.startsWith("-")) {
            genres?.find {
                it.trim().toLowerCase() == tag.substringAfter("-").toLowerCase()
            } == null
        } else {
            genres?.find {
                it.trim().toLowerCase() == tag.toLowerCase()
            } != null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other is LibraryItem) {
            return manga.id == other.manga.id
        }
        return false
    }

    override fun hashCode(): Int {
        return manga.id!!.hashCode()
    }
}
