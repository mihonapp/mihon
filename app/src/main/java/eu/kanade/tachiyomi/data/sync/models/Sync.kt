package eu.kanade.tachiyomi.data.sync.models

import eu.kanade.tachiyomi.data.backup.models.Backup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncStatus(
    @SerialName("last_synced") val lastSynced: String? = null,
    val status: String? = null,
)

@Serializable
data class SyncDevice(
    val id: Int? = null,
    val name: String? = null,
)

@Serializable
data class SData(
    val sync: SyncStatus? = null,
    val backup: Backup? = null,
    val device: SyncDevice? = null,
)
