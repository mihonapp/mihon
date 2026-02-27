package eu.kanade.tachiyomi.util.system

import java.io.File

/**
 * Safe extension to get canonical file, returns null if fails.
 * Prevents path traversal attacks and handles SecurityException.
 */
fun File.safeCanonicalFile(): File? {
    return try {
        this.canonicalFile
    } catch (e: Exception) {
        null
    }
}

/**
 * Safe extension to check if file exists, returns false on SecurityException.
 */
fun File.safeExists(): Boolean {
    return try {
        this.exists()
    } catch (e: SecurityException) {
        false
    }
}

/**
 * Safe extension to check if file is a directory, returns false on SecurityException.
 */
fun File.safeIsDirectory(): Boolean {
    return try {
        this.isDirectory
    } catch (e: SecurityException) {
        false
    }
}

/**
 * Safe extension to get file length, returns 0 on SecurityException.
 */
fun File.safeLength(): Long {
    return try {
        this.length()
    } catch (e: SecurityException) {
        0L
    }
}
