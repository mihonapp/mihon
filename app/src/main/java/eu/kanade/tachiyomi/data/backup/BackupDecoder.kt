package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.source
import tachiyomi.domain.backup.model.Backup
import tachiyomi.domain.backup.model.BackupSerializer
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BackupDecoder(
    private val context: Context,
    private val parser: ProtoBuf = Injekt.get(),
) {

    /**
     * Decode a potentially-gzipped backup.
     */
    fun decode(uri: Uri): Backup {
        val backupStringSource = context.contentResolver.openInputStream(uri)!!.source().buffer()

        val peeked = backupStringSource.peek()
        peeked.require(2)
        val id1id2 = peeked.readShort()
        val backupString = if (id1id2.toInt() == 0x1f8b) { // 0x1f8b is gzip magic bytes
            backupStringSource.gzip().buffer()
        } else {
            backupStringSource
        }.use { it.readByteArray() }

        return parser.decodeFromByteArray(BackupSerializer, backupString)
    }
}
