package mihon.desktop.library.backup

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.ImportReportItemRecord
import mihon.desktop.library.model.ImportReportRecord
import mihon.desktop.library.model.ImportStatus
import mihon.desktop.library.model.ImportType
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.PreferenceSkipReason
import mihon.desktop.library.model.PreferenceSnapshotRecord
import mihon.desktop.library.model.SourcePreferenceSnapshotRecord
import mihon.desktop.library.model.SourceRecord
import mihon.desktop.library.model.TrackingRecord
import mihon.desktop.library.repository.LibraryMutationPort
import okio.buffer
import okio.gzip
import okio.sink
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager

@OptIn(ExperimentalSerializationApi::class)
class AndroidBackupImporterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `preference policy enforces prefixes whitelist types and deterministic JSON`() {
        val policy = SupportedPreferencePolicy(
            sourceKeys = mapOf("allowed-source" to setOf("quality")),
        )

        policy.classifyApp("__PRIVATE_token", AndroidStringPreferenceValue("secret")) shouldBe
            PreferenceDecision.Skip(PreferenceSkipReason.PRIVATE)
        policy.classifyApp("__APP_STATE_last", AndroidIntPreferenceValue(1)) shouldBe
            PreferenceDecision.Skip(PreferenceSkipReason.APP_STATE)
        policy.classifyApp("unknown", AndroidStringPreferenceValue("value")) shouldBe
            PreferenceDecision.Skip(PreferenceSkipReason.UNKNOWN)
        policy.classifyApp("pref_display_mode_library", AndroidIntPreferenceValue(1)) shouldBe
            PreferenceDecision.Skip(PreferenceSkipReason.UNSUPPORTED_TYPE)
        policy.classifyApp("pref_display_mode_library", AndroidStringPreferenceValue("GRID")) shouldBe
            PreferenceDecision.Import("STRING", "\"GRID\"")
        policy.classifyApp("pref_library_columns_portrait_key", AndroidIntPreferenceValue(4)) shouldBe
            PreferenceDecision.Import("INT", "4")
        policy.classifyApp(
            "library_update_categories",
            AndroidStringSetPreferenceValue(setOf("20", "3", "10")),
        ) shouldBe PreferenceDecision.Import("STRING_SET", "[\"10\",\"20\",\"3\"]")
        SupportedPreferencePolicy().classifySource(
            "allowed-source",
            "quality",
            AndroidStringPreferenceValue("high"),
        ) shouldBe PreferenceDecision.Skip(PreferenceSkipReason.UNKNOWN)
        policy.classifySource("allowed-source", "quality", AndroidBooleanPreferenceValue(true)) shouldBe
            PreferenceDecision.Import("BOOLEAN", "true")
        policy.classifySource("other-source", "quality", AndroidStringPreferenceValue("high")) shouldBe
            PreferenceDecision.Skip(PreferenceSkipReason.UNKNOWN)
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { nonFinite ->
            policy.classifySource("allowed-source", "quality", AndroidFloatPreferenceValue(nonFinite)) shouldBe
                PreferenceDecision.Skip(PreferenceSkipReason.UNSUPPORTED_TYPE)
        }
    }

    @Test
    fun `pure merge policy is deterministic and never regresses progress`() {
        val existingManga = MangaRecord(
            id = 1,
            sourceId = 9,
            url = "/m",
            title = "existing title",
            author = "existing author",
            genreJson = "[\"Beta\",\"Alpha\"]",
            favorite = false,
            lastModifiedAt = 100,
            excludedScanlatorsJson = "[\"Zeta\"]",
            version = 7,
        )
        val incomingManga = existingManga.copy(
            id = 0,
            title = "incoming title",
            author = "",
            genreJson = "[\"Gamma\",\"Alpha\"]",
            favorite = true,
            lastModifiedAt = 200,
            excludedScanlatorsJson = "[\"Eta\",\"Zeta\"]",
            version = 3,
        )

        BackupMergePolicy.mergeManga(existingManga, incomingManga) shouldBe existingManga.copy(
            title = "incoming title",
            genreJson = "[\"Alpha\",\"Beta\",\"Gamma\"]",
            favorite = true,
            lastModifiedAt = 200,
            excludedScanlatorsJson = "[\"Eta\",\"Zeta\"]",
        )

        val existingChapter = ChapterRecord(
            id = 2,
            mangaId = 1,
            url = "/c",
            name = "existing",
            read = true,
            bookmark = false,
            lastPageRead = 50,
            dateFetch = 400,
            dateUpload = 300,
            lastModifiedAt = 100,
            version = 8,
        )
        val incomingChapter = existingChapter.copy(
            id = 0,
            name = "incoming",
            read = false,
            bookmark = true,
            lastPageRead = 20,
            dateFetch = 200,
            dateUpload = 600,
            lastModifiedAt = 200,
            version = 4,
        )
        BackupMergePolicy.mergeChapter(existingChapter, incomingChapter) shouldBe existingChapter.copy(
            name = "incoming",
            bookmark = true,
            dateUpload = 600,
            lastModifiedAt = 200,
        )

        BackupMergePolicy.mergeHistory(
            HistoryRecord(2, lastRead = 2_000, readDuration = 40),
            HistoryRecord(2, lastRead = 1_000, readDuration = 90),
        ) shouldBe HistoryRecord(2, lastRead = 2_000, readDuration = 90)

        val existingTracking = TrackingRecord(
            id = 3,
            mangaId = 1,
            trackerId = 4,
            remoteId = 5,
            title = "existing remote",
            lastChapterRead = 12.0,
            totalChapters = 20,
            score = 9.0,
            status = 2,
            startedReadingDate = 500,
            finishedReadingDate = 900,
            trackingUrl = "existing-url",
        )
        val regressiveTracking = existingTracking.copy(
            id = 0,
            remoteId = 55,
            title = "incoming remote",
            lastChapterRead = 10.0,
            totalChapters = 22,
            score = 1.0,
            status = 1,
            startedReadingDate = 300,
            finishedReadingDate = 1_000,
            trackingUrl = "incoming-url",
        )
        BackupMergePolicy.mergeTracking(existingTracking, regressiveTracking) shouldBe existingTracking.copy(
            totalChapters = 22,
            startedReadingDate = 300,
            finishedReadingDate = 1_000,
        )
        BackupMergePolicy.mergeTracking(
            existingTracking,
            regressiveTracking.copy(lastChapterRead = 13.0),
        ) shouldBe existingTracking.copy(
            remoteId = 55,
            title = "incoming remote",
            lastChapterRead = 13.0,
            totalChapters = 22,
            score = 1.0,
            status = 1,
            startedReadingDate = 300,
            finishedReadingDate = 1_000,
            trackingUrl = "incoming-url",
        )
    }

    @Test
    fun `import merges one transaction without regressing progress and remaps category preferences`() {
        val database = tempDir.resolve("merge.db")
        val backupFile = tempDir.resolve("merge.tachibk")
        DesktopLibraryDatabaseFactory.open(database).use { repository ->
            val mangaId = repository.insertManga(
                MangaRecord(
                    sourceId = 42,
                    url = "/manga",
                    title = "older existing title",
                    favorite = false,
                    lastModifiedAt = 100,
                    genreJson = "[\"ExistingGenre\"]",
                    version = 9,
                ),
            )
            val existingCategoryId = repository.upsertCategory(CategoryRecord(name = "Existing", sortOrder = 1))
            repository.linkCategory(mangaId, existingCategoryId)
            val chapterId = repository.insertChapter(
                ChapterRecord(
                    mangaId = mangaId,
                    url = "/chapter",
                    name = "older existing chapter",
                    read = true,
                    lastPageRead = 50,
                    lastModifiedAt = 100,
                    version = 8,
                ),
            )
            repository.upsertHistory(HistoryRecord(chapterId, lastRead = 2_000, readDuration = 30))
            repository.insertTracking(
                TrackingRecord(
                    mangaId = mangaId,
                    trackerId = 7,
                    remoteId = 70,
                    title = "existing tracker title",
                    lastChapterRead = 12.0,
                    totalChapters = 15,
                    score = 8.0,
                    status = 2,
                    trackingUrl = "existing-tracker-url",
                ),
            )

            val backup = AndroidBackup(
                backupManga = listOf(
                    AndroidBackupManga(
                        source = 42,
                        url = "/manga",
                        title = "newer incoming title",
                        favorite = true,
                        lastModifiedAt = 200,
                        version = 3,
                        genre = listOf("IncomingGenre"),
                        categories = listOf(2),
                        chapters = listOf(
                            AndroidBackupChapter(
                                url = "/chapter",
                                name = "newer incoming chapter",
                                bookmark = true,
                                lastPageRead = 20,
                                lastModifiedAt = 200,
                                version = 4,
                            ),
                        ),
                        history = listOf(AndroidBackupHistory("/chapter", lastRead = 1_000, readDuration = 90)),
                        tracking = listOf(
                            AndroidBackupTracking(
                                syncId = 7,
                                libraryId = 71,
                                mediaId = 700,
                                title = "incoming tracker title",
                                lastChapterRead = 10F,
                                totalChapters = 18,
                                score = 1F,
                                status = 1,
                                trackingUrl = "incoming-tracker-url",
                            ),
                        ),
                    ),
                ),
                backupCategories = listOf(AndroidBackupCategory(name = "Imported", order = 2, id = 10, flags = 4)),
                backupSources = listOf(AndroidBackupSource(name = "Source 42", sourceId = 42)),
                backupPreferences = listOf(
                    AndroidBackupPreference("pref_display_mode_library", AndroidStringPreferenceValue("COMPACT_GRID")),
                    AndroidBackupPreference("default_category", AndroidIntPreferenceValue(10)),
                    AndroidBackupPreference(
                        "library_update_categories",
                        AndroidStringSetPreferenceValue(setOf("10")),
                    ),
                    AndroidBackupPreference("__PRIVATE_token", AndroidStringPreferenceValue("never-leak-this")),
                    AndroidBackupPreference("__APP_STATE_last", AndroidIntPreferenceValue(1)),
                    AndroidBackupPreference("unknown", AndroidStringPreferenceValue("unknown-value")),
                ),
                backupSourcePreferences = listOf(
                    AndroidBackupSourcePreferences(
                        "source-42",
                        listOf(AndroidBackupPreference("quality", AndroidStringPreferenceValue("high"))),
                    ),
                ),
                backupExtensionStores = listOf(
                    AndroidBackupExtensionStore(
                        indexUrl = "https://extensions.invalid/index.json",
                        name = "Untrusted",
                        badgeLabel = null,
                        contactWebsite = "https://extensions.invalid",
                        signingKey = "never-persist-signing-key",
                        contactDiscord = null,
                        isLegacy = false,
                        extensionListUrl = null,
                    ),
                ),
            )
            encode(backup, backupFile)

            val report = AndroidBackupImporter(
                codec = AndroidBackupCodec(),
                validator = AndroidBackupValidator(),
                mutations = repository,
                preferences = SupportedPreferencePolicy(sourceKeys = mapOf("source-42" to setOf("quality"))),
            ).import(backupFile, nowMillis = 5_000)

            report.status shouldBe ImportStatus.SUCCEEDED
            repository.findManga(42, "/manga")!!.run {
                favorite shouldBe true
                title shouldBe "newer incoming title"
                version shouldBe 9
                genreJson shouldBe "[\"ExistingGenre\",\"IncomingGenre\"]"
            }
            repository.findChapter(mangaId, "/chapter")!!.run {
                name shouldBe "newer incoming chapter"
                read shouldBe true
                bookmark shouldBe true
                lastPageRead shouldBe 50
                version shouldBe 8
            }
            repository.findTracking(mangaId, 7)!!.run {
                remoteId shouldBe 70
                title shouldBe "existing tracker title"
                lastChapterRead shouldBe 12.0
                totalChapters shouldBe 18
                score shouldBe 8.0
                status shouldBe 2
                trackingUrl shouldBe "existing-tracker-url"
            }
            repository.mangaSnapshot(mangaId)!!.categories.map { it.name }.toSet() shouldBe
                setOf("Existing", "Imported")

            val importedCategoryId = repository.mangaSnapshot(mangaId)!!.categories.single { it.name == "Imported" }.id
            queryRows(database, "SELECT last_read, read_duration FROM history") shouldBe listOf(listOf("2000", "90"))
            queryRows(
                database,
                "SELECT key, value_type, value_json FROM preference_snapshot ORDER BY key",
            ) shouldBe listOf(
                listOf("default_category", "INT", importedCategoryId.toString()),
                listOf("library_update_categories", "STRING_SET", "[\"$importedCategoryId\"]"),
                listOf("pref_display_mode_library", "STRING", "\"COMPACT_GRID\""),
            )
            queryRows(
                database,
                "SELECT source_key, key, value_type, value_json FROM source_preference_snapshot",
            ) shouldBe listOf(listOf("source-42", "quality", "STRING", "\"high\""))
            queryRows(database, "SELECT source_id, name FROM source_metadata") shouldBe
                listOf(listOf("42", "Source 42"))

            report.items.filter { it.itemType == "PREFERENCE" }.map { it.reason }.filterNotNull()
                .shouldContainExactlyInAnyOrder(listOf("PRIVATE", "APP_STATE", "UNKNOWN"))
            report.items.joinToString { it.message }.run {
                shouldNotContain("never-leak-this")
                shouldNotContain("unknown-value")
            }
            report.items.single { it.itemType == "EXTENSION_STORE" }.run {
                outcome shouldBe "SKIPPED"
                reason shouldBe "UNKNOWN"
                message shouldBe "Extension store installation is outside Plan 2"
            }
            repository.latestImportReport()!!.run {
                id shouldBe report.id
                status shouldBe report.status
                counts shouldBe report.counts
                items.map { it.copy(id = 0) } shouldBe report.items
            }
        }
    }

    @Test
    fun `older or equal incoming timestamps keep existing nonblank metadata`() {
        listOf(99L, 100L).forEach { incomingModifiedAt ->
            val database = tempDir.resolve("metadata-$incomingModifiedAt.db")
            val backupFile = tempDir.resolve("metadata-$incomingModifiedAt.tachibk")
            DesktopLibraryDatabaseFactory.open(database).use { repository ->
                val mangaId = repository.insertManga(
                    MangaRecord(
                        sourceId = 1,
                        url = "/m",
                        title = "existing manga",
                        author = "existing author",
                        lastModifiedAt = 100,
                    ),
                )
                repository.insertChapter(
                    ChapterRecord(
                        mangaId = mangaId,
                        url = "/c",
                        name = "existing chapter",
                        scanlator = "existing group",
                        lastModifiedAt = 100,
                    ),
                )
                encode(
                    AndroidBackup(
                        backupManga = listOf(
                            AndroidBackupManga(
                                source = 1,
                                url = "/m",
                                title = "incoming manga",
                                author = "incoming author",
                                lastModifiedAt = incomingModifiedAt,
                                chapters = listOf(
                                    AndroidBackupChapter(
                                        url = "/c",
                                        name = "incoming chapter",
                                        scanlator = "incoming group",
                                        lastModifiedAt = incomingModifiedAt,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    backupFile,
                )

                AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), repository)
                    .import(backupFile, 200)

                repository.findManga(1, "/m")!!.run {
                    title shouldBe "existing manga"
                    author shouldBe "existing author"
                }
                repository.findChapter(mangaId, "/c")!!.run {
                    name shouldBe "existing chapter"
                    scanlator shouldBe "existing group"
                }
            }
        }
    }

    @Test
    fun `category set preferences skip atomically when any backup category id cannot be remapped`() {
        val database = tempDir.resolve("strict-category-preferences.db")
        val backupFile = tempDir.resolve("strict-category-preferences.tachibk")
        val mixedInvalid = "SECRET_RAW_INVALID"
        val allInvalid = "999"
        encode(
            AndroidBackup(
                backupManga = listOf(
                    AndroidBackupManga(
                        source = 1,
                        url = "/manga",
                        title = "Manga",
                        categories = listOf(1),
                    ),
                ),
                backupCategories = listOf(AndroidBackupCategory(name = "Imported", order = 1, id = 10)),
                backupPreferences = listOf(
                    AndroidBackupPreference(
                        "library_update_categories",
                        AndroidStringSetPreferenceValue(setOf("10", mixedInvalid)),
                    ),
                    AndroidBackupPreference(
                        "library_update_categories_exclude",
                        AndroidStringSetPreferenceValue(setOf(allInvalid)),
                    ),
                ),
            ),
            backupFile,
        )

        DesktopLibraryDatabaseFactory.open(database).use { repository ->
            val report = AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), repository)
                .import(backupFile, 1)

            queryRows(
                database,
                """
                SELECT key, value_type, value_json
                FROM preference_snapshot
                WHERE key IN ('library_update_categories', 'library_update_categories_exclude')
                ORDER BY key
                """.trimIndent(),
            ) shouldBe emptyList()
            report.items.filter { it.itemType == "PREFERENCE" }.map { item ->
                listOf(item.itemKey, item.outcome, item.reason, item.message)
            } shouldBe listOf(
                listOf(
                    "app/library_update_categories",
                    "SKIPPED",
                    "UNKNOWN",
                    "Skipped app preference 'library_update_categories': UNKNOWN",
                ),
                listOf(
                    "app/library_update_categories_exclude",
                    "SKIPPED",
                    "UNKNOWN",
                    "Skipped app preference 'library_update_categories_exclude': UNKNOWN",
                ),
            )
            report.items.joinToString { it.message }.run {
                shouldNotContain(mixedInvalid)
                shouldNotContain(allInvalid)
                shouldNotContain("10")
            }
        }
    }

    @Test
    fun `checkpoint failure rolls every imported table back byte for byte`() {
        val database = tempDir.resolve("rollback.db")
        val backupFile = tempDir.resolve("rollback.tachibk")
        DesktopLibraryDatabaseFactory.open(database).use { repository ->
            val mangaId = repository.insertManga(
                MangaRecord(sourceId = 1, url = "/m", title = "before", lastModifiedAt = 1),
            )
            val categoryId = repository.upsertCategory(CategoryRecord(name = "Before", sortOrder = 1, flags = 1))
            repository.linkCategory(mangaId, categoryId)
            val chapterId = repository.insertChapter(ChapterRecord(mangaId = mangaId, url = "/c", name = "before"))
            repository.upsertHistory(HistoryRecord(chapterId, 1, 1))
            repository.insertTracking(TrackingRecord(mangaId = mangaId, trackerId = 1, remoteId = 1, title = "before"))
            repository.upsertSource(SourceRecord(1, "Before", 1))
            repository.upsertPreference(
                PreferenceSnapshotRecord("pref_display_mode_library", "STRING", "\"before\"", 1),
            )
            repository.upsertSourcePreference(
                SourcePreferenceSnapshotRecord("source", "quality", "STRING", "\"before\"", 1),
            )
            val reportId = repository.insertReport(
                ImportReportRecord(ImportType.ANDROID_BACKUP, "before", ImportStatus.SUCCEEDED, 1, 1),
            )
            repository.insertReportItem(
                reportId,
                ImportReportItemRecord("BEFORE", "before", "IMPORTED", null, "before"),
            )

            encode(fullyMutatingBackup(), backupFile)
            val before = dumpImportTables(database)

            shouldThrow<IllegalStateException> {
                AndroidBackupImporter(
                    codec = AndroidBackupCodec(),
                    validator = AndroidBackupValidator(),
                    mutations = repository,
                    preferences = SupportedPreferencePolicy(sourceKeys = mapOf("source" to setOf("quality"))),
                    checkpoint = ImportCheckpoint { error("checkpoint failure") },
                ).import(backupFile, 2)
            }

            dumpImportTables(database) shouldBe before
        }
    }

    @Test
    fun `validation failure does not open a transaction or insert a report`() {
        val database = tempDir.resolve("invalid.db")
        val backupFile = tempDir.resolve("invalid.tachibk")
        encode(
            AndroidBackup(
                backupManga = listOf(
                    AndroidBackupManga(source = 1, url = "/duplicate", title = "one"),
                    AndroidBackupManga(source = 1, url = "/duplicate", title = "two"),
                ),
            ),
            backupFile,
        )
        DesktopLibraryDatabaseFactory.open(database).use { repository ->
            val counting = CountingMutationPort(repository)

            shouldThrow<BackupValidationException> {
                AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), counting).import(backupFile, 1)
            }

            counting.transactions shouldBe 0
            repository.librarySnapshot() shouldBe emptyList()
            repository.latestImportReport() shouldBe null
        }
    }

    @Test
    fun `deeply nested memo is rejected without opening a transaction or mutating any table`() {
        val database = tempDir.resolve("deep-memo.db")
        val backupFile = tempDir.resolve("deep-memo.tachibk")
        val deeplyNestedMemo = buildString {
            append("{\"value\":")
            repeat(10_000) { append('[') }
            append('0')
            repeat(10_000) { append(']') }
            append('}')
        }
        encode(
            AndroidBackup(
                backupManga = listOf(
                    AndroidBackupManga(
                        source = 1,
                        url = "/deep",
                        title = "Deep",
                        memo = deeplyNestedMemo.encodeToByteArray(),
                    ),
                ),
            ),
            backupFile,
        )

        DesktopLibraryDatabaseFactory.open(database).use { repository ->
            val counting = CountingMutationPort(repository)
            val before = dumpImportTables(database)

            shouldThrow<BackupValidationException> {
                AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), counting)
                    .import(backupFile, 1)
            }.message.shouldContain("backupManga[0].memo")

            counting.transactions shouldBe 0
            dumpImportTables(database) shouldBe before
            repository.librarySnapshot() shouldBe emptyList()
            repository.latestImportReport() shouldBe null
        }
    }

    @Test
    fun `non-finite backup values never open a transaction or mutate any table`() {
        nonFiniteBackupCases().forEachIndexed { index, case ->
            val database = tempDir.resolve("non-finite-$index.db")
            val backupFile = tempDir.resolve("non-finite-$index.tachibk")
            encode(case.backup, backupFile)
            DesktopLibraryDatabaseFactory.open(database).use { repository ->
                val counting = CountingMutationPort(repository)
                val before = dumpImportTables(database)

                shouldThrow<BackupValidationException> {
                    AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), counting)
                        .import(backupFile, 1)
                }.message.shouldContain(case.expectedPath)

                counting.transactions shouldBe 0
                dumpImportTables(database) shouldBe before
                repository.librarySnapshot() shouldBe emptyList()
                repository.latestImportReport() shouldBe null
            }
        }
    }

    private fun fullyMutatingBackup() = AndroidBackup(
        backupManga = listOf(
            AndroidBackupManga(
                source = 1,
                url = "/m",
                title = "after",
                lastModifiedAt = 2,
                categories = listOf(2),
                chapters = listOf(AndroidBackupChapter("/c", "after", read = true)),
                history = listOf(AndroidBackupHistory("/c", 2, 2)),
                tracking = listOf(
                    AndroidBackupTracking(
                        syncId = 1,
                        libraryId = 2,
                        mediaId = 2,
                        title = "after",
                        lastChapterRead = 2F,
                    ),
                ),
            ),
        ),
        backupCategories = listOf(AndroidBackupCategory("After", order = 2, id = 2)),
        backupSources = listOf(AndroidBackupSource("After", 1)),
        backupPreferences = listOf(
            AndroidBackupPreference(
                "pref_display_mode_library",
                AndroidStringPreferenceValue("after"),
            ),
        ),
        backupSourcePreferences = listOf(
            AndroidBackupSourcePreferences(
                "source",
                listOf(
                    AndroidBackupPreference(
                        "quality",
                        AndroidStringPreferenceValue("after"),
                    ),
                ),
            ),
        ),
    )

    private fun encode(backup: AndroidBackup, path: Path) {
        path.sink().gzip().buffer().use { sink ->
            sink.write(ProtoBuf.encodeToByteArray(AndroidBackup.serializer(), backup))
        }
    }
}

private class CountingMutationPort(
    private val delegate: LibraryMutationPort,
) : LibraryMutationPort by delegate {
    var transactions = 0

    override fun <T> transaction(block: LibraryMutationPort.() -> T): T {
        transactions++
        return delegate.transaction(block)
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

private fun dumpImportTables(path: Path): Map<String, List<List<String?>>> = IMPORT_TABLES.associateWith { table ->
    queryRows(path, "SELECT * FROM $table ORDER BY rowid")
}

private fun queryRows(path: Path, sql: String): List<List<String?>> =
    DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                val columns = result.metaData.columnCount
                buildList {
                    while (result.next()) {
                        add((1..columns).map { index -> result.getObject(index)?.toString() })
                    }
                }
            }
        }
    }
