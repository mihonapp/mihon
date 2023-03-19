package eu.kanade.tachiyomi.data.library

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.chapter.interactor.SyncChaptersWithTrackServiceTwoWay
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.copyFrom
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.util.prepUpdateCover
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.preference.getAndSet
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.GetChapterByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.NoChaptersException
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_BATTERY_NOT_LOW
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_HAS_UNREAD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_READ
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import tachiyomi.domain.source.model.SourceNotInstalledException
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LibraryUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val sourceManager: SourceManager = Injekt.get()
    private val downloadPreferences: DownloadPreferences = Injekt.get()
    private val libraryPreferences: LibraryPreferences = Injekt.get()
    private val downloadManager: DownloadManager = Injekt.get()
    private val trackManager: TrackManager = Injekt.get()
    private val coverCache: CoverCache = Injekt.get()
    private val getLibraryManga: GetLibraryManga = Injekt.get()
    private val getManga: GetManga = Injekt.get()
    private val updateManga: UpdateManga = Injekt.get()
    private val getChapterByMangaId: GetChapterByMangaId = Injekt.get()
    private val getCategories: GetCategories = Injekt.get()
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get()
    private val getTracks: GetTracks = Injekt.get()
    private val insertTrack: InsertTrack = Injekt.get()
    private val syncChaptersWithTrackServiceTwoWay: SyncChaptersWithTrackServiceTwoWay = Injekt.get()

    private val notifier = LibraryUpdateNotifier(context)

    private var mangaToUpdate: List<LibraryManga> = mutableListOf()

    override suspend fun doWork(): Result {
        if (tags.contains(WORK_NAME_AUTO)) {
            val preferences = Injekt.get<LibraryPreferences>()
            val restrictions = preferences.libraryUpdateDeviceRestriction().get()
            if ((DEVICE_ONLY_ON_WIFI in restrictions) && !context.isConnectedToWifi()) {
                return Result.failure()
            }

            // Find a running manual worker. If exists, try again later
            if (context.workManager.isRunning(WORK_NAME_MANUAL)) {
                return Result.retry()
            }
        }

        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            logcat(LogPriority.ERROR, e) { "Not allowed to set foreground job" }
        }

        val target = inputData.getString(KEY_TARGET)?.let { Target.valueOf(it) } ?: Target.CHAPTERS

        // If this is a chapter update, set the last update time to now
        if (target == Target.CHAPTERS) {
            libraryPreferences.libraryUpdateLastTimestamp().set(Date().time)
        }

        val categoryId = inputData.getLong(KEY_CATEGORY, -1L)
        addMangaToQueue(categoryId)

        return withIOContext {
            try {
                when (target) {
                    Target.CHAPTERS -> updateChapterList()
                    Target.COVERS -> updateCovers()
                    Target.TRACKING -> updateTrackings()
                }
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Assume success although cancelled
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e)
                    Result.failure()
                }
            } finally {
                notifier.cancelProgressNotification()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notifier = LibraryUpdateNotifier(context)
        return ForegroundInfo(
            Notifications.ID_LIBRARY_PROGRESS,
            notifier.progressNotificationBuilder.build(),
        )
    }

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
        val restrictions = libraryPreferences.libraryUpdateMangaRestriction().get()

        coroutineScope {
            mangaToUpdate.groupBy { it.manga.source }.values
                .map { mangaInSource ->
                    async {
                        semaphore.withPermit {
                            mangaInSource.forEach { libraryManga ->
                                val manga = libraryManga.manga
                                ensureActive()

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
                                            skippedUpdates.add(manga to context.getString(R.string.skipped_reason_completed))

                                        MANGA_HAS_UNREAD in restrictions && libraryManga.unreadCount != 0L ->
                                            skippedUpdates.add(manga to context.getString(R.string.skipped_reason_not_caught_up))

                                        MANGA_NON_READ in restrictions && libraryManga.totalChapters > 0L && !libraryManga.hasStarted ->
                                            skippedUpdates.add(manga to context.getString(R.string.skipped_reason_not_started))

                                        manga.updateStrategy != UpdateStrategy.ALWAYS_UPDATE ->
                                            skippedUpdates.add(manga to context.getString(R.string.skipped_reason_not_always_update))

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

                                                    libraryPreferences.newUpdatesCount().getAndSet { it + newChapters.size }

                                                    // Convert to the manga that contains new chapters
                                                    newUpdates.add(manga to newChapters.toTypedArray())
                                                }
                                            } catch (e: Throwable) {
                                                val errorMessage = when (e) {
                                                    is NoChaptersException -> context.getString(R.string.no_chapters_error)
                                                    // failedUpdates will already have the source, don't need to copy it into the message
                                                    is SourceNotInstalledException -> context.getString(R.string.loader_not_implemented_error)
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
            if (hasDownloads.get()) {
                downloadManager.startDownloads()
            }
        }

        if (failedUpdates.isNotEmpty()) {
            val errorFile = writeErrorFile(failedUpdates)
            notifier.showUpdateErrorNotification(
                failedUpdates.size,
                errorFile.getUriCompat(context),
            )
        }
        if (skippedUpdates.isNotEmpty()) {
            notifier.showUpdateSkippedNotification(skippedUpdates.size)
        }
    }

    private fun downloadChapters(manga: Manga, chapters: List<Chapter>) {
        // We don't want to start downloading while the library is updating, because websites
        // may don't like it and they could ban the user.
        downloadManager.downloadChapters(manga, chapters, false)
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

        coroutineScope {
            mangaToUpdate.groupBy { it.manga.source }
                .values
                .map { mangaInSource ->
                    async {
                        semaphore.withPermit {
                            mangaInSource.forEach { libraryManga ->
                                val manga = libraryManga.manga
                                ensureActive()

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
        coroutineScope {
            var progressCount = 0
            val loggedServices = trackManager.services.filter { it.isLogged }

            mangaToUpdate.forEach { libraryManga ->
                val manga = libraryManga.manga

                ensureActive()

                notifier.showProgressNotification(listOf(manga), progressCount++, mangaToUpdate.size)

                // Update the tracking details.
                updateTrackings(manga, loggedServices)
            }

            notifier.cancelProgressNotification()
        }
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
        coroutineScope {
            ensureActive()

            updatingManga.add(manga)
            notifier.showProgressNotification(
                updatingManga,
                completed.get(),
                mangaToUpdate.size,
            )

            block()

            ensureActive()

            updatingManga.remove(manga)
            completed.getAndIncrement()
            notifier.showProgressNotification(
                updatingManga,
                completed.get(),
                mangaToUpdate.size,
            )
        }
    }

    /**
     * Writes basic file of update errors to cache dir.
     */
    private fun writeErrorFile(errors: List<Pair<Manga, String?>>): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("tachiyomi_update_errors.txt")
                file.bufferedWriter().use { out ->
                    out.write(context.getString(R.string.library_errors_help, ERROR_LOG_HELP_URL) + "\n\n")
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

    /**
     * Defines what should be updated within a service execution.
     */
    enum class Target {
        CHAPTERS, // Manga chapters
        COVERS, // Manga covers
        TRACKING, // Tracking metadata
    }

    companion object {
        private const val TAG = "LibraryUpdate"
        private const val WORK_NAME_AUTO = "LibraryUpdate-auto"
        private const val WORK_NAME_MANUAL = "LibraryUpdate-manual"

        private const val ERROR_LOG_HELP_URL = "https://tachiyomi.org/help/guides/troubleshooting"

        private const val MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60

        /**
         * Key for category to update.
         */
        private const val KEY_CATEGORY = "category"

        /**
         * Key that defines what should be updated.
         */
        private const val KEY_TARGET = "target"

        fun cancelAllWorks(context: Context) {
            context.workManager.cancelAllWorkByTag(TAG)
        }

        fun setupTask(
            context: Context,
            prefInterval: Int? = null,
        ) {
            val preferences = Injekt.get<LibraryPreferences>()
            val interval = prefInterval ?: preferences.libraryUpdateInterval().get()
            if (interval > 0) {
                val restrictions = preferences.libraryUpdateDeviceRestriction().get()
                val constraints = Constraints(
                    requiredNetworkType = if (DEVICE_NETWORK_NOT_METERED in restrictions) { NetworkType.UNMETERED } else { NetworkType.CONNECTED },
                    requiresCharging = DEVICE_CHARGING in restrictions,
                    requiresBatteryNotLow = DEVICE_BATTERY_NOT_LOW in restrictions,
                )

                val request = PeriodicWorkRequestBuilder<LibraryUpdateJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .addTag(WORK_NAME_AUTO)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(WORK_NAME_AUTO, ExistingPeriodicWorkPolicy.UPDATE, request)
            } else {
                context.workManager.cancelUniqueWork(WORK_NAME_AUTO)
            }
        }

        fun startNow(
            context: Context,
            category: Category? = null,
            target: Target = Target.CHAPTERS,
        ): Boolean {
            val wm = context.workManager
            if (wm.isRunning(TAG)) {
                // Already running either as a scheduled or manual job
                return false
            }

            val inputData = workDataOf(
                KEY_CATEGORY to category?.id,
                KEY_TARGET to target.name,
            )
            val request = OneTimeWorkRequestBuilder<LibraryUpdateJob>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .setInputData(inputData)
                .build()
            wm.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)

            return true
        }

        fun stop(context: Context) {
            val wm = context.workManager
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            wm.getWorkInfos(workQuery).get()
                // Should only return one work but just in case
                .forEach {
                    wm.cancelWorkById(it.id)

                    // Re-enqueue cancelled scheduled work
                    if (it.tags.contains(WORK_NAME_AUTO)) {
                        setupTask(context)
                    }
                }
        }
    }
}
