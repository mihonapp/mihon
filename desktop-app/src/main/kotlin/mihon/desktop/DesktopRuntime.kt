package mihon.desktop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import mihon.desktop.cli.CommandLineException
import mihon.desktop.cli.DesktopCommand
import mihon.desktop.cli.DesktopCommandParser
import mihon.desktop.download.DownloadCacheCleaner
import mihon.desktop.download.DownloadDiskProvider
import mihon.desktop.library.backup.AndroidBackupCodec
import mihon.desktop.library.backup.AndroidBackupImporter
import mihon.desktop.library.backup.AndroidBackupValidator
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.db.SqlDelightLibraryRepository
import mihon.desktop.library.local.LocalMangaImporter
import mihon.desktop.platform.AppDirectories
import mihon.desktop.platform.AppDirectoryResolver
import mihon.desktop.platform.DistributionMode
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.reader.DesktopReaderFactory
import mihon.desktop.reader.DesktopReaderSettingsStore
import java.awt.EventQueue
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DesktopRuntime(
    val directories: AppDirectories,
    val preferences: DesktopPreferenceStore,
    val command: DesktopCommand,
    val library: SqlDelightLibraryRepository,
    val backupImporter: AndroidBackupImporter,
    val localImporter: LocalMangaImporter,
    val localLibraryRoot: Path,
    val readerFactory: DesktopReaderFactory? = null,
    val downloader: mihon.desktop.download.DesktopDownloader? = null,
    val updateService: mihon.desktop.updates.DesktopLibraryUpdateService? = null,
    val notificationService: mihon.desktop.notification.DesktopNotificationService? = null,
    val historyService: mihon.desktop.history.DesktopHistoryService? = null,
    val categoryService: mihon.desktop.category.DesktopCategoryService? = null,
    val trackerStore: mihon.desktop.track.DesktopTrackerStore? = null,
    val trackerManager: mihon.desktop.track.DesktopTrackerManager? = null,
    val trackingQueue: mihon.desktop.track.OfflineTrackingQueue? = null,
    val trackSyncService: mihon.desktop.track.TrackOnReadSyncService? = null,
    val diagnosticService: mihon.desktop.diagnostics.DiagnosticBundleService? = null,
    val statsService: mihon.desktop.stats.DesktopStatsService? = null,
    val backupScheduler: mihon.desktop.backup.DesktopBackupScheduler? = null,
    val cookieStore: mihon.desktop.extension.DesktopCookieStore? = null,
    val desktopNotificationService: mihon.desktop.platform.DesktopNotificationService? = null,
    val libraryUpdateService: mihon.desktop.library.update.LibraryUpdateService? = null,
    val libraryUpdateScheduler: mihon.desktop.library.update.LibraryUpdateScheduler? = null,
    val customCoverManager: mihon.desktop.image.CustomCoverManager =
        mihon.desktop.image.CustomCoverManager(directories.covers),
    val imageLoader: mihon.desktop.image.DesktopImageLoader =
        mihon.desktop.image.DesktopImageLoader(
            diskCacheDir = directories.cache.resolve("covers"),
            customCoverManager = customCoverManager,
        ),
    val backupExporter: mihon.desktop.library.backup.AndroidBackupExporter =
        mihon.desktop.library.backup.AndroidBackupExporter(library),
    val extensionInstaller: mihon.desktop.extension.DesktopExtensionInstaller =
        mihon.desktop.extension.DesktopExtensionInstaller(
            installRoot = directories.root.resolve("extensions").toFile(),
            preferenceStore = preferences,
        ),
    val extensionStoreService: mihon.desktop.extension.ExtensionStoreService =
        mihon.desktop.extension.ExtensionStoreService(preferenceStore = preferences),
    val processManager: mihon.desktop.extension.WindowsExtensionProcessManager? = null,
    val sourceManager: mihon.desktop.extension.DesktopSourceManager =
        mihon.desktop.extension.DesktopSourceManager(
            installer = extensionInstaller,
            processManager = processManager,
        ),
    val onlineMangaSyncService: mihon.desktop.extension.OnlineMangaSyncService =
        mihon.desktop.extension.OnlineMangaSyncService(
            libraryRepository = library,
            sourceManager = sourceManager,
        ),
    val diskProvider: DownloadDiskProvider =
        downloader?.diskProvider ?: DownloadDiskProvider(directories.root.resolve("media").resolve("downloads")),
    val downloadCacheCleaner: DownloadCacheCleaner =
        DownloadCacheCleaner(repository = library, diskProvider = diskProvider),
    private val closeReaderSessions: suspend () -> Unit = readerFactory?.let { it::shutdown } ?: {},
    private val closeReaderServices: () -> Unit = readerFactory?.let { it::closeServices } ?: {},
    internal val closeLibrary: () -> Unit = library::close,
    private val blockingShutdown: ExecutorService = BLOCKING_SHUTDOWN_EXECUTOR,
) : AutoCloseable {
    private val shutdownLock = Any()
    private var shutdownResult: CompletableDeferred<Result<Unit>>? = null

    /** Stops session admission, flushes sessions, then disposes runtime services and the database. */
    suspend fun shutdown() {
        val deferred: CompletableDeferred<Result<Unit>>
        val owner: Boolean
        synchronized(shutdownLock) {
            val existing = shutdownResult
            if (existing == null) {
                deferred = CompletableDeferred()
                shutdownResult = deferred
                owner = true
            } else {
                deferred = existing
                owner = false
            }
        }
        if (!owner) return deferred.await().getOrThrow()

        var failure: Throwable? = null
        try {
            try {
                closeReaderSessions()
            } catch (error: Throwable) {
                failure = failure.append(error)
            }
            try {
                closeReaderServices()
            } catch (error: Throwable) {
                failure = failure.append(error)
            }
            try {
                closeLibrary()
            } catch (error: Throwable) {
                failure = failure.append(error)
            }
            if (failure == null) {
                deferred.complete(Result.success(Unit))
            } else {
                deferred.complete(Result.failure(failure))
            }
        } catch (error: Throwable) {
            deferred.complete(Result.failure(error))
        }
        return deferred.await().getOrThrow()
    }

    override fun close() {
        check(!EventQueue.isDispatchThread()) { "DesktopRuntime.close() must not run on the EDT; use shutdown()" }
        try {
            blockingShutdown.submit<Unit> { runBlocking { shutdown() } }.get()
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }
    }

    companion object {
        private val BLOCKING_SHUTDOWN_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "mihon-runtime-shutdown").apply { isDaemon = true }
        }

        internal fun forTesting(
            closeSessions: suspend () -> Unit = {},
            closeReaderServices: () -> Unit = {},
            closeLibrary: () -> Unit = {},
        ): DesktopRuntime {
            val root = java.nio.file.Files.createTempDirectory("mihon-runtime-test")
            val directories = AppDirectories(root).create()
            val library = DesktopLibraryDatabaseFactory.open(directories.database.resolve("library.db"))
            return DesktopRuntime(
                directories = directories,
                preferences = DesktopPreferenceStore(root.resolve("preferences.properties")),
                command = DesktopCommand.FoundationSmoke,
                library = library,
                backupImporter = AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), library),
                localImporter = LocalMangaImporter(library),
                localLibraryRoot = root.resolve("media").resolve("local"),
                closeReaderSessions = closeSessions,
                closeReaderServices = closeReaderServices,
                closeLibrary = {
                    closeLibrary()
                    library.close()
                },
            )
        }
    }
}

