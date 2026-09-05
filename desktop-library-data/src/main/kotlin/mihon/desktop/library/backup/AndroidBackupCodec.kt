@file:OptIn(ExperimentalSerializationApi::class)

package mihon.desktop.library.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import okio.gzip
import okio.sink
import okio.source
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class AndroidBackupCodec private constructor(
    private val protoBuf: ProtoBuf,
    private val compressedSize: (Path) -> Long,
    private val sourceFactory: (Path) -> Source,
) {
    constructor(protoBuf: ProtoBuf = ProtoBuf) : this(
        protoBuf,
        Files::size,
        { path -> Files.newInputStream(path).source() },
    )

    internal constructor(
        compressedSize: (Path) -> Long,
        sourceFactory: (Path) -> Source,
        protoBuf: ProtoBuf = ProtoBuf,
    ) : this(protoBuf, compressedSize, sourceFactory)

    fun decode(path: Path, limits: BackupLimits = BackupLimits.DEFAULT): AndroidBackup {
        val compressedBytes = compressedSize(path)
        if (compressedBytes > limits.maxCompressedBytes) {
            throw BackupDecodeException.compressed(compressedBytes)
        }

        var gzipPayload = false
        try {
            CompressedLimitSource(sourceFactory(path), limits.maxCompressedBytes).buffer().use { source ->
                val magic = source.peek().run {
                    if (request(2)) {
                        intArrayOf(readByte().toInt() and 0xff, readByte().toInt() and 0xff)
                    } else {
                        intArrayOf()
                    }
                }
                gzipPayload = magic.contentEquals(intArrayOf(GZIP_MAGIC_FIRST, GZIP_MAGIC_SECOND))
                val payload: Source = if (gzipPayload) source.gzip() else source
                val bytes = ExpandedLimitSource(payload, limits.maxExpandedBytes).buffer().use {
                    it.readByteArray()
                }
                rejectLegacyJson(bytes)
                return try {
                    protoBuf.decodeFromByteArray(AndroidBackup.serializer(), bytes)
                } catch (error: SerializationException) {
                    throw BackupDecodeException.invalidProto(error)
                }
            }
        } catch (error: BackupDecodeException) {
            throw error
        } catch (error: IOException) {
            if (gzipPayload) {
                throw BackupDecodeException.corruptGzip(error)
            }
            throw BackupDecodeException.invalidProto(error)
        }
    }

    fun encode(backup: AndroidBackup, path: Path) {
        val bytes = protoBuf.encodeToByteArray(AndroidBackup.serializer(), backup)
        Files.createDirectories(path.parent ?: Path.of("."))
        val temp = path.resolveSibling("${path.fileName}.tmp")
        Files.newOutputStream(temp).sink().gzip().buffer().use { sink ->
            sink.write(bytes)
        }
        Files.move(
            temp,
            path,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private fun rejectLegacyJson(bytes: ByteArray) {
        if (
            bytes.size >= 2 &&
            bytes[0] == JSON_OBJECT_START &&
            bytes[1] in LEGACY_JSON_SECOND_BYTES
        ) {
            throw BackupDecodeException.legacyJson()
        }
    }

    private companion object {
        const val GZIP_MAGIC_FIRST = 0x1f
        const val GZIP_MAGIC_SECOND = 0x8b
        val JSON_OBJECT_START = '{'.code.toByte()
        val LEGACY_JSON_SECOND_BYTES = byteArrayOf('}'.code.toByte(), '"'.code.toByte(), '\n'.code.toByte())
    }
}

private class CompressedLimitSource(
    delegate: Source,
    private val maxCompressedBytes: Long,
) : ForwardingSource(delegate) {
    private var compressedBytes = 0L

    override fun read(sink: Buffer, byteCount: Long): Long {
        val remaining = maxCompressedBytes - compressedBytes
        val boundedByteCount = if (remaining == Long.MAX_VALUE) byteCount else minOf(byteCount, remaining + 1)
        val read = super.read(sink, boundedByteCount)
        if (read > 0) {
            compressedBytes += read
            if (compressedBytes > maxCompressedBytes) {
                throw BackupDecodeException.compressed(compressedBytes)
            }
        }
        return read
    }
}

class BackupDecodeException private constructor(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    enum class Kind {
        COMPRESSED_LIMIT,
        EXPANDED_LIMIT,
        LEGACY_JSON,
        CORRUPT_GZIP,
        INVALID_PROTOBUF,
    }

    companion object {
        fun compressed(bytes: Long) = BackupDecodeException(
            kind = Kind.COMPRESSED_LIMIT,
            message = "Backup compressed size exceeds its limit: $bytes bytes",
        )

        fun expanded(bytes: Long) = BackupDecodeException(
            kind = Kind.EXPANDED_LIMIT,
            message = "Backup expanded size exceeds its limit: $bytes bytes",
        )

        fun legacyJson() = BackupDecodeException(
            kind = Kind.LEGACY_JSON,
            message = "Legacy JSON backups are not supported",
        )

        fun corruptGzip(cause: Throwable) = BackupDecodeException(
            kind = Kind.CORRUPT_GZIP,
            message = "Backup gzip stream is corrupt or truncated",
            cause = cause,
        )

        fun invalidProto(cause: Throwable) = BackupDecodeException(
            kind = Kind.INVALID_PROTOBUF,
            message = "Backup is not valid Android ProtoBuf data",
            cause = cause,
        )
    }
}

private class ExpandedLimitSource(
    delegate: Source,
    private val maxExpandedBytes: Long,
) : ForwardingSource(delegate) {
    private var expandedBytes = 0L

    override fun read(sink: Buffer, byteCount: Long): Long {
        val remaining = maxExpandedBytes - expandedBytes
        val boundedByteCount = if (remaining == Long.MAX_VALUE) byteCount else minOf(byteCount, remaining + 1)
        val read = super.read(sink, boundedByteCount)
        if (read > 0) {
            expandedBytes += read
            if (expandedBytes > maxExpandedBytes) {
                throw BackupDecodeException.expanded(expandedBytes)
            }
        }
        return read
    }
}
