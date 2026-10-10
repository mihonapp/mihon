package mihon.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.core.common.extensions.JsonObjectEmptyBytes
import okio.buffer
import okio.gzip
import okio.sink
import okio.source
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Reads and writes the documents the sync keeps on Drive.
 *
 * Everything is an ordinary gzipped Mihon backup, so any file the sync produces can also be fed to
 * the normal restore screen if the sync is ever abandoned.
 */
@Inject
@SingleIn(AppScope::class)
class SyncCodec(
    private val protoBuf: ProtoBuf,
) {

    fun encode(backup: Backup): ByteArray {
        val raw = protoBuf.encodeToByteArray(Backup.serializer(), backup)
        val out = ByteArrayOutputStream()
        out.sink().gzip().buffer().use { it.write(raw) }
        return out.toByteArray()
    }

    fun decode(payload: ByteArray): Backup {
        val raw = payload.inputStream().source().gzip().buffer().use { it.readByteArray() }
        return protoBuf.decodeFromByteArray(Backup.serializer(), raw)
    }

    /**
     * Identifies a shard by what the user decided, ignoring everything else it carries.
     *
     * This digest is what decides whether a device publishes an entry, so two devices that agree on
     * every decision must arrive at the same one — otherwise each keeps handing the entry back to the
     * other on every round, for ever. The rule that keeps that true: a field the merge may decline to
     * take from the incoming side must not count here, because nothing could ever settle a
     * difference in it. Three kinds of field fall under that rule:
     *
     * - Bookkeeping particular to the device that wrote the shard. A merge rewrites the rows it
     *   touches, so modification times and version counters move on the receiving device, and the
     *   dates an entry was added or a chapter fetched were never shared to begin with.
     * - What the source or the tracker provides: titles, covers, descriptions, chapter names and
     *   numbering, a tracker's status and score. Each device refreshes those itself, and the merge
     *   keeps its own copy, so a refresh on one device used to ping-pong until the other refreshed
     *   too — and never stopped when the other lacked the extension.
     * - Order. Nothing imposes one on these rows, neither the queries that read them nor the restore
     *   that writes them.
     *
     * The fields are still published, and a device that does not have the entry yet still receives
     * every one of them: they are only left out of the comparison.
     */
    fun identityDigest(payload: ByteArray): String {
        val backup = decode(payload)
        backup.backupManga.forEach { it.reduceToIdentity() }
        return digest(protoBuf.encodeToByteArray(Backup.serializer(), backup))
    }

    /**
     * Identifies a document by its content alone, whatever compressed it.
     *
     * Comparing the gzipped files instead made two devices whose zlib builds differ see a change
     * where there was none, and upload the same list again on every round.
     */
    fun contentDigest(backup: Backup): String = digest(protoBuf.encodeToByteArray(Backup.serializer(), backup))

    fun textDigest(text: String): String = digest(text.toByteArray())

    /**
     * The checksum Drive reports for a file with this content, to recognise it without a download.
     */
    fun md5(payload: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(payload).joinToString("") { "%02x".format(it) }

    private fun BackupManga.reduceToIdentity() {
        dateAdded = 0
        chapterListAt = 0

        title = ""
        artist = null
        author = null
        description = null
        genre = emptyList()
        status = 0
        thumbnailUrl = null
        initialized = false
        memo = JsonObjectEmptyBytes

        categories = categories.sorted()
        excludedScanlators = excludedScanlators.sorted()
        history = history.sortedBy { it.url }
        tracking = tracking.sortedWith(compareBy({ it.syncId }, { it.mediaId }))
        chapters = chapters.sortedBy { it.url }

        chapters.forEach { it.reduceToIdentity() }
        tracking.forEach { it.reduceToIdentity() }
    }

    /**
     * What is left is the chapter's identity and its reading state: read, bookmarked, how far, and
     * when that was decided.
     */
    private fun BackupChapter.reduceToIdentity() {
        dateFetch = 0
        dateUpload = 0

        name = ""
        scanlator = null
        chapterNumber = 0f
        sourceOrder = 0
        memo = JsonObjectEmptyBytes
    }

    /**
     * What is left is the binding and the furthest chapter read, the only parts of a track the merge
     * takes from the other side. Status, score and dates come from the tracker itself.
     */
    private fun BackupTracking.reduceToIdentity() {
        title = ""
        trackingUrl = ""
        totalChapters = 0
        score = 0f
        status = 0
        startedReadingDate = 0
        finishedReadingDate = 0
        this.private = false
    }

    private fun digest(payload: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
}
