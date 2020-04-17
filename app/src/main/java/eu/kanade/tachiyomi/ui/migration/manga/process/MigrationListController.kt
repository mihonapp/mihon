package eu.kanade.tachiyomi.ui.migration.manga.process

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.smartsearch.SmartSearchEngine
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.migration.MigrationMangaDialog
import eu.kanade.tachiyomi.ui.migration.SearchController
import eu.kanade.tachiyomi.util.await
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.RecyclerWindowInsetsListener
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.android.synthetic.main.chapters_controller.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import rx.schedulers.Schedulers
import uy.kohesive.injekt.injectLazy

class MigrationListController(bundle: Bundle? = null) : BaseController(bundle),
    MigrationProcessAdapter.MigrationProcessInterface,
    CoroutineScope {

    init {
        setHasOptionsMenu(true)
    }

    private var adapter: MigrationProcessAdapter? = null

    override val coroutineContext: CoroutineContext = Job() + Dispatchers.Default

    val config: MigrationProcedureConfig? = args.getParcelable(CONFIG_EXTRA)

    private val db: DatabaseHelper by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()

    private val smartSearchEngine = SmartSearchEngine(coroutineContext, config?.extraSearchParams)

    private var migrationsJob: Job? = null
    private var migratingManga: MutableList<MigratingManga>? = null
    private var selectedPosition: Int? = null
    private var manaulMigrations = 0

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.migration_list_controller, container, false)
    }

    override fun getTitle(): String? {
        return resources?.getString(R.string.migration) + " (${adapter?.items?.count { it.manga
            .migrationStatus != MigrationStatus.RUNNUNG }}/${adapter?.itemCount ?: 0})"
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        setTitle()
        val config = this.config ?: return

        val newMigratingManga = migratingManga ?: run {
            val new = config.mangaIds.map {
                MigratingManga(db, sourceManager, it, coroutineContext)
            }
            migratingManga = new.toMutableList()
            new
        }

        adapter = MigrationProcessAdapter(this, view.context)

        recycler.adapter = adapter
        recycler.layoutManager = LinearLayoutManager(view.context)
        recycler.setHasFixedSize(true)
        recycler.setOnApplyWindowInsetsListener(RecyclerWindowInsetsListener)

        adapter?.updateDataSet(newMigratingManga.map { it.toModal() })

        if (migrationsJob == null) {
            migrationsJob = launch {
                runMigrations(newMigratingManga)
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
        val useSourceWithMost = preferences.useSourceWithMost().getOrDefault()
        val useSmartSearch = preferences.smartMigration().getOrDefault()

        val sources = preferences.migrationSources().getOrDefault().split("/").mapNotNull {
            val value = it.toLongOrNull() ?: return
            sourceManager.get(value) as? CatalogueSource }
        if (config == null) return
        for (manga in mangas) {
            if (migrationsJob?.isCancelled == true) {
                break
            }
            // in case it was removed
            if (manga.mangaId !in config.mangaIds) {
                continue
            }
            if (!manga.searchResult.initialized && manga.migrationJob.isActive) {
                val mangaObj = manga.manga()

                if (mangaObj == null) {
                    manga.searchResult.initialize(null)
                    continue
                }

                val mangaSource = manga.mangaSource()

                val result = try {
                    CoroutineScope(manga.migrationJob).async {
                        val validSources = sources.filter {
                            it.id != mangaSource.id
                        }
                        if (useSourceWithMost) {
                            val sourceSemaphore = Semaphore(3)
                            val processedSources = AtomicInteger()

                            validSources.map { source ->
                                async {
                                    sourceSemaphore.withPermit {
                                        try {
                                            val searchResult = if (useSmartSearch) {
                                                smartSearchEngine.smartSearch(source, mangaObj.title)
                                            } else {
                                                smartSearchEngine.normalSearch(source, mangaObj.title)
                                            }

                                            if (searchResult != null) {
                                                val localManga = smartSearchEngine.networkToLocalManga(searchResult, source.id)
                                                val chapters = source.fetchChapterList(localManga).toSingle().await(
                                                    Schedulers.io())
                                                try {
                                                    syncChaptersWithSource(db, chapters, localManga, source)
                                                } catch (e: Exception) {
                                                    return@async null
                                                }
                                                manga.progress.send(validSources.size to processedSources.incrementAndGet())
                                                localManga to chapters.size
                                            } else {
                                                null
                                            }
                                        } catch (e: CancellationException) {
                                            // Ignore cancellations
                                            throw e
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                }
                            }.mapNotNull { it.await() }.maxBy { it.second }?.first
                        } else {
                            validSources.forEachIndexed { index, source ->
                                val searchResult = try {
                                    val searchResult = if (useSmartSearch) {
                                        smartSearchEngine.smartSearch(source, mangaObj.title)
                                    } else {
                                        smartSearchEngine.normalSearch(source, mangaObj.title)
                                    }

                                    if (searchResult != null) {
                                        val localManga = smartSearchEngine.networkToLocalManga(searchResult, source.id)
                                        val chapters = source.fetchChapterList(localManga).toSingle().await(
                                            Schedulers.io())
                                        withContext(Dispatchers.IO) {
                                            syncChaptersWithSource(db, chapters, localManga, source)
                                        }
                                        localManga
                                    } else null
                                } catch (e: CancellationException) {
                                    // Ignore cancellations
                                    throw e
                                } catch (e: Exception) {
                                    null
                                }

                                manga.progress.send(validSources.size to (index + 1))

                                if (searchResult != null) return@async searchResult
                            }

                            null
                        }
                    }.await()
                } catch (e: CancellationException) {
                    // Ignore canceled migrations
                    continue
                }

                if (result != null && result.thumbnail_url == null) {
                    try {
                        val newManga = sourceManager.getOrStub(result.source)
                            .fetchMangaDetails(result)
                            .toSingle()
                            .await()
                        result.copyFrom(newManga)

                        db.insertManga(result).executeAsBlocking()
                    } catch (e: CancellationException) {
                        // Ignore cancellations
                        throw e
                    } catch (e: Exception) {
                    }
                }

                manga.migrationStatus = if (result == null) MigrationStatus.MANGA_NOT_FOUND else
                    MigrationStatus.MANGA_FOUND
                adapter?.sourceFinished()
                manga.searchResult.initialize(result?.id)
            }
        }
    }

    override fun updateCount() {
        launchUI {
            if (router.backstack.last().controller() == this@MigrationListController) {
                setTitle()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun enableButtons() {
        activity?.invalidateOptionsMenu()
    }

    override fun removeManga(item: MigrationProcessItem) {
        val ids = config?.mangaIds?.toMutableList() ?: return
        val index = ids.indexOf(item.manga.mangaId)
        if (index > -1) {
            ids.removeAt(index)
            config.mangaIds = ids
            val index2 = migratingManga?.indexOf(item.manga) ?: return
            if (index2 > -1)
                migratingManga?.removeAt(index2)
        }
    }

    override fun noMigration() {
        launchUI {
            val res = resources
            if (res != null) {
                activity?.toast(
                    res.getString(
                        R.string.x_migrations,
                        if (manaulMigrations == 0) res.getString(R.string.no)
                        else "$manaulMigrations"
                    )
                )
            }
            router.popCurrentController()
        }
    }

    override fun onMenuItemClick(position: Int, item: MenuItem) {

        when (item.itemId) {
            R.id.action_search_manually -> {
                launchUI {
                    val manga = adapter?.getItem(position) ?: return@launchUI
                    selectedPosition = position
                    val searchController = SearchController(manga.manga.manga())
                    searchController.targetController = this@MigrationListController
                    router.pushController(searchController.withFadeTransaction())
                }
            }
            R.id.action_skip -> adapter?.removeManga(position)
            R.id.action_migrate_now -> {
                adapter?.migrateManga(position, false)
                manaulMigrations++
            }
            R.id.action_copy_now -> {
                adapter?.migrateManga(position, true)
                manaulMigrations++
            }
        }
    }

    fun useMangaForMigration(manga: Manga, source: Source) {
        val firstIndex = selectedPosition ?: return
        val migratingManga = adapter?.getItem(firstIndex) ?: return
        migratingManga.manga.migrationStatus = MigrationStatus.RUNNUNG
        adapter?.notifyItemChanged(firstIndex)
        launchUI {
            val result = CoroutineScope(migratingManga.manga.migrationJob).async {
                val localManga = smartSearchEngine.networkToLocalManga(manga, source.id)
                val chapters = source.fetchChapterList(localManga).toSingle().await(
                    Schedulers.io()
                )
                try {
                    syncChaptersWithSource(db, chapters, localManga, source)
                } catch (e: Exception) {
                    return@async null
                }
                localManga
            }.await()

            if (result != null) {
                try {
                    val newManga =
                        sourceManager.getOrStub(result.source).fetchMangaDetails(result).toSingle()
                            .await()
                    result.copyFrom(newManga)

                    db.insertManga(result).executeAsBlocking()
                } catch (e: CancellationException) {
                    // Ignore cancellations
                    throw e
                } catch (e: Exception) {
                }

                migratingManga.manga.migrationStatus = MigrationStatus.MANGA_FOUND
                migratingManga.manga.searchResult.set(result.id)
                adapter?.notifyDataSetChanged()
            } else {
                migratingManga.manga.migrationStatus = MigrationStatus.MANGA_NOT_FOUND
                activity?.toast(R.string.error_fetching_migration, Toast.LENGTH_LONG)
                adapter?.notifyDataSetChanged()
            }
        }
    }

    fun migrateMangas() {
        launchUI {
            adapter?.performMigrations(false)
            router.popCurrentController()
        }
    }

    fun copyMangas() {
        launchUI {
            adapter?.performMigrations(true)
            router.popCurrentController()
        }
    }

    override fun handleBack(): Boolean {
        activity?.let {
            MaterialDialog.Builder(it).title(R.string.stop_migration)
                .positiveText(R.string.yes)
                .negativeText(R.string.no)
                .onPositive { _, _ ->
                    router.popCurrentController()
                    migrationsJob?.cancel()
                }
                .show()
        }
        return true
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.migration_list, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        // Initialize menu items.

        val allMangasDone = adapter?.allMangasDone() ?: return

        val menuCopy = menu.findItem(R.id.action_copy_manga)
        val menuMigrate = menu.findItem(R.id.action_migrate_manga)

        if (adapter?.itemCount == 1) {
            menuMigrate.icon = VectorDrawableCompat.create(
                resources!!, R.drawable.ic_done, null
            )
        }
        val translucentWhite = ColorUtils.setAlphaComponent(Color.WHITE, 127)
        menuCopy.icon?.setTint(if (allMangasDone) Color.WHITE else translucentWhite)
        menuMigrate?.icon?.setTint(if (allMangasDone) Color.WHITE else translucentWhite)
        menuCopy.isEnabled = allMangasDone
        menuMigrate.isEnabled = allMangasDone
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val totalManga = adapter?.itemCount ?: 0
        val mangaSkipped = adapter?.mangasSkipped() ?: 0
        when (item.itemId) {
            R.id.action_copy_manga -> MigrationMangaDialog(this, true, totalManga, mangaSkipped)
                .showDialog(router)
            R.id.action_migrate_manga -> MigrationMangaDialog(this, false, totalManga, mangaSkipped)
                .showDialog(router)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    companion object {
        const val CONFIG_EXTRA = "config_extra"

        fun create(config: MigrationProcedureConfig): MigrationListController {
            return MigrationListController(Bundle().apply {
                putParcelable(CONFIG_EXTRA, config)
            })
        }
    }
}
