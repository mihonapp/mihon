package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer
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
    fun decodeMetadata(uri: Uri): Pair<Int, Backup> {
        context.contentResolver.openInputStream(uri)!!.use { inputStream ->
            val source = inputStream.source().buffer().prepareBackupSource(context)

            var mangaCount = 0
            while (!source.exhausted()) {
                val tag = source.readByte().toInt() and 0xFF
                if (tag == 0x0A) {
                    mangaCount++
                    val length = source.readVarInt().toLong()
                    source.skip(length)
                } else {
                    val tagBuffer = Buffer().writeByte(tag)
                    tagBuffer.writeAll(source)

                    val backup = try {
                        parser.decodeFromByteArray(Backup.serializer(), tagBuffer.readByteArray())
                    } catch (_: SerializationException) {
                        throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
                    }

                    return Pair(mangaCount, backup)
                }
            }
            return Pair(mangaCount, Backup(emptyList()))
        }
    }

    fun decodeManga(uri: Uri): Flow<BackupManga> = flow {
        context.contentResolver.openInputStream(uri)!!.use { inputStream ->
            val source = inputStream.source().buffer().prepareBackupSource(context)

            while (!source.exhausted()) {
                val tag = source.readByte().toInt() and 0xFF
                if (tag == 0x0A) {
                    val length = source.readVarInt().toLong()
                    val bytes = source.readByteArray(length)

                    val manga = try {
                        parser.decodeFromByteArray(BackupManga.serializer(), bytes)
                    } catch (_: SerializationException) {
                        throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
                    }

                    emit(manga)
                } else {
                    break
                }
            }
        }
    }

    private fun BufferedSource.readVarInt(): Int {
        var result = 0
        var shift = 0

        while (shift < 32) {
            val b = readByte().toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)

            if ((b and 0x80) == 0) {
                return result
            }

            shift += 7
        }

        throw IOException(context.stringResource(MR.strings.invalid_backup_file_json))
    }

    private fun BufferedSource.prepareBackupSource(context: Context): BufferedSource {
        val source = if (peek().request(2) && peek().readShort().toInt() == 0x1f8b) {
            gzip().buffer()
        } else {
            this
        }

        val peeked = source.peek()
        if (peeked.request(2)) {
            val id1id2 = peeked.readShort().toInt()
            if (id1id2 == MAGIC_JSON_SIGNATURE1 || id1id2 == MAGIC_JSON_SIGNATURE2 || id1id2 == MAGIC_JSON_SIGNATURE3) {
                throw IOException(context.stringResource(MR.strings.invalid_backup_file_json))
            }
        }

        return source
    }

    companion object {
        private const val MAGIC_JSON_SIGNATURE1 = 0x7b7d // `{}`
        private const val MAGIC_JSON_SIGNATURE2 = 0x7b22 // `{"`
        private const val MAGIC_JSON_SIGNATURE3 = 0x7b0a // `{\n`
    }
}
