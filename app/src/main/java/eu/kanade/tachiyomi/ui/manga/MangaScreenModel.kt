package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.core.prefs.CheckboxState
import eu.kanade.core.prefs.mapAsCheckboxState
import eu.kanade.data.chapter.NoChaptersException
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.interactor.SetMangaCategories
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.chapter.interactor.SetMangaDefaultChapterFlags
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.chapter.interactor.UpdateChapter
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.download.service.DownloadPreferences
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.manga.interactor.GetDuplicateLibraryManga
import eu.kanade.domain.manga.interactor.GetMangaWithChapters
import eu.kanade.domain.manga.interactor.SetMangaChapterFlags
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.TriStateFilter
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.domain.track.interactor.GetTracks
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.ChapterDownloadAction
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.util.chapter.getChapterSort
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchNonCancellable
import eu.kanade.tachiyomi.util.lang.toRelativeString
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.preference.asHotFlow
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Date

class MangaInfoScreenModel(
    val context: Context,
    val mangaId: Long,
    private val isFromSource: Boolean,
    basePreferences: BasePreferences = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val getMangaAndChapters: GetMangaWithChapters = Injekt.get(),
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val setMangaChapterFlags: SetMangaChapterFlags = Injekt.get(),
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<MangaScreenState>(MangaScreenState.Loading) {

    private val successState: MangaScreenState.Success?
        get() = state.value as? MangaScreenState.Success

    private val loggedServices by lazy { trackManager.services.filter { it.isLogged } }

    val manga: Manga?
        get() = successState?.manga

    val source: Source?
        get() = successState?.source

    private val isFavoritedManga: Boolean
        get() = manga?.favorite ?: false

    private val processedChapters: Sequence<ChapterItem>?
        get() = successState?.processedChapters

    private val selectedPositions: Array<Int> = arrayOf(-1, -1) // first and last selected index in list

    /**
     * Helper function to update the UI state only if it's currently in success state
     */
    private fun updateSuccessState(func: (MangaScreenState.Success) -> MangaScreenState.Success) {
        mutableState.update { if (it is MangaScreenState.Success) func(it) else it }
    }

    private var incognitoMode = false
        set(value) {
            updateSuccessState { it.copy(isIncognitoMode = value) }
            field = value
        }
    private var downloadedOnlyMode = false
        set(value) {
            updateSuccessState { it.copy(isDownloadedOnlyMode = value) }
            field = value
        }

    init {
        val toChapterItemsParams: List<Chapter>.(manga: Manga) -> List<ChapterItem> = { manga ->
            val uiPreferences = Injekt.get<UiPreferences>()
            toChapterItems(
                context = context,
                manga = manga,
                dateRelativeTime = uiPreferences.relativeTime().get(),
                dateFormat = UiPreferences.dateFormat(uiPreferences.dateFormat().get()),
            )
        }

        coroutineScope.launchIO {
            combine(
                getMangaAndChapters.subscribe(mangaId).distinctUntilChanged(),
                downloadCache.changes,
            ) { mangaAndChapters, _ -> mangaAndChapters }
                .collectLatest { (manga, chapters) ->
                    val chapterItems = chapters.toChapterItemsParams(manga)
                    updateSuccessState {
                        it.copy(
                            manga = manga,
                            chapters = chapterItems,
                        )
                    }
                }
        }

        observeDownloads()

        coroutineScope.launchIO {
            val manga = getMangaAndChapters.awaitManga(mangaId)
            val chapters = getMangaAndChapters.awaitChapters(mangaId)
                .toChapterItemsParams(manga)

            if (!manga.favorite) {
                setMangaDefaultChapterFlags.await(manga)
            }

            val needRefreshInfo = !manga.initialized
            val needRefreshChapter = chapters.isEmpty()

            // Show what we have earlier
            mutableState.update {
                MangaScreenState.Success(
                    manga = manga,
                    source = Injekt.get<SourceManager>().getOrStub(manga.source),
                    isFromSource = isFromSource,
                    chapters = chapters,
                    isRefreshingData = needRefreshInfo || needRefreshChapter,
                    isIncognitoMode = incognitoMode,
                    isDownloadedOnlyMode = downloadedOnlyMode,
                    dialog = null,
                )
            }

            // Start observe tracking since it only needs mangaId
            observeTrackers()

            // Fetch info-chapters when needed
            if (coroutineScope.isActive) {
                val fetchFromSourceTasks = listOf(
                    async { if (needRefreshInfo) fetchMangaFromSource() },
                    async { if (needRefreshChapter) fetchChaptersFromSource() },
                )
                fetchFromSourceTasks.awaitAll()
            }

            // Initial loading finished
            updateSuccessState { it.copy(isRefreshingData = false) }
        }

        basePreferences.incognitoMode()
            .asHotFlow { incognitoMode = it }
            .launchIn(coroutineScope)

        basePreferences.downloadedOnly()
            .asHotFlow { downloadedOnlyMode = it }
            .launchIn(coroutineScope)
    }

    fun fetchAllFromSource(manualFetch: Boolean = true) {
        coroutineScope.launch {
            updateSuccessState { it.copy(isRefreshingData = true) }
            val fetchFromSourceTasks = listOf(
                async { fetchMangaFromSource(manualFetch) },
                async { fetchChaptersFromSource(manualFetch) },
            )
            fetchFromSourceTasks.awaitAll()
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    // Manga info - start

    /**
     * Fetch manga information from source.
     */
    private suspend fun fetchMangaFromSource(manualFetch: Boolean = false) {
        withIOContext {
            try {
                successState?.let {
                    val networkManga = it.source.getMangaDetails(it.manga.toSManga())
                    updateManga.awaitUpdateFromSource(it.manga, networkManga, manualFetch)
                }
            } catch (e: Throwable) {
                withUIContext {
                    // Ignore early hints "errors" that aren't handled by OkHttp
                    if (e !is HttpException || e.code != 103) {
                        snackbarHostState.showSnackbar(message = "${e.message}")
                        logcat(LogPriority.ERROR, e)
                    }
                }
            }
        }
    }

    fun toggleFavorite() {
        toggleFavorite(
            onRemoved = {
                coroutineScope.launch {
                    if (!hasDownloads()) return@launch
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.delete_downloads_for_manga),
                        actionLabel = context.getString(R.string.action_delete),
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        deleteDownloads()
                    }
                }
            },
        )
    }

    /**
     * Update favorite status of manga, (removes / adds) manga (to / from) library.
     */
    fun toggleFavorite(
        onRemoved: () -> Unit,
        checkDuplicate: Boolean = true,
    ) {
        val state = successState ?: return
        coroutineScope.launchIO {
            val manga = state.manga

            if (isFavoritedManga) {
                // Remove from library
                if (updateManga.awaitUpdateFavorite(manga.id, false)) {
                    // Remove covers and update last modified in db
                    if (manga.toDbManga().removeCovers() > 0) {
                        updateManga.awaitUpdateCoverLastModified(manga.id)
                    }
                    withUIContext { onRemoved() }
                }
            } else {
                // Add to library
                // First, check if duplicate exists if callback is provided
                if (checkDuplicate) {
                    val duplicate = getDuplicateLibraryManga.await(manga.title, manga.source)

                    if (duplicate != null) {
                        mutableState.update { state ->
                            when (state) {
                                MangaScreenState.Loading -> state
                                is MangaScreenState.Success -> state.copy(dialog = Dialog.DuplicateManga(manga, duplicate))
                            }
                        }
                        return@launchIO
                    }
                }

                // Now check if user previously set categories, when available
                val categories = getCategories()
                val defaultCategoryId = libraryPreferences.defaultCategory().get().toLong()
                val defaultCategory = categories.find { it.id == defaultCategoryId }
                when {
                    // Default category set
                    defaultCategory != null -> {
                        val result = updateManga.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launchIO
                        moveMangaToCategory(defaultCategory)
                    }

                    // Automatic 'Default' or no categories
                    defaultCategoryId == 0L || categories.isEmpty() -> {
                        val result = updateManga.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launchIO
                        moveMangaToCategory(null)
                    }

                    // Choose a category
                    else -> promptChangeCategories()
                }

                // Finally match with enhanced tracking when available
                val source = state.source
                state.trackItems
                    .map { it.service }
                    .filterIsInstance<EnhancedTrackService>()
                    .filter { it.accept(source) }
                    .forEach { service ->
                        launchIO {
                            try {
                                service.match(manga.toDbManga())?.let { track ->
                                    (service as TrackService).registerTracking(track, mangaId)
                                }
                            } catch (e: Exception) {
                                logcat(LogPriority.WARN, e) {
                                    "Could not match manga: ${manga.title} with service $service"
                                }
                            }
                        }
                    }
            }
        }
    }

    fun promptChangeCategories() {
        val state = successState ?: return
        val manga = state.manga
        coroutineScope.launch {
            val categories = getCategories()
            val selection = getMangaCategoryIds(manga)
            mutableState.update { state ->
                when (state) {
                    MangaScreenState.Loading -> state
                    is MangaScreenState.Success -> state.copy(
                        dialog = Dialog.ChangeCategory(
                            manga = manga,
                            initialSelection = categories.mapAsCheckboxState { it.id in selection },
                        ),
                    )
                }
            }
        }
    }

    /**
     * Returns true if the manga has any downloads.
     */
    fun hasDownloads(): Boolean {
        val manga = successState?.manga ?: return false
        return downloadManager.getDownloadCount(manga) > 0
    }

    /**
     * Deletes all the downloads for the manga.
     */
    private fun deleteDownloads() {
        val state = successState ?: return
        downloadManager.deleteManga(state.manga, state.source)
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    /**
     * Gets the category id's the manga is in, if the manga is not in a category, returns the default id.
     *
     * @param manga the manga to get categories from.
     * @return Array of category ids the manga is in, if none returns default id
     */
    private suspend fun getMangaCategoryIds(manga: Manga): List<Long> {
        return getCategories.await(manga.id)
            .map { it.id }
    }

    fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        moveMangaToCategory(categories)
        if (!manga.favorite) {
            coroutineScope.launchIO {
                updateManga.awaitUpdateFavorite(manga.id, true)
            }
        }
    }

    /**
     * Move the given manga to categories.
     *
     * @param categories the selected categories.
     */
    private fun moveMangaToCategories(categories: List<Category>) {
        val categoryIds = categories.map { it.id }
        moveMangaToCategory(categoryIds)
    }

    private fun moveMangaToCategory(categoryIds: List<Long>) {
        coroutineScope.launchIO {
            setMangaCategories.await(mangaId, categoryIds)
        }
    }

    /**
     * Move the given manga to the category.
     *
     * @param category the selected category, or null for default category.
     */
    private fun moveMangaToCategory(category: Category?) {
        moveMangaToCategories(listOfNotNull(category))
    }

    // Manga info - end

    // Chapters list - start

    private fun observeDownloads() {
        coroutineScope.launchIO {
            downloadManager.queue.statusFlow()
                .filter { it.manga.id == successState?.manga?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }

        coroutineScope.launchIO {
            downloadManager.queue.progressFlow()
                .filter { it.manga.id == successState?.manga?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }
    }

    private fun updateDownloadState(download: Download) {
        updateSuccessState { successState ->
            val modifiedIndex = successState.chapters.indexOfFirst { it.chapter.id == download.chapter.id }
            if (modifiedIndex < 0) return@updateSuccessState successState

            val newChapters = successState.chapters.toMutableList().apply {
                val item = removeAt(modifiedIndex)
                    .copy(downloadState = download.status, downloadProgress = download.progress)
                add(modifiedIndex, item)
            }
            successState.copy(chapters = newChapters)
        }
    }

    private fun List<Chapter>.toChapterItems(
        context: Context,
        manga: Manga,
        dateRelativeTime: Int,
        dateFormat: DateFormat,
    ): List<ChapterItem> {
        return map { chapter ->
            val activeDownload = downloadManager.queue.find { chapter.id == it.chapter.id }
            val downloaded = downloadManager.isChapterDownloaded(chapter.name, chapter.scanlator, manga.title, manga.source)
            val downloadState = when {
                activeDownload != null -> activeDownload.status
                downloaded -> Download.State.DOWNLOADED
                else -> Download.State.NOT_DOWNLOADED
            }
            ChapterItem(
                chapter = chapter,
                downloadState = downloadState,
                downloadProgress = activeDownload?.progress ?: 0,
                chapterTitleString = if (manga.displayMode == Manga.CHAPTER_DISPLAY_NUMBER) {
                    context.getString(
                        R.string.display_mode_chapter,
                        chapterDecimalFormat.format(chapter.chapterNumber.toDouble()),
                    )
                } else {
                    chapter.name
                },
                dateUploadString = chapter.dateUpload
                    .takeIf { it > 0 }
                    ?.let {
                        Date(it).toRelativeString(
                            context,
                            dateRelativeTime,
                            dateFormat,
                        )
                    },
                readProgressString = chapter.lastPageRead.takeIf { !chapter.read && it > 0 }?.let {
                    context.getString(
                        R.string.chapter_progress,
                        it + 1,
                    )
                },
            )
        }
    }

    /**
     * Requests an updated list of chapters from the source.
     */
    private suspend fun fetchChaptersFromSource(manualFetch: Boolean = false) {
        withIOContext {
            try {
                successState?.let { successState ->
                    val chapters = successState.source.getChapterList(successState.manga.toSManga())

                    val newChapters = syncChaptersWithSource.await(
                        chapters,
                        successState.manga,
                        successState.source,
                    )

                    if (manualFetch) {
                        downloadNewChapters(newChapters)
                    }
                }
            } catch (e: Throwable) {
                withUIContext {
                    if (e is NoChaptersException) {
                        snackbarHostState.showSnackbar(message = context.getString(R.string.no_chapters_error))
                    } else {
                        snackbarHostState.showSnackbar(message = "${e.message}")
                        logcat(LogPriority.ERROR, e)
                    }
                }
            }
        }
    }

    /**
     * Returns the next unread chapter or null if everything is read.
     */
    fun getNextUnreadChapter(): Chapter? {
        val successState = successState ?: return null
        return successState.chapters.getNextUnread(successState.manga)
    }

    fun getUnreadChapters(): List<Chapter> {
        return successState?.processedChapters
            ?.filter { (chapter, dlStatus) -> !chapter.read && dlStatus == Download.State.NOT_DOWNLOADED }
            ?.map { it.chapter }
            ?.toList()
            ?: emptyList()
    }

    fun getUnreadChaptersSorted(): List<Chapter> {
        val manga = successState?.manga ?: return emptyList()
        val chapters = getUnreadChapters().sortedWith(getChapterSort(manga))
        return if (manga.sortDescending()) chapters.reversed() else chapters
    }

    fun startDownload(
        chapters: List<Chapter>,
        startNow: Boolean,
    ) {
        if (startNow) {
            val chapterId = chapters.singleOrNull()?.id ?: return
            downloadManager.startDownloadNow(chapterId)
        } else {
            downloadChapters(chapters)
        }
        if (!isFavoritedManga) {
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.snack_add_to_library),
                    actionLabel = context.getString(R.string.action_add),
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed && !isFavoritedManga) {
                    toggleFavorite()
                }
            }
        }
    }

    fun runChapterDownloadActions(
        items: List<ChapterItem>,
        action: ChapterDownloadAction,
    ) {
        when (action) {
            ChapterDownloadAction.START -> {
                startDownload(items.map { it.chapter }, false)
                if (items.any { it.downloadState == Download.State.ERROR }) {
                    DownloadService.start(context)
                }
            }
            ChapterDownloadAction.START_NOW -> {
                val chapter = items.singleOrNull()?.chapter ?: return
                startDownload(listOf(chapter), true)
            }
            ChapterDownloadAction.CANCEL -> {
                val chapterId = items.singleOrNull()?.chapter?.id ?: return
                cancelDownload(chapterId)
            }
            ChapterDownloadAction.DELETE -> {
                deleteChapters(items.map { it.chapter })
            }
        }
    }

    fun runDownloadAction(action: DownloadAction) {
        val chaptersToDownload = when (action) {
            DownloadAction.NEXT_1_CHAPTER -> getUnreadChaptersSorted().take(1)
            DownloadAction.NEXT_5_CHAPTERS -> getUnreadChaptersSorted().take(5)
            DownloadAction.NEXT_10_CHAPTERS -> getUnreadChaptersSorted().take(10)
            DownloadAction.CUSTOM -> {
                showDownloadCustomDialog()
                return
            }
            DownloadAction.UNREAD_CHAPTERS -> getUnreadChapters()
            DownloadAction.ALL_CHAPTERS -> successState?.chapters?.map { it.chapter }
        }
        if (!chaptersToDownload.isNullOrEmpty()) {
            startDownload(chaptersToDownload, false)
        }
    }

    fun cancelDownload(chapterId: Long) {
        val activeDownload = downloadManager.queue.find { chapterId == it.chapter.id } ?: return
        downloadManager.deletePendingDownload(activeDownload)
        updateDownloadState(activeDownload.apply { status = Download.State.NOT_DOWNLOADED })
    }

    fun markPreviousChapterRead(pointer: Chapter) {
        val successState = successState ?: return
        val chapters = processedChapters.orEmpty().map { it.chapter }.toList()
        val prevChapters = if (successState.manga.sortDescending()) chapters.asReversed() else chapters
        val pointerPos = prevChapters.indexOf(pointer)
        if (pointerPos != -1) markChaptersRead(prevChapters.take(pointerPos), true)
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param chapters the list of selected chapters.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChaptersRead(chapters: List<Chapter>, read: Boolean) {
        coroutineScope.launchIO {
            setReadStatus.await(
                read = read,
                chapters = chapters.toTypedArray(),
            )
        }
        toggleAllSelection(false)
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param chapters the list of chapters to download.
     */
    private fun downloadChapters(chapters: List<Chapter>) {
        val manga = successState?.manga ?: return
        downloadManager.downloadChapters(manga, chapters.map { it.toDbChapter() })
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of chapters.
     * @param chapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(chapters: List<Chapter>, bookmarked: Boolean) {
        coroutineScope.launchIO {
            chapters
                .filterNot { it.bookmark == bookmarked }
                .map { ChapterUpdate(id = it.id, bookmark = bookmarked) }
                .let { updateChapter.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    /**
     * Deletes the given list of chapter.
     *
     * @param chapters the list of chapters to delete.
     */
    fun deleteChapters(chapters: List<Chapter>) {
        coroutineScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    downloadManager.deleteChapters(
                        chapters.map { it.toDbChapter() },
                        state.manga,
                        state.source,
                    )
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private fun downloadNewChapters(chapters: List<Chapter>) {
        coroutineScope.launchNonCancellable {
            val manga = successState?.manga ?: return@launchNonCancellable
            val categories = getCategories.await(manga.id).map { it.id }
            if (chapters.isEmpty() || !manga.shouldDownloadNewChapters(categories, downloadPreferences)) return@launchNonCancellable
            downloadChapters(chapters)
        }
    }

    /**
     * Sets the read filter and requests an UI update.
     * @param state whether to display only unread chapters or all chapters.
     */
    fun setUnreadFilter(state: TriStateFilter) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriStateFilter.DISABLED -> Manga.SHOW_ALL
            TriStateFilter.ENABLED_IS -> Manga.CHAPTER_SHOW_UNREAD
            TriStateFilter.ENABLED_NOT -> Manga.CHAPTER_SHOW_READ
        }
        coroutineScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetUnreadFilter(manga, flag)
        }
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param state whether to display only downloaded chapters or all chapters.
     */
    fun setDownloadedFilter(state: TriStateFilter) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriStateFilter.DISABLED -> Manga.SHOW_ALL
            TriStateFilter.ENABLED_IS -> Manga.CHAPTER_SHOW_DOWNLOADED
            TriStateFilter.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_DOWNLOADED
        }

        coroutineScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDownloadedFilter(manga, flag)
        }
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param state whether to display only bookmarked chapters or all chapters.
     */
    fun setBookmarkedFilter(state: TriStateFilter) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriStateFilter.DISABLED -> Manga.SHOW_ALL
            TriStateFilter.ENABLED_IS -> Manga.CHAPTER_SHOW_BOOKMARKED
            TriStateFilter.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
        }

        coroutineScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetBookmarkFilter(manga, flag)
        }
    }

    /**
     * Sets the active display mode.
     * @param mode the mode to set.
     */
    fun setDisplayMode(mode: Long) {
        val manga = successState?.manga ?: return

        coroutineScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDisplayMode(manga, mode)
        }
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Long) {
        val manga = successState?.manga ?: return

        coroutineScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetSortingModeOrFlipOrder(manga, sort)
        }
    }

    fun setCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val manga = successState?.manga ?: return
        coroutineScope.launchNonCancellable {
            libraryPreferences.setChapterSettingsDefault(manga)
            if (applyToExisting) {
                setMangaDefaultChapterFlags.awaitAll()
            }
            snackbarHostState.showSnackbar(message = context.getString(R.string.chapter_settings_updated))
        }
    }

    fun toggleSelection(
        item: ChapterItem,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        updateSuccessState { successState ->
            val newChapters = successState.processedChapters.toMutableList().apply {
                val modifiedIndex = successState.processedChapters.indexOfFirst { it == item }
                if (modifiedIndex < 0) return@apply

                val oldItem = get(modifiedIndex)
                if ((oldItem.selected && selected) || (!oldItem.selected && !selected)) return@apply

                val firstSelection = none { it.selected }
                var newItem = removeAt(modifiedIndex)
                add(modifiedIndex, newItem.copy(selected = selected))

                if (selected && userSelected && fromLongPress) {
                    if (firstSelection) {
                        selectedPositions[0] = modifiedIndex
                        selectedPositions[1] = modifiedIndex
                    } else {
                        // Try to select the items in-between when possible
                        val range: IntRange
                        if (modifiedIndex < selectedPositions[0]) {
                            range = modifiedIndex + 1 until selectedPositions[0]
                            selectedPositions[0] = modifiedIndex
                        } else if (modifiedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1) until modifiedIndex
                            selectedPositions[1] = modifiedIndex
                        } else {
                            // Just select itself
                            range = IntRange.EMPTY
                        }

                        range.forEach {
                            newItem = removeAt(it)
                            add(it, newItem.copy(selected = true))
                        }
                    }
                } else if (userSelected && !fromLongPress) {
                    if (!selected) {
                        if (modifiedIndex == selectedPositions[0]) {
                            selectedPositions[0] = indexOfFirst { it.selected }
                        } else if (modifiedIndex == selectedPositions[1]) {
                            selectedPositions[1] = indexOfLast { it.selected }
                        }
                    } else {
                        if (modifiedIndex < selectedPositions[0]) {
                            selectedPositions[0] = modifiedIndex
                        } else if (modifiedIndex > selectedPositions[1]) {
                            selectedPositions[1] = modifiedIndex
                        }
                    }
                }
            }
            successState.copy(chapters = newChapters)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        updateSuccessState { successState ->
            val newChapters = successState.chapters.map {
                it.copy(selected = selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(chapters = newChapters)
        }
    }

    fun invertSelection() {
        updateSuccessState { successState ->
            val newChapters = successState.chapters.map {
                it.copy(selected = !it.selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(chapters = newChapters)
        }
    }

    // Chapters list - end

    // Track sheet - start

    private fun observeTrackers() {
        val manga = successState?.manga ?: return

        coroutineScope.launchIO {
            getTracks.subscribe(manga.id)
                .catch { logcat(LogPriority.ERROR, it) }
                .map { tracks ->
                    val dbTracks = tracks.map { it.toDbTrack() }
                    loggedServices
                        // Map to TrackItem
                        .map { service -> TrackItem(dbTracks.find { it.sync_id.toLong() == service.id }, service) }
                        // Show only if the service supports this manga's source
                        .filter { (it.service as? EnhancedTrackService)?.accept(source!!) ?: true }
                }
                .distinctUntilChanged()
                .collectLatest { trackItems ->
                    updateSuccessState { it.copy(trackItems = trackItems) }
                }
        }
    }

    // Track sheet - end

    fun getSourceOrStub(manga: Manga): Source {
        return sourceManager.getOrStub(manga.source)
    }

    sealed class Dialog {
        data class ChangeCategory(val manga: Manga, val initialSelection: List<CheckboxState<Category>>) : Dialog()
        data class DeleteChapters(val chapters: List<Chapter>) : Dialog()
        data class DuplicateManga(val manga: Manga, val duplicate: Manga) : Dialog()
        data class DownloadCustomAmount(val max: Int) : Dialog()
        object SettingsSheet : Dialog()
        object TrackSheet : Dialog()
        object FullCover : Dialog()
    }

    fun dismissDialog() {
        mutableState.update { state ->
            when (state) {
                MangaScreenState.Loading -> state
                is MangaScreenState.Success -> state.copy(dialog = null)
            }
        }
    }

    private fun showDownloadCustomDialog() {
        val max = processedChapters?.count() ?: return
        mutableState.update { state ->
            when (state) {
                MangaScreenState.Loading -> state
                is MangaScreenState.Success -> state.copy(dialog = Dialog.DownloadCustomAmount(max))
            }
        }
    }

    fun showDeleteChapterDialog(chapters: List<Chapter>) {
        mutableState.update { state ->
            when (state) {
                MangaScreenState.Loading -> state
                is MangaScreenState.Success -> state.copy(dialog = Dialog.DeleteChapters(chapters))
            }
        }
    }

    fun showSettingsDialog() {
        mutableState.update { state ->
            when (state) {
                MangaScreenState.Loading -> state
                is MangaScreenState.Success -> state.copy(dialog = Dialog.SettingsSheet)
            }
        }
    }

    fun showTrackDialog() {
        mutableState.update { state ->
            when (state) {
                MangaScreenState.Loading -> state
                is MangaScreenState.Success -> {
                    state.copy(dialog = Dialog.TrackSheet)
                }
            }
        }
    }

    fun showCoverDialog() {
        mutableState.update { state ->
            when (state) {
                MangaScreenState.Loading -> state
                is MangaScreenState.Success -> {
                    state.copy(dialog = Dialog.FullCover)
                }
            }
        }
    }
}

sealed class MangaScreenState {
    @Immutable
    object Loading : MangaScreenState()

    @Immutable
    data class Success(
        val manga: Manga,
        val source: Source,
        val isFromSource: Boolean,
        val chapters: List<ChapterItem>,
        val trackItems: List<TrackItem> = emptyList(),
        val isRefreshingData: Boolean = false,
        val isIncognitoMode: Boolean = false,
        val isDownloadedOnlyMode: Boolean = false,
        val dialog: MangaInfoScreenModel.Dialog? = null,
    ) : MangaScreenState() {

        val processedChapters: Sequence<ChapterItem>
            get() = chapters.applyFilters(manga)

        val trackingAvailable: Boolean
            get() = trackItems.isNotEmpty()

        val trackingCount: Int
            get() = trackItems.count { it.track != null }

        /**
         * Applies the view filters to the list of chapters obtained from the database.
         * @return an observable of the list of chapters filtered and sorted.
         */
        private fun List<ChapterItem>.applyFilters(manga: Manga): Sequence<ChapterItem> {
            val isLocalManga = manga.isLocal()
            val unreadFilter = manga.unreadFilter
            val downloadedFilter = manga.downloadedFilter
            val bookmarkedFilter = manga.bookmarkedFilter
            return asSequence()
                .filter { (chapter) ->
                    when (unreadFilter) {
                        TriStateFilter.DISABLED -> true
                        TriStateFilter.ENABLED_IS -> !chapter.read
                        TriStateFilter.ENABLED_NOT -> chapter.read
                    }
                }
                .filter { (chapter) ->
                    when (bookmarkedFilter) {
                        TriStateFilter.DISABLED -> true
                        TriStateFilter.ENABLED_IS -> chapter.bookmark
                        TriStateFilter.ENABLED_NOT -> !chapter.bookmark
                    }
                }
                .filter {
                    when (downloadedFilter) {
                        TriStateFilter.DISABLED -> true
                        TriStateFilter.ENABLED_IS -> it.isDownloaded || isLocalManga
                        TriStateFilter.ENABLED_NOT -> !it.isDownloaded && !isLocalManga
                    }
                }
                .sortedWith { (chapter1), (chapter2) -> getChapterSort(manga).invoke(chapter1, chapter2) }
        }
    }
}

@Immutable
data class ChapterItem(
    val chapter: Chapter,
    val downloadState: Download.State,
    val downloadProgress: Int,

    val chapterTitleString: String,
    val dateUploadString: String?,
    val readProgressString: String?,

    val selected: Boolean = false,
) {
    val isDownloaded = downloadState == Download.State.DOWNLOADED
}

val chapterDecimalFormat = DecimalFormat(
    "#.###",
    DecimalFormatSymbols()
        .apply { decimalSeparator = '.' },
)
