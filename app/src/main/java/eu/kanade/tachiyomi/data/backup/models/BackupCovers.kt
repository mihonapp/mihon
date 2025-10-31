package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupCovers(
    @ProtoNumber(1) val covers: List<BackupCover> = emptyList(),
) {
    operator fun invoke(): List<BackupCover> = covers
}

@Serializable
data class BackupCover(
    @ProtoNumber(1) val filename: String,
    @ProtoNumber(2) val mangaUrl: String,
    @ProtoNumber(3) val sourceId: Long,
)
