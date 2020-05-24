package eu.kanade.tachiyomi.ui.migration.manga.process

import android.view.View
import android.widget.PopupMenu
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.Gson
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.data.glide.toMangaThumbnail
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.manga.MangaAllInOneController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.invisible
import eu.kanade.tachiyomi.util.view.setVectorCompat
import eu.kanade.tachiyomi.util.view.visible
import exh.MERGED_SOURCE_ID
import java.text.DecimalFormat
import kotlinx.android.synthetic.main.migration_manga_card.gradient
import kotlinx.android.synthetic.main.migration_manga_card.loading_group
import kotlinx.android.synthetic.main.migration_manga_card.manga_chapters
import kotlinx.android.synthetic.main.migration_manga_card.manga_last_chapter_label
import kotlinx.android.synthetic.main.migration_manga_card.manga_source_label
import kotlinx.android.synthetic.main.migration_manga_card.thumbnail
import kotlinx.android.synthetic.main.migration_manga_card.view.loading_group
import kotlinx.android.synthetic.main.migration_manga_card.view.title
import kotlinx.android.synthetic.main.migration_process_item.migration_manga_card_from
import kotlinx.android.synthetic.main.migration_process_item.migration_manga_card_to
import kotlinx.android.synthetic.main.migration_process_item.migration_menu
import kotlinx.android.synthetic.main.migration_process_item.skip_manga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class MigrationProcessHolder(
    private val view: View,
    private val adapter: MigrationProcessAdapter
) : BaseFlexibleViewHolder(view, adapter) {

    private val db: DatabaseHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private var item: MigrationProcessItem? = null
    private val gson: Gson by injectLazy()

    init {
        // We need to post a Runnable to show the popup to make sure that the PopupMenu is
        // correctly positioned. The reason being that the view may change position before the
        // PopupMenu is shown.
        migration_menu.setOnClickListener { it.post { showPopupMenu(it) } }
        skip_manga.setOnClickListener { it.post { adapter.removeManga(bindingAdapterPosition) } }
    }

    fun bind(item: MigrationProcessItem) {
        this.item = item
        launchUI {
            val manga = item.manga.manga()
            val source = item.manga.mangaSource()

            migration_menu.setVectorCompat(
                R.drawable.ic_more_vert_24dp,
                view.context
                    .getResourceColor(R.attr.colorOnPrimary)
            )
            skip_manga.setVectorCompat(
                R.drawable.ic_close_24dp,
                view.context.getResourceColor(
                    R
                        .attr.colorOnPrimary
                )
            )
            migration_menu.invisible()
            skip_manga.visible()
            migration_manga_card_to.resetManga()
            if (manga != null) {
                withContext(Dispatchers.Main) {
                    migration_manga_card_from.attachManga(manga, source)
                    migration_manga_card_from.setOnClickListener {
                        if (Injekt.get<PreferencesHelper>().eh_useNewMangaInterface().get()) {
                            adapter.controller.router.pushController(
                                MangaAllInOneController(
                                    manga,
                                    true
                                ).withFadeTransaction()
                            )
                        } else {
                            adapter.controller.router.pushController(
                                MangaController(
                                    manga,
                                    true
                                ).withFadeTransaction()
                            )
                        }
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
                    if (item.manga.mangaId != this@MigrationProcessHolder.item?.manga?.mangaId ||
                        item.manga.migrationStatus == MigrationStatus.RUNNUNG
                    ) {
                        return@withContext
                    }
                    if (searchResult != null && resultSource != null) {
                        migration_manga_card_to.attachManga(searchResult, resultSource)
                        migration_manga_card_to.setOnClickListener {
                            adapter.controller.router.pushController(
                                MangaController(
                                    searchResult, true
                                ).withFadeTransaction()
                            )
                        }
                    } else {
                        migration_manga_card_to.loading_group.gone()
                        migration_manga_card_to.title.text = view.context.applicationContext
                            .getString(R.string.no_alternatives_found)
                    }
                    migration_menu.visible()
                    skip_manga.gone()
                    adapter.sourceFinished()
                }
            }
        }
    }

    private fun View.resetManga() {
        loading_group.visible()
        thumbnail.setImageDrawable(null)
        title.text = ""
        manga_source_label.text = ""
        manga_chapters.text = ""
        manga_chapters.gone()
        manga_last_chapter_label.text = ""
        migration_manga_card_to.setOnClickListener(null)
    }

    private fun View.attachManga(manga: Manga, source: Source) {
        loading_group.gone()
        GlideApp.with(view.context).clear(thumbnail)
        GlideApp.with(view.context)
            .load(manga.toMangaThumbnail())
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()
            .dontAnimate()
            .into(thumbnail)

        title.text = if (manga.title.isBlank()) {
            view.context.getString(R.string.unknown)
        } else {
            manga.title
        }

        gradient.visible()
        manga_source_label.text = if (source.id == MERGED_SOURCE_ID) {
            MergedSource.MangaConfig.readFromUrl(gson, manga.url).children.map {
                sourceManager.getOrStub(it.source).toString()
            }.distinct().joinToString()
        } else {
            source.toString()
        }

        val mangaChapters = db.getChapters(manga).executeAsBlocking()
        manga_chapters.visible()
        manga_chapters.text = mangaChapters.size.toString()
        val latestChapter = mangaChapters.maxBy { it.chapter_number }?.chapter_number ?: -1f

        if (latestChapter > 0f) {
            manga_last_chapter_label.text = context.getString(
                R.string.latest_,
                DecimalFormat("#.#").format(latestChapter)
            )
        } else {
            manga_last_chapter_label.text = context.getString(
                R.string.latest_,
                context.getString(R.string.unknown)
            )
        }
    }

    private fun showPopupMenu(view: View) {
        val item = adapter.getItem(bindingAdapterPosition) ?: return

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
            adapter.menuItemListener.onMenuItemClick(bindingAdapterPosition, menuItem)
            true
        }

        // Finally show the PopupMenu
        popup.show()
    }
}
