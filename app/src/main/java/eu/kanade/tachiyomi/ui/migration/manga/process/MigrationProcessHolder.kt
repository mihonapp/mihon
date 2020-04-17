package eu.kanade.tachiyomi.ui.migration.manga.process

import android.view.View
import android.widget.PopupMenu
import com.bumptech.glide.load.engine.DiskCacheStrategy
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.setVectorCompat
import eu.kanade.tachiyomi.util.view.visible
import java.text.DecimalFormat
import kotlinx.android.synthetic.main.migration_new_manga_card.view.*
import kotlinx.android.synthetic.main.migration_new_process_item.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy

class MigrationProcessHolder(
    private val view: View,
    private val adapter: MigrationProcessAdapter
) : BaseFlexibleViewHolder(view, adapter) {

    private val db: DatabaseHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()

    init {
        // We need to post a Runnable to show the popup to make sure that the PopupMenu is
        // correctly positioned. The reason being that the view may change position before the
        // PopupMenu is shown.
        migration_menu.setOnClickListener { it.post { showPopupMenu(it) } }
        skip_manga.setOnClickListener { it.post { adapter.removeManga(adapterPosition) } }
    }

    fun bind(item: MigrationProcessItem) {
        launchUI {
            val manga = item.manga.manga()
            val source = item.manga.mangaSource()

            migration_menu.setVectorCompat(R.drawable.ic_more_vert_24dp, view.context.getResourceColor(R.attr.colorOnPrimary))
            skip_manga.setVectorCompat(R.drawable.ic_close_24dp, view.context.getResourceColor(R
                .attr.colorOnPrimary))
            migration_menu.gone()
            if (manga != null) {
                withContext(Dispatchers.Main) {
                    migration_manga_card_from.loading_group.gone()
                    attachManga(migration_manga_card_from, manga, source)
                    migration_manga_card_from.setOnClickListener {
                        adapter.controller.router.pushController(
                            MangaController(
                                manga,
                                true
                            ).withFadeTransaction()
                        )
                    }
                }

                /*launchUI {
                    item.manga.progress.asFlow().collect { (max, progress) ->
                        withContext(Dispatchers.Main) {
                            migration_manga_card_to.search_progress.let { progressBar ->
                                progressBar.max = max
                                progressBar.progress = progress
                            }
                        }
                    }
                }*/

                val searchResult = item.manga.searchResult.get()?.let {
                    db.getManga(it).executeAsBlocking()
                }
                val resultSource = searchResult?.source?.let {
                    sourceManager.get(it)
                }
                withContext(Dispatchers.Main) {
                    if (searchResult != null && resultSource != null) {
                        migration_manga_card_to.loading_group.gone()
                        attachManga(migration_manga_card_to, searchResult, resultSource)
                        migration_manga_card_to.setOnClickListener {
                            adapter.controller.router.pushController(
                                MangaController(
                                    searchResult, true
                                ).withFadeTransaction()
                            )
                        }
                    } else {
                        migration_manga_card_to.loading_group.gone()
                        migration_manga_card_to.title.text = "No Alternatives Found"
                    }
                    migration_menu.visible()
                    skip_manga.gone()
                    adapter.sourceFinished()
                }
            }
        }
    }

    fun showSpinner() {
        migration_manga_card_to.loading_group.visible()
    }

    fun attachManga(view: View, manga: Manga, source: Source) {
        view.loading_group.gone()
        GlideApp.with(view.context.applicationContext)
            .load(manga)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()
            .into(view.thumbnail)

        view.title.text = if (manga.title.isBlank()) {
            view.context.getString(R.string.unknown)
        } else {
            manga.title
        }

        view.gradient.visible()
        view.manga_source_label.text = /*if (source.id == MERGED_SOURCE_ID) {
            MergedSource.MangaConfig.readFromUrl(gson, manga.url).children.map {
                sourceManager.getOrStub(it.source).toString()
            }.distinct().joinToString()
        } else {*/
            source.toString()
        // }

        val mangaChapters = db.getChapters(manga).executeAsBlocking()
        view.manga_chapters.visible()
        view.manga_chapters.text = mangaChapters.size.toString()
        val latestChapter = mangaChapters.maxBy { it.chapter_number }?.chapter_number ?: -1f

        if (latestChapter > 0f) {
            view.manga_last_chapter_label.text = view.context.getString(R.string.latest_x,
                DecimalFormat("#.#").format(latestChapter))
        } else {
            view.manga_last_chapter_label.setText(R.string.unknown)
        }
    }

    private fun showPopupMenu(view: View) {
        val item = adapter.getItem(adapterPosition) ?: return

        // Create a PopupMenu, giving it the clicked view for an anchor
        val popup = PopupMenu(view.context, view)

        // Inflate our menu resource into the PopupMenu's Menu
        popup.menuInflater.inflate(R.menu.migration_single, popup.menu)

        val mangas = item.manga

        popup.menu.findItem(R.id.action_search_manually).isVisible = true
        // Hide download and show delete if the chapter is downloaded
        if (mangas.searchResult.content != null) {
            popup.menu.findItem(R.id.action_migrate_now).isVisible = true
            popup.menu.findItem(R.id.action_copy_now).isVisible = true
        }

        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            adapter.menuItemListener.onMenuItemClick(adapterPosition, menuItem)
            true
        }

        // Finally show the PopupMenu
        popup.show()
    }
}
