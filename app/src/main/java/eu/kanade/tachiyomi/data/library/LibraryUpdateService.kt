package eu.kanade.tachiyomi.data.library

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import eu.kanade.data.chapter.NoChaptersException
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.chapter.interactor.SyncChaptersWithTrackServiceTwoWay
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.download.service.DownloadPreferences
import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.manga.interactor.GetLibraryManga
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.toMangaUpdate
import eu.kanade.domain.track.interactor.GetTracks
import eu.kanade.domain.track.interactor.InsertTrack
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.library.LibraryUpdateService.Companion.start
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.MANGA_HAS_UNREAD
import eu.kanade.tachiyomi.data.preference.MANGA_NON_COMPLETED
import eu.kanade.tachiyomi.data.preference.MANGA_NON_READ
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.prepUpdateCover
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.acquireWakeLock
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.getSerializableExtraCompat
import eu.kanade.tachiyomi.util.system.isServiceRunning
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * This class will take care of updating the chapters of the manga from the library. It can be
 * started calling the [start] method. If it's already running, it won't do anything.
 * While the library is updating, a [PowerManager.WakeLock] will be held until the update is
 * completed, preventing the device from going to sleep mode. A notification will display the
 * progress of the update, and if case of an unexpected error, this service will be silently
 * destroyed.
 */
