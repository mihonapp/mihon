package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.protobuf.ProtoBuf
import okio.BufferedSource
import okio.buffer
import okio.gzip
import okio.source
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.IOException

@Inject
class BackupDecoder(
    private val context: Context,
    private val parser: ProtoBuf,
) {
    /**
     * Decode everything but the entries of a potentially-gzipped backup, counting the entries without decoding them.
     */
    fun decodeMetadata(uri: Uri): Metadata {
        return read(uri) { reader ->
            orInvalid {
                var entryCount = 0
                while (reader.nextEntry()) {
                    reader.skipEntry()
                    entryCount++
                }
                Metadata(entryCount, reader.readMetadata())
            }
        }
    }

    /**
     * Like [decodeMetadata], but hands each entry to [onEntry] decoded as [entry], which can declare only the fields
     * it needs so the rest are skipped.
     */
    fun <T> decodeMetadata(uri: Uri, entry: DeserializationStrategy<T>, onEntry: (T) -> Unit): Metadata {
        return read(uri) { reader ->
            var entryCount = 0
            while (orInvalid { reader.nextEntry() }) {
                onEntry(orInvalid { reader.readEntry(entry) })
                entryCount++
            }
            Metadata(entryCount, orInvalid { reader.readMetadata() })
        }
    }

    /**
     * Decode the entries of a potentially-gzipped backup one at a time.
     */
    fun decodeEntries(uri: Uri): Flow<BackupManga> = flow {
        read(uri) { reader ->
            while (orInvalid { reader.nextEntry() }) {
                emit(orInvalid { reader.readEntry() })
            }
        }
    }

    private inline fun <T> read(uri: Uri, block: (BackupReader) -> T): T {
        return context.contentResolver.openInputStream(uri)!!.use { inputStream ->
            val source = inputStream.source().buffer()

            val peeked = source.peek().apply {
                require(2)
            }
            val id1id2 = peeked.readShort()
            val backupSource: BufferedSource = when (id1id2.toInt()) {
                0x1f8b -> source.gzip().buffer() // 0x1f8b is gzip magic bytes
                MAGIC_JSON_SIGNATURE1, MAGIC_JSON_SIGNATURE2, MAGIC_JSON_SIGNATURE3 -> {
                    throw IOException(context.stringResource(MR.strings.invalid_backup_file_json))
                }
                else -> source
            }

            backupSource.use { block(BackupReader(it, parser)) }
        }
    }

    // Only around reading, so the JSON error above and anything thrown downstream of emit keep their own messages
    private inline fun <T> orInvalid(block: () -> T): T {
        return try {
            block()
        } catch (e: IOException) {
            throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown), e)
        }
    }

    data class Metadata(
        val entryCount: Int,
        val backup: Backup,
    )

    companion object {
        private const val MAGIC_JSON_SIGNATURE1 = 0x7b7d // `{}`
        private const val MAGIC_JSON_SIGNATURE2 = 0x7b22 // `{"`
        private const val MAGIC_JSON_SIGNATURE3 = 0x7b0a // `{\n`
    }
}
