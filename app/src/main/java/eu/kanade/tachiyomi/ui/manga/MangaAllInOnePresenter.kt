package eu.kanade.tachiyomi.ui.manga

import android.os.Bundle
import com.google.gson.Gson
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.browse.source.SourceController
import eu.kanade.tachiyomi.ui.manga.chapter.ChaptersPresenter
import eu.kanade.tachiyomi.ui.manga.chapter.MangaAllInOneChapterItem
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.prepUpdateCover
import eu.kanade.tachiyomi.util.removeCovers
import exh.MERGED_SOURCE_ID
import exh.debug.DebugToggles
import exh.eh.EHentaiUpdateHelper
import exh.isEhBasedSource
import exh.util.await
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

/**
 * Presenter of MangaInfoFragment.
 * Contains information and data for fragment.
 * Observable updates should be called from here.
 */
class MangaAllInOnePresenter(
    val controller: MangaAllInOneController,
    val manga: Manga,
    val source: Source,
    val smartSearchConfig: SourceController.SmartSearchConfig?,
    private val db: DatabaseHelper = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val gson: Gson = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get()
) : BasePresenter<MangaAllInOneController>() {

    /**
     * List of chapters of the manga. It's always unfiltered and unsorted.
     */
    var chapters: List<MangaAllInOneChapterItem> = emptyList()
        private set

    private var lastUpdateDate: Date = Date(0L)

    private var chapterCount: Float = 0F

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    /**
     * Whether the chapter list has been requested to the source.
     */
    var hasRequested = false
        private set

    /**
     * Subscription to observe download status changes.
     */
    private var observeDownloadsSubscription: Subscription? = null

    // EXH -->
    private val updateHelper: EHentaiUpdateHelper by injectLazy()

    private val redirectUserRelay = BehaviorRelay.create<ChaptersPresenter.EXHRedirect>()
    // EXH <--

    var headerItem = MangaAllInOneHeaderItem(manga, source, smartSearchConfig)

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        updateManga()

        // Listen for download status changes
        observeDownloads()

        add(
            db.getChapters(manga).asRxObservable().subscribe {
                scope.launch(Dispatchers.IO) {
                    updateChaptersView(updateInfo = true)
                }
            }
        )
    }

    private suspend fun updateChapters() {
        val chapters = db.getChapters(manga).await().map { it.toModel() }

        // Find downloaded chapters
        setDownloadedChapters(chapters)

        // EXH -->
        if (chapters.isNotEmpty() && (source.isEhBasedSource()) && DebugToggles.ENABLE_EXH_ROOT_REDIRECT.enabled) {
            // Check for gallery in library and accept manga with lowest id
            // Find chapters sharing same root
            add(
                updateHelper.findAcceptedRootAndDiscardOthers(manga.source, chapters)
                    .subscribeOn(Schedulers.io())
                    .subscribe { (acceptedChain, _) ->
                        // Redirect if we are not the accepted root
                        if (manga.id != acceptedChain.manga.id) {
                            // Update if any of our chapters are not in accepted manga's chapters
                            val ourChapterUrls = chapters.map { it.url }.toSet()
                            val acceptedChapterUrls = acceptedChain.chapters.map { it.url }.toSet()
                            val update = (ourChapterUrls - acceptedChapterUrls).isNotEmpty()
                            redirectUserRelay.call(
                                ChaptersPresenter.EXHRedirect(
                                    acceptedChain.manga,
                                    update
                                )
                            )
                        }
                    }
            )
        }
        // EXH <--

        this.chapters = applyChapterFilters(chapters)
    }

    private fun updateChaptersView(updateInfo: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            updateChapters()
            if (updateInfo) {
                updateChapterInfo()
            }
            withContext(Dispatchers.Main) {
                Observable.just(manga)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeLatestCache({ view, manga -> view.onNextManga(manga, source, chapters, lastUpdateDate, chapterCount) })
            }
        }
    }

    private fun updateChapterInfo() {
        scope.launch(Dispatchers.IO) {
            lastUpdateDate = Date(
                chapters.maxBy { it.date_upload }?.date_upload ?: 0
            )

            chapterCount = chapters.maxBy { it.chapter_number }?.chapter_number ?: 0f
        }
    }

    private fun updateManga(updateInfo: Boolean = true) {
        scope.launch(Dispatchers.IO) {
            var manga2: Manga? = null
            if (updateInfo) {
                manga2 = db.getManga(manga.url, manga.source).await()
                updateChapters()
                updateChapterInfo()
            }

            withContext(Dispatchers.Main) {
                if (manga2 != null) {
                    Observable.just(manga2)
                } else {
                    Observable.just(manga)
                }.observeOn(AndroidSchedulers.mainThread())
                    .subscribeLatestCache({ view, manga -> view.onNextManga(manga, source, chapters, lastUpdateDate, chapterCount) })
            }
        }
    }

    /**
     * Fetch manga information from source.
     */
    fun fetchMangaFromSource(manualFetch: Boolean = false, fetchManga: Boolean = true, fetchChapters: Boolean = true) {
        if (fetchChapters) {
            hasRequested = true
        }

        scope.launch(Dispatchers.IO) {
            if (fetchManga) {
                val networkManga = try {
                    source.fetchMangaDetails(manga).toBlocking().single()
                } catch (e: Exception) {
                    controller.onFetchMangaError(e)
                    return@launch
                }
                if (networkManga != null) {
                    manga.prepUpdateCover(coverCache, networkManga, manualFetch)
                    manga.copyFrom(networkManga)
                    manga.initialized = true
                    db.insertManga(manga).await()
                }
            }
            var chapters: List<SChapter> = listOf()
            if (fetchChapters) {
                try {
                    chapters = source.fetchChapterList(manga).toBlocking().single()
                } catch (e: Exception) {
                    controller.onFetchMangaError(e)
                    return@launch
                }
            }
            try {
                if (fetchChapters) {
                    syncChaptersWithSource(db, chapters, manga, source)

                    updateChapters()
                    updateChapterInfo()
                }
                withContext(Dispatchers.Main) {
                    updateManga(updateInfo = false)
                    controller.onFetchMangaDone()
                }
            } catch (e: Exception) {
                controller.onFetchMangaError(e)
            }
        }
    }

    /**
     * Update favorite status of manga, (removes / adds) manga (to / from) library.
     *
     * @return the new status of the manga.
     */
    fun toggleFavorite(): Boolean {
        manga.favorite = !manga.favorite
        controller.setFavoriteButtonState(manga.favorite)
        if (!manga.favorite) {
            manga.removeCovers(coverCache)
        }
        db.insertManga(manga).executeAsBlocking()
        return manga.favorite
    }

    private fun setFavorite(favorite: Boolean) {
        if (manga.favorite == favorite) {
            return
        }
        toggleFavorite()
    }

    /**
     * Returns true if the manga has any downloads.
     */
    fun hasDownloads(): Boolean {
        return downloadManager.getDownloadCount(manga) > 0
    }

    /**
     * Deletes all the downloads for the manga.
     */
    fun deleteDownloads() {
        downloadManager.deleteManga(manga, source)
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    fun getCategories(): List<Category> {
        return db.getCategories().executeAsBlocking()
    }

    /**
     * Gets the category id's the manga is in, if the manga is not in a category, returns the default id.
     *
     * @param manga the manga to get categories from.
     * @return Array of category ids the manga is in, if none returns default id
     */
    fun getMangaCategoryIds(manga: Manga): Array<Int> {
        val categories = db.getCategoriesForManga(manga).executeAsBlocking()
        return categories.mapNotNull { it.id }.toTypedArray()
    }

    /**
     * Move the given manga to categories.
     *
     * @param manga the manga to move.
     * @param categories the selected categories.
     */
    fun moveMangaToCategories(manga: Manga, categories: List<Category>) {
        val mc = categories.filter { it.id != 0 }.map { MangaCategory.create(manga, it) }
        db.setMangaCategories(mc, listOf(manga))
    }

    /**
     * Move the given manga to the category.
     *
     * @param manga the manga to move.
     * @param category the selected category, or null for default category.
     */
    fun moveMangaToCategory(manga: Manga, category: Category?) {
        moveMangaToCategories(manga, listOfNotNull(category))
    }

    suspend fun smartSearchMerge(manga: Manga, originalMangaId: Long): Manga {
        val originalManga = db.getManga(originalMangaId).await()
            ?: throw IllegalArgumentException("Unknown manga ID: $originalMangaId")
        val toInsert = if (originalManga.source == MERGED_SOURCE_ID) {
            originalManga.apply {
                val originalChildren = MergedSource.MangaConfig.readFromUrl(gson, url).children
                if (originalChildren.any { it.source == manga.source && it.url == manga.url }) {
                    throw IllegalArgumentException("This manga is already merged with the current manga!")
                }

                url = MergedSource.MangaConfig(
                    originalChildren + MergedSource.MangaSource(
                        manga.source,
                        manga.url
                    )
                ).writeAsUrl(gson)
            }
        } else {
            val newMangaConfig = MergedSource.MangaConfig(
                listOf(
                    MergedSource.MangaSource(
                        originalManga.source,
                        originalManga.url
                    ),
                    MergedSource.MangaSource(
                        manga.source,
                        manga.url
                    )
                )
            )
            Manga.create(newMangaConfig.writeAsUrl(gson), originalManga.title, MERGED_SOURCE_ID).apply {
                copyFrom(originalManga)
                favorite = true
                last_update = originalManga.last_update
                viewer = originalManga.viewer
                chapter_flags = originalManga.chapter_flags
                sorting = Manga.SORTING_NUMBER
            }
        }

        // Note that if the manga are merged in a different order, this won't trigger, but I don't care lol
        val existingManga = db.getManga(toInsert.url, toInsert.source).await()
        if (existingManga != null) {
            withContext(NonCancellable) {
                if (toInsert.id != null) {
                    db.deleteManga(toInsert).await()
                }
            }

            return existingManga
        }

        // Reload chapters immediately
        toInsert.initialized = false

        val newId = db.insertManga(toInsert).await().insertedId()
        if (newId != null) toInsert.id = newId

        return toInsert
    }

    private fun observeDownloads() {
        observeDownloadsSubscription?.let { remove(it) }
        observeDownloadsSubscription = downloadManager.queue.getStatusObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .filter { download -> download.manga.id == manga.id }
            .doOnNext { onDownloadStatusChange(it) }
            .subscribeLatestCache(MangaAllInOneController::onChapterStatusChange) { _, error ->
                Timber.e(error)
            }
    }

    /**
     * Converts a chapter from the database to an extended model, allowing to store new fields.
     */
    private fun Chapter.toModel(): MangaAllInOneChapterItem {
        // Create the model object.
        val model = MangaAllInOneChapterItem(this, manga)

        // Find an active download for this chapter.
        val download = downloadManager.queue.find { it.chapter.id == id }

        if (download != null) {
            // If there's an active download, assign it.
            model.download = download
        }
        return model
    }

    /**
     * Finds and assigns the list of downloaded chapters.
     *
     * @param chapters the list of chapter from the database.
     */
    private fun setDownloadedChapters(chapters: List<MangaAllInOneChapterItem>) {
        for (chapter in chapters) {
            if (downloadManager.isChapterDownloaded(chapter, manga)) {
                chapter.status = Download.DOWNLOADED
            }
        }
    }

    /**
     * Applies the view filters to the list of chapters obtained from the database.
     * @param chapters the list of chapters from the database
     * @return an observable of the list of chapters filtered and sorted.
     */
    private fun applyChapterFilters(chapterList: List<MangaAllInOneChapterItem>): List<MangaAllInOneChapterItem> {
        var chapters = chapterList
        if (onlyUnread()) {
            chapters = chapters.filter { !it.read }
        } else if (onlyRead()) {
            chapters = chapters.filter { it.read }
        }
        if (onlyDownloaded()) {
            chapters = chapters.filter { it.isDownloaded || it.manga.source == LocalSource.ID }
        }
        if (onlyBookmarked()) {
            chapters = chapters.filter { it.bookmark }
        }
        val sortFunction: (Chapter, Chapter) -> Int = when (manga.sorting) {
            Manga.SORTING_SOURCE -> when (sortDescending()) {
                true -> { c1, c2 -> c1.source_order.compareTo(c2.source_order) }
                false -> { c1, c2 -> c2.source_order.compareTo(c1.source_order) }
            }
            Manga.SORTING_NUMBER -> when (sortDescending()) {
                true -> { c1, c2 -> c2.chapter_number.compareTo(c1.chapter_number) }
                false -> { c1, c2 -> c1.chapter_number.compareTo(c2.chapter_number) }
            }
            else -> { c1, c2 -> c1.source_order.compareTo(c2.source_order) }
        }
        chapters = chapters.sortedWith(Comparator(sortFunction))
        return chapters
    }

    /**
     * Called when a download for the active manga changes status.
     * @param download the download whose status changed.
     */
    fun onDownloadStatusChange(download: Download) {
        // Assign the download to the model object.
        if (download.status == Download.QUEUE) {
            chapters.find { it.id == download.chapter.id }?.let {
                if (it.download == null) {
                    it.download = download
                }
            }
        }

        // Force UI update if downloaded filter active and download finished.
        if (onlyDownloaded() && download.status == Download.DOWNLOADED) {
            updateChaptersView()
        }
    }

    /**
     * Returns the next unread chapter or null if everything is read.
     */
    fun getNextUnreadChapter(): MangaAllInOneChapterItem? {
        return chapters.sortedByDescending { it.source_order }.find { !it.read }
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param selectedChapters the list of selected chapters.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChaptersRead(selectedChapters: List<MangaAllInOneChapterItem>, read: Boolean) {
        Observable.from(selectedChapters)
            .doOnNext { chapter ->
                chapter.read = read
                if (!read /* --> EH */ && !preferences
                    .eh_preserveReadingPosition()
                    .get() /* <-- EH */
                ) {
                    chapter.last_page_read = 0
                }
            }
            .toList()
            .flatMap { db.updateChaptersProgress(it).asRxObservable() }
            .subscribeOn(Schedulers.io())
            .subscribe()
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param chapters the list of chapters to download.
     */
    fun downloadChapters(chapters: List<MangaAllInOneChapterItem>) {
        downloadManager.downloadChapters(manga, chapters)
    }

    /**
     * Bookmarks the given list of chapters.
     * @param selectedChapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(selectedChapters: List<MangaAllInOneChapterItem>, bookmarked: Boolean) {
        Observable.from(selectedChapters)
            .doOnNext { chapter ->
                chapter.bookmark = bookmarked
            }
            .toList()
            .flatMap { db.updateChaptersProgress(it).asRxObservable() }
            .subscribeOn(Schedulers.io())
            .subscribe()
    }

    /**
     * Deletes the given list of chapter.
     * @param chapters the list of chapters to delete.
     */
    fun deleteChapters(chapters: List<MangaAllInOneChapterItem>) {
        Observable.just(chapters)
            .doOnNext { deleteChaptersInternal(chapters) }
            .doOnNext { if (onlyDownloaded()) updateChaptersView() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { view, _ ->
                    view.onChaptersDeleted(chapters)
                },
                MangaAllInOneController::onChaptersDeletedError
            )
    }

    /**
     * Deletes a list of chapters from disk. This method is called in a background thread.
     * @param chapters the chapters to delete.
     */
    private fun deleteChaptersInternal(chapters: List<MangaAllInOneChapterItem>) {
        downloadManager.deleteChapters(chapters, manga, source)
        chapters.forEach {
            it.status = Download.NOT_DOWNLOADED
            it.download = null
        }
    }

    /**
     * Reverses the sorting and requests an UI update.
     */
    fun revertSortOrder() {
        manga.setChapterOrder(if (sortDescending()) Manga.SORT_ASC else Manga.SORT_DESC)
        db.updateFlags(manga).executeAsBlocking()
        updateChaptersView()
    }

    /**
     * Sets the read filter and requests an UI update.
     * @param onlyUnread whether to display only unread chapters or all chapters.
     */
    fun setUnreadFilter(onlyUnread: Boolean) {
        manga.readFilter = if (onlyUnread) Manga.SHOW_UNREAD else Manga.SHOW_ALL
        db.updateFlags(manga).executeAsBlocking()
        updateChaptersView()
    }

    /**
     * Sets the read filter and requests an UI update.
     * @param onlyRead whether to display only read chapters or all chapters.
     */
    fun setReadFilter(onlyRead: Boolean) {
        manga.readFilter = if (onlyRead) Manga.SHOW_READ else Manga.SHOW_ALL
        db.updateFlags(manga).executeAsBlocking()
        updateChaptersView()
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param onlyDownloaded whether to display only downloaded chapters or all chapters.
     */
    fun setDownloadedFilter(onlyDownloaded: Boolean) {
        manga.downloadedFilter = if (onlyDownloaded) Manga.SHOW_DOWNLOADED else Manga.SHOW_ALL
        db.updateFlags(manga).executeAsBlocking()
        updateChaptersView()
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param onlyBookmarked whether to display only bookmarked chapters or all chapters.
     */
    fun setBookmarkedFilter(onlyBookmarked: Boolean) {
        manga.bookmarkedFilter = if (onlyBookmarked) Manga.SHOW_BOOKMARKED else Manga.SHOW_ALL
        db.updateFlags(manga).executeAsBlocking()
        updateChaptersView()
    }

    /**
     * Removes all filters and requests an UI update.
     */
    fun removeFilters() {
        manga.readFilter = Manga.SHOW_ALL
        manga.downloadedFilter = Manga.SHOW_ALL
        manga.bookmarkedFilter = Manga.SHOW_ALL
        db.updateFlags(manga).executeAsBlocking()
        updateChaptersView()
    }

    /**
     * Adds manga to library
     */
    fun addToLibrary() {
        setFavorite(true)
    }

    /**
     * Sets the active display mode.
     * @param mode the mode to set.
     */
    fun setDisplayMode(mode: Int) {
        manga.displayMode = mode
        db.updateFlags(manga).executeAsBlocking()
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Int) {
        manga.sorting = sort
        db.updateFlags(manga).executeAsBlocking()
        updateChaptersView()
    }

    /**
     * Whether downloaded only mode is enabled.
     */
    fun forceDownloaded(): Boolean {
        return manga.favorite && preferences.downloadedOnly().get()
    }

    /**
     * Whether the display only downloaded filter is enabled.
     */
    fun onlyDownloaded(): Boolean {
        return forceDownloaded() || manga.downloadedFilter == Manga.SHOW_DOWNLOADED
    }

    /**
     * Whether the display only downloaded filter is enabled.
     */
    fun onlyBookmarked(): Boolean {
        return manga.bookmarkedFilter == Manga.SHOW_BOOKMARKED
    }

    /**
     * Whether the display only unread filter is enabled.
     */
    fun onlyUnread(): Boolean {
        return manga.readFilter == Manga.SHOW_UNREAD
    }

    /**
     * Whether the display only read filter is enabled.
     */
    fun onlyRead(): Boolean {
        return manga.readFilter == Manga.SHOW_READ
    }

    /**
     * Whether the sorting method is descending or ascending.
     */
    fun sortDescending(): Boolean {
        return manga.sortDescending()
    }
}
