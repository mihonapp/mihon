package eu.kanade.tachiyomi.ui.reader

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.chapter.interactor.UpdateChapter
import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.download.service.DownloadPreferences
import eu.kanade.domain.history.interactor.GetNextChapters
import eu.kanade.domain.history.interactor.UpsertHistory
import eu.kanade.domain.history.model.HistoryUpdate
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.domain.track.interactor.GetTracks
import eu.kanade.domain.track.interactor.InsertTrack
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.database.models.toDomainManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.job.DelayedTrackingStore
import eu.kanade.tachiyomi.data.track.job.DelayedTrackingUpdateJob
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.loader.DownloadPageLoader
import eu.kanade.tachiyomi.ui.reader.loader.HttpPageLoader
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.StencilPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.util.chapter.getChapterSort
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.lang.byteSize
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchNonCancellable
import eu.kanade.tachiyomi.util.lang.takeBytes
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.Date
import java.util.concurrent.TimeUnit
import eu.kanade.domain.manga.model.Manga as DomainManga

/**
 * Presenter used by the activity to perform background operations.
 */
class ReaderPresenter(
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadProvider: DownloadProvider = Injekt.get(),
    preferences: BasePreferences = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val readerPreferences: ReaderPreferences = Injekt.get(),
    private val trackPreferences: TrackPreferences = Injekt.get(),
    private val delayedTrackingStore: DelayedTrackingStore = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getChapterByMangaId: GetChapterByMangaId = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    private val upsertHistory: UpsertHistory = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val setMangaViewerFlags: SetMangaViewerFlags = Injekt.get(),
) : BasePresenter<ReaderActivity>() {

    /**
     * The manga loaded in the reader. It can be null when instantiated for a short time.
     */
    var manga: Manga? = null
        private set

    /**
     * The chapter id of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterId = -1L

    /**
     * The chapter loader for the loaded manga. It'll be null until [manga] is set.
     */
    private var loader: ChapterLoader? = null

    /**
     * The time the chapter was started reading
     */
    private var chapterReadStartTime: Long? = null

    /**
     * Subscription to prevent setting chapters as active from multiple threads.
     */
    private var activeChapterSubscription: Subscription? = null

    /**
     * Relay for currently active viewer chapters.
     */
    private val viewerChaptersRelay = BehaviorRelay.create<ViewerChapters>()

    /**
     * Relay used when loading prev/next chapter needed to lock the UI (with a dialog).
     */
    private val isLoadingAdjacentChapterRelay = BehaviorRelay.create<Boolean>()

    private val imageSaver: ImageSaver by injectLazy()

    private var chapterToDownload: Download? = null

    /**
     * Chapter list for the active manga. It's retrieved lazily and should be accessed for the first
     * time in a background thread to avoid blocking the UI.
     */
    private val chapterList by lazy {
        val manga = manga!!
        val chapters = runBlocking { getChapterByMangaId.await(manga.id!!) }

        val selectedChapter = chapters.find { it.id == chapterId }
            ?: error("Requested chapter of id $chapterId not found in chapter list")

        val chaptersForReader = when {
            (readerPreferences.skipRead().get() || readerPreferences.skipFiltered().get()) -> {
                val filteredChapters = chapters.filterNot {
                    when {
                        readerPreferences.skipRead().get() && it.read -> true
                        readerPreferences.skipFiltered().get() -> {
                            (manga.readFilter == DomainManga.CHAPTER_SHOW_READ.toInt() && !it.read) ||
                                (manga.readFilter == DomainManga.CHAPTER_SHOW_UNREAD.toInt() && it.read) ||
                                (manga.downloadedFilter == DomainManga.CHAPTER_SHOW_DOWNLOADED.toInt() && !downloadManager.isChapterDownloaded(it.name, it.scanlator, manga.title, manga.source)) ||
                                (manga.downloadedFilter == DomainManga.CHAPTER_SHOW_NOT_DOWNLOADED.toInt() && downloadManager.isChapterDownloaded(it.name, it.scanlator, manga.title, manga.source)) ||
                                (manga.bookmarkedFilter == DomainManga.CHAPTER_SHOW_BOOKMARKED.toInt() && !it.bookmark) ||
                                (manga.bookmarkedFilter == DomainManga.CHAPTER_SHOW_NOT_BOOKMARKED.toInt() && it.bookmark)
                        }
                        else -> false
                    }
                }

                if (filteredChapters.any { it.id == chapterId }) {
                    filteredChapters
                } else {
                    filteredChapters + listOf(selectedChapter)
                }
            }
            else -> chapters
        }

        chaptersForReader
            .sortedWith(getChapterSort(manga.toDomainManga()!!, sortDescending = false))
            .map { it.toDbChapter() }
            .map(::ReaderChapter)
    }

    private var hasTrackers: Boolean = false
    private val checkTrackers: (DomainManga) -> Unit = { manga ->
        val tracks = runBlocking { getTracks.await(manga.id) }
        hasTrackers = tracks.isNotEmpty()
    }

    private val incognitoMode = preferences.incognitoMode().get()

    /**
     * Called when the presenter is created. It retrieves the saved active chapter if the process
     * was restored.
     */
    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        if (savedState != null) {
            chapterId = savedState.getLong(::chapterId.name, -1)
        }
    }

    /**
     * Called when the presenter is destroyed. It saves the current progress and cleans up
     * references on the currently active chapters.
     */
    override fun onDestroy() {
        super.onDestroy()
        val currentChapters = viewerChaptersRelay.value
        if (currentChapters != null) {
            currentChapters.unref()
            saveReadingProgress(currentChapters.currChapter)
            chapterToDownload?.let {
                downloadManager.addDownloadsToStartOfQueue(listOf(it))
            }
        }
    }

    /**
     * Called when the presenter instance is being saved. It saves the currently active chapter
     * id and the last page read.
     */
    override fun onSave(state: Bundle) {
        super.onSave(state)
        val currentChapter = getCurrentChapter()
        if (currentChapter != null) {
            currentChapter.requestedPage = currentChapter.chapter.last_page_read
            state.putLong(::chapterId.name, currentChapter.chapter.id!!)
        }
    }

    /**
     * Called when the user pressed the back button and is going to leave the reader. Used to
     * trigger deletion of the downloaded chapters.
     */
    fun onBackPressed() {
        deletePendingChapters()
    }

    /**
     * Called when the activity is saved and not changing configurations. It updates the database
     * to persist the current progress of the active chapter.
     */
    fun onSaveInstanceStateNonConfigurationChange() {
        val currentChapter = getCurrentChapter() ?: return
        presenterScope.launchNonCancellable {
            saveChapterProgress(currentChapter)
        }
    }

    /**
     * Whether this presenter is initialized yet.
     */
    fun needsInit(): Boolean {
        return manga == null
    }

    /**
     * Initializes this presenter with the given [mangaId] and [initialChapterId]. This method will
     * fetch the manga from the database and initialize the initial chapter.
     */
    fun init(mangaId: Long, initialChapterId: Long) {
        if (!needsInit()) return

        presenterScope.launchIO {
            try {
                val manga = getManga.await(mangaId)
                withUIContext {
                    manga?.let { init(it.toDbManga(), initialChapterId) }
                }
            } catch (e: Throwable) {
                view?.setInitialChapterError(e)
            }
        }
    }

    /**
     * Initializes this presenter with the given [manga] and [initialChapterId]. This method will
     * set the chapter loader, view subscriptions and trigger an initial load.
     */
    private fun init(manga: Manga, initialChapterId: Long) {
        if (!needsInit()) return

        this.manga = manga
        if (chapterId == -1L) chapterId = initialChapterId

        checkTrackers(manga.toDomainManga()!!)

        val context = Injekt.get<Application>()
        val source = sourceManager.getOrStub(manga.source)
        loader = ChapterLoader(context, downloadManager, downloadProvider, manga.toDomainManga()!!, source)

        Observable.just(manga).subscribeLatestCache(ReaderActivity::setManga)
        viewerChaptersRelay.subscribeLatestCache(ReaderActivity::setChapters)
        isLoadingAdjacentChapterRelay.subscribeLatestCache(ReaderActivity::setProgressDialog)

        // Read chapterList from an io thread because it's retrieved lazily and would block main.
        activeChapterSubscription?.unsubscribe()
        activeChapterSubscription = Observable
            .fromCallable { chapterList.first { chapterId == it.chapter.id } }
            .flatMap { getLoadObservable(loader!!, it) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { _, _ ->
                    // Ignore onNext event
                },
                ReaderActivity::setInitialChapterError,
            )
    }

    /**
     * Returns an observable that loads the given [chapter] with this [loader]. This observable
     * handles main thread synchronization and updating the currently active chapters on
     * [viewerChaptersRelay], however callers must ensure there won't be more than one
     * subscription active by unsubscribing any existing [activeChapterSubscription] before.
     * Callers must also handle the onError event.
     */
    private fun getLoadObservable(
        loader: ChapterLoader,
        chapter: ReaderChapter,
    ): Observable<ViewerChapters> {
        return loader.loadChapter(chapter)
            .andThen(
                Observable.fromCallable {
                    val chapterPos = chapterList.indexOf(chapter)

                    ViewerChapters(
                        chapter,
                        chapterList.getOrNull(chapterPos - 1),
                        chapterList.getOrNull(chapterPos + 1),
                    )
                },
            )
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { newChapters ->
                val oldChapters = viewerChaptersRelay.value

                // Add new references first to avoid unnecessary recycling
                newChapters.ref()
                oldChapters?.unref()

                chapterToDownload = deleteChapterFromDownloadQueue(newChapters.currChapter)
                viewerChaptersRelay.call(newChapters)
            }
    }

    /**
     * Called when the user changed to the given [chapter] when changing pages from the viewer.
     * It's used only to set this chapter as active.
     */
    private fun loadNewChapter(chapter: ReaderChapter) {
        val loader = loader ?: return

        logcat { "Loading ${chapter.chapter.url}" }

        activeChapterSubscription?.unsubscribe()
        activeChapterSubscription = getLoadObservable(loader, chapter)
            .toCompletable()
            .onErrorComplete()
            .subscribe()
            .also(::add)
    }

    /**
     * Called when the user is going to load the prev/next chapter through the menu button. It
     * sets the [isLoadingAdjacentChapterRelay] that the view uses to prevent any further
     * interaction until the chapter is loaded.
     */
    private fun loadAdjacent(chapter: ReaderChapter) {
        val loader = loader ?: return

        logcat { "Loading adjacent ${chapter.chapter.url}" }

        activeChapterSubscription?.unsubscribe()
        activeChapterSubscription = getLoadObservable(loader, chapter)
            .doOnSubscribe { isLoadingAdjacentChapterRelay.call(true) }
            .doOnUnsubscribe { isLoadingAdjacentChapterRelay.call(false) }
            .subscribeFirst(
                { view, _ ->
                    view.moveToPageIndex(0)
                },
                { _, _ ->
                    // Ignore onError event, viewers handle that state
                },
            )
    }

    /**
     * Called when the viewers decide it's a good time to preload a [chapter] and improve the UX so
     * that the user doesn't have to wait too long to continue reading.
     */
    private fun preload(chapter: ReaderChapter) {
        if (chapter.pageLoader is HttpPageLoader) {
            val manga = manga ?: return
            val dbChapter = chapter.chapter
            val isDownloaded = downloadManager.isChapterDownloaded(
                dbChapter.name,
                dbChapter.scanlator,
                manga.title,
                manga.source,
                skipCache = true,
            )
            if (isDownloaded) {
                chapter.state = ReaderChapter.State.Wait
            }
        }

        if (chapter.state != ReaderChapter.State.Wait && chapter.state !is ReaderChapter.State.Error) {
            return
        }

        logcat { "Preloading ${chapter.chapter.url}" }

        val loader = loader ?: return
        loader.loadChapter(chapter)
            .observeOn(AndroidSchedulers.mainThread())
            // Update current chapters whenever a chapter is preloaded
            .doOnCompleted { viewerChaptersRelay.value?.let(viewerChaptersRelay::call) }
            .onErrorComplete()
            .subscribe()
            .also(::add)
    }

    /**
     * Called every time a page changes on the reader. Used to mark the flag of chapters being
     * read, update tracking services, enqueue downloaded chapter deletion, and updating the active chapter if this
     * [page]'s chapter is different from the currently active.
     */
    fun onPageSelected(page: ReaderPage) {
        val currentChapters = viewerChaptersRelay.value ?: return

        val selectedChapter = page.chapter

        // InsertPage and StencilPage doesn't change page progress
        if (page is InsertPage || page is StencilPage) {
            return
        }

        // Save last page read and mark as read if needed
        selectedChapter.chapter.last_page_read = page.index
        val shouldTrack = !incognitoMode || hasTrackers
        if (selectedChapter.pages?.lastIndex == page.index && shouldTrack) {
            selectedChapter.chapter.read = true
            updateTrackChapterRead(selectedChapter)
            deleteChapterIfNeeded(selectedChapter)
        }

        if (selectedChapter != currentChapters.currChapter) {
            logcat { "Setting ${selectedChapter.chapter.url} as active" }
            saveReadingProgress(currentChapters.currChapter)
            setReadStartTime()
            loadNewChapter(selectedChapter)
        }
        val pages = page.chapter.pages ?: return
        val inDownloadRange = page.number.toDouble() / pages.size > 0.25
        if (inDownloadRange) {
            downloadNextChapters()
        }
    }

    private fun downloadNextChapters() {
        val manga = manga ?: return
        val amount = downloadPreferences.autoDownloadWhileReading().get()
        if (amount == 0 || !manga.favorite) return

        // Only download ahead if current + next chapter is already downloaded too to avoid jank
        if (getCurrentChapter()?.pageLoader !is DownloadPageLoader) return
        val nextChapter = viewerChaptersRelay.value?.nextChapter?.chapter ?: return

        presenterScope.launchIO {
            val isNextChapterDownloaded = downloadManager.isChapterDownloaded(
                nextChapter.name,
                nextChapter.scanlator,
                manga.title,
                manga.source,
            )
            if (!isNextChapterDownloaded) return@launchIO

            val chaptersToDownload = getNextChapters.await(manga.id!!, nextChapter.id!!)
                .take(amount)
            downloadManager.downloadChapters(
                manga.toDomainManga()!!,
                chaptersToDownload,
            )
        }
    }

    /**
     * Removes [currentChapter] from download queue
     * if setting is enabled and [currentChapter] is queued for download
     */
    private fun deleteChapterFromDownloadQueue(currentChapter: ReaderChapter): Download? {
        return downloadManager.getChapterDownloadOrNull(currentChapter.chapter.toDomainChapter()!!)?.apply {
            downloadManager.deletePendingDownload(this)
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last chapter actually exists.
     * If both conditions are satisfied enqueues chapter for delete
     * @param currentChapter current chapter, which is going to be marked as read.
     */
    private fun deleteChapterIfNeeded(currentChapter: ReaderChapter) {
        // Determine which chapter should be deleted and enqueue
        val currentChapterPosition = chapterList.indexOf(currentChapter)
        val removeAfterReadSlots = downloadPreferences.removeAfterReadSlots().get()
        val chapterToDelete = chapterList.getOrNull(currentChapterPosition - removeAfterReadSlots)

        // If chapter is completely read no need to download it
        chapterToDownload = null

        // Check if deleting option is enabled and chapter exists
        if (removeAfterReadSlots != -1 && chapterToDelete != null) {
            enqueueDeleteReadChapters(chapterToDelete)
        }
    }

    fun saveCurrentChapterReadingProgress() {
        getCurrentChapter()?.let { saveReadingProgress(it) }
    }

    /**
     * Called when reader chapter is changed in reader or when activity is paused.
     */
    private fun saveReadingProgress(readerChapter: ReaderChapter) {
        presenterScope.launchNonCancellable {
            saveChapterProgress(readerChapter)
            saveChapterHistory(readerChapter)
        }
    }

    /**
     * Saves this [readerChapter] progress (last read page and whether it's read).
     * If incognito mode isn't on or has at least 1 tracker
     */
    private suspend fun saveChapterProgress(readerChapter: ReaderChapter) {
        if (!incognitoMode || hasTrackers) {
            val chapter = readerChapter.chapter
            updateChapter.await(
                ChapterUpdate(
                    id = chapter.id!!,
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    lastPageRead = chapter.last_page_read.toLong(),
                ),
            )
        }
    }

    /**
     * Saves this [readerChapter] last read history if incognito mode isn't on.
     */
    private suspend fun saveChapterHistory(readerChapter: ReaderChapter) {
        if (!incognitoMode) {
            val chapterId = readerChapter.chapter.id!!
            val readAt = Date()
            val sessionReadDuration = chapterReadStartTime?.let { readAt.time - it } ?: 0

            upsertHistory.await(
                HistoryUpdate(chapterId, readAt, sessionReadDuration),
            ).also {
                chapterReadStartTime = null
            }
        }
    }

    fun setReadStartTime() {
        chapterReadStartTime = Date().time
    }

    /**
     * Called from the activity to preload the given [chapter].
     */
    fun preloadChapter(chapter: ReaderChapter) {
        preload(chapter)
    }

    /**
     * Called from the activity to load and set the next chapter as active.
     */
    fun loadNextChapter() {
        val nextChapter = viewerChaptersRelay.value?.nextChapter ?: return
        loadAdjacent(nextChapter)
    }

    /**
     * Called from the activity to load and set the previous chapter as active.
     */
    fun loadPreviousChapter() {
        val prevChapter = viewerChaptersRelay.value?.prevChapter ?: return
        loadAdjacent(prevChapter)
    }

    /**
     * Returns the currently active chapter.
     */
    fun getCurrentChapter(): ReaderChapter? {
        return viewerChaptersRelay.value?.currChapter
    }

    fun getSource() = manga?.source?.let { sourceManager.getOrStub(it) } as? HttpSource

    fun getChapterUrl(): String? {
        val sChapter = getCurrentChapter()?.chapter ?: return null
        val source = getSource() ?: return null

        return source.getChapterUrl(sChapter)
    }

    /**
     * Bookmarks the currently active chapter.
     */
    fun bookmarkCurrentChapter(bookmarked: Boolean) {
        val chapter = getCurrentChapter()?.chapter ?: return
        chapter.bookmark = bookmarked // Otherwise the bookmark icon doesn't update
        presenterScope.launchNonCancellable {
            updateChapter.await(
                ChapterUpdate(
                    id = chapter.id!!.toLong(),
                    bookmark = bookmarked,
                ),
            )
        }
    }

    /**
     * Returns the viewer position used by this manga or the default one.
     */
    fun getMangaReadingMode(resolveDefault: Boolean = true): Int {
        val default = readerPreferences.defaultReadingMode().get()
        val readingMode = ReadingModeType.fromPreference(manga?.readingModeType)
        return when {
            resolveDefault && readingMode == ReadingModeType.DEFAULT -> default
            else -> manga?.readingModeType ?: default
        }
    }

    /**
     * Updates the viewer position for the open manga.
     */
    fun setMangaReadingMode(readingModeType: Int) {
        val manga = manga ?: return
        manga.readingModeType = readingModeType
        runBlocking {
            setMangaViewerFlags.awaitSetMangaReadingMode(manga.id!!.toLong(), readingModeType.toLong())
        }

        Observable.timer(250, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
            .subscribeFirst({ view, _ ->
                val currChapters = viewerChaptersRelay.value
                if (currChapters != null) {
                    // Save current page
                    val currChapter = currChapters.currChapter
                    currChapter.requestedPage = currChapter.chapter.last_page_read

                    // Emit manga and chapters to the new viewer
                    view.setManga(manga)
                    view.setChapters(currChapters)
                }
            },)
    }

    /**
     * Returns the orientation type used by this manga or the default one.
     */
    fun getMangaOrientationType(resolveDefault: Boolean = true): Int {
        val default = readerPreferences.defaultOrientationType().get()
        val orientation = OrientationType.fromPreference(manga?.orientationType)
        return when {
            resolveDefault && orientation == OrientationType.DEFAULT -> default
            else -> manga?.orientationType ?: default
        }
    }

    /**
     * Updates the orientation type for the open manga.
     */
    fun setMangaOrientationType(rotationType: Int) {
        val manga = manga ?: return
        manga.orientationType = rotationType
        runBlocking {
            setMangaViewerFlags.awaitSetOrientationType(manga.id!!.toLong(), rotationType.toLong())
        }

        logcat(LogPriority.INFO) { "Manga orientation is ${manga.orientationType}" }

        Observable.timer(250, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
            .subscribeFirst({ view, _ ->
                val currChapters = viewerChaptersRelay.value
                if (currChapters != null) {
                    view.setOrientation(getMangaOrientationType())
                }
            },)
    }

    /**
     * Generate a filename for the given [manga] and [page]
     */
    private fun generateFilename(
        manga: Manga,
        page: ReaderPage,
    ): String {
        val chapter = page.chapter.chapter
        val filenameSuffix = " - ${page.number}"
        return DiskUtil.buildValidFilename(
            "${manga.title} - ${chapter.name}".takeBytes(MAX_FILE_NAME_BYTES - filenameSuffix.byteSize()),
        ) + filenameSuffix
    }

    /**
     * Saves the image of this [page] on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage(page: ReaderPage) {
        if (page.status != Page.READY) return
        val manga = manga ?: return

        val context = Injekt.get<Application>()
        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        val filename = generateFilename(manga, page)

        // Pictures directory.
        val relativePath = if (readerPreferences.folderPerManga().get()) DiskUtil.buildValidFilename(manga.title) else ""

        // Copy file in background.
        try {
            presenterScope.launchNonCancellable {
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = page.stream!!,
                        name = filename,
                        location = Location.Pictures.create(relativePath),
                    ),
                )
                withUIContext {
                    notifier.onComplete(uri)
                    view!!.onSaveImageResult(SaveImageResult.Success(uri))
                }
            }
        } catch (e: Throwable) {
            notifier.onError(e.message)
            view!!.onSaveImageResult(SaveImageResult.Error(e))
        }
    }

    /**
     * Shares the image of this [page] and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file and it has to be decompressed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(page: ReaderPage) {
        if (page.status != Page.READY) return
        val manga = manga ?: return

        val context = Injekt.get<Application>()
        val destDir = context.cacheImageDir

        val filename = generateFilename(manga, page)

        try {
            presenterScope.launchNonCancellable {
                destDir.deleteRecursively()
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = page.stream!!,
                        name = filename,
                        location = Location.Cache,
                    ),
                )
                withUIContext {
                    view!!.onShareImageResult(uri, page)
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
        }
    }

    /**
     * Sets the image of this [page] as cover and notifies the UI of the result.
     */
    fun setAsCover(context: Context, page: ReaderPage) {
        if (page.status != Page.READY) return
        val manga = manga?.toDomainManga() ?: return
        val stream = page.stream ?: return

        presenterScope.launchNonCancellable {
            try {
                manga.editCover(context, stream())
                withUIContext {
                    view?.onSetAsCoverResult(
                        if (manga.isLocal() || manga.favorite) {
                            SetAsCoverResult.Success
                        } else {
                            SetAsCoverResult.AddToLibraryFirst
                        },
                    )
                }
            } catch (e: Exception) {
                withUIContext { view?.onSetAsCoverResult(SetAsCoverResult.Error) }
            }
        }
    }

    /**
     * Results of the set as cover feature.
     */
    enum class SetAsCoverResult {
        Success, AddToLibraryFirst, Error
    }

    /**
     * Results of the save image feature.
     */
    sealed class SaveImageResult {
        class Success(val uri: Uri) : SaveImageResult()
        class Error(val error: Throwable) : SaveImageResult()
    }

    /**
     * Starts the service that updates the last chapter read in sync services. This operation
     * will run in a background thread and errors are ignored.
     */
    private fun updateTrackChapterRead(readerChapter: ReaderChapter) {
        if (!trackPreferences.autoUpdateTrack().get()) return
        val manga = manga ?: return

        val chapterRead = readerChapter.chapter.chapter_number.toDouble()

        val trackManager = Injekt.get<TrackManager>()
        val context = Injekt.get<Application>()

        presenterScope.launchNonCancellable {
            getTracks.await(manga.id!!)
                .mapNotNull { track ->
                    val service = trackManager.getService(track.syncId)
                    if (service != null && service.isLogged && chapterRead > track.lastChapterRead) {
                        val updatedTrack = track.copy(lastChapterRead = chapterRead)

                        // We want these to execute even if the presenter is destroyed and leaks
                        // for a while. The view can still be garbage collected.
                        async {
                            runCatching {
                                try {
                                    if (!context.isOnline()) error("Couldn't update tracker as device is offline")
                                    service.update(updatedTrack.toDbTrack(), true)
                                    insertTrack.await(updatedTrack)
                                } catch (e: Exception) {
                                    delayedTrackingStore.addItem(updatedTrack)
                                    DelayedTrackingUpdateJob.setupTask(context)
                                    throw e
                                }
                            }
                        }
                    } else {
                        null
                    }
                }
                .awaitAll()
                .mapNotNull { it.exceptionOrNull() }
                .forEach { logcat(LogPriority.INFO, it) }
        }
    }

    /**
     * Enqueues this [chapter] to be deleted when [deletePendingChapters] is called. The download
     * manager handles persisting it across process deaths.
     */
    private fun enqueueDeleteReadChapters(chapter: ReaderChapter) {
        if (!chapter.chapter.read) return
        val manga = manga ?: return

        presenterScope.launchNonCancellable {
            downloadManager.enqueueDeleteChapters(listOf(chapter.chapter.toDomainChapter()!!), manga.toDomainManga()!!)
        }
    }

    /**
     * Deletes all the pending chapters. This operation will run in a background thread and errors
     * are ignored.
     */
    private fun deletePendingChapters() {
        presenterScope.launchNonCancellable {
            downloadManager.deletePendingChapters()
        }
    }

    /**
     * Subscribes an observable with [deliverFirst] and adds it to the presenter's lifecycle
     * subscription list.
     *
     * @param onNext function to execute when the observable emits an item.
     * @param onError function to execute when the observable throws an error.
     */
    private fun <T> Observable<T>.subscribeFirst(onNext: (ReaderActivity, T) -> Unit, onError: ((ReaderActivity, Throwable) -> Unit) = { _, _ -> }) = compose(deliverFirst<T>()).subscribe(split(onNext, onError)).apply { add(this) }

    companion object {
        // Safe theoretical max filename size is 255 bytes and 1 char = 2-4 bytes (UTF-8)
        private const val MAX_FILE_NAME_BYTES = 250
    }
}
