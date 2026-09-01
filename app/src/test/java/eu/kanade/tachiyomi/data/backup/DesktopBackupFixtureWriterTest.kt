package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.sink
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

@OptIn(ExperimentalSerializationApi::class)
class DesktopBackupFixtureWriterTest {
    @Test
    fun `writes deterministic Android backup fixture and checksum beneath app build`() {
        val outputDirectory = requiredOutputDirectory()
        Files.createDirectories(outputDirectory)
        val fixture = outputDirectory.resolve("android-generated.tachibk")
        val checksumFile = outputDirectory.resolve("android-generated.tachibk.sha256")

        fixture.sink().gzip().buffer().use { sink ->
            sink.write(ProtoBuf.encodeToByteArray(Backup.serializer(), fixtureBackup()))
        }
        val checksum = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(fixture))
            .joinToString("") { byte -> "%02x".format(byte) }
        Files.writeString(checksumFile, checksum + System.lineSeparator())

        Files.isRegularFile(fixture) shouldBe true
        checksum.matches(Regex("[0-9a-f]{64}")) shouldBe true
        Files.readString(checksumFile).trim() shouldBe checksum
    }

    private fun requiredOutputDirectory(): Path {
        val configured = System.getProperty(FIXTURE_DIRECTORY_PROPERTY)?.takeIf(String::isNotBlank)
        if (configured == null && System.getProperty(FIXTURE_WRITER_SELECTED_PROPERTY) == "true") {
            error(
                "Required system property '$FIXTURE_DIRECTORY_PROPERTY' is missing. " +
                    "Select this writer test with -PmihonPlan2FixtureDir=app/build/plan2-fixtures.",
            )
        }
        assumeTrue(configured != null, "Android fixture writer is disabled during normal unit-test runs")
        checkNotNull(configured)
        val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val repositoryRoot = generateSequence(workingDirectory) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("app/build.gradle.kts")) }
            ?: error("Could not locate repository root from $workingDirectory")
        val appBuild = repositoryRoot.resolve("app/build").toAbsolutePath().normalize()
        val requested = Path.of(configured).let { path ->
            if (path.isAbsolute) path else repositoryRoot.resolve(path)
        }.toAbsolutePath().normalize()
        require(requested != appBuild && requested.startsWith(appBuild)) {
            "Fixture output directory must be beneath $appBuild, but was $requested"
        }
        return requested
    }

    private fun fixtureBackup(): Backup = Backup(
        backupManga = listOf(
            BackupManga(source = 42, url = "/cross-platform", title = "跨平台备份").apply {
                author = "Windows 迁移验证"
                categories = listOf(CATEGORY_ORDER)
                chapters = listOf(
                    BackupChapter(
                        url = "/cross-platform/chapter-1",
                        name = "第 1 话",
                        read = true,
                        lastPageRead = 7,
                        chapterNumber = 1F,
                    ),
                )
                history =
                    listOf(BackupHistory("/cross-platform/chapter-1", lastRead = 1_700_000_000_000, readDuration = 90))
                tracking = listOf(
                    BackupTracking(
                        syncId = 1,
                        libraryId = 2,
                        mediaId = 420,
                        title = "跨平台备份",
                        lastChapterRead = 1F,
                    ),
                )
            },
        ),
        backupCategories = listOf(
            BackupCategory(name = "Android 收藏", order = CATEGORY_ORDER, id = CATEGORY_ID),
        ),
        backupSources = listOf(BackupSource(name = "Android Fixture Source", sourceId = 42)),
        backupPreferences = listOf(
            BackupPreference("pref_display_mode_library", StringPreferenceValue("COMPACT_GRID")),
            BackupPreference("default_category", IntPreferenceValue(CATEGORY_ID.toInt())),
            BackupPreference("library_update_categories", StringSetPreferenceValue(setOf(CATEGORY_ID.toString()))),
            BackupPreference("__PRIVATE_auth_token", StringPreferenceValue("must-not-import")),
            BackupPreference("__APP_STATE_last_version_code", IntPreferenceValue(29)),
            BackupPreference("unrecognized_plan2_key", StringPreferenceValue("must-skip")),
        ),
    )

    private companion object {
        const val FIXTURE_DIRECTORY_PROPERTY = "mihon.plan2.fixtureDir"
        const val FIXTURE_WRITER_SELECTED_PROPERTY = "mihon.plan2.fixtureWriterSelected"
        const val CATEGORY_ID = 700L
        const val CATEGORY_ORDER = 7L
    }
}
