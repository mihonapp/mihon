package mihon.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mihon.sync.drive.DriveFile

/**
 * What this device knows about one library shard, the last time it reconciled it.
 *
 * Both sides are recorded on purpose:
 *
 * - [remoteVersion] is Drive's own counter. Comparing it — rather than modification times — is what
 *   stops a device re-downloading the shards it just uploaded, with no dependence on clocks agreeing
 *   between devices.
 * - [changeCount] is re-read *after* a merge. Merging chapters moves the entry's change counter through
 *   the database triggers, so without this a pull would immediately look like a local change and be
 *   pushed straight back, and the other device would do the same, forever.
 */
@Serializable
data class SyncShardState(
    @SerialName("fileId") val fileId: String,
    @SerialName("remoteVersion") val remoteVersion: String = "",
    /**
     * The entry's change counter as of the last reconciliation. While it has not moved, nothing the sync
     * publishes about the entry can have changed, so there is no need to rebuild its payload.
     */
    @SerialName("changeCount") val changeCount: Long = -1,
    /**
     * Digest of the payload believed to be on Drive right now.
     *
     * This is what actually decides whether to publish. Comparing local counters against themselves
     * cannot tell "the merge accepted the remote value, so there is nothing to send" from "the merge
     * rejected it and the local value must be sent back" — and getting that wrong the second way
     * leaves a device silently holding a change no one else ever receives.
     */
    @SerialName("contentHash") val contentHash: String = "",
    /**
     * The rules [contentHash] was worked out under. A shard reconciled under older ones is rebuilt
     * and compared again even when nothing local moved: before [CURRENT_FORMAT], for one, entries
     * referred to their categories by position rather than by id.
     */
    @SerialName("format") val format: Int = 1,
    /**
     * Checksum of the content behind [remoteVersion]. Drive bumps a file's version again shortly
     * after an upload with nothing changed, which made every device download back the shard it had
     * just published — and record a change in the history that never happened.
     */
    @SerialName("remoteMd5") val remoteMd5: String = "",
) {
    /** Whether Drive still holds what this device last reconciled for the shard. */
    fun matches(remote: DriveFile): Boolean =
        remote.version == remoteVersion || (remoteMd5.isNotEmpty() && remote.md5Checksum == remoteMd5)

    companion object {
        const val CURRENT_FORMAT = 3
    }
}
