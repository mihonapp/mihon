package mihon.desktop.library.backup

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class AndroidBackupValidatorTest {
    private val validator = AndroidBackupValidator()

    @Test
    fun `rejects cumulative limits with stable paths`() {
        val manga = manga(url = "/one")
        val cases = listOf(
            InvalidBackupCase(
                "manga count",
                AndroidBackup(listOf(manga, manga(url = "/two"))),
                tinyLimits(maxManga = 1),
                "backupManga[1]",
            ),
            InvalidBackupCase(
                "chapter count",
                AndroidBackup(
                    listOf(
                        manga(url = "/one", chapters = listOf(chapter("/a"))),
                        manga(url = "/two", chapters = listOf(chapter("/b"))),
                    ),
                ),
                tinyLimits(maxChapters = 1),
                "backupManga[1].chapters[0]",
            ),
            InvalidBackupCase(
                "tracking count",
                AndroidBackup(
                    listOf(
                        manga(url = "/one", tracking = listOf(tracking(1))),
                        manga(url = "/two", tracking = listOf(tracking(2))),
                    ),
                ),
                tinyLimits(maxTracks = 1),
                "backupManga[1].tracking[0]",
            ),
            InvalidBackupCase(
                "preference count",
                AndroidBackup(
                    backupManga = listOf(manga),
                    backupPreferences = listOf(preference("one")),
                    backupSourcePreferences = listOf(
                        AndroidBackupSourcePreferences("source", listOf(preference("two"))),
                    ),
                ),
                tinyLimits(maxPreferences = 1),
                "backupSourcePreferences[0].prefs[0]",
            ),
            InvalidBackupCase(
                "string length",
                AndroidBackup(listOf(manga.copy(title = "12345"))),
                tinyLimits(maxStringChars = 4),
                "backupManga[0].title",
            ),
        )

        cases.forEach { case ->
            shouldThrow<BackupValidationException> {
                validator.validate(case.backup, case.limits)
            }.message.shouldContain(case.expectedPath)
        }
    }

    @Test
    fun `rejects duplicate identities and invalid references with stable paths`() {
        val baseManga = manga()
        val cases = listOf(
            InvalidBackupCase(
                "manga identity",
                AndroidBackup(listOf(baseManga, baseManga.copy(title = "duplicate"))),
                expectedPath = "backupManga[1]",
            ),
            InvalidBackupCase(
                "chapter identity",
                AndroidBackup(listOf(manga(chapters = listOf(chapter("/same"), chapter("/same"))))),
                expectedPath = "backupManga[0].chapters[1].url",
            ),
            InvalidBackupCase(
                "category id",
                AndroidBackup(
                    listOf(baseManga),
                    backupCategories = listOf(
                        category(id = 1, name = "A", order = 1),
                        category(id = 1, name = "B", order = 2),
                    ),
                ),
                expectedPath = "backupCategories[1].id",
            ),
            InvalidBackupCase(
                "category name",
                AndroidBackup(
                    listOf(baseManga),
                    backupCategories = listOf(
                        category(id = 1, name = "Same", order = 1),
                        category(id = 2, name = "same", order = 2),
                    ),
                ),
                expectedPath = "backupCategories[1].name",
            ),
            InvalidBackupCase(
                "category order",
                AndroidBackup(
                    listOf(baseManga),
                    backupCategories = listOf(
                        category(id = 1, name = "A", order = 1),
                        category(id = 2, name = "B", order = 1),
                    ),
                ),
                expectedPath = "backupCategories[1].order",
            ),
            InvalidBackupCase(
                "missing category order",
                AndroidBackup(
                    listOf(manga(categories = listOf(99))),
                    backupCategories = listOf(category(id = 1, name = "A", order = 1)),
                ),
                expectedPath = "backupManga[0].categories[0]",
            ),
            InvalidBackupCase(
                "missing history chapter",
                AndroidBackup(
                    listOf(
                        manga(
                            chapters = listOf(chapter("/present")),
                            history = listOf(AndroidBackupHistory("/missing", 1)),
                        ),
                    ),
                ),
                expectedPath = "backupManga[0].history[0].url",
            ),
            InvalidBackupCase(
                "tracking identity",
                AndroidBackup(listOf(manga(tracking = listOf(tracking(7), tracking(7))))),
                expectedPath = "backupManga[0].tracking[1].syncId",
            ),
        )

        cases.forEach { case ->
            shouldThrow<BackupValidationException> {
                validator.validate(case.backup, case.limits)
            }.message.shouldContain(case.expectedPath)
        }
    }

    @Test
    fun `rejects malformed non-object and over-nested memo JSON`() {
        val nested = buildString {
            repeat(65) { append("{\"x\":") }
            append("{}")
            repeat(65) { append('}') }
        }
        val cases = listOf(
            InvalidBackupCase(
                "invalid JSON",
                AndroidBackup(listOf(manga(memo = "not-json".encodeToByteArray()))),
                expectedPath = "backupManga[0].memo",
            ),
            InvalidBackupCase(
                "JSON array",
                AndroidBackup(listOf(manga(memo = "[]".encodeToByteArray()))),
                expectedPath = "backupManga[0].memo",
            ),
            InvalidBackupCase(
                "chapter JSON array",
                AndroidBackup(listOf(manga(chapters = listOf(chapter("/c", memo = "[]".encodeToByteArray()))))),
                expectedPath = "backupManga[0].chapters[0].memo",
            ),
            InvalidBackupCase(
                "nesting",
                AndroidBackup(listOf(manga(memo = nested.encodeToByteArray()))),
                tinyLimits(maxStringChars = nested.length + 1, maxNestingDepth = 64),
                "backupManga[0].memo",
            ),
        )

        cases.forEach { case ->
            shouldThrow<BackupValidationException> {
                validator.validate(case.backup, case.limits)
            }.message.shouldContain(case.expectedPath)
        }
    }

    @Test
    fun `accepts the boundary and exposes canonical memo JSON`() {
        val boundaryMemo = buildString {
            repeat(64) { append("{\"x\":") }
            append('0')
            repeat(64) { append('}') }
        }
        val backup = AndroidBackup(
            backupManga = listOf(
                manga(
                    memo = "{ \"b\" : 2, \"a\" : 1 }".encodeToByteArray(),
                    chapters = listOf(chapter("/c", memo = "{\"ok\":true}".encodeToByteArray())),
                ),
            ),
        )

        val validated = validator.validate(backup)

        validated.mangaMemoJson shouldBe listOf("{\"b\":2,\"a\":1}")
        validated.chapterMemoJson shouldBe listOf(listOf("{\"ok\":true}"))
        validator.validate(
            AndroidBackup(listOf(manga(memo = boundaryMemo.encodeToByteArray()))),
            tinyLimits(maxStringChars = boundaryMemo.length),
        ).mangaMemoJson shouldBe listOf(boundaryMemo)
    }

    private data class InvalidBackupCase(
        val name: String,
        val backup: AndroidBackup,
        val limits: BackupLimits = tinyLimits(),
        val expectedPath: String,
    )
}

