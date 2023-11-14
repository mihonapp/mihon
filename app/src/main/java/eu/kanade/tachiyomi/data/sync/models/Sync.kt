package eu.kanade.tachiyomi.data.sync.models

import eu.kanade.tachiyomi.data.backup.models.Backup
import kotlinx.serialization.Serializable

@Serializable
data class SyncData(
    val backup: Backup? = null,
)
