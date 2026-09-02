package mihon.reader.source

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale

fun interface BeforeSecureOpen {
    fun beforeOpen(path: Path)
}

class SecureLocalPath(
    trustedRoot: Path,
    private val beforeOpen: BeforeSecureOpen = BeforeSecureOpen {},
) {
    val root: Path = trustedRoot.toAbsolutePath().normalize()
    private val windowsAccess = if (isWindows()) WindowsNativePathAccess else null
    private val trustedRootIdentity: WindowsFileIdentity?
    private var windowsChannelOpener: (Path) -> WindowsNativeFileChannel = WindowsNativePathAccess::openFileChannel
    private var windowsDirectoryLister: (Path, Int) -> WindowsDirectorySnapshot =
        WindowsNativePathAccess::listDirectory

    init {
        trustedRootIdentity = windowsAccess?.let { validateWindowsWalk(root, expectDirectory = true).last().identity }
        if (windowsAccess == null) validateNioWalk(root, expectDirectory = true)
    }

    fun resolveDirectory(relativePath: Path): Path {
        val resolved = resolve(relativePath)
        validateWalk(resolved, expectDirectory = true)
        return resolved
    }

    fun openRegularFile(relativePath: Path): FileChannel {
        val resolved = resolve(relativePath)
        val windows = windowsAccess
        return if (windows == null) {
            openNioRegularFile(relativePath, resolved)
        } else {
            openWindowsRegularFile(relativePath, resolved)
        }
    }

    fun secureAttributes(path: Path): BasicFileAttributes {
        val normalized = path.toAbsolutePath().normalize()
        if (!normalized.startsWith(root)) throw ReaderFailure.UnsafePath(path.toString(), "outside trusted root")
        return validateWalk(normalized, expectDirectory = null)
    }

    fun listDirectory(
        relativePath: Path,
        maxEntries: Int = ReaderLimits.MAX_ENTRIES,
    ): List<Path> {
        require(maxEntries >= 0) { "maxEntries must not be negative" }
        val resolved = resolve(relativePath)
        val windows = windowsAccess
        return if (windows == null) {
            val before = validateNioWalk(resolved, expectDirectory = true)
            val children = mutableListOf<Path>()
            Files.newDirectoryStream(resolved).use { stream ->
                stream.forEach { child ->
                    if (children.size >= maxEntries) throw ReaderFailure.TooManyEntries(ReaderLimits.MAX_ENTRIES)
                    children.add(child)
                }
            }
            val after = validateNioWalk(resolved, expectDirectory = true)
            if (!before.sameIdentity(after)) throw ReaderFailure.ResourceChanged(relativePath.toString())
            children
        } else {
            val before = validateWindowsWalk(resolved, expectDirectory = true)
            val snapshot = try {
                windowsDirectoryLister(resolved, maxEntries)
            } catch (error: ReaderFailure) {
                throw error
            } catch (error: Exception) {
                throw ReaderFailure.UnsafePath(relativePath.toString(), error.message ?: "cannot list directory")
            }
            val after = validateWindowsWalk(resolved, expectDirectory = true)
            if (snapshot.names.size > maxEntries) throw ReaderFailure.TooManyEntries(ReaderLimits.MAX_ENTRIES)
            if (
                snapshot.identity != before.last().identity ||
                before.map { it.identity } != after.map { it.identity }
            ) {
                throw ReaderFailure.ResourceChanged(relativePath.toString())
            }
            snapshot.names.map(resolved::resolve)
        }
    }

    private fun openWindowsRegularFile(
        relativePath: Path,
        resolved: Path,
    ): FileChannel {
        val before = validateWindowsWalk(resolved, expectDirectory = false)
        beforeOpen.beforeOpen(resolved)
        val channel = try {
            windowsChannelOpener(resolved)
        } catch (error: Exception) {
            throw ReaderFailure.UnsafePath(relativePath.toString(), error.message ?: "cannot securely open file")
        }
        try {
            val after = validateWindowsWalk(resolved, expectDirectory = false)
            val identitiesStable = before.map { it.identity } == after.map { it.identity }
            if (!identitiesStable || channel.identity != before.last().identity) {
                throw ReaderFailure.ResourceChanged(relativePath.toString())
            }
            return channel
        } catch (error: Throwable) {
            channel.close()
            throw error
        }
    }

    private fun openNioRegularFile(relativePath: Path, resolved: Path): FileChannel {
        val before = validateNioWalk(resolved, expectDirectory = false)
        beforeOpen.beforeOpen(resolved)
        val channel = try {
            FileChannel.open(resolved, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
        } catch (error: Exception) {
            throw ReaderFailure.UnsafePath(relativePath.toString(), error.message ?: "cannot securely open file")
        }
        try {
            val after = validateNioWalk(resolved, expectDirectory = false)
            if (!before.sameIdentity(after)) throw ReaderFailure.ResourceChanged(relativePath.toString())
            return channel
        } catch (error: Throwable) {
            channel.close()
            throw error
        }
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
        val windows = windowsAccess
        return if (windows == null) {
            validateNioWalk(path, expectDirectory)
        } else {
            validateWindowsWalk(path, expectDirectory).last().attributes
        }
    }

    private fun validateWindowsWalk(path: Path, expectDirectory: Boolean?): List<WindowsPathMetadata> {
        val absolute = path.toAbsolutePath().normalize()
        val fileSystemRoot = absolute.root ?: throw ReaderFailure.UnsafePath(path.toString(), "missing filesystem root")
        val paths = buildList {
            var current = fileSystemRoot
            add(current)
            for (component in absolute) {
                current = current.resolve(component)
                add(current)
            }
        }
        val metadata = paths.map { component ->
            try {
                requireNotNull(windowsAccess).readMetadata(component)
            } catch (error: Exception) {
                throw ReaderFailure.UnsafePath(path.toString(), error.message ?: "unreadable component")
            }
        }
        val rootIndex = paths.indexOf(root)
        if (rootIndex < 0 || (metadata[rootIndex].identity != trustedRootIdentity && trustedRootIdentity != null)) {
            throw ReaderFailure.ResourceChanged(root.toString())
        }
        checkExpectedType(path, metadata.last().attributes, expectDirectory)
        return metadata
    }

    private fun validateNioWalk(path: Path, expectDirectory: Boolean?): BasicFileAttributes {
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
        checkExpectedType(path, finalAttributes, expectDirectory)
        return finalAttributes
    }

    internal companion object {
        fun forWindowsTesting(
            trustedRoot: Path,
            opener: (Path) -> WindowsNativeFileChannel,
        ): SecureLocalPath = SecureLocalPath(trustedRoot).also { it.windowsChannelOpener = opener }

        fun forWindowsDirectoryTesting(
            trustedRoot: Path,
            lister: (Path) -> WindowsDirectorySnapshot,
        ): SecureLocalPath = SecureLocalPath(trustedRoot).also { securePath ->
            securePath.windowsDirectoryLister = { path, _ -> lister(path) }
        }
    }
}

private fun checkExpectedType(path: Path, attributes: BasicFileAttributes, expectDirectory: Boolean?) {
    when (expectDirectory) {
        true -> if (!attributes.isDirectory) throw ReaderFailure.UnsafePath(path.toString(), "not a directory")
        false -> if (!attributes.isRegularFile) throw ReaderFailure.UnsafePath(path.toString(), "not a regular file")
        null -> Unit
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

private fun isWindows(): Boolean = System.getProperty("os.name").lowercase(Locale.ROOT).startsWith("windows")
