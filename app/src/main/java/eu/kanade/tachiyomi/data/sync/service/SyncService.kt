package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.sync.models.SData
import kotlinx.serialization.json.Json
import tachiyomi.domain.sync.SyncPreferences

abstract class SyncService(
    val context: Context,
    val json: Json,
    val syncPreferences: SyncPreferences,
) {
    abstract suspend fun doSync(syncData: SData): Backup?

    /**
     * Decodes the given sync data string into a Backup object.
     *
     * @param data The sync data string to be decoded.
     * @return The decoded Backup object.
     */
    protected fun decodeSyncBackup(data: String): Backup {
        val sData = json.decodeFromString(SData.serializer(), data)
        return sData.backup!!
    }
}
