package mihon.desktop.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.reader.session.ReaderLoadState
import java.nio.file.Path

/** Runs with installed app jars first on the classpath and a copied database, without network. */
object InstalledOfflineReaderSmoke {
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        val profileCopy = Path.of(args[0]).toAbsolutePath()
        val chapterId = args[1].toLong()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = DesktopLibraryDatabaseFactory.open(profileCopy.resolve("library.db"))
        val factory = DesktopReaderFactory(
            scope,
            repository,
            DesktopReaderSettingsStore(DesktopPreferenceStore(profileCopy.resolve("preferences.properties"))),
            onlineChapters = null,
        )
        try {
            val session = factory.createSession()
            withTimeout(30_000) { session.open(chapterId) }
            val state = session.state.value
            println("OFFLINE_READER state=${state.loadState} pages=${state.pages.size}")
            state.error?.cause?.printStackTrace()
            check(state.loadState is ReaderLoadState.Ready) { state.error.toString() }
            for (index in setOf(0, state.pages.size / 2, state.pages.lastIndex)) {
                val frame = withTimeout(30_000) { factory.loadFrame(state.pages[index].id, 0) }
                println("OFFLINE_PAGE index=$index width=${frame.metadata.width} height=${frame.metadata.height}")
                frame.tile.close()
            }
            session.cancelWithoutFlush()
        } finally {
            factory.shutdown()
            repository.close()
            scope.cancel()
        }
    }
}
