package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.Backup
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.source
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.IOException
import java.io.InputStream

@Inject
class BackupDecoder(
    private val context: Context,
    private val parser: ProtoBuf,
) {
    /**
     * Decode a potentially-gzipped backup.
     */
    fun decode(uri: Uri): Backup {
        val inputStream = openOrNull(uri)
            ?: openOrNull(mediaStoreUri(uri))
            ?: throw IOException("Can't open backup file")

        return inputStream.use {
            val source = it.source().buffer()

            val peeked = source.peek().apply {
                require(2)
            }
            val id1id2 = peeked.readShort()
            val backupString = when (id1id2.toInt()) {
                0x1f8b -> source.gzip().buffer() // 0x1f8b is gzip magic bytes
                MAGIC_JSON_SIGNATURE1, MAGIC_JSON_SIGNATURE2, MAGIC_JSON_SIGNATURE3 -> {
                    throw IOException(context.stringResource(MR.strings.invalid_backup_file_json))
                }
                else -> source
            }.use { it.readByteArray() }

            try {
                parser.decodeFromByteArray(Backup.serializer(), backupString)
            } catch (_: SerializationException) {
                throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
            }
        }
    }

    private fun mediaStoreUri(uri: Uri): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()
    }

    private fun openOrNull(uri: Uri?): InputStream? {
        if (uri == null) return null
        return runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
    }

    companion object {
        private const val MAGIC_JSON_SIGNATURE1 = 0x7b7d // `{}`
        private const val MAGIC_JSON_SIGNATURE2 = 0x7b22 // `{"`
        private const val MAGIC_JSON_SIGNATURE3 = 0x7b0a // `{\n`
    }
}
