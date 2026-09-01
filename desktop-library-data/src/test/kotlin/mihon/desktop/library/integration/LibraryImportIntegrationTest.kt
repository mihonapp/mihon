package mihon.desktop.library.integration

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.desktop.library.backup.AndroidBackup
import mihon.desktop.library.backup.AndroidBackupCategory
import mihon.desktop.library.backup.AndroidBackupChapter
import mihon.desktop.library.backup.AndroidBackupCodec
import mihon.desktop.library.backup.AndroidBackupHistory
import mihon.desktop.library.backup.AndroidBackupImporter
import mihon.desktop.library.backup.AndroidBackupManga
import mihon.desktop.library.backup.AndroidBackupPreference
import mihon.desktop.library.backup.AndroidBackupSource
import mihon.desktop.library.backup.AndroidBackupTracking
import mihon.desktop.library.backup.AndroidBackupValidator
import mihon.desktop.library.backup.AndroidIntPreferenceValue
import mihon.desktop.library.backup.AndroidStringPreferenceValue
import mihon.desktop.library.backup.AndroidStringSetPreferenceValue
import mihon.desktop.library.backup.ImportCheckpoint
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.ImportCounts
import mihon.desktop.library.model.PreferenceSkipReason
import okio.buffer
import okio.gzip
import okio.sink
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager

@OptIn(ExperimentalSerializationApi::class)
class LibraryImportIntegrationTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `two file backups merge without regression and persist after reopen`() {
        val database = tempDir.resolve("library.db")
        val first = writeBackup("first.tachibk", firstBackup())
        val second = writeBackup("second.tachibk", secondBackup())

