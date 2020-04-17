package eu.kanade.tachiyomi.ui.migration.manga.process

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.Gson
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.inflate
import eu.kanade.tachiyomi.util.view.visible
import exh.MERGED_SOURCE_ID
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date
import kotlin.coroutines.CoroutineContext
import kotlinx.android.synthetic.main.migration_manga_card.view.loading_group
import kotlinx.android.synthetic.main.migration_manga_card.view.manga_artist
import kotlinx.android.synthetic.main.migration_manga_card.view.manga_author
import kotlinx.android.synthetic.main.migration_manga_card.view.manga_chapters
import kotlinx.android.synthetic.main.migration_manga_card.view.manga_cover
import kotlinx.android.synthetic.main.migration_manga_card.view.manga_full_title
import kotlinx.android.synthetic.main.migration_manga_card.view.manga_last_chapter
import kotlinx.android.synthetic.main.migration_manga_card.view.manga_last_update
import kotlinx.android.synthetic.main.migration_manga_card.view.manga_source
import kotlinx.android.synthetic.main.migration_manga_card.view.manga_source_label
import kotlinx.android.synthetic.main.migration_manga_card.view.manga_status
import kotlinx.android.synthetic.main.migration_manga_card.view.search_progress
import kotlinx.android.synthetic.main.migration_manga_card.view.search_status
import kotlinx.android.synthetic.main.migration_process_item.view.accept_migration
import kotlinx.android.synthetic.main.migration_process_item.view.migrating_frame
import kotlinx.android.synthetic.main.migration_process_item.view.migration_manga_card_from
import kotlinx.android.synthetic.main.migration_process_item.view.migration_manga_card_to
import kotlinx.android.synthetic.main.migration_process_item.view.skip_migration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy

