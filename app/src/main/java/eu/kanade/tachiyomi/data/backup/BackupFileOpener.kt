package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.Inject
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.storage.service.StoragePreferences
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Opens a user-picked backup URI.
 *
 * Volume-root ExternalStorage document URIs often arrive without a usable grant.
 * The same file is opened through the app storage-location tree, which is how
 * automatic backups are already written. Persisted tree grants are only used to
 * rewrite the picker URI into a tree document URI.
 */
@Inject
class BackupFileOpener(
    private val context: Context,
    private val storagePreferences: StoragePreferences,
    private val storageManager: StorageManager,
) {
    fun open(uri: Uri): InputStream {
        val resolver = context.contentResolver
        val storageDir = storagePreferences.baseStorageDirectory.get()
        return openBackupFile(
            uri = uri.toString(),
            treeUris = buildList {
                if (storageDir.isNotEmpty()) add(storageDir)
                addAll(
                    resolver.persistedUriPermissions
                        .filter { it.isReadPermission }
                        .map { it.uri.toString() },
                )
            },
            openUri = { candidate -> openBackupUri(candidate.toUri()) },
            relativeTreeUri = storageDir.takeIf { it.isNotEmpty() },
            openRelative = { relative -> openViaStorageTree(storageDir, relative) },
            openByName = { name -> openAutomaticBackup(name) },
            openPath = { path -> runCatching { File(path).inputStream() }.getOrNull() },
            primaryRoot = Environment.getExternalStorageDirectory().absolutePath,
        )
    }

    private fun openAutomaticBackup(name: String): InputStream? {
        storageManager.getAutomaticBackupsDirectory()?.findFile(name)?.let { file ->
            try {
                return file.openInputStream()
            } catch (_: Exception) {
            }
        }
        return openViaStorageTree(
            storagePreferences.baseStorageDirectory.get(),
            "autobackup/$name",
        )
    }

    private fun openViaStorageTree(storageDir: String, relative: String): InputStream? {
        if (storageDir.isEmpty()) return null
        var current = UniFile.fromUri(context, storageDir.toUri()) ?: return null
        for (part in relative.split('/')) {
            if (part.isEmpty()) continue
            current = current.findFile(part) ?: return null
        }
        return try {
            current.openInputStream()
        } catch (_: Exception) {
            null
        }
    }

    private fun openBackupUri(uri: Uri): InputStream? {
        val resolver = context.contentResolver
        try {
            resolver.openInputStream(uri)?.let { return it }
        } catch (_: Exception) {
        }
        try {
            resolver.openFileDescriptor(uri, "r")?.let {
                return ParcelFileDescriptor.AutoCloseInputStream(it)
            }
        } catch (_: Exception) {
        }
        try {
            resolver.openTypedAssetFileDescriptor(uri, "*/*", null)?.createInputStream()?.let {
                return it
            }
        } catch (_: Exception) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                MediaStore.getMediaUri(context, uri)?.let { mediaUri ->
                    resolver.openInputStream(mediaUri)?.let { return it }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }
}

internal data class BackupLocation(
    val relativePath: String?,
    val fileName: String?,
    val filePath: String?,
)

internal fun backupLocation(
    uri: String,
    storageTreeUri: String? = null,
    primaryRoot: String? = null,
): BackupLocation {
    return BackupLocation(
        relativePath = storageTreeUri?.let { coveredTree(uri, it) }?.relativePath,
        fileName = backupFileName(uri),
        filePath = primaryRoot?.let { externalStorageFilePath(uri, it) },
    )
}

private fun openBackupFile(
    uri: String,
    treeUris: Iterable<String>,
    openUri: (String) -> InputStream?,
    relativeTreeUri: String? = null,
    openRelative: (String) -> InputStream? = { null },
    openByName: (String) -> InputStream? = { null },
    openPath: (String) -> InputStream? = { null },
    primaryRoot: String? = null,
): InputStream {
    val trees = treeUris.distinct().mapNotNull { treeUri ->
        coveredTree(uri, treeUri)
    }

    val candidates = buildList {
        add(uri)
        trees.forEach { add(it.documentUri) }
    }

    var lastError: Exception? = null
    for (candidate in candidates) {
        try {
            openUri(candidate)?.let { return it }
        } catch (e: Exception) {
            lastError = e
        }
    }

    val location = backupLocation(uri, relativeTreeUri, primaryRoot)
    location.relativePath?.let { relative ->
        try {
            openRelative(relative)?.let { return it }
        } catch (e: Exception) {
            lastError = e
        }
    }
    location.fileName?.let { name ->
        try {
            openByName(name)?.let { return it }
        } catch (e: Exception) {
            lastError = e
        }
    }
    location.filePath?.let { path ->
        try {
            openPath(path)?.let { return it }
        } catch (e: Exception) {
            lastError = e
        }
    }

    throw IOException("Can't open backup file", lastError)
}

private data class CoveredTree(
    val documentUri: String,
    val relativePath: String,
)

private fun coveredTree(documentUri: String, treeUri: String): CoveredTree? {
    val document = parseContentPath(documentUri) ?: return null
    val tree = parseContentPath(treeUri) ?: return null
    if (document.authority != tree.authority) return null

    val documentId = documentId(document.segments) ?: return null
    val treeId = treeId(tree.segments) ?: return null
    if (!treeCovers(treeId, documentId)) return null

    val relativePath = relativePath(treeId, documentId) ?: return null
    return CoveredTree(
        documentUri = "content://${document.authority}/tree/${encodeSegment(treeId)}/" +
            "document/${encodeSegment(documentId)}",
        relativePath = relativePath,
    )
}

private fun backupFileName(uri: String): String? {
    val document = parseContentPath(uri) ?: return null
    if (document.authority != "com.android.externalstorage.documents") return null
    val documentId = documentId(document.segments) ?: return null
    return documentId.substringAfterLast('/').takeIf { it.isNotEmpty() }
}

private fun relativePath(treeId: String, documentId: String): String? {
    return when {
        documentId == treeId -> null
        documentId.startsWith("$treeId/") -> documentId.removePrefix("$treeId/")
        treeId.endsWith(':') && documentId.startsWith(treeId) -> {
            documentId.removePrefix(treeId).trimStart('/')
        }
        else -> null
    }?.takeIf { it.isNotEmpty() }
}

private fun documentId(segments: List<String>): String? {
    if (segments.isEmpty()) return null
    if (segments[0] == "tree") {
        val documentIndex = segments.indexOf("document")
        if (documentIndex < 0 || documentIndex >= segments.lastIndex) return null
        return segments.drop(documentIndex + 1).joinToString("/").ifEmpty { null }
    }
    if (segments[0] == "document") {
        return segments.drop(1).joinToString("/").ifEmpty { null }
    }
    return null
}

private fun treeId(segments: List<String>): String? {
    if (segments.size < 2 || segments[0] != "tree") return null
    val documentIndex = segments.indexOf("document")
    val end = if (documentIndex >= 0) documentIndex else segments.size
    return segments.subList(1, end).joinToString("/").ifEmpty { null }
}

private fun treeCovers(treeId: String, documentId: String): Boolean {
    if (documentId == treeId || documentId.startsWith("$treeId/")) return true
    return treeId.endsWith(':') && documentId.startsWith(treeId)
}

private fun externalStorageFilePath(uri: String, primaryRoot: String): String? {
    val document = parseContentPath(uri) ?: return null
    if (document.authority != "com.android.externalstorage.documents") return null
    val documentId = documentId(document.segments) ?: return null
    val separator = documentId.indexOf(':')
    if (separator <= 0) return null
    val volume = documentId.substring(0, separator)
    val relative = documentId.substring(separator + 1)
    if (relative.isEmpty()) return null
    val root = if (volume.equals("primary", ignoreCase = true)) {
        primaryRoot
    } else {
        "/storage/$volume"
    }
    return "$root/$relative"
}

private fun parseContentPath(uri: String): ContentPath? {
    return try {
        val parsed = URI(uri)
        if (parsed.scheme != "content") return null
        val authority = parsed.authority ?: return null
        val rawPath = parsed.rawPath ?: return null
        val segments = rawPath.trim('/').split('/').map {
            URLDecoder.decode(it, StandardCharsets.UTF_8)
        }
        ContentPath(authority, segments)
    } catch (_: Exception) {
        null
    }
}

private fun encodeSegment(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}

private data class ContentPath(
    val authority: String,
    val segments: List<String>,
)
