package mihon.desktop.library.local

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

fun interface LocalStagingCheckpoint {
    fun afterStagingCopy(stagingPath: Path)

    companion object {
        val NONE = LocalStagingCheckpoint {}
    }
}

internal interface LocalFileFaults {
    fun afterCopyChunk(source: Path, target: Path, copiedBytes: Long) = Unit
    fun beforeAtomicMove(source: Path, target: Path) = Unit

    companion object {
        val NONE = object : LocalFileFaults {}
    }
}

class LocalImportStager private constructor(
    private val scanner: LocalImportScanner = LocalImportScanner(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val checkpoint: LocalStagingCheckpoint = LocalStagingCheckpoint.NONE,
    private val fileFaults: LocalFileFaults,
) {
    constructor(
        scanner: LocalImportScanner = LocalImportScanner(),
        idFactory: () -> String = { UUID.randomUUID().toString() },
        checkpoint: LocalStagingCheckpoint = LocalStagingCheckpoint.NONE,
    ) : this(scanner, idFactory, checkpoint, LocalFileFaults.NONE)

    internal constructor(
        fileFaults: LocalFileFaults,
        scanner: LocalImportScanner = LocalImportScanner(),
        idFactory: () -> String = { UUID.randomUUID().toString() },
        checkpoint: LocalStagingCheckpoint = LocalStagingCheckpoint.NONE,
    ) : this(scanner, idFactory, checkpoint, fileFaults)

    fun stage(manifest: LocalImportManifest, localLibraryRoot: Path): StagedLocalManga {
        validateManifestShape(manifest)
        validateNoReparseAncestors(manifest.sourceRoot, "source root")
        requireUnchangedManifest(manifest, scanner.scan(manifest.sourceRoot), "source changed after scan")
        val root = localLibraryRoot.toAbsolutePath().normalize()
        val stagingRoot = root.resolve(STAGING_DIRECTORY)
        val mangaRoot = root.resolve(MANGA_DIRECTORY)
        safeEnsureDirectory(root)
        safeEnsureDirectory(stagingRoot)
        restrictOwnerOnlyWhenSupported(stagingRoot, DIRECTORY_OWNER_PERMISSIONS)
        safeEnsureDirectory(mangaRoot)
        val id = idFactory()
        if (!isValidImportId(id)) rejectStaging("invalid staging identifier")
        val stagingPath = stagingRoot.resolve(id).normalize()
        val finalPath = mangaRoot.resolve(id).normalize()
        requireImmediateChild(stagingRoot, stagingPath, "staging path")
        requireImmediateChild(mangaRoot, finalPath, "promotion path")
        var stagingCreated = false
        try {
            try {
                createPrivateDirectory(stagingPath)
                stagingCreated = true
                createOwnershipMarkerAtomically(stagingPath, id)
            } catch (error: FileAlreadyExistsException) {
                rejectStaging("staging target already exists: $stagingPath", error)
            }
            manifest.chapters.forEach { chapter ->
                val source = resolveManifestEntry(manifest.sourceRoot, chapter.relativePath)
                val target = resolveManifestEntry(stagingPath, chapter.relativePath)
                copyEntryNoFollow(source, target)
            }
            requireUnchangedManifest(manifest, scanner.scan(manifest.sourceRoot), "source changed during staging")
            val stagedManifest = scanner.scan(stagingPath)
            if (stagedManifest.chapters != manifest.chapters || stagedManifest.sha256 != manifest.sha256) {
                rejectStaging("staged manifest verification failed")
            }
            checkpoint.afterStagingCopy(stagingPath)
            return StagedLocalManga(stagingPath, finalPath, manifest, stagingRoot, mangaRoot, fileFaults)
        } catch (error: Throwable) {
            if (stagingCreated) {
                runCatching { deleteValidated(stagingRoot, stagingPath) }.onFailure(error::addSuppressed)
            }
            throw error
        }
    }

    fun cleanupOrphans(localLibraryRoot: Path, retainedFinalPaths: Set<Path>) {
        val root = localLibraryRoot.toAbsolutePath().normalize()
        val stagingRoot = root.resolve(STAGING_DIRECTORY)
        val mangaRoot = root.resolve(MANGA_DIRECTORY)
        safeEnsureDirectory(root)
        safeEnsureDirectory(stagingRoot)
        safeEnsureDirectory(mangaRoot)
        val retained = retainedFinalPaths.mapTo(mutableSetOf()) { retainedPath ->
            val normalized = retainedPath.toAbsolutePath().normalize()
            requireImmediateChild(mangaRoot, normalized, "database local manga path")
            normalized
        }
        deleteOwnedChildren(stagingRoot, emptySet())
        deleteOwnedChildren(mangaRoot, retained)
    }

    private fun validateManifestShape(manifest: LocalImportManifest) {
        if (manifest.chapters.isEmpty()) rejectStaging("manifest contains no chapters")
        scanner.validateCandidatePaths(manifest.sourceRoot, manifest.chapters.map(LocalChapterCandidate::relativePath))
        manifest.chapters.forEach { chapter ->
            if (chapter.name != chapter.relativePath.fileName?.toString()) {
                rejectStaging("manifest chapter name does not match its path: ${chapter.relativePath}")
            }
            if (chapter.relativePath.nameCount != 1) {
                rejectStaging("manifest chapter must be an immediate child: ${chapter.relativePath}")
            }
            if (chapter.sizeBytes < 0) rejectStaging("manifest chapter has a negative size: ${chapter.relativePath}")
        }
    }

    private fun copyEntryNoFollow(source: Path, target: Path) {
        val before = safeSourceAttributes(source)
        when {
            before.isDirectory -> copyDirectoryNoFollow(source, target, before)
            before.isRegularFile -> copyFileNoFollow(source, target, before)
            else -> rejectStaging("source entry is not a regular file or directory: $source")
        }
    }

    private fun copyDirectoryNoFollow(source: Path, target: Path, before: BasicFileAttributes) {
        try {
            Files.createDirectory(target)
            Files.newDirectoryStream(source).use { children ->
                children.forEach { child -> copyEntryNoFollow(child, target.resolve(child.fileName.toString())) }
            }
            verifySourceUnchanged(source, before)
            Files.setLastModifiedTime(target, before.lastModifiedTime())
        } catch (error: LocalImportRejected) {
            throw error
        } catch (error: IOException) {
            rejectStaging("failed to copy source directory without following links: $source", error)
        }
    }

    private fun copyFileNoFollow(source: Path, target: Path, before: BasicFileAttributes) {
        try {
            FileChannel.open(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { input ->
                FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                    val buffer = ByteBuffer.allocateDirect(COPY_BUFFER_SIZE)
                    var copiedBytes = 0L
                    while (input.read(buffer) != -1) {
                        buffer.flip()
                        while (buffer.hasRemaining()) copiedBytes += output.write(buffer)
                        fileFaults.afterCopyChunk(source, target, copiedBytes)
                        buffer.clear()
                    }
                }
            }
            verifySourceUnchanged(source, before)
            Files.setLastModifiedTime(target, before.lastModifiedTime())
        } catch (error: LocalImportRejected) {
            throw error
        } catch (error: IOException) {
            rejectStaging("failed to copy source file without following links: $source", error)
        }
    }

    private fun safeSourceAttributes(source: Path): BasicFileAttributes {
        validateNoReparseAncestors(source, "staged source")
        val attributes = readAttributes(source, "staged source entry")
        if (isLinkOrReparsePoint(source, attributes)) rejectStaging("link or reparse point is not allowed: $source")
        if (!Files.isReadable(source)) rejectStaging("source entry is unreadable: $source")
        return attributes
    }

    private fun verifySourceUnchanged(source: Path, before: BasicFileAttributes) {
        val after = safeSourceAttributes(source)
        if (!sameIdentityAndMetadata(before, after)) rejectStaging("source entry changed while copying: $source")
    }

    private fun deleteOwnedChildren(directory: Path, retained: Set<Path>) {
        try {
            Files.newDirectoryStream(directory).use { children ->
                children.forEach { child ->
                    val normalized = child.toAbsolutePath().normalize()
                    if (normalized !in retained && isOwnedImportDirectory(normalized)) {
                        deleteValidated(directory, normalized)
                    }
                }
            }
        } catch (error: LocalImportRejected) {
            throw error
        } catch (error: IOException) {
            rejectStaging("failed to clean local import orphans below $directory", error)
        }
    }
}

class StagedLocalManga internal constructor(
    val stagingPath: Path,
    val finalPath: Path,
    val manifest: LocalImportManifest,
    private val stagingRoot: Path,
    private val mangaRoot: Path,
    private val fileFaults: LocalFileFaults,
) : AutoCloseable {
    private var promoted = false
    private var committed = false
    private var closed = false

    fun promote(): Path {
        check(!closed) { "staged local manga is closed" }
        check(!promoted) { "staged local manga was already promoted" }
        if (Files.exists(finalPath, LinkOption.NOFOLLOW_LINKS)) {
            rejectStaging("promotion target already exists: $finalPath")
        }
        try {
            fileFaults.beforeAtomicMove(stagingPath, finalPath)
            Files.move(stagingPath, finalPath, StandardCopyOption.ATOMIC_MOVE)
            promoted = true
            return finalPath
        } catch (error: AtomicMoveNotSupportedException) {
            rejectStaging("atomic local manga promotion is not supported", error)
        } catch (error: FileAlreadyExistsException) {
            rejectStaging("promotion target already exists: $finalPath", error)
        } catch (error: IOException) {
            rejectStaging("atomic local manga promotion failed", error)
        }
    }

    fun markCommitted() {
        check(promoted) { "cannot commit local manga before promotion" }
        check(!closed) { "staged local manga is closed" }
        committed = true
    }

    override fun close() {
        if (closed) return
        closed = true
        if (committed) return
        if (promoted) deleteValidated(mangaRoot, finalPath)
        deleteValidated(stagingRoot, stagingPath)
    }
}

private fun safeEnsureDirectory(directory: Path) {
    val absolute = directory.toAbsolutePath().normalize()
    val parent = absolute.parent
    if (Files.notExists(absolute, LinkOption.NOFOLLOW_LINKS)) {
        if (parent != null) safeEnsureDirectory(parent)
        try {
            Files.createDirectory(absolute)
        } catch (error: FileAlreadyExistsException) {
            // A competing creator still has to pass the no-follow validation below.
        } catch (error: IOException) {
            rejectStaging("cannot create local import directory: $absolute", error)
        }
    }
    val attributes = readAttributes(absolute, "local import directory")
    if (!attributes.isDirectory || isLinkOrReparsePoint(absolute, attributes)) {
        rejectStaging("local import directory is not a safe directory: $absolute")
    }
    validateNoReparseAncestors(absolute, "local import directory")
}

private fun createPrivateDirectory(directory: Path) {
    val parent = checkNotNull(directory.parent)
    val posixView = Files.getFileAttributeView(parent, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
    if (posixView == null) {
        Files.createDirectory(directory)
    } else {
        Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(DIRECTORY_OWNER_PERMISSIONS))
    }
    restrictOwnerOnlyWhenSupported(directory, DIRECTORY_OWNER_PERMISSIONS)
    validateNoReparseAncestors(directory, "private staging directory")
}

private fun restrictOwnerOnlyWhenSupported(path: Path, permissions: Set<PosixFilePermission>) {
    val view = Files.getFileAttributeView(path, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS) ?: return
    try {
        view.setPermissions(permissions)
        if (view.readAttributes().permissions() != permissions) {
            rejectStaging("private staging permissions could not be validated: $path")
        }
    } catch (error: IOException) {
        rejectStaging("private staging permissions could not be applied: $path", error)
    }
}

private fun createOwnershipMarkerAtomically(directory: Path, id: String) {
    val marker = directory.resolve(OWNERSHIP_MARKER)
    val temporary = directory.resolve("$OWNERSHIP_MARKER.${UUID.randomUUID()}.tmp")
    val content = ownershipMarkerContent(id)
    try {
        Files.writeString(
            temporary,
            content,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            StandardOpenOption.SYNC,
        )
        restrictOwnerOnlyWhenSupported(temporary, FILE_OWNER_PERMISSIONS)
        Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE)
    } catch (error: AtomicMoveNotSupportedException) {
        rejectStaging("atomic ownership marker creation is not supported", error)
    } catch (error: IOException) {
        rejectStaging("cannot create local import ownership marker", error)
    } finally {
        runCatching { Files.deleteIfExists(temporary) }
    }
    if (!isOwnedImportDirectory(directory)) rejectStaging("local import ownership marker validation failed")
}

