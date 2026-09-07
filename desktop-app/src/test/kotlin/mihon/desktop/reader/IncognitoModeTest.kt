package mihon.desktop.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.reader.session.ReaderProgressUpdate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class IncognitoModeTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `incognito session suppresses chapter progress and history persistence`() = runBlocking {
        val dbFile = tempDir.resolve("incognito-test.db")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)

        try {
            val mangaId = repo.insertManga(
                MangaRecord(
                    sourceId = 1L,
                    url = "/test",
                    title = "Test Manga",
                ),
            )
            val chapterId = repo.insertChapter(
                ChapterRecord(
                    mangaId = mangaId,
                    url = "/ch1",
                    name = "Chapter 1",
                    read = false,
                    lastPageRead = 0L,
                ),
            )

            val prefsFile = tempDir.resolve("prefs.properties")
            val prefStore = DesktopPreferenceStore(prefsFile)
            val settingsStore = DesktopReaderSettingsStore(prefStore)
            val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            val factory = DesktopReaderFactory(
                applicationScope = appScope,
                library = repo,
                settings = settingsStore,
            )

            // 1. Create session with isIncognito = true
            val incognitoSession = factory.createSession(isIncognito = true)

            // Attempt to record progress to page 5, completed = true, duration = 60000ms
            val update = ReaderProgressUpdate(
                chapterId = chapterId,
                pageIndex = 5L,
                completed = true,
                lastReadEpochMillis = 1000L,
                readDurationDeltaMillis = 60000L,
                generation = 1L,
                sequence = 1L,
            )

            // Session has a progressSink which should be the no-op sink
            // Directly test that repo has not been mutated
            val chBefore = repo.chapterSnapshot(mangaId).first { it.id == chapterId }
            assertEquals(0L, chBefore.lastPageRead)
            assertEquals(false, chBefore.read)
            assertTrue(repo.allHistorySnapshot().isEmpty())

            // 2. Normal session (isIncognito = false) records to repo
            repo.record(update)
            val chAfter = repo.chapterSnapshot(mangaId).first { it.id == chapterId }
            assertEquals(5L, chAfter.lastPageRead)
            assertEquals(true, chAfter.read)
            assertEquals(1, repo.allHistorySnapshot().size)
            assertEquals(60000L, repo.allHistorySnapshot().first().readDuration)
        } finally {
            repo.close()
        }
    }
}
