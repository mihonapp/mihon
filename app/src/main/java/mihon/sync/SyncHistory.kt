package mihon.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.sync.drive.GoogleDriveApi
import mihon.sync.model.SyncDeviceInfo
import mihon.sync.model.SyncDeviceRegistry
import mihon.sync.model.SyncHistoryEntry
import tachiyomi.core.common.util.system.logcat

/**
 * The shared log of every sync round, kept on Drive so each device sees what the others did.
 *
 * Stored as JSON Lines: one entry per line, newest last. Drive has no append operation, so a write
 * is still read-modify-write, but a malformed or truncated line costs one entry instead of the whole
 * file — which a single JSON array would.
 */
@Inject
@SingleIn(AppScope::class)
class SyncHistory(
    private val driveApi: GoogleDriveApi,
    private val syncPreferences: SyncPreferences,
    private val device: SyncDevice,
    private val json: Json,
) {

    suspend fun record(rootId: String, entry: SyncHistoryEntry) {
        if (rootId.isBlank()) return

        // The two files are independent, so neither waits for the other.
        coroutineScope {
            launch { writeEntry(rootId, entry) }
            launch { refreshDevice(rootId, entry.at) }
        }
    }

    private suspend fun writeEntry(rootId: String, entry: SyncHistoryEntry) {
        try {
            val fileId = resolveHistoryFileId(rootId)
            val existing = fileId?.let { driveApi.downloadText(it) }.orEmpty()

            val lines = existing.lineSequence()
                .filter(String::isNotBlank)
                .toMutableList()
            lines += json.encodeToString(entry)

            val trimmed = lines.takeLast(MAX_ENTRIES).joinToString("\n", postfix = "\n")
            val uploaded = driveApi.upsert(
                name = SyncLayout.HISTORY_FILE,
                parentId = rootId,
                content = trimmed.toByteArray(),
                knownId = fileId,
                mimeType = GoogleDriveApi.TEXT_MIME,
            )
            syncPreferences.historyFileId().set(uploaded.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The history is a diagnostic, never a reason to fail a sync that otherwise worked.
            logcat(LogPriority.WARN, e) { "Could not write the sync history" }
        }
    }

    private suspend fun refreshDevice(rootId: String, at: Long) {
        try {
            recordDevice(rootId, at)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not update the device list" }
        }
    }

    /**
     * Keeps this device current in the shared device list after a round that changed nothing, which
     * the history itself no longer records.
     *
     * At most every few hours: when a device was last seen matters to the day, not to the minute,
     * and each refresh costs a download and an upload.
     */
    suspend fun touchDevice(rootId: String, at: Long) {
        if (rootId.isBlank()) return
        if (at - syncPreferences.lastDeviceRecordAt().get() < DEVICE_REFRESH_MS) return

        refreshDevice(rootId, at)
    }

    /**
     * Newest first, for display.
     */
    suspend fun read(limit: Int = MAX_ENTRIES): List<SyncHistoryEntry> {
        val rootId = syncPreferences.rootFolderId().get()
        if (rootId.isBlank()) return emptyList()

        return try {
            val fileId = resolveHistoryFileId(rootId) ?: return emptyList()
            driveApi.downloadText(fileId)
                .orEmpty()
                .lineSequence()
                .filter(String::isNotBlank)
                .mapNotNull { line -> runCatching { json.decodeFromString<SyncHistoryEntry>(line) }.getOrNull() }
                .toList()
                .asReversed()
                .take(limit)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not read the sync history" }
            emptyList()
        }
    }

    /**
     * Empties the shared log, leaving the file in place so the next sync just appends to it again.
     *
     * The history is a record of what happened, not state the sync relies on, so clearing it costs
     * the user nothing beyond the record itself — and it is shared, so it clears for every device.
     */
    suspend fun clear(): Boolean {
        val rootId = syncPreferences.rootFolderId().get()
        if (rootId.isBlank()) return false

        return try {
            val fileId = resolveHistoryFileId(rootId) ?: return true
            driveApi.update(fileId, ByteArray(0), GoogleDriveApi.TEXT_MIME)
            true
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not clear the sync history" }
            false
        }
    }

    suspend fun readDevices(): List<SyncDeviceInfo> {
        val rootId = syncPreferences.rootFolderId().get()
        if (rootId.isBlank()) return emptyList()

        return try {
            val fileId = resolveDevicesFileId(rootId) ?: return emptyList()
            val payload = driveApi.downloadText(fileId).orEmpty()
            if (payload.isBlank()) return emptyList()
            json.decodeFromString<SyncDeviceRegistry>(payload).devices.sortedByDescending { it.lastSeenAt }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not read the device registry" }
            emptyList()
        }
    }

    private suspend fun recordDevice(rootId: String, at: Long) {
        val fileId = resolveDevicesFileId(rootId)
        val existing = fileId?.let { driveApi.downloadText(it) }.orEmpty()
        val registry = runCatching { json.decodeFromString<SyncDeviceRegistry>(existing) }
            .getOrElse { SyncDeviceRegistry() }

        val updated = registry.devices.filterNot { it.id == device.id } +
            SyncDeviceInfo(id = device.id, name = device.name, lastSeenAt = at)

        val uploaded = driveApi.upsert(
            name = SyncLayout.DEVICES_FILE,
            parentId = rootId,
            content = json.encodeToString(SyncDeviceRegistry(updated)).toByteArray(),
            knownId = fileId,
            mimeType = GoogleDriveApi.JSON_MIME,
        )
        syncPreferences.devicesFileId().set(uploaded.id)
        syncPreferences.lastDeviceRecordAt().set(at)
    }

    private suspend fun resolveHistoryFileId(rootId: String): String? =
        syncPreferences.historyFileId().get().ifBlank {
            driveApi.findFile(SyncLayout.HISTORY_FILE, rootId)?.id.orEmpty()
        }.takeIf { it.isNotBlank() }

    private suspend fun resolveDevicesFileId(rootId: String): String? =
        syncPreferences.devicesFileId().get().ifBlank {
            driveApi.findFile(SyncLayout.DEVICES_FILE, rootId)?.id.orEmpty()
        }.takeIf { it.isNotBlank() }

    private companion object {
        /**
         * Enough to cover weeks of activity now that a round exchanging nothing writes no line, while
         * keeping the file a single small download: at 300 lines, rewriting it took five seconds.
         */
        const val MAX_ENTRIES = 100

        const val DEVICE_REFRESH_MS = 6 * 60 * 60 * 1000L
    }
}
