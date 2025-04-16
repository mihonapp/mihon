package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.manga.model.HiddenDuplicate

@Serializable
data class BackupHiddenDuplicate(
    @ProtoNumber(1) var manga1Id: Long,
    @ProtoNumber(2) var manga2Id: Long,
)

val backupHiddenDuplicateMapper = { hiddenDuplicate: HiddenDuplicate ->
    BackupHiddenDuplicate(
        manga1Id = hiddenDuplicate.manga1Id,
        manga2Id = hiddenDuplicate.manga2Id,
    )
}
