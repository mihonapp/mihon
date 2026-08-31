package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.PreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.desktop.library.backup.AndroidBackup
import mihon.desktop.library.backup.AndroidBackupCodec
import mihon.desktop.library.backup.AndroidPreferenceValue
import okio.buffer
import okio.gzip
import okio.sink
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@OptIn(ExperimentalSerializationApi::class)
class DesktopBackupImportContractTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `current Android encoder produces bytes understood by desktop`() {
        val android = Backup(
            backupManga = listOf(
                BackupManga(source = 42, url = "/作品/一", title = "作品 一").apply {
                    author = "作者"
                    favorite = true
                    lastModifiedAt = 900
                    version = 7
                    categories = listOf(2)
                    chapters = listOf(
                        BackupChapter("/章节/一", "第 1 话", read = true, bookmark = true, lastPageRead = 12, version = 3),
                    )
                    history = listOf(BackupHistory("/章节/一", lastRead = 800, readDuration = 1234))
                    tracking = listOf(
                        BackupTracking(
                            syncId = 1,
                            libraryId = 2,
                            mediaId = 3,
                            title = "远程作品",
                            lastChapterRead = 1F,
                        ),
                    )
                },
            ),
            backupCategories = listOf(BackupCategory("收藏", order = 2, id = 10, flags = 4)),
            backupSources = listOf(BackupSource("真实来源", 42)),
            backupPreferences = listOf(
                BackupPreference("pref_display_mode_library", StringPreferenceValue("COMPACT_GRID")),
                BackupPreference("__PRIVATE_auth_token", StringPreferenceValue("must-not-appear-in-report")),
                BackupPreference("__APP_STATE_last_version_code", IntPreferenceValue(1)),
                BackupPreference("unrecognized_plan2_key", StringPreferenceValue("unknown")),
            ),
        )

        encodeWithAndroidSerializer(android, "android-encoder.tachibk")
            .let(AndroidBackupCodec()::decode)
            .toSemanticProjection() shouldBe android.toSemanticProjection()
    }

    @Test
    fun `desktop preserves Android legacy fallbacks and every preference value type`() {
        val android = Backup(
            backupManga = listOf(
                BackupManga(source = 99, url = "/legacy", title = "Legacy").apply {
                    viewer = 17
                    updateStrategy = UpdateStrategy.ONLY_FETCH_ONCE
                    tracking = listOf(
                        BackupTracking(
                            syncId = 4,
                            libraryId = 5,
                            mediaIdInt = 73,
                            mediaId = 999,
                            title = "旧追踪",
                            lastChapterRead = 2.5F,
                        ),
                    )
                },
            ),
            backupPreferences = listOf(
                BackupPreference("int", IntPreferenceValue(1)),
                BackupPreference("long", LongPreferenceValue(2L)),
                BackupPreference("float", FloatPreferenceValue(3.5F)),
                BackupPreference("string", StringPreferenceValue("四")),
                BackupPreference("boolean", BooleanPreferenceValue(true)),
                BackupPreference("set", StringSetPreferenceValue(setOf("甲", "乙"))),
            ),
        )

        encodeWithAndroidSerializer(android, "legacy-fallbacks.tachibk")
            .let(AndroidBackupCodec()::decode)
            .toSemanticProjection() shouldBe android.toSemanticProjection()
    }

    private fun encodeWithAndroidSerializer(backup: Backup, fileName: String): Path =
        tempDir.resolve(fileName).also { path ->
            path.sink().gzip().buffer().use { sink ->
                sink.write(ProtoBuf.encodeToByteArray(Backup.serializer(), backup))
            }
        }
}

private fun Backup.toSemanticProjection() = BackupProjection(
    manga = backupManga.map {
        MangaProjection(
            source = it.source,
            url = it.url,
            title = it.title,
            author = it.author,
            favorite = it.favorite,
            viewerFlags = it.viewer_flags ?: it.viewer,
            updateStrategy = it.updateStrategy.name,
            lastModifiedAt = it.lastModifiedAt,
            version = it.version,
            categoryOrders = it.categories,
            chapters = it.chapters.map { chapter ->
                ChapterProjection(
                    url = chapter.url,
                    name = chapter.name,
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    lastPageRead = chapter.lastPageRead,
                    version = chapter.version,
                )
            },
            history = it.history.map { history ->
                HistoryProjection(history.url, history.lastRead, history.readDuration)
            },
            tracking = it.tracking.map { tracking ->
                TrackingProjection(
                    syncId = tracking.syncId,
                    libraryId = tracking.libraryId,
                    mediaId = tracking.effectiveMediaId(),
                    title = tracking.title,
                    lastChapterRead = tracking.lastChapterRead,
                )
            },
        )
    },
    categories = backupCategories.map { CategoryProjection(it.id, it.name, it.order, it.flags) },
    sources = backupSources.map { SourceProjection(it.sourceId, it.name) },
    preferences = backupPreferences.map { PreferenceProjection(it.key, it.value.toPrimitive()) },
)

