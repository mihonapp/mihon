package mihon.sync.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One completed sync round, as recorded in `history.jsonl` on Drive.
 *
 * Stored as JSON Lines rather than one JSON array: appending a line never requires re-reading and
 * rewriting the whole history, and a truncated write costs at most the last entry.
 */
@Serializable
data class SyncHistoryEntry(
    @SerialName("at") val at: Long,
    @SerialName("deviceId") val deviceId: String,
    @SerialName("deviceName") val deviceName: String,
    @SerialName("ok") val ok: Boolean = true,
    @SerialName("error") val error: String? = null,
    /** Entries merged in from other devices. */
    @SerialName("pulled") val pulled: Int = 0,
    /** Entries published from this device. */
    @SerialName("pushed") val pushed: Int = 0,
    @SerialName("added") val added: List<String> = emptyList(),
    @SerialName("removed") val removed: List<String> = emptyList(),
    @SerialName("progressed") val progressed: List<String> = emptyList(),
) {
    val hasChanges: Boolean get() = pulled > 0 || pushed > 0
}

@Serializable
data class SyncDeviceInfo(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("lastSeenAt") val lastSeenAt: Long,
)

@Serializable
data class SyncDeviceRegistry(
    @SerialName("devices") val devices: List<SyncDeviceInfo> = emptyList(),
)

/**
 * One extension the sync knows about, as listed in `extensions.json` on Drive.
 *
 * Extensions are apks, so nothing here installs anything: the list only lets a device say "these
 * are the extensions your other devices use" and offer to fetch them. Nothing is ever removed from
 * it, because a device uninstalling an extension — for want of storage, say — is no reason to stop
 * offering it everywhere else.
 */
@Serializable
data class SyncExtensionInfo(
    @SerialName("pkgName") val pkgName: String,
    @SerialName("name") val name: String,
    @SerialName("lang") val lang: String? = null,
    @SerialName("sourceIds") val sourceIds: List<Long> = emptyList(),
)

@Serializable
data class SyncExtensionRegistry(
    @SerialName("extensions") val extensions: List<SyncExtensionInfo> = emptyList(),
    /**
     * Ids of the sources pinned to the top of the browse list, as the account last left them.
     */
    @SerialName("pinnedSources") val pinnedSources: List<String> = emptyList(),
)

/**
 * What one sync round changed, accumulated as it runs and then written to the history.
 */
class SyncTally {
    val added = mutableListOf<String>()
    val removed = mutableListOf<String>()
    val progressed = mutableListOf<String>()
    var pulled = 0
    var pushed = 0

    /** Shards that could not be merged this round, left for the next one. */
    var failed = 0

    fun toEntry(deviceId: String, deviceName: String, at: Long, error: String? = null) = SyncHistoryEntry(
        at = at,
        deviceId = deviceId,
        deviceName = deviceName,
        ok = error == null,
        error = error,
        pulled = pulled,
        pushed = pushed,
        // Capped so a first sync of a large library cannot bloat every history line.
        added = added.take(MAX_TITLES),
        removed = removed.take(MAX_TITLES),
        progressed = progressed.take(MAX_TITLES),
    )

    private companion object {
        const val MAX_TITLES = 10
    }
}
