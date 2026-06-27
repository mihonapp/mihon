package tachiyomi.core.common.storage

import com.hippo.unifile.UniFile
import java.io.IOException

val UniFile.extension: String?
    get() = name?.substringAfterLast('.')

val UniFile.nameWithoutExtension: String?
    get() = name?.substringBeforeLast('.')

val UniFile.displayablePath: String
    get() = filePath ?: uri.toString()

/**
 * Renames this file, falling back to copying and deleting it when the storage provider does not
 * support renaming documents.
 *
 * @return the renamed file, or the newly created file when the fallback is used.
 */
fun UniFile.renameToOrCopy(targetName: String): UniFile {
    if (renameTo(targetName)) return this

    val parent = parentFile
        ?: throw IOException("Failed to rename $displayablePath: parent directory is unavailable")
    if (parent.findFile(targetName) != null) {
        throw IOException("Failed to rename $displayablePath: target '$targetName' already exists")
    }

    val target = createCopyTarget(parent, targetName)
        ?: throw IOException("Failed to rename $displayablePath: could not create target '$targetName'")

    try {
        copyContentsTo(target)
    } catch (e: Exception) {
        val rollbackFailed = !target.deleteSafely()
        val rollbackMessage = if (rollbackFailed) "; failed to remove incomplete target" else ""
        throw IOException("Failed to copy $displayablePath to '$targetName'$rollbackMessage", e)
    }

    val sourceDeleted = try {
        delete()
    } catch (e: Exception) {
        val rollbackFailed = !target.deleteSafely()
        val rollbackMessage = if (rollbackFailed) "; failed to remove copied target" else ""
        throw IOException("Copied $displayablePath to '$targetName', but failed to delete source$rollbackMessage", e)
    }
    if (!sourceDeleted) {
        val rollbackFailed = !target.deleteSafely()
        val rollbackMessage = if (rollbackFailed) "; failed to remove copied target" else ""
        throw IOException("Copied $displayablePath to '$targetName', but failed to delete source$rollbackMessage")
    }

    return target
}

private fun UniFile.createCopyTarget(parent: UniFile, targetName: String): UniFile? =
    if (isDirectory) parent.createDirectory(targetName) else parent.createFile(targetName)

private fun UniFile.deleteSafely(): Boolean = try {
    delete()
} catch (_: Exception) {
    false
}

private fun UniFile.copyContentsTo(target: UniFile) {
    if (isDirectory) {
        val children = listFiles()
            ?: throw IOException("Failed to list directory $displayablePath")
        children.forEach { child ->
            val childName = child.name
                ?: throw IOException("A file in $displayablePath has no name")
            val childTarget = child.createCopyTarget(target, childName)
                ?: throw IOException("Failed to create '$childName' in " + target.displayablePath)
            child.copyContentsTo(childTarget)
        }
    } else {
        openInputStream().use { input ->
            target.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
