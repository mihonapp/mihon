package eu.kanade.tachiyomi.ui.library

import android.os.Bundle
import android.util.Pair
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.source.SourceManager
import eu.kanade.tachiyomi.event.LibraryMangasEvent
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import org.greenrobot.eventbus.EventBus
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.BehaviorSubject
import java.io.File
import java.io.IOException
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
    lateinit var selectedMangas: MutableList<Manga>

    /**
     * Search query of the library.
     */
    lateinit var searchSubject: BehaviorSubject<String>

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

    companion object {
        /**
         * Id of the restartable that listens for library updates.
         */
        const val GET_LIBRARY = 1
    }

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        selectedMangas = ArrayList()

        searchSubject = BehaviorSubject.create()

        restartableLatestCache(GET_LIBRARY,
                { getLibraryObservable() },
                { view, pair -> view.onNextLibraryUpdate(pair.first, pair.second) })

        if (savedState == null) {
            start(GET_LIBRARY)
        }

    }

    override fun onDropView() {
        EventBus.getDefault().removeStickyEvent(LibraryMangasEvent::class.java)
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
     * Get the categories from the database.
     *
     * @return an observable of the categories.
     */
    fun getCategoriesObservable(): Observable<List<Category>> {
        return db.categories.asRxObservable()
                .doOnNext { categories -> this.categories = categories }
    }

    /**
     * Get the manga grouped by categories.
     *
     * @return an observable containing a map with the category id as key and a list of manga as the
     * value.
     */
    fun getLibraryMangasObservable(): Observable<Map<Int, List<Manga>>> {
        return db.libraryMangas.asRxObservable()
                .flatMap { mangas -> Observable.from(mangas)
                        .groupBy { it.category }
                        .flatMap { group -> group.toList().map { Pair(group.key, it) } }
                        .toMap({ it.first }, { it.second })
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
     * Get the category names as a list.
     */
    fun getCategoryNames(): List<String> {
        return categories.map { it.name }
    }

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
     * @param file the new cover.
     * @param manga the manga edited.
     * @return true if the cover is updated, false otherwise
     */
    @Throws(IOException::class)
    fun editCoverWithLocalFile(file: File, manga: Manga): Boolean {
        if (!manga.initialized)
            return false

        if (manga.favorite) {
            coverCache.copyToLocalCache(manga.thumbnail_url, file)
            return true
        }
        return false
    }

}