object DesktopRuntimeFactory {
    fun create(
        args: Array<String>,
        environment: Map<String, String>,
        executableDirectory: Path,
    ): DesktopRuntime = create(args, environment, executableDirectory, LocalMangaImporter::cleanupOrphans)

    internal fun create(
        args: Array<String>,
        environment: Map<String, String>,
        executableDirectory: Path,
        cleanupOrphans: (LocalMangaImporter, Path) -> Unit,
    ): DesktopRuntime {
        val command = DesktopCommandParser.parse(args, environment)
        val dataDirectoryArguments = args.filter { it.startsWith("--data-dir=") }
        if (dataDirectoryArguments.size > 1) {
            throw CommandLineException("--data-dir may be specified only once", "--data-dir")
        }
        val explicitRoot = dataDirectoryArguments.singleOrNull()?.let { argument ->
            val value = argument.substringAfter('=')
            if (value.isBlank()) throw CommandLineException("--data-dir requires a non-blank path", "--data-dir")
            commandPath(value, "--data-dir")
        }
        val isPortable = "--portable" in args ||
            java.nio.file.Files.exists(executableDirectory.resolve(".portable"))
        val mode = if (isPortable) DistributionMode.Portable else DistributionMode.Installed
        val appData = environment["APPDATA"]
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
        val directories = AppDirectoryResolver(appData, executableDirectory)
            .resolve(mode, explicitRoot)
            .create()
        val library = DesktopLibraryDatabaseFactory.open(directories.database.resolve("library.db"))
        try {
            val backupImporter = AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), library)
            val localImporter = LocalMangaImporter(library)
            val localLibraryRoot = directories.root.resolve("media").resolve("local").toAbsolutePath().normalize()
            cleanupOrphans(localImporter, localLibraryRoot)
            val preferences = DesktopPreferenceStore(directories.root.resolve("preferences.properties"))
            val notificationService = mihon.desktop.notification.WindowsDesktopNotificationService(
                enabledProvider = { preferences.load().desktopNotificationsEnabled },
                hideContentProvider = { preferences.load().desktopNotificationsHideContent },
            )
            val downloadsDir = directories.root.resolve("media").resolve("downloads").toAbsolutePath().normalize()
            val downloadDiskProvider = mihon.desktop.download.DownloadDiskProvider(downloadsDir)
            val downloadStore = mihon.desktop.download.DownloadStore(directories.root.resolve("downloads.json"))
            val cookieStore = mihon.desktop.extension.DesktopCookieStore(directories.root.resolve("cookies.json"))
            val networkHelper = mihon.desktop.extension.DesktopNetworkHelper(cookieStore = cookieStore)
            val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            val extensionDir = directories.root.resolve("extensions").toFile()
            val extensionHostDir = directories.root.resolve("extension-host").toFile()
            val processManager = mihon.desktop.extension.WindowsExtensionProcessManager(
                workingDirectory = extensionHostDir,
                sourceSessionFile = directories.root.resolve("cookies.json").toFile(),
                onBrokerHttp = { request -> networkHelper.executeBrokeredRequest(request) },
                scope = appScope,
            )
            val extensionInstaller = mihon.desktop.extension.DesktopExtensionInstaller(
                installRoot = extensionDir,
                preferenceStore = preferences,
            )
            extensionInstaller.getInstalledExtensions().filter { it.isEnabled }.forEach { ext ->
                networkHelper.registerExtensionDomains(ext.pkg, ext.manifest.declaredDomains)
            }

