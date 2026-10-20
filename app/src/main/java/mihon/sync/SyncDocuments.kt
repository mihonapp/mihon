package mihon.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mihon.sync.drive.DriveFile

/**
 * The files at the root of the sync folder, from the single listing a round starts with.
 *
 * Each carries Drive's version counter, which is what lets a round skip a document that nobody
 * touched since this device last settled it instead of downloading it to find out.
 */
class SyncRootListing(files: List<DriveFile>) {

    // Two devices creating the same file at once leave two copies; the newest is authoritative, as
    // when a single file is looked up by name.
    private val byName = files.groupBy { it.name }.mapValues { (_, copies) -> copies.maxBy { it.modifiedTime } }

    operator fun get(name: String): DriveFile? = byName[name]
}

/**
 * What this device last settled for one root document: the Drive version it agreed with, and a
 * digest of its own side of the document at that point.
 */
@Serializable
data class SyncDocumentState(
    @SerialName("version") val version: String,
    @SerialName("localDigest") val localDigest: String = "",
    /** Checksum of the content behind [version]: Drive bumps versions with nothing changed. */
    @SerialName("md5") val md5: String = "",
) {
    fun isSame(remote: DriveFile): Boolean =
        remote.version == version || (md5.isNotEmpty() && remote.md5Checksum == md5)
}

/**
 * Remembers, per root document, the state this device last settled, so that a round where neither
 * side moved costs nothing beyond the listing it already made.
 */
@Inject
@SingleIn(AppScope::class)
class SyncDocuments(
    private val syncPreferences: SyncPreferences,
    private val codec: SyncCodec,
    private val json: Json,
) {

    /**
     * True when Drive still holds the version this device settled and its own side has not changed
     * since: merging again could only reproduce what both already hold.
     */
    fun isSettled(name: String, remote: DriveFile?, localDigest: String = ""): Boolean {
        val known = load()[name] ?: return false
        return remote != null && known.isSame(remote) && localDigest == known.localDigest
    }

    /** Whether Drive still holds the content this device last settled for [name]. */
    fun isUnchanged(name: String, remote: DriveFile): Boolean = load()[name]?.isSame(remote) == true

    /**
     * Records what Drive holds for [name] once this device has settled it: the [file] it agreed
     * with, or the content it just wrote there.
     */
    fun record(name: String, file: DriveFile, content: ByteArray?, localDigest: String = "") {
        val states = load().toMutableMap()
        states[name] = SyncDocumentState(
            version = file.version,
            localDigest = localDigest,
            md5 = content?.let(codec::md5) ?: file.md5Checksum,
        )
        syncPreferences.documentState().set(json.encodeToString(states))
    }

    private fun load(): Map<String, SyncDocumentState> =
        runCatching { json.decodeFromString<Map<String, SyncDocumentState>>(syncPreferences.documentState().get()) }
            .getOrElse { emptyMap() }
}
