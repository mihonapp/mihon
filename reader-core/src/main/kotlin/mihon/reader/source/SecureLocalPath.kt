package mihon.reader.source

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

fun interface BeforeSecureOpen {
    fun beforeOpen(path: Path)
}

class SecureLocalPath(
    trustedRoot: Path,
    private val beforeOpen: BeforeSecureOpen = BeforeSecureOpen {},
) {
    val root: Path = trustedRoot.toAbsolutePath().normalize()

    init {
        validateWalk(root, expectDirectory = true)
    }

    fun resolveDirectory(relativePath: Path): Path {
        val resolved = resolve(relativePath)
        validateWalk(resolved, expectDirectory = true)
        return resolved
    }

    fun openRegularFile(relativePath: Path): FileChannel {
        val resolved = resolve(relativePath)
        val before = validateWalk(resolved, expectDirectory = false)
        beforeOpen.beforeOpen(resolved)
        val channel = try {
            FileChannel.open(resolved, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
        } catch (error: Exception) {
            throw ReaderFailure.UnsafePath(relativePath.toString(), error.message ?: "cannot securely open file")
        }
        try {
            val after = validateWalk(resolved, expectDirectory = false)
            if (!before.sameIdentity(after)) throw ReaderFailure.ResourceChanged(relativePath.toString())
            return channel
        } catch (error: Throwable) {
            channel.close()
            throw error
        }
    }

    fun secureAttributes(path: Path): BasicFileAttributes {
        val normalized = path.toAbsolutePath().normalize()
        if (!normalized.startsWith(root)) throw ReaderFailure.UnsafePath(path.toString(), "outside trusted root")
        return validateWalk(normalized, expectDirectory = null)
    }

    private fun resolve(relativePath: Path): Path {
        if (relativePath.isAbsolute) throw ReaderFailure.UnsafePath(relativePath.toString(), "absolute path")
        val normalized = relativePath.normalize()
        if (normalized.toString().isEmpty() || normalized.startsWith("..")) {
            throw ReaderFailure.UnsafePath(relativePath.toString(), "outside trusted root")
        }
        val resolved = root.resolve(normalized).normalize()
        if (!resolved.startsWith(root)) throw ReaderFailure.UnsafePath(relativePath.toString(), "outside trusted root")
        return resolved
    }

    private fun validateWalk(path: Path, expectDirectory: Boolean?): BasicFileAttributes {
        val absolute = path.toAbsolutePath().normalize()
        var current = absolute.root ?: throw ReaderFailure.UnsafePath(path.toString(), "missing filesystem root")
        var attributes: BasicFileAttributes? = null
        for (component in absolute) {
            current = current.resolve(component)
            attributes = try {
                Files.readAttributes(current, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            } catch (error: Exception) {
                throw ReaderFailure.UnsafePath(path.toString(), error.message ?: "unreadable component")
            }
            if (attributes.isSymbolicLink || attributes.isOther) {
                throw ReaderFailure.UnsafePath(path.toString(), "symbolic link or reparse-point component")
            }
        }
        val finalAttributes = attributes ?: throw ReaderFailure.UnsafePath(path.toString(), "missing path")
        when (expectDirectory) {
            true -> if (!finalAttributes.isDirectory) throw ReaderFailure.UnsafePath(path.toString(), "not a directory")
            false -> if (!finalAttributes.isRegularFile) {
                throw ReaderFailure.UnsafePath(
                    path.toString(),
                    "not a regular file",
                )
            }
            null -> Unit
        }
        return finalAttributes
    }
}

private fun BasicFileAttributes.sameIdentity(other: BasicFileAttributes): Boolean {
    val leftKey = fileKey()
    val rightKey = other.fileKey()
    return isRegularFile == other.isRegularFile &&
        isDirectory == other.isDirectory &&
        size() == other.size() &&
        lastModifiedTime() == other.lastModifiedTime() &&
        creationTime() == other.creationTime() &&
        (leftKey == null || rightKey == null || leftKey == rightKey)
}
