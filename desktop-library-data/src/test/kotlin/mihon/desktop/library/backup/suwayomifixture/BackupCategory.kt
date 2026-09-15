// Adapted from Suwayomi-Server d10e000e1fdcac6f3c84d002f0b459c90c1b00f3 (MPL-2.0).
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package mihon.desktop.library.backup.suwayomifixture

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// Scalars need defaults (proto3 peers omit zero values); see BackupManga.
// A data class so that SyncManager's category list comparison is structural.
@Serializable
data class BackupCategory(
    @ProtoNumber(1) @EncodeDefault var name: String = "",
    @ProtoNumber(2) var order: Int = 0,
    // @ProtoNumber(3) val updateInterval: Int = 0, 1.x value not used in 0.x
    // Bump by 100 to specify this is a 0.x value
    @ProtoNumber(100) var flags: Int = 0,
    // syncyomi
    @ProtoNumber(601) var version: Long = 0,
    @ProtoNumber(602) var uid: Long = 0,
    @ProtoNumber(603) var lastModifiedAt: Long = 0,
    // suwayomi
    @ProtoNumber(9000) var meta: Map<String, String> = emptyMap(),
)
