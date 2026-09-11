package mihon.desktop.reader

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.extension.DesktopExtensionInstaller
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.WindowsExtensionProcessManager
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.reader.session.ReaderLoadState
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class InstalledSourceReaderSmokeTest {
    @Test
    fun `real installed extension opens and decodes first middle and last pages`(
        @TempDir temp: Path,
    ): Unit = runBlocking {
        val exe = System.getenv("MIHON_PACKAGED_EXE")?.let(::File)
        val packageFile = System.getenv("MIHON_EXTENSION_SMOKE_PACKAGE")?.let(::File)
        val builtin = System.getenv("MIHON_SMOKE_BUILTIN") == "mangadex"
        assumeTrue(exe?.isFile == true && (builtin || packageFile?.isFile == true), "Real package smoke test is opt-in")
        val preferences = DesktopPreferenceStore(temp.resolve("preferences.properties"))
        val installer = DesktopExtensionInstaller(temp.resolve("extensions").toFile(), preferences)
        val installed = if (builtin) {
            null
        } else {
            installer.installFromLocalFile(
                requireNotNull(packageFile),
                trustOnInstall = true,
            )
        }
        val process = WindowsExtensionProcessManager(
            temp.resolve("host").toFile(),
            customCommand = listOf(requireNotNull(exe).absolutePath, "--extension-host", "--stdio"),
        )
        val sources = DesktopSourceManager(installer, process, preferences)
        val repository = DesktopLibraryDatabaseFactory.open(temp.resolve("library.db"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val factory = DesktopReaderFactory(
            applicationScope = scope,
            library = repository,
            settings = DesktopReaderSettingsStore(preferences),
            onlineChapters = repository,
            sourceManager = sources,
            networkHelper = DesktopNetworkHelper(),
            onlineCacheDir = temp.resolve("reader-cache").toFile(),
        )
        val session = factory.createSession()
        try {
            val source = if (builtin) {
                requireNotNull(
                    sources.findSourceDescriptor(
                        mihon.desktop.extension.builtin.BundledMangaDexSource.MANGADEX_SOURCE_ID,
                    ),
                )
            } else {
                requireNotNull(installed).manifest.sources.firstOrNull { it.lang == "en" }
                    ?: installed.manifest.sources.first()
            }
            val query = System.getenv("MIHON_EXTENSION_SMOKE_QUERY").orEmpty()
            val manga = (
                if (query.isBlank()) {
                    sources.getPopular(
                        source.id,
                        1,
                    )
                } else {
                    sources.searchManga(source.id, 1, query)
                }
                ).mangas.first()
            val details = sources.getMangaDetails(source.id, manga)
            val chapters = sources.getChapterList(source.id, details)
            val chapter = chapters.firstOrNull { !it.name.contains("\uD83D\uDD12") } ?: chapters.last()
            val mangaId = repository.insertManga(
                MangaRecord(sourceId = source.id, url = details.url, title = details.title),
            )
            val chapterId = repository.insertChapter(
                ChapterRecord(mangaId = mangaId, url = chapter.url, name = chapter.name),
            )
            session.open(chapterId)
            val state = withTimeout(120_000) {
                session.state.first { it.loadState is ReaderLoadState.Ready || it.loadState is ReaderLoadState.Failed }
            }
            (state.loadState is ReaderLoadState.Ready) shouldBe true
            for (index in listOf(0, state.pages.size / 2, state.pages.lastIndex).distinct()) {
                val frame = withTimeout(120_000) { factory.loadFrame(state.pages[index].id, 0) }
                (frame.metadata.width > 0 && frame.metadata.height > 0) shouldBe true
                (frame.tile.image.width > 0) shouldBe true
                println(
                    "REAL_READER ${source.name} (${source.lang}) page=${index + 1}/${state.pages.size} decoded=${frame.metadata.width}x${frame.metadata.height}",
                )
            }
        } finally {
            session.closeAndFlush()
            factory.shutdown()
            factory.closeServices()
            sources.close()
            repository.close()
            scope.cancel()
        }
    }
}
