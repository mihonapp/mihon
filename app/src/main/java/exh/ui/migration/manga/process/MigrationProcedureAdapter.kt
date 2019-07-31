package exh.ui.migration.manga.process

import android.support.v4.view.PagerAdapter
import android.view.View
import android.view.ViewGroup
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
import eu.kanade.tachiyomi.ui.manga.info.MangaInfoController
import eu.kanade.tachiyomi.util.gone
import eu.kanade.tachiyomi.util.inflate
import exh.MERGED_SOURCE_ID
import exh.debug.DebugFunctions.sourceManager
import exh.util.await
import kotlinx.android.synthetic.main.eh_manga_card.view.*
import kotlinx.android.synthetic.main.eh_migration_process_item.view.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import uy.kohesive.injekt.injectLazy
import kotlin.coroutines.CoroutineContext

class MigrationProcedureAdapter(val controller: MigrationProcedureController,
                                val migratingManga: List<MigratingManga>,
                                override val coroutineContext: CoroutineContext) : PagerAdapter(), CoroutineScope {
    private val db: DatabaseHelper by injectLazy()
    private val gson: Gson by injectLazy()
    private val sourceManager: SourceManager by injectLazy()

    override fun isViewFromObject(p0: View, p1: Any): Boolean {
        return p0 == p1
    }

    override fun getCount() = migratingManga.size

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val item = migratingManga[position]
        val view = container.inflate(R.layout.eh_migration_process_item)
        container.addView(view)

        view.skip_migration.setOnClickListener {
            controller.nextMigration()
        }

        view.accept_migration.setOnClickListener {

        }

        val viewTag = ViewTag(coroutineContext)
        view.tag = viewTag
        view.setupView(viewTag, item)

        return view
    }

    fun View.setupView(tag: ViewTag, migratingManga: MigratingManga) {
        tag.launch {
            val manga = migratingManga.manga()
            val source = migratingManga.mangaSource()
            if(manga != null) {
                withContext(Dispatchers.Main) {
                    eh_manga_card_from.loading_group.gone()
                    eh_manga_card_from.attachManga(tag, manga, source)
                    eh_manga_card_from.setOnClickListener {
                        controller.router.pushController(MangaController(manga, true).withFadeTransaction())
                    }
                }

                tag.launch {
                    migratingManga.progress.asFlow().collect { (max, progress) ->
                        withContext(Dispatchers.Main) {
                            eh_manga_card_to.search_progress.let { progressBar ->
                                progressBar.max = max
                                progressBar.progress = progress
                            }
                        }
                    }
                }

                val searchResult = migratingManga.searchResult.get()?.let {
                    db.getManga(it).await()
                }
                val resultSource = searchResult?.source?.let {
                    sourceManager.get(it)
                }
                withContext(Dispatchers.Main) {
                    if(searchResult != null && resultSource != null) {
                        eh_manga_card_to.loading_group.gone()
                        eh_manga_card_to.attachManga(tag, searchResult, resultSource)
                        eh_manga_card_to.setOnClickListener {
                            controller.router.pushController(MangaController(searchResult, true).withFadeTransaction())
                        }
                    } else {
                        eh_manga_card_to.search_progress.gone()
                        eh_manga_card_to.search_status.text = "Found no manga"
                    }
                }
            }
        }
    }

    fun View.attachManga(tag: ViewTag, manga: Manga, source: Source) {
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
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        val objectAsView = `object` as View
        container.removeView(objectAsView)
        (objectAsView.tag as? ViewTag)?.destroy()
    }

    class ViewTag(parent: CoroutineContext): CoroutineScope {
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