        DesktopLibraryDatabaseFactory.open(database).use { repository ->
            AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), repository).apply {
                import(first, nowMillis = 1_000)
                import(second, nowMillis = 2_000)
            }
        }

        DesktopLibraryDatabaseFactory.open(database).use { reopened ->
            val library = reopened.librarySnapshot()
            library.map { it.title } shouldContainExactlyInAnyOrder listOf("保留的新元数据", "仅第一份", "仅第二份")
            val overlap = library.single { it.url == "/overlap" }
            reopened.mangaSnapshot(overlap.id)!!.categories.map { it.name } shouldContainExactlyInAnyOrder
                listOf("第一分类", "第二分类")
            reopened.chapterSnapshot(overlap.id).single().let { chapter ->
                chapter.name shouldBe "新版章节名"
                chapter.read shouldBe true
                chapter.bookmark shouldBe true
                chapter.lastPageRead shouldBe 40
            }

            val report = reopened.latestImportReport()!!
            report.counts shouldBe ImportCounts(
                mangaInserted = 1,
                mangaMerged = 1,
                chaptersInserted = 1,
                chaptersMerged = 1,
                categoriesLinked = 2,
                preferencesImported = 3,
                preferencesSkipped = 3,
            )
            report.items.associate { it.itemKey to it.reason } shouldBe mapOf(
                "app/__PRIVATE_auth_token" to PreferenceSkipReason.PRIVATE.name,
                "app/__APP_STATE_last_version_code" to PreferenceSkipReason.APP_STATE.name,
                "app/unrecognized_plan2_key" to PreferenceSkipReason.UNKNOWN.name,
            )
        }

        query(database, "SELECT source_id, name FROM source_metadata ORDER BY source_id") shouldBe listOf(
            listOf("42", "更新后的来源"),
            listOf("99", "第二来源"),
        )
        query(database, "SELECT key, value_type, value_json FROM preference_snapshot ORDER BY key") shouldBe listOf(
            listOf("default_category", "INT", "2"),
            listOf("library_update_categories", "STRING_SET", "[\"2\"]"),
            listOf("pref_display_mode_library", "STRING", "\"COMPACT_GRID\""),
        )
        query(
            database,
            "SELECT history.last_read, history.read_duration FROM history " +
                "JOIN chapter ON chapter.id=history.chapter_id " +
                "JOIN manga ON manga.id=chapter.manga_id WHERE manga.url='/overlap'",
        ) shouldBe listOf(listOf("2000", "90"))
        query(
            database,
            "SELECT tracking.last_chapter_read, tracking.title FROM tracking " +
                "JOIN manga ON manga.id=tracking.manga_id " +
                "WHERE manga.url='/overlap' AND tracker_id=7",
        ) shouldBe listOf(listOf("3.5", "进度更新"))
    }

    @Test
    fun `checkpoint failure rolls back on disk before reopen`() {
        val database = tempDir.resolve("rollback.db")
        val baseline = writeBackup("baseline.tachibk", firstBackup())
        val failing = writeBackup(
            "failing.tachibk",
            AndroidBackup(
                backupManga = listOf(AndroidBackupManga(source = 404, url = "/must-rollback", title = "不得留下")),
                backupSources = listOf(AndroidBackupSource("回滚来源", 404)),
            ),
        )
        val before: Map<String, List<List<String>>>

        DesktopLibraryDatabaseFactory.open(database).use { repository ->
            AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), repository).import(baseline, 1_000)
            before = snapshot(database)
            val importer = AndroidBackupImporter(
                AndroidBackupCodec(),
                AndroidBackupValidator(),
                repository,
                checkpoint = ImportCheckpoint { error("forced checkpoint failure") },
            )
            shouldThrow<IllegalStateException> { importer.import(failing, 2_000) }
        }

        DesktopLibraryDatabaseFactory.open(database).use { reopened ->
            reopened.librarySnapshot().map { it.title } shouldContainExactlyInAnyOrder listOf("保留的新元数据", "仅第一份")
            reopened.latestImportReport()!!.counts.mangaInserted shouldBe 2
        }
        snapshot(database) shouldBe before
    }

    private fun firstBackup() = AndroidBackup(
        backupManga = listOf(
            AndroidBackupManga(
                source = 42,
                url = "/overlap",
                title = "保留的新元数据",
                author = "新作者",
                lastModifiedAt = 500,
                version = 5,
                categories = listOf(10),
                chapters = listOf(
                    AndroidBackupChapter(
                        url = "/overlap/1",
                        name = "新版章节名",
                        read = true,
                        lastPageRead = 20,
                        lastModifiedAt = 500,
                        version = 5,
                    ),
                ),
                history = listOf(AndroidBackupHistory("/overlap/1", lastRead = 2_000, readDuration = 20)),
                tracking = listOf(
                    AndroidBackupTracking(
                        syncId = 7,
                        libraryId = 1,
                        mediaId = 70,
                        title = "原进度",
                        lastChapterRead = 2F,
                    ),
                ),
            ),
            AndroidBackupManga(source = 42, url = "/first-only", title = "仅第一份"),
        ),
        backupCategories = listOf(AndroidBackupCategory("第一分类", order = 10, id = 100)),
        backupSources = listOf(AndroidBackupSource("初始来源", 42)),
    )

    private fun secondBackup() = AndroidBackup(
        backupManga = listOf(
            AndroidBackupManga(
                source = 42,
                url = "/overlap",
                title = "不应覆盖的旧元数据",
                author = "旧作者",
                lastModifiedAt = 100,
                version = 2,
                categories = listOf(20),
                chapters = listOf(
                    AndroidBackupChapter(
                        url = "/overlap/1",
                        name = "旧章节名",
                        bookmark = true,
                        lastPageRead = 40,
                        lastModifiedAt = 100,
                        version = 2,
                    ),
                ),
                history = listOf(AndroidBackupHistory("/overlap/1", lastRead = 1_000, readDuration = 90)),
                tracking = listOf(
                    AndroidBackupTracking(
                        syncId = 7,
                        libraryId = 2,
                        mediaId = 71,
                        title = "进度更新",
                        lastChapterRead = 3.5F,
                    ),
                ),
            ),
            AndroidBackupManga(
                source = 99,
                url = "/second-only",
                title = "仅第二份",
                categories = listOf(20),
                chapters = listOf(AndroidBackupChapter("/second-only/1", "第二份第 1 话")),
            ),
        ),
        backupCategories = listOf(AndroidBackupCategory("第二分类", order = 20, id = 200)),
        backupSources = listOf(AndroidBackupSource("更新后的来源", 42), AndroidBackupSource("第二来源", 99)),
        backupPreferences = listOf(
            AndroidBackupPreference("pref_display_mode_library", AndroidStringPreferenceValue("COMPACT_GRID")),
            AndroidBackupPreference("default_category", AndroidIntPreferenceValue(200)),
            AndroidBackupPreference("library_update_categories", AndroidStringSetPreferenceValue(setOf("200"))),
            AndroidBackupPreference("__PRIVATE_auth_token", AndroidStringPreferenceValue("secret")),
            AndroidBackupPreference("__APP_STATE_last_version_code", AndroidIntPreferenceValue(29)),
            AndroidBackupPreference("unrecognized_plan2_key", AndroidStringPreferenceValue("skip")),
        ),
    )

    private fun writeBackup(name: String, backup: AndroidBackup): Path = tempDir.resolve(name).also { path ->
        path.sink().gzip().buffer().use { sink ->
            sink.write(ProtoBuf.encodeToByteArray(AndroidBackup.serializer(), backup))
        }
    }
}

private val IMPORT_TABLES = listOf(
    "manga",
    "chapter",
    "category",
    "manga_category",
    "history",
    "tracking",
    "source_metadata",
    "preference_snapshot",
    "source_preference_snapshot",
    "import_report",
    "import_report_item",
)

private fun snapshot(path: Path) = IMPORT_TABLES.associateWith { table ->
    query(path, "SELECT * FROM $table ORDER BY rowid")
}

private fun query(path: Path, sql: String): List<List<String>> =
    DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                val columns = result.metaData.columnCount
                buildList {
                    while (result.next()) add((1..columns).map { result.getObject(it)?.toString() ?: "<null>" })
                }
            }
        }
    }