class LibraryUpdateService(
    val sourceManager: SourceManager = Injekt.get(),
    val downloadPreferences: DownloadPreferences = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
    val downloadManager: DownloadManager = Injekt.get(),
    val trackManager: TrackManager = Injekt.get(),
    val coverCache: CoverCache = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getChapterByMangaId: GetChapterByMangaId = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    private val syncChaptersWithTrackServiceTwoWay: SyncChaptersWithTrackServiceTwoWay = Injekt.get(),
) : Service() {

    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var notifier: LibraryUpdateNotifier
    private var ioScope: CoroutineScope? = null

    private var mangaToUpdate: List<LibraryManga> = mutableListOf()
    private var updateJob: Job? = null

    /**
     * Defines what should be updated within a service execution.
     */
    enum class Target {
        CHAPTERS, // Manga chapters
        COVERS, // Manga covers
        TRACKING, // Tracking metadata
    }

    companion object {

        private var instance: LibraryUpdateService? = null

        /**
         * Key for category to update.
         */
        const val KEY_CATEGORY = "category"

        /**
         * Key that defines what should be updated.
         */
        const val KEY_TARGET = "target"

        /**
         * Returns the status of the service.
         *
         * @param context the application context.
         * @return true if the service is running, false otherwise.
         */
        fun isRunning(context: Context): Boolean {
            return context.isServiceRunning(LibraryUpdateService::class.java)
        }

        /**
         * Starts the service. It will be started only if there isn't another instance already
         * running.
         *
         * @param context the application context.
         * @param category a specific category to update, or null for global update.
         * @param target defines what should be updated.
         * @return true if service newly started, false otherwise
         */
        fun start(context: Context, category: Category? = null, target: Target = Target.CHAPTERS): Boolean {
            if (isRunning(context)) return false

            val intent = Intent(context, LibraryUpdateService::class.java).apply {
                putExtra(KEY_TARGET, target)
                category?.let { putExtra(KEY_CATEGORY, it.id) }
            }
            ContextCompat.startForegroundService(context, intent)

            return true
        }

        /**
         * Stops the service.
         *
         * @param context the application context.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, LibraryUpdateService::class.java))
        }
    }

    /**
     * Method called when the service is created. It injects dagger dependencies and acquire
     * the wake lock.
     */
    override fun onCreate() {
        super.onCreate()

        notifier = LibraryUpdateNotifier(this)
        wakeLock = acquireWakeLock(javaClass.name)

        startForeground(Notifications.ID_LIBRARY_PROGRESS, notifier.progressNotificationBuilder.build())
    }

    /**
     * Method called when the service is destroyed. It destroys subscriptions and releases the wake
     * lock.
     */
    override fun onDestroy() {
        updateJob?.cancel()
        ioScope?.cancel()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }

    /**
     * This method needs to be implemented, but it's not used/needed.
     */
    override fun onBind(intent: Intent): IBinder? = null

    /**
     * Method called when the service receives an intent.
     *
     * @param intent the start intent from.
     * @param flags the flags of the command.
     * @param startId the start id of this command.
     * @return the start value of the command.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        val target = intent.getSerializableExtraCompat<Target>(KEY_TARGET)
            ?: return START_NOT_STICKY

        instance = this

        // Unsubscribe from any previous subscription if needed
        updateJob?.cancel()
        ioScope?.cancel()

        // If this is a chapter update; set the last update time to now
        if (target == Target.CHAPTERS) {
            libraryPreferences.libraryUpdateLastTimestamp().set(Date().time)
        }

        // Update favorite manga
        val categoryId = intent.getLongExtra(KEY_CATEGORY, -1L)
        addMangaToQueue(categoryId)

        // Destroy service when completed or in case of an error.
        val handler = CoroutineExceptionHandler { _, exception ->
            logcat(LogPriority.ERROR, exception)
            stopSelf(startId)
        }
        ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        updateJob = ioScope?.launch(handler) {
            when (target) {
                Target.CHAPTERS -> updateChapterList()
                Target.COVERS -> updateCovers()
                Target.TRACKING -> updateTrackings()
            }
        }
        updateJob?.invokeOnCompletion { stopSelf(startId) }

        return START_REDELIVER_INTENT
    }

    private val isUpdateJobActive: Boolean
        get() = (updateJob?.isActive == true)

    /**
     * Adds list of manga to be updated.
     *
     * @param categoryId the ID of the category to update, or -1 if no category specified.
     */
    private fun addMangaToQueue(categoryId: Long) {
        val libraryManga = runBlocking { getLibraryManga.await() }

        val listToUpdate = if (categoryId != -1L) {
            libraryManga.filter { it.category == categoryId }
        } else {
            val categoriesToUpdate = libraryPreferences.libraryUpdateCategories().get().map { it.toLong() }
            val includedManga = if (categoriesToUpdate.isNotEmpty()) {
                libraryManga.filter { it.category in categoriesToUpdate }
            } else {
                libraryManga
            }

            val categoriesToExclude = libraryPreferences.libraryUpdateCategoriesExclude().get().map { it.toLong() }
            val excludedMangaIds = if (categoriesToExclude.isNotEmpty()) {
                libraryManga.filter { it.category in categoriesToExclude }.map { it.manga.id }
            } else {
                emptyList()
            }

            includedManga
                .filterNot { it.manga.id in excludedMangaIds }
                .distinctBy { it.manga.id }
        }

        mangaToUpdate = listToUpdate
            .sortedBy { it.manga.title }

        // Warn when excessively checking a single source
        val maxUpdatesFromSource = mangaToUpdate
            .groupBy { it.manga.source }
            .filterKeys { sourceManager.get(it) !is UnmeteredSource }
            .maxOfOrNull { it.value.size } ?: 0
        if (maxUpdatesFromSource > MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD) {
            notifier.showQueueSizeWarningNotification()
        }
    }

    /**
     * Method that updates manga in [mangaToUpdate]. It's called in a background thread, so it's safe
     * to do heavy operations or network calls here.
     * For each manga it calls [updateManga] and updates the notification showing the current
     * progress.
     *
     * @return an observable delivering the progress of each update.
     */
    private suspend fun updateChapterList() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInteger(0)
        val currentlyUpdatingManga = CopyOnWriteArrayList<Manga>()
        val newUpdates = CopyOnWriteArrayList<Pair<Manga, Array<Chapter>>>()
        val skippedUpdates = CopyOnWriteArrayList<Pair<Manga, String?>>()
        val failedUpdates = CopyOnWriteArrayList<Pair<Manga, String?>>()
        val hasDownloads = AtomicBoolean(false)
        val loggedServices by lazy { trackManager.services.filter { it.isLogged } }
        val currentUnreadUpdatesCount = libraryPreferences.unreadUpdatesCount().get()
        val restrictions = libraryPreferences.libraryUpdateMangaRestriction().get()

        withIOContext {
            mangaToUpdate.groupBy { it.manga.source }.values
                .map { mangaInSource ->
                    async {
                        semaphore.withPermit {
                            mangaInSource.forEach { libraryManga ->
                                val manga = libraryManga.manga
                                if (!isUpdateJobActive) {
                                    notifier.cancelProgressNotification()
                                    return@async
                                }

                                // Don't continue to update if manga is not in library
                                if (getManga.await(manga.id)?.favorite != true) {
                                    return@forEach
                                }

                                withUpdateNotification(
                                    currentlyUpdatingManga,
                                    progressCount,
                                    manga,
                                ) {
                                    when {
                                        MANGA_NON_COMPLETED in restrictions && manga.status.toInt() == SManga.COMPLETED ->
                                            skippedUpdates.add(manga to getString(R.string.skipped_reason_completed))

                                        MANGA_HAS_UNREAD in restrictions && libraryManga.unreadCount != 0L ->
                                            skippedUpdates.add(manga to getString(R.string.skipped_reason_not_caught_up))

                                        MANGA_NON_READ in restrictions && libraryManga.totalChapters > 0L && !libraryManga.hasStarted ->
                                            skippedUpdates.add(manga to getString(R.string.skipped_reason_not_started))

                                        manga.updateStrategy != UpdateStrategy.ALWAYS_UPDATE ->
                                            skippedUpdates.add(manga to getString(R.string.skipped_reason_not_always_update))

                                        else -> {
                                            try {
                                                val newChapters = updateManga(manga)
                                                    .sortedByDescending { it.sourceOrder }

                                                if (newChapters.isNotEmpty()) {
                                                    val categoryIds = getCategories.await(manga.id).map { it.id }
                                                    if (manga.shouldDownloadNewChapters(categoryIds, downloadPreferences)) {
                                                        downloadChapters(manga, newChapters)
                                                        hasDownloads.set(true)
                                                    }

                                                    // Convert to the manga that contains new chapters
                                                    newUpdates.add(manga to newChapters.toTypedArray())
                                                }
                                            } catch (e: Throwable) {
                                                val errorMessage = when (e) {
                                                    is NoChaptersException -> getString(R.string.no_chapters_error)
                                                    // failedUpdates will already have the source, don't need to copy it into the message
                                                    is SourceManager.SourceNotInstalledException -> getString(R.string.loader_not_implemented_error)
                                                    else -> e.message
                                                }
                                                failedUpdates.add(manga to errorMessage)
                                            }
                                        }
                                    }

                                    if (libraryPreferences.autoUpdateTrackers().get()) {
                                        updateTrackings(manga, loggedServices)
                                    }
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()

        if (newUpdates.isNotEmpty()) {
            notifier.showUpdateNotifications(newUpdates)
            val newChapterCount = newUpdates.sumOf { it.second.size }
            libraryPreferences.unreadUpdatesCount().set(currentUnreadUpdatesCount + newChapterCount)
            if (hasDownloads.get()) {
                DownloadService.start(this)
            }
        }

        if (failedUpdates.isNotEmpty()) {
            val errorFile = writeErrorFile(failedUpdates)
            notifier.showUpdateErrorNotification(
                failedUpdates.size,
                errorFile.getUriCompat(this),
            )
        }
        if (skippedUpdates.isNotEmpty()) {
            notifier.showUpdateSkippedNotification(skippedUpdates.size)
        }
    }

    private fun downloadChapters(manga: Manga, chapters: List<Chapter>) {
        // We don't want to start downloading while the library is updating, because websites
        // may don't like it and they could ban the user.
        downloadManager.downloadChapters(manga, chapters.map { it.toDbChapter() }, false)
    }

    /**
     * Updates the chapters for the given manga and adds them to the database.
     *
     * @param manga the manga to update.
     * @return a pair of the inserted and removed chapters.
     */
    private suspend fun updateManga(manga: Manga): List<Chapter> {
        val source = sourceManager.getOrStub(manga.source)

        // Update manga metadata if needed
        if (libraryPreferences.autoUpdateMetadata().get()) {
            val networkManga = source.getMangaDetails(manga.toSManga())
            updateManga.awaitUpdateFromSource(manga, networkManga, manualFetch = false, coverCache)
        }

        val chapters = source.getChapterList(manga.toSManga())

        // Get manga from database to account for if it was removed during the update and
        // to get latest data so it doesn't get overwritten later on
        val dbManga = getManga.await(manga.id)?.takeIf { it.favorite } ?: return emptyList()

        return syncChaptersWithSource.await(chapters, dbManga, source)
    }

    private suspend fun updateCovers() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInteger(0)
        val currentlyUpdatingManga = CopyOnWriteArrayList<Manga>()

        withIOContext {
            mangaToUpdate.groupBy { it.manga.source }
                .values
                .map { mangaInSource ->
                    async {
                        semaphore.withPermit {
                            mangaInSource.forEach { libraryManga ->
                                val manga = libraryManga.manga
                                if (!isUpdateJobActive) {
                                    notifier.cancelProgressNotification()
                                    return@async
                                }

                                withUpdateNotification(
                                    currentlyUpdatingManga,
                                    progressCount,
                                    manga,
                                ) {
                                    val source = sourceManager.get(manga.source) ?: return@withUpdateNotification
                                    try {
                                        val networkManga = source.getMangaDetails(manga.toSManga())
                                        val updatedManga = manga.prepUpdateCover(coverCache, networkManga, true)
                                            .copyFrom(networkManga)
                                        try {
                                            updateManga.await(updatedManga.toMangaUpdate())
                                        } catch (e: Exception) {
                                            logcat(LogPriority.ERROR) { "Manga doesn't exist anymore" }
                                        }
                                    } catch (e: Throwable) {
                                        // Ignore errors and continue
                                        logcat(LogPriority.ERROR, e)
                                    }
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()
    }

    /**
     * Method that updates the metadata of the connected tracking services. It's called in a
     * background thread, so it's safe to do heavy operations or network calls here.
     */
    private suspend fun updateTrackings() {
        var progressCount = 0
        val loggedServices = trackManager.services.filter { it.isLogged }

        mangaToUpdate.forEach { libraryManga ->
            val manga = libraryManga.manga
            if (!isUpdateJobActive) {
                notifier.cancelProgressNotification()
                return
            }

            notifier.showProgressNotification(listOf(manga), progressCount++, mangaToUpdate.size)

            // Update the tracking details.
            updateTrackings(manga, loggedServices)
        }

        notifier.cancelProgressNotification()
    }

    private suspend fun updateTrackings(manga: Manga, loggedServices: List<TrackService>) {
        getTracks.await(manga.id)
            .map { track ->
                supervisorScope {
                    async {
                        val service = trackManager.getService(track.syncId)
                        if (service != null && service in loggedServices) {
                            try {
                                val updatedTrack = service.refresh(track.toDbTrack())
                                insertTrack.await(updatedTrack.toDomainTrack()!!)

                                if (service is EnhancedTrackService) {
                                    val chapters = getChapterByMangaId.await(manga.id)
                                    syncChaptersWithTrackServiceTwoWay.await(chapters, track, service)
                                }
                            } catch (e: Throwable) {
                                // Ignore errors and continue
                                logcat(LogPriority.ERROR, e)
                            }
                        }
                    }
                }
            }
            .awaitAll()
    }

    private suspend fun withUpdateNotification(
        updatingManga: CopyOnWriteArrayList<Manga>,
        completed: AtomicInteger,
        manga: Manga,
        block: suspend () -> Unit,
    ) {
        if (!isUpdateJobActive) {
            notifier.cancelProgressNotification()
            return
        }

        updatingManga.add(manga)
        notifier.showProgressNotification(
            updatingManga,
            completed.get(),
            mangaToUpdate.size,
        )

        block()

        if (!isUpdateJobActive) {
            notifier.cancelProgressNotification()
            return
        }

        updatingManga.remove(manga)
        completed.getAndIncrement()
        notifier.showProgressNotification(
            updatingManga,
            completed.get(),
            mangaToUpdate.size,
        )
    }

    /**
     * Writes basic file of update errors to cache dir.
     */
    private fun writeErrorFile(errors: List<Pair<Manga, String?>>): File {
        try {
            if (errors.isNotEmpty()) {
                val file = createFileInCacheDir("tachiyomi_update_errors.txt")
                file.bufferedWriter().use { out ->
                    out.write(getString(R.string.library_errors_help, ERROR_LOG_HELP_URL) + "\n\n")
                    // Error file format:
                    // ! Error
                    //   # Source
                    //     - Manga
                    errors.groupBy({ it.second }, { it.first }).forEach { (error, mangas) ->
                        out.write("\n! ${error}\n")
                        mangas.groupBy { it.source }.forEach { (srcId, mangas) ->
                            val source = sourceManager.getOrStub(srcId)
                            out.write("  # $source\n")
                            mangas.forEach {
                                out.write("    - ${it.title}\n")
                            }
                        }
                    }
                }
                return file
            }
        } catch (_: Exception) {}
        return File("")
    }
}

private const val MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60
private const val ERROR_LOG_HELP_URL = "https://tachiyomi.org/help/guides/troubleshooting"
