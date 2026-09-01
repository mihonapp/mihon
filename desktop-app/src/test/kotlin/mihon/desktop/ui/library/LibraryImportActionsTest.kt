package mihon.desktop.ui.library

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import mihon.desktop.library.local.LocalImportRejected
import mihon.desktop.library.model.ImportCounts
import mihon.desktop.library.model.ImportReport
import mihon.desktop.library.model.ImportReportItem
import mihon.desktop.library.model.ImportStatus
import mihon.desktop.library.model.ImportType
import mihon.desktop.library.model.PreferenceSkipReason
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path
import java.util.concurrent.Executors

class LibraryImportActionsTest {
    @Test
    fun `cancelled choosers perform no import`() = runBlocking {
        var imports = 0
        val actions = LibraryImportActions(
            chooseBackup = { null },
            chooseLocal = { null },
            importBackup = {
                imports++
                ImportActionState.Running
            },
            importLocal = {
                imports++
                ImportActionState.Running
            },
        )

        actions.chooseAndImportBackup() shouldBe ImportActionState.Idle
        actions.chooseAndImportLocal() shouldBe ImportActionState.Idle
        imports shouldBe 0
    }

    @Test
    fun `chosen paths are passed exactly once and preserve success or error state`() = runBlocking {
        val backup = Path.of("chosen.tachibk")
        val local = Path.of("chosen-manga")
        val calls = mutableListOf<Path>()
        val completed = ImportActionState.Completed(SanitizedImportResult(4, ImportCounts(), emptyList()))
        val failed = ImportActionState.Failed("Import failed. Check the selected file and try again.")
        val actions = LibraryImportActions(
            chooseBackup = { backup },
            chooseLocal = { local },
            importBackup = {
                calls.add(it)
                completed
            },
            importLocal = {
                calls.add(it)
                failed
            },
        )

        actions.chooseAndImportBackup() shouldBe completed
        actions.chooseAndImportLocal() shouldBe failed
        calls shouldBe listOf(backup, local)
    }

    @Test
    fun `successful import exposes structured counts report id and safe skip categories`() = runBlocking {
        val report = report(
            id = 91,
            counts = ImportCounts(mangaInserted = 2, mangaMerged = 1, chaptersInserted = 8, preferencesSkipped = 4),
            items = PreferenceSkipReason.entries.map { reason ->
                ImportReportItem(
                    "PREFERENCE",
                    "secret.preference",
                    "SKIPPED",
                    reason.name,
                    "secret raw value",
                )
            },
        )
        val controller = LibraryImportController(
            importBackup = { _, _ -> report },
            importLocal = { _, _, _ -> report },
            localLibraryRoot = Path.of("library"),
            ioDispatcher = Dispatchers.Default,
        )

        val state = controller.importBackup(Path.of("backup.tachibk")) as ImportActionState.Completed

        state.result.reportId shouldBe 91
        state.result.counts.mangaInserted shouldBe 2
        state.result.skipCategories.shouldContainExactly(PreferenceSkipReason.entries.map { it.name }.sorted())
        state.result.toString().contains("secret.preference") shouldBe false
        state.result.toString().contains("secret raw value") shouldBe false
    }

    @Test
    fun `unknown report reasons collapse to one safe category without retaining sensitive text`() = runBlocking {
        val sensitiveReasons = listOf(
            "secret.preference=raw-value",
            "C:\\Users\\person\\private-library",
            "SQLException: token 123 leaked",
        )
        val report = report(
            items = listOf(
                ImportReportItem("PREFERENCE", "safe", "SKIPPED", "PRIVATE", "safe category"),
            ) + sensitiveReasons.mapIndexed { index, reason ->
                ImportReportItem("PREFERENCE", "sensitive-$index", "SKIPPED", reason, "must not surface")
            },
        )
        val controller = LibraryImportController(
            importBackup = { _, _ -> report },
            importLocal = { _, _, _ -> report },
            localLibraryRoot = Path.of("library"),
            ioDispatcher = Dispatchers.Default,
        )

        val result = (controller.importBackup(Path.of("backup.tachibk")) as ImportActionState.Completed).result

        result.skipCategories.shouldContainExactly("PRIVATE", "UNKNOWN_SKIP_REASON")
        sensitiveReasons.forEach { sensitive ->
            result.toString().contains(sensitive) shouldBe false
        }
    }

    @Test
    fun `imports execute on configured non UI dispatcher`() = runBlocking {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "library-import-io") }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            var importThread = ""
            val controller = LibraryImportController(
                importBackup = { _, _ ->
                    importThread = Thread.currentThread().name
                    report()
                },
                importLocal = { _, _, _ -> report() },
                localLibraryRoot = Path.of("library"),
                ioDispatcher = dispatcher,
            )

            controller.importBackup(Path.of("backup.tachibk"))

            importThread shouldBe "library-import-io"
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `typed rejection is actionable while unexpected exception text is sanitized`() = runBlocking {
        val rejected = LibraryImportController(
            importBackup = { _, _ -> error("preference token=123") },
            importLocal = { _, _, _ -> throw LocalImportRejected("link or reparse point is not allowed") },
            localLibraryRoot = Path.of("library"),
            ioDispatcher = Dispatchers.Default,
        )

        rejected.importLocal(Path.of("manga")) shouldBe ImportActionState.Rejected("UNSAFE_LOCAL_DIRECTORY")
        val failed = rejected.importBackup(Path.of("backup.tachibk")) as ImportActionState.Failed
        failed.message shouldBe "Import failed. Check the selected file and try again."
        failed.toString().contains("token=123") shouldBe false
    }

    @Test
    fun `import cancellation is never converted into a visible failure`() {
        val controller = LibraryImportController(
            importBackup = { _, _ -> throw CancellationException("window closed") },
            importLocal = { _, _, _ -> report() },
            localLibraryRoot = Path.of("library"),
            ioDispatcher = Dispatchers.Default,
        )

        assertThrows<CancellationException> {
            runBlocking { controller.importBackup(Path.of("backup.tachibk")) }
        }
    }

    private fun report(
        id: Long = 1,
        counts: ImportCounts = ImportCounts(),
        items: List<ImportReportItem> = emptyList(),
    ) = ImportReport(id, ImportType.ANDROID_BACKUP, "source", ImportStatus.SUCCEEDED, 1, 2, counts, items)
}