private fun isOwnedImportDirectory(directory: Path): Boolean {
    val id = directory.fileName?.toString() ?: return false
    if (!isValidImportId(id)) return false
    val attributes = runCatching { readAttributes(directory, "owned import directory") }.getOrNull() ?: return false
    if (!attributes.isDirectory || isLinkOrReparsePoint(directory, attributes)) return false
    val marker = directory.resolve(OWNERSHIP_MARKER)
    val markerAttributes = runCatching { readAttributes(marker, "ownership marker") }.getOrNull() ?: return false
    if (
        !markerAttributes.isRegularFile ||
        isLinkOrReparsePoint(marker, markerAttributes) ||
        markerAttributes.size() > MAX_OWNERSHIP_MARKER_BYTES
    ) {
        return false
    }
    return runCatching { Files.readString(marker, StandardCharsets.UTF_8) == ownershipMarkerContent(id) }
        .getOrDefault(false)
}

private fun ownershipMarkerContent(id: String): String = "$OWNERSHIP_MARKER_FORMAT\nid=$id\n"

private fun isValidImportId(id: String): Boolean = runCatching {
    UUID.fromString(id).toString() == id
}.getOrDefault(false)

private fun resolveManifestEntry(root: Path, relative: Path): Path {
    if (relative.isAbsolute) rejectStaging("manifest path must not be absolute: $relative")
    val resolved = root.resolve(relative).normalize()
    if (!resolved.startsWith(root.normalize())) rejectStaging("manifest path escapes root: $relative")
    return resolved
}

