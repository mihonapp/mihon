package mihon.desktop.download

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.extension.DesktopExtensionInstaller
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.WindowsExtensionProcessManager
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Opt-in reproduction using a copy of a saved queue; the original profile is never written. */
class InstalledDownloadRecoverySmokeTest {
    @Test
    fun `installed extension retries saved images without first browsing its source`(@TempDir temp: Path) {
        assumeTrue(System.getenv("MIHON_DOWNLOAD_SMOKE_PROFILE") != null)
        runSmoke(temp)
    }

    companion object {
        /** Can run with installed app jars first on the classpath to verify the shipped downloader. */
        @JvmStatic
        fun main(args: Array<String>) {
            runSmoke(Path.of(args.single()).toAbsolutePath().normalize())
        }

        private fun runSmoke(temp: Path): Unit = runBlocking {
            Files.createDirectories(temp)
            val profile = Path.of(requireNotNull(System.getenv("MIHON_DOWNLOAD_SMOKE_PROFILE")))
            val sourceId = requireNotNull(System.getenv("MIHON_DOWNLOAD_SMOKE_SOURCE")).toLong()
            val executable = File(requireNotNull(System.getenv("MIHON_PACKAGED_EXE")))
            check(executable.isFile)
            Files.copy(profile.resolve("preferences.properties"), temp.resolve("preferences.properties"))
            Files.copy(profile.resolve("downloads.json"), temp.resolve("original-queue.json"))
            val original = DownloadStore(temp.resolve("original-queue.json")).restore()
                .first { it.sourceId == sourceId && it.pages.isNotEmpty() }
            val saved = original.copy(
                status = DownloadStatus.ERROR,
                progress = 0f,
                bytesDownloaded = 0,
                error = "No isolated host registered for source $sourceId",
                pages = original.pages.map { it.copy(status = PageStatus.ERROR, progress = 0f) },
            )
            val preferences = DesktopPreferenceStore(temp.resolve("preferences.properties"))
            val installer = DesktopExtensionInstaller(temp.resolve("extensions").toFile(), preferences)
            val owner = installer.getInstalledExtensions().single { ext ->
                ext.manifest.sources.any { it.id == sourceId }
            }
            println("DOWNLOAD_SMOKE owner=${owner.pkg} enabled=${owner.isEnabled} pages=${saved.pages.size}")
            val network = DesktopNetworkHelper()
            val process = WindowsExtensionProcessManager(
                temp.resolve("host").toFile(),
                customCommand = listOf(executable.absolutePath, "--extension-host", "--stdio"),
                networkHelper = network,
                onBrokerHttp = network::executeBrokeredRequest,
            )
            val sources = DesktopSourceManager(installer, process, preferences)
            val store = DownloadStore(temp.resolve("queue.json"))
            store.save(listOf(saved))
            val disk = DownloadDiskProvider(temp.resolve("downloads"))
            val downloader = DesktopDownloader(store, disk, network, sourceManager = sources)
            try {
                downloader.retry(saved.chapterId)
                val result = withTimeout(300_000) {
                    while (downloader.queueState.value.single().status !in
                        setOf(DownloadStatus.COMPLETED, DownloadStatus.ERROR)
                    ) {
                        delay(100)
                    }
                    downloader.queueState.value.single()
                }
                println(
                    "DOWNLOAD_SMOKE status=${result.status} ready=${result.downloadedImages}" +
                        " bytes=${result.bytesDownloaded} error=${result.error}",
                )
                check(result.status == DownloadStatus.COMPLETED) { result.error.orEmpty() }
                check(result.downloadedImages == saved.pages.size)
                check(disk.isChapterDownloaded(sourceId, saved.mangaTitle, saved.chapterName))
            } finally {
                downloader.close()
                sources.close()
                network.close()
            }
        }
    }
}
