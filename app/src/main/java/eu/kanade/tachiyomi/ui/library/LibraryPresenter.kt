package eu.kanade.tachiyomi.ui.library

import android.os.Bundle
import android.util.Pair
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.source.SourceManager
import eu.kanade.tachiyomi.event.LibraryMangaEvent
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.BehaviorSubject
import java.io.IOException
import java.io.InputStream
import java.util.*
import javax.inject.Inject

/**
 * Presenter of [LibraryFragment].
 */
class LibraryPresenter : BasePresenter<LibraryFragment>() {

    /**
     * Categories of the library.
     */
    lateinit var categories: List<Category>

    /**
     * Currently selected manga.
     */
    var selectedMangas = mutableListOf<Manga>()

    /**
     * Search query of the library.
     */
    val searchSubject = BehaviorSubject.create<String>()

    /**
     * Subject to notify the library's viewpager for updates.
     */
    val libraryMangaSubject = BehaviorSubject.create<LibraryMangaEvent?>()

    /**
     * Database.
     */
    @Inject lateinit var db: DatabaseHelper

    /**
     * Preferences.
     */
    @Inject lateinit var preferences: PreferencesHelper

    /**
     * Cover cache.
     */
    @Inject lateinit var coverCache: CoverCache

    /**
     * Source manager.
     */
    @Inject lateinit var sourceManager: SourceManager

    /**
     * Download manager.
     */
    @Inject lateinit var downloadManager: DownloadManager

    companion object {
        /**
         * Id of the restartable that listens for library updates.
         */
        const val GET_LIBRARY = 1
    }

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        restartableLatestCache(GET_LIBRARY,
                { getLibraryObservable() },
                { view, pair -> view.onNextLibraryUpdate(pair.first, pair.second) })

        if (savedState == null) {
            start(GET_LIBRARY)
        }

    }

    override fun onDropView() {
        libraryMangaSubject.onNext(null)
        super.onDropView()
    }

    override fun onTakeView(libraryFragment: LibraryFragment) {
        super.onTakeView(libraryFragment)
        if (isUnsubscribed(GET_LIBRARY)) {
            start(GET_LIBRARY)
        }
    }

    /**
     * Get the categories and all its manga from the database.
     *
     * @return an observable of the categories and its manga.
     */
    fun getLibraryObservable(): Observable<Pair<List<Category>, Map<Int, List<Manga>>>> {
        return Observable.combineLatest(getCategoriesObservable(), getLibraryMangasObservable(),
                { a, b -> Pair(a, b) })
                .observeOn(AndroidSchedulers.mainThread())
    }

    /**
     * Update the library information
     */
    fun updateLibrary() {
        start(GET_LIBRARY)
    }

    /**
     * Get the categories from the database.
     *
     * @return an observable of the categories.
     */
    fun getCategoriesObservable(): Observable<List<Category>> {
        return db.getCategories().asRxObservable()
                .doOnNext { categories -> this.categories = categories }
    }

    /**
     * Get the manga grouped by categories.
     *
     * @return an observable containing a map with the category id as key and a list of manga as the
     * value.
     */
    fun getLibraryMangasObservable(): Observable<Map<Int, List<Manga>>> {
        return db.getLibraryMangas().asRxObservable()
                .flatMap { mangas ->
                    Observable.from(mangas)
                            .filter {
                                // Filter library by options
                                filterLibrary(it)
                            }
                            .groupBy { it.category }
                            .flatMap { group -> group.toList().map { Pair(group.key, it) } }
                            .toMap({ it.first }, { it.second })
                }
    }

    /**
     * Filter library by preference
     *
     * @param manga from library
     * @return filter status
     */
    fun filterLibrary(manga: Manga): Boolean {
        val prefFilterDownloaded = preferences.filterDownloaded().getOrDefault()
        val prefFilterUnread = preferences.filterUnread().getOrDefault()

        // Check if filter option is selected
        if (prefFilterDownloaded || prefFilterUnread) {

            // Does it have downloaded chapters.
            var hasDownloaded = false
            var hasUnread = false

            if (prefFilterUnread) {
                // Does it have unread chapters.
                hasUnread = manga.unread > 0
            }

            if (prefFilterDownloaded) {
                val mangaDir = downloadManager.getAbsoluteMangaDirectory(sourceManager.get(manga.source)!!, manga)

                if (mangaDir.exists()) {
                    for (file in mangaDir.listFiles()) {
                        if (file.isDirectory && file.listFiles().isNotEmpty()) {
                            hasDownloaded = true
                            break
                        }
                    }
                }
            }

            // Return correct filter status
            if (prefFilterDownloaded && prefFilterUnread) {
                return (hasDownloaded && hasUnread)
            } else {
                return (hasDownloaded || hasUnread)
            }
        } else {
            return true
        }
    }

    /**
     * Called when a manga is opened.
     */
    fun onOpenManga() {
        // Avoid further db updates for the library when it's not needed
        stop(GET_LIBRARY)
    }

    /**
     * Sets the selection for a given manga.
     *
     * @param manga the manga whose selection has changed.
     * @param selected whether it's now selected or not.
     */
    fun setSelection(manga: Manga, selected: Boolean) {
        if (selected) {
            selectedMangas.add(manga)
        } else {
            selectedMangas.remove(manga)
        }
    }

    /**
     * Returns the common categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    fun getCommonCategories(mangas: List<Manga>) = mangas.toSet()
            .map { db.getCategoriesForManga(it).executeAsBlocking() }
            .reduce { set1: Iterable<Category>, set2 -> set1.intersect(set2) }

    /**
     * Remove the selected manga from the library.
     */
    fun deleteMangas() {
        for (manga in selectedMangas) {
            manga.favorite = false
        }

        db.insertMangas(selectedMangas).executeAsBlocking()
    }

    /**
     * Move the given list of manga to categories.
     *
     * @param positions the indexes of the selected categories.
     * @param mangas the list of manga to move.
     */
    fun moveMangasToCategories(positions: Array<Int>, mangas: List<Manga>) {
        val categoriesToAdd = ArrayList<Category>()
        for (index in positions) {
            categoriesToAdd.add(categories[index])
        }

        moveMangasToCategories(categoriesToAdd, mangas)
    }

    /**
     * Move the given list of manga to categories.
     *
     * @param categories the selected categories.
     * @param mangas the list of manga to move.
     */
    fun moveMangasToCategories(categories: List<Category>, mangas: List<Manga>) {
        val mc = ArrayList<MangaCategory>()

        for (manga in mangas) {
            for (cat in categories) {
                mc.add(MangaCategory.create(manga, cat))
            }
        }

        db.setMangaCategories(mc, mangas)
    }

    /**
     * Update cover with local file.
     *
     * @param inputStream the new cover.
     * @param manga the manga edited.
     * @return true if the cover is updated, false otherwise
     */
    @Throws(IOException::class)
    fun editCoverWithStream(inputStream: InputStream, manga: Manga): Boolean {
        if (manga.thumbnail_url != null && manga.favorite) {
            coverCache.copyToCache(manga.thumbnail_url, inputStream)
            return true
        }
        return false
    }

}
