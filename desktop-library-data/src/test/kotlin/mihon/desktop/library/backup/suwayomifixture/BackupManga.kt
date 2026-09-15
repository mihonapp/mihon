// Adapted from Suwayomi-Server d10e000e1fdcac6f3c84d002f0b459c90c1b00f3 (MPL-2.0).
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package mihon.desktop.library.backup.suwayomifixture

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Every scalar field of the backup models needs a default: proto3 peers (the SyncYomi server
 * re-encodes backups with proto3 semantics) and other clients (Mihon, TachiyomiSY) omit
 * zero-valued scalars on the wire, so a required field fails to decode a library that
 * originated elsewhere with "Field 'x' is required ... but it was missing".
 *
 * [EncodeDefault] on the historically required fields keeps emitting their zero values so
 * that Mihon/TachiyomiSY, whose models still require them, can import Suwayomi backups.
 */
@Serializable
data class BackupManga(
    // in 1.x some of these values have different names
    @ProtoNumber(1) @EncodeDefault var source: Long = 0,
    // url is called key in 1.x
    @ProtoNumber(2) @EncodeDefault var url: String = "",
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var artist: String? = null,
    @ProtoNumber(5) var author: String? = null,
    @ProtoNumber(6) var description: String? = null,
    @ProtoNumber(7) var genre: List<String> = emptyList(),
    @ProtoNumber(8) var status: Int = 0,
    // thumbnailUrl is called cover in 1.x
    @ProtoNumber(9) var thumbnailUrl: String? = null,
    // @ProtoNumber(10) val customCover: String = "", 1.x value, not used in 0.x
    // @ProtoNumber(11) val lastUpdate: Long = 0, 1.x value, not used in 0.x
    // @ProtoNumber(12) val lastInit: Long = 0, 1.x value, not used in 0.x
    @ProtoNumber(13) var dateAdded: Long = 0,
    @ProtoNumber(14) var viewer: Int = 0, // Replaced by viewer_flags
    // @ProtoNumber(15) val flags: Int = 0, 1.x value, not used in 0.x
    @ProtoNumber(16) var chapters: List<BackupChapter> = emptyList(),
    @ProtoNumber(17) var categories: List<Int> = emptyList(),
    @ProtoNumber(18) var tracking: List<BackupTracking> = emptyList(),
    // Bump by 100 for values that are not saved/implemented in 1.x but are used in 0.x
    @ProtoNumber(100) var favorite: Boolean = true,
    @ProtoNumber(101) var chapterFlags: Int = 0,
    // @ProtoNumber(102) var brokenHistory: List<BrokenBackupHistory> = emptyList(),
    @ProtoNumber(103) var viewer_flags: Int? = null,
    @ProtoNumber(104) var history: List<BackupHistory> = emptyList(),
    @ProtoNumber(105) var updateStrategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
    // syncyomi
    @ProtoNumber(106) var lastModifiedAt: Long = 0,
    @ProtoNumber(109) var version: Long = 0,
    @ProtoNumber(111) var initialized: Boolean = false,
    @ProtoNumber(112) var memo: ByteArray = byteArrayOf(123, 125),
    // suwayomi
    @ProtoNumber(9000) var meta: Map<String, String> = emptyMap(),
)
