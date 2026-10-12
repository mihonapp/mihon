package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.core.common.extensions.toInstantOrNull
import tachiyomi.domain.history.model.History

@Serializable
data class BackupHistory(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var lastRead: Long,
    @ProtoNumber(3) var readDuration: Long = 0,
) {
    fun getHistoryImpl(): History {
        return History.create().copy(
            readAt = lastRead.toInstantOrNull(),
            readDuration = readDuration,
        )
    }
}
