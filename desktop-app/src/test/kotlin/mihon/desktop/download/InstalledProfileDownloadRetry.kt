package mihon.desktop.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.extension.DesktopCookieStore
import mihon.desktop.extension.DesktopExtensionInstaller
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.extension.DesktopNetworkSettingsStore
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.WindowsExtensionProcessManager
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.platform.DesktopProfileLock
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.reader.DesktopReaderFactory
import mihon.desktop.reader.DesktopReaderSettingsStore
import java.nio.file.Files
import java.nio.file.Path

/** Explicit manual acceptance helper: retries one real queue entry with the installed app jars. */
object InstalledProfileDownloadRetry {
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        require(args.size == 3) { "Expected profile directory, installed executable, chapter ID" }
        val profile = Path.of(args[0]).toAbsolutePath().normalize()
        val executable = Path.of(args[1]).toRealPath()
        val chapterId = args[2].toLong()
        val loadedFrom = Path.of(DesktopDownloader::class.java.protectionDomain.codeSource.location.toURI())
        check(loadedFrom.startsWith(executable.parent.resolve("app"))) { "Must use installed app classes" }
        println("INSTALLED_RETRY classes=$loadedFrom executable=$executable chapter=$chapterId")
        DesktopProfileLock.acquire(profile).use {
            DesktopLibraryDatabaseFactory.open(profile.resolve("database/library.db")).use { library ->
                val preferences = DesktopPreferenceStore(profile.resolve("preferences.properties"))
                val policy = DesktopNetworkSettingsStore(preferences)
                DesktopNetworkHelper(
                    cookieStore = DesktopCookieStore(profile.resolve("cookies.json")),
                    policyProvider = policy::load,
                ).use { network ->
                    val process = WindowsExtensionProcessManager(
                        profile.resolve("extension-host").toFile(),
                        customCommand = listOf(executable.toString(), "--extension-host", "--stdio"),
                        sourceSessionFile = profile.resolve("cookies.json").toFile(),
                        networkHelper = network,
                        onBrokerHttp = network::executeBrokeredRequest,
                    )
                    val installer = DesktopExtensionInstaller(profile.resolve("extensions").toFile(), preferences)
                    DesktopSourceManager(installer, process, preferences).use { sources ->
                        val defaultRoot = profile.resolve("media/downloads")
                        val configured = preferences.load().downloadStoragePath.trim()
                        val root = if (configured.isEmpty()) defaultRoot else profile.resolve(configured).normalize()
                        val disk = DownloadDiskProvider(root, legacyDownloadsDirs = listOf(defaultRoot))
                        DesktopDownloader(
                            DownloadStore(profile.resolve("downloads.json")),
                            disk,
                            network,
                            sourceManager = sources,
                            mutationPort = library,
                            pageParallelism = { preferences.load().downloadPageParallelCount },
                        ).use { downloader ->
                            val original = downloader.queueState.value.single { it.chapterId == chapterId }
                            check(original.status in setOf(DownloadStatus.ERROR, DownloadStatus.COMPLETED))
                            if (original.status != DownloadStatus.COMPLETED) downloader.retry(chapterId)
                            val result = withTimeout(300_000) {
                                var lastCount = -1
                                while (true) {
                                    val item = downloader.queueState.value.single { it.chapterId == chapterId }
                                    if (item.downloadedImages != lastCount) {
                                        lastCount = item.downloadedImages
                                        println("INSTALLED_RETRY progress=$lastCount/${item.pages.size}")
                                    }
                                    if (item.status in setOf(DownloadStatus.COMPLETED, DownloadStatus.ERROR)) {
                                        check(item.status == DownloadStatus.COMPLETED) { item.error.orEmpty() }
                                        break
                                    }
                                    delay(200)
                                }
                                while (downloader.isRunning.value) delay(50)
                                downloader.queueState.value.single { it.chapterId == chapterId }
                            }
                            val asset = requireNotNull(library.chapterAsset(chapterId))
                            check(Files.isDirectory(asset.storageRoot.resolve(asset.relativePath)))
                            check(disk.isChapterDownloaded(result.sourceId, result.mangaTitle, result.chapterName))
                            println(
                                "INSTALLED_RETRY status=${result.status} ready=${result.downloadedImages}" +
                                    " bytes=${result.bytesDownloaded} error=${result.error}",
                            )
                            // No source/network is supplied: all reader pages must come from the registered local asset.
                            val reader = DesktopReaderFactory(
                                CoroutineScope(SupervisorJob() + Dispatchers.Default),
                                library,
                                DesktopReaderSettingsStore(preferences),
                                onlineChapters = null,
                            )
                            try {
                                val session = reader.createSession(isIncognito = true)
                                withTimeout(30_000) { session.open(chapterId) }
                                val pages = session.state.value.pages
                                check(pages.size == result.pages.size)
                                for (index in listOf(0, pages.lastIndex / 2, pages.lastIndex).distinct()) {
                                    val frame = reader.loadFrame(pages[index].id, 0)
                                    try {
                                        check(frame.metadata.width > 0 && frame.metadata.height > 0)
                                        println("OFFLINE_READER page=${index + 1} decoded=true")
                                    } finally {
                                        frame.tile.close()
                                    }
                                }
                            } finally {
                                reader.shutdown()
                                reader.closeServices()
                            }
                        }
                    }
                }
            }
        }
    }
}
