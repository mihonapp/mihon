package eu.kanade.tachiyomi.util

import java.io.File
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object DiskUtil {

    fun hashKeyForDisk(key: String): String {
        return try {
            val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
            val sb = StringBuilder()
            bytes.forEach { byte ->
                sb.append(Integer.toHexString(byte.toInt() and 0xFF or 0x100).substring(1, 3))
            }
            sb.toString()
        } catch (e: NoSuchAlgorithmException) {
            key.hashCode().toString()
        }
    }

    fun getDirectorySize(f: File): Long {
        var size: Long = 0
        if (f.isDirectory) {
            for (file in f.listFiles()) {
                size += getDirectorySize(file)
            }
        } else {
            size = f.length()
        }
        return size
    }

    /**
     * Mutate the given filename to make it valid for a FAT filesystem,
     * replacing any invalid characters with "_". This method doesn't allow private files (starting
     * with a dot), but you can manually add it later.
     */
    fun buildValidFilename(origName: String): String {
        val name = origName.trim('.', ' ')
        if (name.isNullOrEmpty()) {
            return "(invalid)"
        }
        val sb = StringBuilder(name.length)
        name.forEach { c ->
            if (isValidFatFilenameChar(c)) {
                sb.append(c)
            } else {
                sb.append('_')
            }
        }
        // Even though vfat allows 255 UCS-2 chars, we might eventually write to
        // ext4 through a FUSE layer, so use that limit minus 15 reserved characters.
        return sb.toString().take(240)
    }

    /**
     * Returns true if the given character is a valid filename character, false otherwise.
     */
    private fun isValidFatFilenameChar(c: Char): Boolean {
        if (0x00.toChar() <= c && c <= 0x1f.toChar()) {
            return false
        }
        return when (c) {
            '"', '*', '/', ':', '<', '>', '?', '\\', '|', 0x7f.toChar() -> false
            else -> true
        }
    }
}

