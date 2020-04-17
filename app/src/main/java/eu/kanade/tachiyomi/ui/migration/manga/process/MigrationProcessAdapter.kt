package eu.kanade.tachiyomi.ui.migration.manga.process

import android.content.Context
import android.view.MenuItem
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.migration.MigrationFlags
import eu.kanade.tachiyomi.util.lang.launchUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy

class MigrationProcessAdapter(
    val controller: MigrationListController,
    context: Context
) : FlexibleAdapter<MigrationProcessItem>(null, controller, true) {

    private val db: DatabaseHelper by injectLazy()
    var items: List<MigrationProcessItem> = emptyList()
    val preferences: PreferencesHelper by injectLazy()

    val menuItemListener: MigrationProcessInterface = controller

    override fun updateDataSet(items: List<MigrationProcessItem>?) {
        this.items = items ?: emptyList()
        super.updateDataSet(items)
    }

    interface MigrationProcessInterface {
        fun onMenuItemClick(position: Int, item: MenuItem)
        fun enableButtons()
        fun removeManga(item: MigrationProcessItem)
        fun noMigration()
        fun updateCount()
    }

    fun sourceFinished() {
        menuItemListener.updateCount()
        if (itemCount == 0) menuItemListener.noMigration()
        if (allMangasDone()) menuItemListener.enableButtons()
    }

    fun allMangasDone() = (items.all { it.manga.migrationStatus != MigrationStatus
        .RUNNUNG } && items.any { it.manga.migrationStatus == MigrationStatus.MANGA_FOUND })

    fun mangasSkipped() = (items.count { it.manga.migrationStatus == MigrationStatus.MANGA_NOT_FOUND })

    suspend fun performMigrations(copy: Boolean) {
        withContext(Dispatchers.IO) {
            db.inTransaction {
                currentItems.forEach { migratingManga ->
                    val manga = migratingManga.manga
                    if (manga.searchResult.initialized) {
                        val toMangaObj =
                            db.getManga(manga.searchResult.get() ?: return@forEach).executeAsBlocking()
                                ?: return@forEach
                        migrateMangaInternal(
                            manga.manga() ?: return@forEach,
                            toMangaObj,
                            !copy)
                    }
                }
            }
        }
    }

    fun migrateManga(position: Int, copy: Boolean) {
        launchUI {
            val manga = getItem(position)?.manga ?: return@launchUI
            db.inTransaction {
                val toMangaObj = db.getManga(manga.searchResult.get() ?: return@launchUI).executeAsBlocking()
                    ?: return@launchUI
                migrateMangaInternal(
                    manga.manga() ?: return@launchUI, toMangaObj, !copy
                )
            }
            removeManga(position)
        }
    }

    fun removeManga(position: Int) {
        val item = getItem(position) ?: return
        menuItemListener.removeManga(item)
        item?.manga?.migrationJob?.cancel()
        removeItem(position)
        items = currentItems
        sourceFinished()
    }

    private fun migrateMangaInternal(
        prevManga: Manga,
        manga: Manga,
        replace: Boolean
    ) {
        if (controller.config == null) return
        val flags = preferences.migrateFlags().getOrDefault()
        // Update chapters read
        if (MigrationFlags.hasChapters(flags)) {
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
        if (MigrationFlags.hasCategories(flags)) {
            val categories = db.getCategoriesForManga(prevManga).executeAsBlocking()
            val mangaCategories = categories.map { MangaCategory.create(manga, it) }
            db.setMangaCategories(mangaCategories, listOf(manga))
        }
        // Update track
        if (MigrationFlags.hasTracks(flags)) {
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
        // }
    }
}
