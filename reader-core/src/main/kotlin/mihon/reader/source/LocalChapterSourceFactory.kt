package mihon.reader.source

import mihon.reader.memory.BoundedReaderMemoryBudget
import mihon.reader.memory.ReaderMemoryBudget
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.nio.ByteBuffer

class LocalChapterSourceFactory(
    private val memoryBudget: ReaderMemoryBudget = BoundedReaderMemoryBudget(),
    private val securePathFactory: (ReaderChapterAsset) -> SecureLocalPath = { SecureLocalPath(it.storageRoot) },
) : ChapterSourceFactory {
    override fun create(asset: ReaderChapterAsset): ChapterSource {
        val securePath = securePathFactory(asset)
        if (asset.assetKind == "DIRECTORY") {
            securePath.resolveDirectory(asset.relativePath)
            return DirectoryChapterSource(asset, securePath)
        }

        val signature = securePath.openRegularFile(asset.relativePath).use { channel ->
            val bytes = ByteArray(512)
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) <= 0) break
            }
            bytes.copyOf(buffer.position())
        }
        if (matchesImage(signature)) return StandaloneImageChapterSource(asset, securePath)
        if (asset.assetKind != "ARCHIVE") throw ReaderFailure.UnsupportedFormat(asset.assetKind)

        return when {
            matchesZip(signature) -> if (EpubChapterSource.looksLikeEpub(asset, securePath)) {
                EpubChapterSource(asset, securePath)
            } else {
                ZipChapterSource(asset, securePath)
            }
            SevenZFile.matches(signature, signature.size) -> SevenZipChapterSource(asset, memoryBudget, securePath)
            matchesRar(signature) -> RarChapterSource(asset, securePath)
            matchesGzip(signature) -> compressedTar(asset, securePath, TarCompression.GZIP)
            matchesBzip2(signature) -> compressedTar(asset, securePath, TarCompression.BZIP2)
            matchesXz(signature) -> compressedTar(asset, securePath, TarCompression.XZ)
            TarArchiveInputStream.matches(signature, signature.size) -> TarChapterSource(asset, securePath)
            else -> ReaderFailure.UnsupportedFormat("unknown magic").let { throw it }
        }
    }
}

private fun compressedTar(
    asset: ReaderChapterAsset,
    securePath: SecureLocalPath,
    compression: TarCompression,
): ChapterSource {
    if (!TarChapterSource.hasTarMagic(asset, securePath, compression)) {
        throw ReaderFailure.UnsupportedFormat("compressed stream is not TAR")
    }
    return TarChapterSource(asset, securePath, compression = compression)
}

private fun matchesZip(bytes: ByteArray): Boolean {
    if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte()) return false
    return (bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) ||
        (bytes[2] == 0x05.toByte() && bytes[3] == 0x06.toByte()) ||
        (bytes[2] == 0x07.toByte() && bytes[3] == 0x08.toByte())
}

private fun matchesGzip(bytes: ByteArray): Boolean =
    bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()

private fun matchesBzip2(bytes: ByteArray): Boolean =
    bytes.size >= 3 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'Z'.code.toByte() && bytes[2] == 'h'.code.toByte()

private fun matchesXz(bytes: ByteArray): Boolean {
    val signature = byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00)
    return bytes.size >= signature.size && signature.indices.all { bytes[it] == signature[it] }
}

private fun matchesRar(bytes: ByteArray): Boolean {
    val rarPrefix = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07)
    return bytes.size >= 7 && rarPrefix.indices.all { bytes[it] == rarPrefix[it] } &&
        (bytes[6] == 0x00.toByte() || bytes[6] == 0x01.toByte())
}

private fun matchesImage(bytes: ByteArray): Boolean {
    if (bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
        )
    ) {
        return true
    }
    if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
        return true
    }
    if (bytes.size >= 6) {
        val gif = bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII)
        if (gif == "GIF87a" || gif == "GIF89a") return true
    }
    if (bytes.size >= 2 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) return true
    return matchesWbmp(bytes)
}

private fun matchesWbmp(bytes: ByteArray): Boolean {
    if (bytes.size < 4 || bytes[0] != 0.toByte() || bytes[1] != 0.toByte()) return false
    fun readMultiByte(start: Int): Pair<Long, Int>? {
        var value = 0L
        var index = start
        repeat(5) {
            if (index >= bytes.size) return null
            val current = bytes[index].toInt() and 0xFF
            if (value > (Long.MAX_VALUE ushr 7)) return null
            value = (value shl 7) or (current and 0x7F).toLong()
            index += 1
            if (current and 0x80 == 0) return value to index
        }
        return null
    }
    val width = readMultiByte(2) ?: return false
    val height = readMultiByte(width.second) ?: return false
    return width.first > 0 && height.first > 0
}
