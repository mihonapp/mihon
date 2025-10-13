package eu.kanade.tachiyomi.util.storage

import com.hippo.unifile.UniFile

/**
 * Converts a long to a readable file size.
 */
fun Long.toSize(): String {
    val kb = 1000
    val mb = kb * kb
    val gb = mb * kb
    return when {
        this >= gb -> "%.2f GB".format(this.toFloat() / gb)
        this >= mb -> "%.2f MB".format(this.toFloat() / mb)
        this >= kb -> "%.2f KB".format(this.toFloat() / kb)
        else -> "$this B"
    }
}

/**
 * Returns the size of a file or directory.
 */
fun UniFile.size(): Long {
    var totalSize = 0L
    if (isDirectory) {
        listFiles()?.forEach { file ->
            totalSize += if (file.isDirectory) {
                file.size()
            } else {
                val length = file.length()
                if (length > 0) length else 0
            }
        }
    } else {
        totalSize = length()
    }
    return totalSize
}
