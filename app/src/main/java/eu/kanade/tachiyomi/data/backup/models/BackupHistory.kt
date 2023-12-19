package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.history.model.History
import java.util.Date

@Serializable
data class BackupHistory(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var lastRead: Long,
    @ProtoNumber(3) var readDuration: Long = 0,
) {
    fun getHistoryImpl(): History {
        return History.create().copy(
            readAt = Date(lastRead),
            readDuration = readDuration,
        )
    }
}

@Deprecated("Replaced with BackupHistory. This is retained for legacy reasons.")
@Serializable
data class BrokenBackupHistory(
    @ProtoNumber(0) var url: String,
    @ProtoNumber(1) var lastRead: Long,
    @ProtoNumber(2) var readDuration: Long = 0,
) {
    fun toBackupHistory(): BackupHistory {
        return BackupHistory(url, lastRead, readDuration)
    }
}
