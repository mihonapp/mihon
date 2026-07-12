package eu.kanade.tachiyomi.ui.readinglist

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.charset.Charset

internal class CblDocumentReader(
    private val contentResolver: ContentResolver,
    private val maxBytes: Int = MAX_CBL_DOCUMENT_BYTES,
) {

    fun read(uri: Uri): String {
        val input = contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("The selected document could not be opened")

        return input.use { stream ->
            decodeCblDocument(stream.readLimitedBytes(maxBytes))
        }
    }
}

internal class CblDocumentTooLargeException(
    val maxBytes: Int,
) : IllegalArgumentException("The selected CBL file exceeds the $maxBytes byte import limit")

internal fun decodeCblDocument(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""

    val encoding = when {
        bytes.hasPrefix(UTF8_BOM) -> DocumentEncoding(Charsets.UTF_8, UTF8_BOM.size)
        bytes.hasPrefix(UTF16_LE_BOM) -> DocumentEncoding(Charsets.UTF_16LE, UTF16_LE_BOM.size)
        bytes.hasPrefix(UTF16_BE_BOM) -> DocumentEncoding(Charsets.UTF_16BE, UTF16_BE_BOM.size)
        bytes.looksLikeUtf16LeXml() -> DocumentEncoding(Charsets.UTF_16LE, 0)
        bytes.looksLikeUtf16BeXml() -> DocumentEncoding(Charsets.UTF_16BE, 0)
        else -> DocumentEncoding(Charsets.UTF_8, 0)
    }

    return String(
        bytes = bytes,
        offset = encoding.byteOffset,
        length = bytes.size - encoding.byteOffset,
        charset = encoding.charset,
    ).removePrefix("\uFEFF")
}

private fun InputStream.readLimitedBytes(maxBytes: Int): ByteArray {
    require(maxBytes > 0) { "maxBytes must be positive" }

    val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0

    while (true) {
        val read = read(buffer)
        if (read < 0) break

        total += read
        if (total > maxBytes) {
            throw CblDocumentTooLargeException(maxBytes)
        }
        output.write(buffer, 0, read)
    }

    return output.toByteArray()
}

private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    return prefix.indices.all { index -> this[index] == prefix[index] }
}

private fun ByteArray.looksLikeUtf16LeXml(): Boolean {
    return size >= 4 && this[0] == '<'.code.toByte() && this[1] == 0.toByte()
}

private fun ByteArray.looksLikeUtf16BeXml(): Boolean {
    return size >= 4 && this[0] == 0.toByte() && this[1] == '<'.code.toByte()
}

private data class DocumentEncoding(
    val charset: Charset,
    val byteOffset: Int,
)

internal const val MAX_CBL_DOCUMENT_BYTES = 16 * 1024 * 1024

private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
private val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
private val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