            val extensionStoreService = mihon.desktop.extension.ExtensionStoreService(preferenceStore = preferences)
            val sourceManager = mihon.desktop.extension.DesktopSourceManager(
                installer = extensionInstaller,
                processManager = processManager,
                cookieStore = cookieStore,
            )
            val onlineMangaSyncService = mihon.desktop.extension.OnlineMangaSyncService(
                libraryRepository = library,
                sourceManager = sourceManager,
            )
            val readerFactory = if (command == DesktopCommand.LaunchUi || command is DesktopCommand.VerifyReader) {
                DesktopReaderFactory(
                    applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                    library = library,
                    settings = DesktopReaderSettingsStore(preferences),
                    onlineChapters = library,
                    sourceManager = sourceManager,
                    networkHelper = networkHelper,
                    onlineCacheDir = directories.cache.resolve("online-pages").toFile(),
                )
            } else {
                null
            }

            val downloader = mihon.desktop.download.DesktopDownloader(
                store = downloadStore,
                diskProvider = downloadDiskProvider,
                networkHelper = networkHelper,
                processManager = processManager,
                sourceManager = sourceManager,
                mutationPort = library,
                downloadParallelism = { preferences.load().downloadParallelCount },
                pageParallelism = { preferences.load().downloadPageParallelCount },
                onDownloadCompleted = { download ->
                    notificationService.notifyDownloadComplete(download.mangaTitle, download.chapterName)
                },
                onDownloadFailed = { download, error ->
                    notificationService.notifyDownloadError(download.mangaTitle, download.chapterName, error)
                },
                onDownloadProgress = { download ->
                    notificationService.notifyDownloadProgress(
                        download.mangaTitle,
                        download.chapterName,
                        download.progress,
                    )
                },
            )
            val updateService = mihon.desktop.updates.DesktopLibraryUpdateService(
                repository = library,
                mutationPort = library,
                processManager = processManager,
                onUpdateCompleted = { result ->
                    if (result.newChaptersFound > 0) {
                        notificationService.notifyLibraryUpdate(result.newChaptersFound, result.mangaWithNewChapters)
                    }
                },
            )
            val historyService = mihon.desktop.history.DesktopHistoryService(
                repository = library,
                mutationPort = library,
                scope = appScope,
            )
            val categoryService = mihon.desktop.category.DesktopCategoryService(
                repository = library,
                mutationPort = library,
                scope = appScope,
            )
            val trackerStore = mihon.desktop.track.DesktopTrackerStore(preferences)
            val trackerManager = mihon.desktop.track.DesktopTrackerManager(store = trackerStore)
            val trackingQueue = mihon.desktop.track.OfflineTrackingQueue(
                queueFile = directories.root.resolve("tracking-queue.json"),
            )
            val trackSyncService = mihon.desktop.track.TrackOnReadSyncService(
                repository = library,
                mutationPort = library,
                trackerManager = trackerManager,
                trackingQueue = trackingQueue,
            )
            val diagnosticService = mihon.desktop.diagnostics.DiagnosticBundleService(
                directories = directories,
                repository = library,
            )
            val statsService = mihon.desktop.stats.DesktopStatsService(library)
            val backupDir = directories.root.resolve("backups").toAbsolutePath().normalize()
            java.nio.file.Files.createDirectories(backupDir)
            val backupExporter = mihon.desktop.library.backup.AndroidBackupExporter(library)
            val backupScheduler = mihon.desktop.backup.DesktopBackupScheduler(
                backupExporter = backupExporter,
                preferenceStore = preferences,
                defaultBackupDir = backupDir,
                scope = appScope,
            )
            val desktopNotificationService = mihon.desktop.platform.DesktopNotificationService(
                enabledProvider = { preferences.load().desktopNotificationsEnabled },
                hideContentProvider = { preferences.load().desktopNotificationsHideContent },
            )
            val libraryUpdateService = mihon.desktop.library.update.LibraryUpdateService(
                repository = library,
                syncService = onlineMangaSyncService,
                downloader = downloader,
                notificationService = desktopNotificationService,
            )
            val libraryUpdateScheduler = mihon.desktop.library.update.LibraryUpdateScheduler(
                updateService = libraryUpdateService,
                preferenceStore = preferences,
                scope = appScope,
            )
            if (command == DesktopCommand.LaunchUi) {
                backupScheduler.start()
            }
            return DesktopRuntime(
                directories = directories,
                preferences = preferences,
                command = command,
                library = library,
                backupImporter = backupImporter,
                localImporter = localImporter,
                localLibraryRoot = localLibraryRoot,
                readerFactory = readerFactory,
                downloader = downloader,
                updateService = updateService,
                notificationService = notificationService,
                historyService = historyService,
                categoryService = categoryService,
                trackerStore = trackerStore,
                trackerManager = trackerManager,
                trackingQueue = trackingQueue,
                trackSyncService = trackSyncService,
                diagnosticService = diagnosticService,
                statsService = statsService,
                backupScheduler = backupScheduler,
                cookieStore = cookieStore,
                desktopNotificationService = desktopNotificationService,
                libraryUpdateService = libraryUpdateService,
                libraryUpdateScheduler = libraryUpdateScheduler,
                extensionInstaller = extensionInstaller,
                extensionStoreService = extensionStoreService,
                processManager = processManager,
                sourceManager = sourceManager,
                onlineMangaSyncService = onlineMangaSyncService,
                closeReaderServices = {
                    backupScheduler.stop()
                    readerFactory?.closeServices()
                    sourceManager.close()
                    processManager.close()
                    appScope.cancel()
                },
            )
        } catch (error: Throwable) {
            try {
                library.close()
            } catch (closeError: Throwable) {
                error.addSuppressed(closeError)
            }
            throw error
        }
    }

    private fun commandPath(value: String, option: String): Path = try {
        Path.of(value)
    } catch (error: RuntimeException) {
        throw CommandLineException("$option contains an invalid path", option, error)
    }
}

private fun Throwable?.append(error: Throwable): Throwable = this?.also { it.addSuppressed(error) } ?: error
