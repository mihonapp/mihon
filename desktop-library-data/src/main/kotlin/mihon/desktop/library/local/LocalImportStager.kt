package mihon.desktop.library.local

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
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
import java.util.UUID

fun interface LocalStagingCheckpoint {
    fun afterStagingCopy(stagingPath: Path)

    companion object {
        val NONE = LocalStagingCheckpoint {}
    }
}

class LocalImportStager(
    private val scanner: LocalImportScanner = LocalImportScanner(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val checkpoint: LocalStagingCheckpoint = LocalStagingCheckpoint.NONE,
) {
    fun stage(manifest: LocalImportManifest, localLibraryRoot: Path): StagedLocalManga {
        validateManifestShape(manifest)
        requireUnchangedManifest(manifest, scanner.scan(manifest.sourceRoot), "source changed after scan")
        val root = localLibraryRoot.toAbsolutePath().normalize()
        val stagingRoot = root.resolve(STAGING_DIRECTORY)
        val mangaRoot = root.resolve(MANGA_DIRECTORY)
        safeEnsureDirectory(root)
        safeEnsureDirectory(stagingRoot)
        safeEnsureDirectory(mangaRoot)
        val id = idFactory()
        if (!SAFE_ID.matches(id)) rejectStaging("invalid staging identifier")
        val stagingPath = stagingRoot.resolve(id).normalize()
        val finalPath = mangaRoot.resolve(id).normalize()
        requireImmediateChild(stagingRoot, stagingPath, "staging path")
        requireImmediateChild(mangaRoot, finalPath, "promotion path")
        var stagingCreated = false
        try {
            try {
                Files.createDirectory(stagingPath)
                stagingCreated = true
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
            return StagedLocalManga(stagingPath, finalPath, manifest, stagingRoot, mangaRoot)
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
        deleteChildren(stagingRoot, emptySet())
        deleteChildren(mangaRoot, retained)
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
                    while (input.read(buffer) != -1) {
                        buffer.flip()
                        while (buffer.hasRemaining()) output.write(buffer)
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
        val attributes = readAttributes(source, "staged source entry")
        if (isLinkOrReparsePoint(source, attributes)) rejectStaging("link or reparse point is not allowed: $source")
        if (!Files.isReadable(source)) rejectStaging("source entry is unreadable: $source")
        return attributes
    }

    private fun verifySourceUnchanged(source: Path, before: BasicFileAttributes) {
        val after = safeSourceAttributes(source)
        if (!sameIdentityAndMetadata(before, after)) rejectStaging("source entry changed while copying: $source")
    }

    private fun deleteChildren(directory: Path, retained: Set<Path>) {
        try {
            Files.newDirectoryStream(directory).use { children ->
                children.forEach { child ->
                    val normalized = child.toAbsolutePath().normalize()
                    if (normalized !in retained) deleteValidated(directory, normalized)
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
}

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
private const val COPY_BUFFER_SIZE = 64 * 1024
private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9-]{0,127}")