class MigrationProcedureAdapter(
    val controller: MigrationProcedureController,
    val migratingManga: List<MigratingManga>,
    override val coroutineContext: CoroutineContext
) : PagerAdapter(), CoroutineScope {
    private val db: DatabaseHelper by injectLazy()
    private val gson: Gson by injectLazy()
    private val sourceManager: SourceManager by injectLazy()

    override fun isViewFromObject(p0: View, p1: Any): Boolean {
        return p0 == p1
    }

    override fun getCount() = migratingManga.size

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val item = migratingManga[position]
        val view = container.inflate(R.layout.migration_process_item)
        container.addView(view)

        view.skip_migration.setOnClickListener {
            // controller.nextMigration()
        }

        val viewTag = ViewTag(coroutineContext)
        view.tag = viewTag
        view.setupView(viewTag, item)

        view.accept_migration.setOnClickListener {
            viewTag.launch(Dispatchers.Main) {
                view.migrating_frame.visible()
                try {
                    withContext(Dispatchers.Default) {
                        performMigration(item)
                    }
                    controller.nextMigration()
                } catch (e: Exception) {
                    controller.migrationFailure()
                }
                view.migrating_frame.gone()
            }
        }

        return view
    }

    suspend fun performMigration(manga: MigratingManga) {
            if (!manga.searchResult.initialized) {
                return
            }

            val toMangaObj = db.getManga(manga.searchResult.get() ?: return).executeAsBlocking() ?: return

            withContext(Dispatchers.IO) {
                migrateMangaInternal(
                    manga.manga() ?: return@withContext,
                    toMangaObj,
                    false
                )
            }
    }

    private fun migrateMangaInternal(
        prevManga: Manga,
        manga: Manga,
        replace: Boolean
    ) {
        val config = controller.config ?: return
        // db.inTransaction {
            // Update chapters read
            /* if (MigrationFlags.hasChapters(controller.config.migrationFlags)) {
                val prevMangaChapters = db.getChapters(prevManga).executeAsBlocking()
                val maxChapterRead = prevMangaChapters.filter { it.read }
                        .maxBy { it.chapter_number }?.chapter_number
                if (maxChapterRead != null) {
                    val dbChapters = db.getChapters(manga).executeAsBlocking()
                    for (chapter in dbChapters) {
                        if (chapter.isRecognizedNumber && chapter.chapter_number <= maxChapterRead) {
                            chapter.read = true
                        }
                    }
                    db.insertChapters(dbChapters).executeAsBlocking()
                }
            }
            // Update categories
            if (MigrationFlags.hasCategories(controller.config.migrationFlags)) {
                val categories = db.getCategoriesForManga(prevManga).executeAsBlocking()
                val mangaCategories = categories.map { MangaCategory.create(manga, it) }
                db.setMangaCategories(mangaCategories, listOf(manga))
            }
            // Update track
            if (MigrationFlags.hasTracks(controller.config.migrationFlags)) {
                val tracks = db.getTracks(prevManga).executeAsBlocking()
                for (track in tracks) {
                    track.id = null
                    track.manga_id = manga.id!!
                }
                db.insertTracks(tracks).executeAsBlocking()
            }
            // Update favorite status
            if (replace) {
                prevManga.favorite = false
                db.updateMangaFavorite(prevManga).executeAsBlocking()
            }
            manga.favorite = true
            db.updateMangaFavorite(manga).executeAsBlocking()

            // SearchPresenter#networkToLocalManga may have updated the manga title, so ensure db gets updated title
            db.updateMangaTitle(manga).executeAsBlocking()
        //}*/
    }

    fun View.setupView(tag: ViewTag, migratingManga: MigratingManga) {
        tag.launch {
            val manga = migratingManga.manga()
            val source = migratingManga.mangaSource()
            if (manga != null) {
                withContext(Dispatchers.Main) {
                    migration_manga_card_from.loading_group.gone()
                    migration_manga_card_from.attachManga(tag, manga, source)
                    migration_manga_card_from.setOnClickListener {
                        controller.router.pushController(MangaController(manga, true).withFadeTransaction())
                    }
                }

                tag.launch {
                    migratingManga.progress.asFlow().collect { (max, progress) ->
                        withContext(Dispatchers.Main) {
                            migration_manga_card_to.search_progress.let { progressBar ->
                                progressBar.max = max
                                progressBar.progress = progress
                            }
                        }
                    }
                }

                val searchResult = migratingManga.searchResult.get()?.let {
                    db.getManga(it).executeAsBlocking()
                }
                val resultSource = searchResult?.source?.let {
                    sourceManager.get(it)
                }
                withContext(Dispatchers.Main) {
                    if (searchResult != null && resultSource != null) {
                        migration_manga_card_to.loading_group.gone()
                        migration_manga_card_to.attachManga(tag, searchResult, resultSource)
                        migration_manga_card_to.setOnClickListener {
                            controller.router.pushController(MangaController(searchResult, true).withFadeTransaction())
                        }
                        accept_migration.isEnabled = true
                        accept_migration.alpha = 1.0f
                    } else {
                        migration_manga_card_to.search_progress.gone()
                        migration_manga_card_to.search_status.text = "Found no manga"
                    }
                }
            }
        }
    }

    suspend fun View.attachManga(tag: ViewTag, manga: Manga, source: Source) {
        // TODO Duplicated in MangaInfoController

        GlideApp.with(context)
                .load(manga)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .centerCrop()
                .into(manga_cover)

        manga_full_title.text = if (manga.title.isBlank()) {
            context.getString(R.string.unknown)
        } else {
            manga.title
        }

        manga_artist.text = if (manga.artist.isNullOrBlank()) {
            context.getString(R.string.unknown)
        } else {
            manga.artist
        }

        manga_author.text = if (manga.author.isNullOrBlank()) {
            context.getString(R.string.unknown)
        } else {
            manga.author
        }

        manga_source.text = if (source.id == MERGED_SOURCE_ID) {
            MergedSource.MangaConfig.readFromUrl(gson, manga.url).children.map {
                sourceManager.getOrStub(it.source).toString()
            }.distinct().joinToString()
        } else {
            source.toString()
        }

        if (source.id == MERGED_SOURCE_ID) {
            manga_source_label.text = "Sources"
        } else {
            manga_source_label.setText(R.string.manga_info_source_label)
        }

        manga_status.setText(when (manga.status) {
            SManga.ONGOING -> R.string.ongoing
            SManga.COMPLETED -> R.string.completed
            SManga.LICENSED -> R.string.licensed
            else -> R.string.unknown
        })

        val mangaChapters = db.getChapters(manga).executeAsBlocking()
        manga_chapters.text = mangaChapters.size.toString()
        val latestChapter = mangaChapters.maxBy { it.chapter_number }?.chapter_number ?: -1f
        val lastUpdate = Date(mangaChapters.maxBy { it.date_upload }?.date_upload ?: 0)

        if (latestChapter > 0f) {
            manga_last_chapter.text = DecimalFormat("#.#").format(latestChapter)
        } else {
            manga_last_chapter.setText(R.string.unknown)
        }

        if (lastUpdate.time != 0L) {
            manga_last_update.text = DateFormat.getDateInstance(DateFormat.SHORT).format(lastUpdate)
        } else {
            manga_last_update.setText(R.string.unknown)
        }
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        val objectAsView = `object` as View
        container.removeView(objectAsView)
        (objectAsView.tag as? ViewTag)?.destroy()
    }

    class ViewTag(parent: CoroutineContext) : CoroutineScope {
        /**
         * The context of this scope.
         * Context is encapsulated by the scope and used for implementation of coroutine builders that are extensions on the scope.
         * Accessing this property in general code is not recommended for any purposes except accessing the [Job] instance for advanced usages.
         *
         * By convention, should contain an instance of a [job][Job] to enforce structured concurrency.
         */
        override val coroutineContext = parent + Job() + Dispatchers.Default

        fun destroy() {
            cancel()
        }
    }
}
