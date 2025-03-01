package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupCovers(
    @ProtoNumber(1) val mappings: List<BackupCover> = emptyList(),
)

@Serializable
data class BackupCover(
    @ProtoNumber(1) val mangaId: Long,
    @ProtoNumber(2) val url: String,
)