private fun manga(
    url: String = "/manga",
    chapters: List<AndroidBackupChapter> = emptyList(),
    categories: List<Long> = emptyList(),
    history: List<AndroidBackupHistory> = emptyList(),
    tracking: List<AndroidBackupTracking> = emptyList(),
    memo: ByteArray = "{}".encodeToByteArray(),
) = AndroidBackupManga(
    source = 1,
    url = url,
    title = "Manga",
    chapters = chapters,
    categories = categories,
    history = history,
    tracking = tracking,
    memo = memo,
)

private fun chapter(url: String, memo: ByteArray = "{}".encodeToByteArray()) =
    AndroidBackupChapter(url = url, name = "Chapter", memo = memo)

private fun tracking(syncId: Int) = AndroidBackupTracking(syncId = syncId, libraryId = 1)

private fun category(id: Long, name: String, order: Long) = AndroidBackupCategory(name, order, id)

private fun preference(key: String) = AndroidBackupPreference(key, AndroidStringPreferenceValue("value"))

private fun tinyLimits(
    maxManga: Int = 100,
    maxChapters: Int = 100,
    maxTracks: Int = 100,
    maxPreferences: Int = 100,
    maxStringChars: Int = 1_000,
    maxNestingDepth: Int = 64,
) = BackupLimits.DEFAULT.copy(
    maxManga = maxManga,
    maxChapters = maxChapters,
    maxTracks = maxTracks,
    maxPreferences = maxPreferences,
    maxStringChars = maxStringChars,
    maxNestingDepth = maxNestingDepth,
)
