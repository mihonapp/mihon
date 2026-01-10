package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupScanlatorFilter(
    @ProtoNumber(1) val scanlator: String? = null,
    @ProtoNumber(2) val priority: Int,
    @ProtoNumber(3) val excluded: Boolean = false,
)
