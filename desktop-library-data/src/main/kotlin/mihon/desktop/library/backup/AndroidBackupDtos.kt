@file:OptIn(ExperimentalSerializationApi::class)

package mihon.desktop.library.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class AndroidBackup(
    @ProtoNumber(1) val backupManga: List<AndroidBackupManga>,
    @ProtoNumber(2) val backupCategories: List<AndroidBackupCategory> = emptyList(),
    @ProtoNumber(101) val backupSources: List<AndroidBackupSource> = emptyList(),
    @ProtoNumber(104) val backupPreferences: List<AndroidBackupPreference> = emptyList(),
    @ProtoNumber(105) val backupSourcePreferences: List<AndroidBackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) val backupExtensionStores: List<AndroidBackupExtensionStore> = emptyList(),
)

@Serializable
data class AndroidBackupManga(
    @ProtoNumber(1) val source: Long,
    @ProtoNumber(2) val url: String,
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val artist: String? = null,
    @ProtoNumber(5) val author: String? = null,
    @ProtoNumber(6) val description: String? = null,
    @ProtoNumber(7) val genre: List<String> = emptyList(),
    @ProtoNumber(8) val status: Int = 0,
    @ProtoNumber(9) val thumbnailUrl: String? = null,
    @ProtoNumber(13) val dateAdded: Long = 0,
    @ProtoNumber(14) val viewer: Int = 0,
    @ProtoNumber(16) val chapters: List<AndroidBackupChapter> = emptyList(),
    @ProtoNumber(17) val categories: List<Long> = emptyList(),
    @ProtoNumber(18) val tracking: List<AndroidBackupTracking> = emptyList(),
    @ProtoNumber(100) val favorite: Boolean = true,
    @ProtoNumber(101) val chapterFlags: Int = 0,
    @ProtoNumber(103) val viewerFlags: Int? = null,
    @ProtoNumber(104) val history: List<AndroidBackupHistory> = emptyList(),
    @ProtoNumber(105) val updateStrategy: AndroidUpdateStrategy = AndroidUpdateStrategy.ALWAYS_UPDATE,
    @ProtoNumber(106) val lastModifiedAt: Long = 0,
    @ProtoNumber(107) val favoriteModifiedAt: Long? = null,
    @ProtoNumber(108) val excludedScanlators: List<String> = emptyList(),
    @ProtoNumber(109) val version: Long = 0,
    @ProtoNumber(110) val notes: String = "",
    @ProtoNumber(111) val initialized: Boolean = false,
    @ProtoNumber(112) val memo: ByteArray = byteArrayOf(123, 125),
)

@Serializable
enum class AndroidUpdateStrategy {
    ALWAYS_UPDATE,
    ONLY_FETCH_ONCE,
}

@Serializable
data class AndroidBackupChapter(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val scanlator: String? = null,
    @ProtoNumber(4) val read: Boolean = false,
    @ProtoNumber(5) val bookmark: Boolean = false,
    @ProtoNumber(6) val lastPageRead: Long = 0,
    @ProtoNumber(7) val dateFetch: Long = 0,
    @ProtoNumber(8) val dateUpload: Long = 0,
    @ProtoNumber(9) val chapterNumber: Float = 0F,
    @ProtoNumber(10) val sourceOrder: Long = 0,
    @ProtoNumber(11) val lastModifiedAt: Long = 0,
    @ProtoNumber(12) val version: Long = 0,
    @ProtoNumber(13) val memo: ByteArray = byteArrayOf(123, 125),
)

@Serializable
data class AndroidBackupCategory(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val order: Long = 0,
    @ProtoNumber(3) val id: Long = 0,
    @ProtoNumber(100) val flags: Long = 0,
)

@Serializable
data class AndroidBackupHistory(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val lastRead: Long,
    @ProtoNumber(3) val readDuration: Long = 0,
)

@Serializable
data class AndroidBackupSource(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val sourceId: Long,
)

@Serializable
data class AndroidBackupTracking(
    @ProtoNumber(1) val syncId: Int,
    @ProtoNumber(2) val libraryId: Long,
    @Deprecated("Use mediaId instead", level = DeprecationLevel.WARNING)
    @ProtoNumber(3)
    val mediaIdInt: Int = 0,
    @ProtoNumber(4) val trackingUrl: String = "",
    @ProtoNumber(5) val title: String = "",
    @ProtoNumber(6) val lastChapterRead: Float = 0F,
    @ProtoNumber(7) val totalChapters: Int = 0,
    @ProtoNumber(8) val score: Float = 0F,
    @ProtoNumber(9) val status: Int = 0,
    @ProtoNumber(10) val startedReadingDate: Long = 0,
    @ProtoNumber(11) val finishedReadingDate: Long = 0,
    @ProtoNumber(12) val private: Boolean = false,
    @ProtoNumber(100) val mediaId: Long = 0,
)

@Serializable
data class AndroidBackupPreference(
    @ProtoNumber(1) val key: String,
    @ProtoNumber(2) val value: AndroidPreferenceValue,
)

@Serializable
data class AndroidBackupSourcePreferences(
    @ProtoNumber(1) val sourceKey: String,
    @ProtoNumber(2) val prefs: List<AndroidBackupPreference>,
)

@Serializable
sealed class AndroidPreferenceValue

@Serializable
@SerialName("eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue")
data class AndroidIntPreferenceValue(val value: Int) : AndroidPreferenceValue()

@Serializable
@SerialName("eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue")
data class AndroidLongPreferenceValue(val value: Long) : AndroidPreferenceValue()

@Serializable
@SerialName("eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue")
data class AndroidFloatPreferenceValue(val value: Float) : AndroidPreferenceValue()

@Serializable
@SerialName("eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue")
data class AndroidStringPreferenceValue(val value: String) : AndroidPreferenceValue()

@Serializable
@SerialName("eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue")
data class AndroidBooleanPreferenceValue(val value: Boolean) : AndroidPreferenceValue()

@Serializable
@SerialName("eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue")
data class AndroidStringSetPreferenceValue(val value: Set<String>) : AndroidPreferenceValue()

@Serializable
data class AndroidBackupExtensionStore(
    @ProtoNumber(1) val indexUrl: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val badgeLabel: String?,
    @ProtoNumber(4) val contactWebsite: String,
    @ProtoNumber(5) val signingKey: String,
    @ProtoNumber(6) val contactDiscord: String?,
    @ProtoNumber(7) val isLegacy: Boolean?,
    @ProtoNumber(8) val extensionListUrl: String?,
)