private fun AndroidBackup.toSemanticProjection() = BackupProjection(
    manga = backupManga.map {
        MangaProjection(
            source = it.source,
            url = it.url,
            title = it.title,
            author = it.author,
            favorite = it.favorite,
            viewerFlags = it.viewerFlags ?: it.viewer,
            updateStrategy = it.updateStrategy.name,
            lastModifiedAt = it.lastModifiedAt,
            version = it.version,
            categoryOrders = it.categories,
            chapters = it.chapters.map { chapter ->
                ChapterProjection(
                    url = chapter.url,
                    name = chapter.name,
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    lastPageRead = chapter.lastPageRead,
                    version = chapter.version,
                )
            },
            history = it.history.map { history ->
                HistoryProjection(history.url, history.lastRead, history.readDuration)
            },
            tracking = it.tracking.map { tracking ->
                TrackingProjection(
                    syncId = tracking.syncId,
                    libraryId = tracking.libraryId,
                    mediaId = tracking.effectiveMediaId(),
                    title = tracking.title,
                    lastChapterRead = tracking.lastChapterRead,
                )
            },
        )
    },
    categories = backupCategories.map { CategoryProjection(it.id, it.name, it.order, it.flags) },
    sources = backupSources.map { SourceProjection(it.sourceId, it.name) },
    preferences = backupPreferences.map { PreferenceProjection(it.key, it.value.toPrimitive()) },
)

@Suppress("DEPRECATION")
private fun BackupTracking.effectiveMediaId(): Long = if (mediaIdInt != 0) mediaIdInt.toLong() else mediaId

@Suppress("DEPRECATION")
private fun mihon.desktop.library.backup.AndroidBackupTracking.effectiveMediaId(): Long =
    if (mediaIdInt != 0) mediaIdInt.toLong() else mediaId

private fun PreferenceValue.toPrimitive(): String = when (this) {
    is IntPreferenceValue -> "int:$value"
    is LongPreferenceValue -> "long:$value"
    is FloatPreferenceValue -> "float:$value"
    is StringPreferenceValue -> "string:$value"
    is BooleanPreferenceValue -> "boolean:$value"
    is StringSetPreferenceValue -> "set:${value.sorted().joinToString(",")}"
}

private fun AndroidPreferenceValue.toPrimitive(): String = when (this) {
    is mihon.desktop.library.backup.AndroidIntPreferenceValue -> "int:$value"
    is mihon.desktop.library.backup.AndroidLongPreferenceValue -> "long:$value"
    is mihon.desktop.library.backup.AndroidFloatPreferenceValue -> "float:$value"
    is mihon.desktop.library.backup.AndroidStringPreferenceValue -> "string:$value"
    is mihon.desktop.library.backup.AndroidBooleanPreferenceValue -> "boolean:$value"
    is mihon.desktop.library.backup.AndroidStringSetPreferenceValue -> "set:${value.sorted().joinToString(",")}"
}

private data class BackupProjection(
    val manga: List<MangaProjection>,
    val categories: List<CategoryProjection>,
    val sources: List<SourceProjection>,
    val preferences: List<PreferenceProjection>,
)

private data class MangaProjection(
    val source: Long,
    val url: String,
    val title: String,
    val author: String?,
    val favorite: Boolean,
    val viewerFlags: Int,
    val updateStrategy: String,
    val lastModifiedAt: Long,
    val version: Long,
    val categoryOrders: List<Long>,
    val chapters: List<ChapterProjection>,
    val history: List<HistoryProjection>,
    val tracking: List<TrackingProjection>,
)

private data class ChapterProjection(
    val url: String,
    val name: String,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Long,
    val version: Long,
)

private data class CategoryProjection(val id: Long, val name: String, val order: Long, val flags: Long)

private data class HistoryProjection(val url: String, val lastRead: Long, val readDuration: Long)

private data class TrackingProjection(
    val syncId: Int,
    val libraryId: Long,
    val mediaId: Long,
    val title: String,
    val lastChapterRead: Float,
)

private data class SourceProjection(val sourceId: Long, val name: String)

private data class PreferenceProjection(val key: String, val value: String)