private fun requireUnchangedManifest(expected: LocalImportManifest, actual: LocalImportManifest, message: String) {
    if (
        expected.title != actual.title ||
        expected.sourceRoot != actual.sourceRoot ||
        expected.chapters != actual.chapters ||
        expected.sha256 != actual.sha256
    ) {
        rejectStaging(message)
    }
}

private fun sameIdentityAndMetadata(before: BasicFileAttributes, after: BasicFileAttributes): Boolean =
    before.isDirectory == after.isDirectory &&
        before.isRegularFile == after.isRegularFile &&
        before.isSymbolicLink == after.isSymbolicLink &&
        before.isOther == after.isOther &&
        before.size() == after.size() &&
        before.lastModifiedTime() == after.lastModifiedTime() &&
        (before.fileKey() == null || after.fileKey() == null || before.fileKey() == after.fileKey())

private fun requireImmediateChild(parent: Path, child: Path, label: String) {
    val normalizedParent = parent.toAbsolutePath().normalize()
    val normalizedChild = child.toAbsolutePath().normalize()
    if (normalizedChild.parent !=
        normalizedParent
    ) {
        rejectStaging("$label is not directly below $normalizedParent: $child")
    }
}

private fun deleteValidated(parent: Path, target: Path) {
    val normalizedParent = parent.toAbsolutePath().normalize()
    val normalizedTarget = target.toAbsolutePath().normalize()
    requireImmediateChild(normalizedParent, normalizedTarget, "cleanup target")
    if (Files.notExists(normalizedTarget, LinkOption.NOFOLLOW_LINKS)) return
    try {
        Files.walkFileTree(
            normalizedTarget,
            setOf(),
            Int.MAX_VALUE,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(directory: Path, error: IOException?): FileVisitResult {
                    if (error != null) throw error
                    Files.delete(directory)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, error: IOException): FileVisitResult {
                    throw error
                }
            },
        )
    } catch (error: IOException) {
        rejectStaging("failed to clean local import path: $normalizedTarget", error)
    }
}

private fun rejectStaging(message: String, cause: Throwable? = null): Nothing = throw LocalImportRejected(
    message,
    cause,
)

private const val STAGING_DIRECTORY = ".staging"
private const val MANGA_DIRECTORY = "manga"
private const val OWNERSHIP_MARKER = ".mihon-local-import-owner"
private const val OWNERSHIP_MARKER_FORMAT = "mihon-desktop-local-import-v1"
private const val MAX_OWNERSHIP_MARKER_BYTES = 256L
private const val COPY_BUFFER_SIZE = 64 * 1024
private val DIRECTORY_OWNER_PERMISSIONS = setOf(
    PosixFilePermission.OWNER_READ,
    PosixFilePermission.OWNER_WRITE,
    PosixFilePermission.OWNER_EXECUTE,
)
private val FILE_OWNER_PERMISSIONS = setOf(
    PosixFilePermission.OWNER_READ,
    PosixFilePermission.OWNER_WRITE,
)
