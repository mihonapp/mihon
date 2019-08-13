package exh.ui.migration.manga.process

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import com.afollestad.materialdialogs.MaterialDialog
import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.syncChaptersWithSource
import eu.kanade.tachiyomi.util.toast
import exh.smartsearch.SmartSearchEngine
import exh.ui.base.BaseExhController
import exh.util.await
import kotlinx.android.synthetic.main.eh_migration_process.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import rx.schedulers.Schedulers
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.atomic.AtomicInteger

// TODO Will probably implode if activity is fully destroyed
class MigrationProcedureController(bundle: Bundle? = null) : BaseExhController(bundle), CoroutineScope {
    override val layoutId = R.layout.eh_migration_process

    private var titleText = "Migrate manga"

    private var adapter: MigrationProcedureAdapter? = null

    val config: MigrationProcedureConfig = args.getParcelable(CONFIG_EXTRA)

    private val db: DatabaseHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()

    private val smartSearchEngine = SmartSearchEngine(coroutineContext, config.extraSearchParams)

    private val logger = XLog.tag("MigrationProcedureController")

    private var migrationsJob: Job? = null
    private var migratingManga: List<MigratingManga>? = null

    override fun getTitle(): String {
        return titleText
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        setTitle()

        activity?.requestedOrientation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val newMigratingManga = migratingManga ?: run {
            val new = config.mangaIds.map {
                MigratingManga(db, sourceManager, it, coroutineContext)
            }
            migratingManga = new
            new
        }

        adapter = MigrationProcedureAdapter(this, newMigratingManga, coroutineContext)

        pager.adapter = adapter
        pager.isEnabled = false

        if(migrationsJob == null) {
            migrationsJob = launch {
                runMigrations(newMigratingManga)
            }
        }

        pager.post {
            // pager.currentItem doesn't appear to be valid if we don't do this in a post
            updateTitle()
        }
    }

    fun updateTitle() {
        titleText = "Migrate manga (${pager.currentItem + 1}/${adapter?.count ?: 0})"
        setTitle()
    }

    fun nextMigration() {
        adapter?.let { adapter ->
            if(pager.currentItem >= adapter.count - 1) {
                applicationContext?.toast("All migrations complete!")
                router.popCurrentController()
            } else {
                adapter.migratingManga[pager.currentItem].migrationJob.cancel()
                pager.setCurrentItem(pager.currentItem + 1, true)
                launch(Dispatchers.Main) {
                    updateTitle()
                }
            }
        }
    }

    fun migrationFailure() {
        activity?.let {
            MaterialDialog.Builder(it)
                    .title("Migration failure")
                    .content("An unknown error occured while migrating this manga!")
                    .positiveText("Ok")
                    .show()
        }
    }

    suspend fun runMigrations(mangas: List<MigratingManga>) {
        val sources = config.targetSourceIds.mapNotNull { sourceManager.get(it) as? CatalogueSource }

        for(manga in mangas) {
            if(!manga.searchResult.initialized && manga.migrationJob.isActive) {
                val mangaObj = manga.manga()

                if(mangaObj == null) {
                    manga.searchResult.initialize(null)
                    continue
                }

                val mangaSource = manga.mangaSource()

                val result = try {
                    CoroutineScope(manga.migrationJob).async {
                        val validSources = sources.filter {
                            it.id != mangaSource.id
                        }
                        if(config.useSourceWithMostChapters) {
                            val sourceSemaphore = Semaphore(3)
                            val processedSources = AtomicInteger()

                            validSources.map { source ->
                               async {
                                   sourceSemaphore.withPermit {
                                       try {
                                           val searchResult = if (config.enableLenientSearch) {
                                               smartSearchEngine.smartSearch(source, mangaObj.title)
                                           } else {
                                               smartSearchEngine.normalSearch(source, mangaObj.title)
                                           }

                                           if(searchResult != null) {
                                               val localManga = smartSearchEngine.networkToLocalManga(searchResult, source.id)
                                               val chapters = source.fetchChapterList(localManga).toSingle().await(Schedulers.io())
                                               withContext(Dispatchers.IO) {
                                                   syncChaptersWithSource(db, chapters, localManga, source)
                                               }
                                               manga.progress.send(validSources.size to processedSources.incrementAndGet())
                                               localManga to chapters.size
                                           } else {
                                               null
                                           }
                                       } catch(e: CancellationException) {
                                           // Ignore cancellations
                                           throw e
                                       } catch(e: Exception) {
                                           logger.e("Failed to search in source: ${source.id}!", e)
                                           null
                                       }
                                   }
                                }
                            }.mapNotNull { it.await() }.maxBy { it.second }?.first
                        } else {
                            validSources.forEachIndexed { index, source ->
                                val searchResult = try {
                                    val searchResult = if (config.enableLenientSearch) {
                                        smartSearchEngine.smartSearch(source, mangaObj.title)
                                    } else {
                                        smartSearchEngine.normalSearch(source, mangaObj.title)
                                    }

                                    if (searchResult != null) {
                                        val localManga = smartSearchEngine.networkToLocalManga(searchResult, source.id)
                                        val chapters = source.fetchChapterList(localManga).toSingle().await(Schedulers.io())
                                        withContext(Dispatchers.IO) {
                                            syncChaptersWithSource(db, chapters, localManga, source)
                                        }
                                        localManga
                                    } else null
                                } catch(e: CancellationException) {
                                    // Ignore cancellations
                                    throw e
                                } catch(e: Exception) {
                                    logger.e("Failed to search in source: ${source.id}!", e)
                                    null
                                }

                                manga.progress.send(validSources.size to (index + 1))

                                if(searchResult != null) return@async searchResult
                            }

                            null
                        }
                    }.await()
                } catch(e: CancellationException) {
                    // Ignore canceled migrations
                    continue
                }

                if(result != null && result.thumbnail_url == null) {
                    try {
                        val newManga = sourceManager.getOrStub(result.source)
                                .fetchMangaDetails(result)
                                .toSingle()
                                .await()
                        result.copyFrom(newManga)

                        db.insertManga(result).await()
                    } catch(e: CancellationException) {
                        // Ignore cancellations
                        throw e
                    } catch(e: Exception) {
                        logger.e("Could not load search manga details", e)
                    }
                }

                manga.searchResult.initialize(result?.id)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    companion object {
        const val CONFIG_EXTRA = "config_extra"

        fun create(config: MigrationProcedureConfig): MigrationProcedureController {
            return MigrationProcedureController(Bundle().apply {
                putParcelable(CONFIG_EXTRA, config)
            })
        }
    }
}