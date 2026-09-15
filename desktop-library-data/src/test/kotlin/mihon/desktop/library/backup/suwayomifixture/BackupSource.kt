// Adapted from Suwayomi-Server d10e000e1fdcac6f3c84d002f0b459c90c1b00f3 (MPL-2.0).
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package mihon.desktop.library.backup.suwayomifixture

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// Scalars need defaults (proto3 peers omit zero values); see BackupManga.
@Serializable
data class BackupSource(
    @ProtoNumber(1) var name: String = "",
    @ProtoNumber(2) @EncodeDefault var sourceId: Long = 0,
    // suwayomi
    @ProtoNumber(9000) var meta: Map<String, String> = emptyMap(),
)
