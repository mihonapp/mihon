package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupHistory(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var lastRead: Long,
    @ProtoNumber(3) var readDuration: Long = 0,
)

@Deprecated("Replaced with BackupHistory. This is retained for legacy reasons.")
@Serializable
data class BrokenBackupHistory(
    @ProtoNumber(0) var url: String,
    @ProtoNumber(1) var lastRead: Long,
    @ProtoNumber(2) var readDuration: Long = 0,
)
