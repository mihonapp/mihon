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
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryFlag
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.UserPrincipal
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
    fun afterClaimCreated(claim: Path) = Unit
    fun afterStagingDirectoryCreated(directory: Path) = Unit
    fun duringMarkerPublication(temporary: Path, marker: Path) = Unit

    companion object {
        val NONE = object : LocalFileFaults {}
    }
}

/** Test-only seam that models process death by intentionally bypassing synchronous compensation. */
internal class LocalImportCrashSimulation : Error("simulated local import process crash")

class LocalImportStager private constructor(
    private val scanner: LocalImportScanner,
    private val idFactory: () -> String,
    private val checkpoint: LocalStagingCheckpoint,
    private val fileFaults: LocalFileFaults,
    private val clock: () -> Long,
) {
    constructor(
        scanner: LocalImportScanner = LocalImportScanner(),
        idFactory: () -> String = { UUID.randomUUID().toString() },
        checkpoint: LocalStagingCheckpoint = LocalStagingCheckpoint.NONE,
    ) : this(scanner, idFactory, checkpoint, LocalFileFaults.NONE, System::currentTimeMillis)

    internal constructor(
        fileFaults: LocalFileFaults,
        scanner: LocalImportScanner = LocalImportScanner(),
        idFactory: () -> String = { UUID.randomUUID().toString() },
        checkpoint: LocalStagingCheckpoint = LocalStagingCheckpoint.NONE,
    ) : this(scanner, idFactory, checkpoint, fileFaults, System::currentTimeMillis)

    internal constructor(
        clock: () -> Long,
        fileFaults: LocalFileFaults = LocalFileFaults.NONE,
        scanner: LocalImportScanner = LocalImportScanner(),
        idFactory: () -> String = { UUID.randomUUID().toString() },
        checkpoint: LocalStagingCheckpoint = LocalStagingCheckpoint.NONE,
    ) : this(scanner, idFactory, checkpoint, fileFaults, clock)

    fun stage(manifest: LocalImportManifest, localLibraryRoot: Path): StagedLocalManga {
        validateManifestShape(manifest)
        validateNoReparseAncestors(manifest.sourceRoot, "source root")
        requireUnchangedManifest(manifest, scanner.scan(manifest.sourceRoot), "source changed after scan")
        val root = localLibraryRoot.toAbsolutePath().normalize()
        val stagingRoot = root.resolve(STAGING_DIRECTORY)
        val claimsRoot = root.resolve(CLAIMS_DIRECTORY)
        val mangaRoot = root.resolve(MANGA_DIRECTORY)
        safeEnsureDirectory(root)
        safeEnsurePrivateDirectory(stagingRoot)
        safeEnsurePrivateDirectory(claimsRoot)
        safeEnsureDirectory(mangaRoot)
        val id = idFactory()
        if (!isValidImportId(id)) rejectStaging("invalid staging identifier")
        val claimPath = claimsRoot.resolve("$id$CLAIM_SUFFIX").normalize()
        val stagingPath = stagingRoot.resolve(id).normalize()
        val finalPath = mangaRoot.resolve(id).normalize()
        requireImmediateChild(claimsRoot, claimPath, "claim path")
        requireImmediateChild(stagingRoot, stagingPath, "staging path")
        requireImmediateChild(mangaRoot, finalPath, "promotion path")
        var claimCreated = false
        var stagingCreated = false
        try {
            createClaim(claimPath, id, clock())
            claimCreated = true
            fileFaults.afterClaimCreated(claimPath)
            try {
                createPrivateDirectory(stagingPath)
                stagingCreated = true
            } catch (error: FileAlreadyExistsException) {
                rejectStaging("staging target already exists: $stagingPath", error)
            }
            fileFaults.afterStagingDirectoryCreated(stagingPath)
            createOwnershipMarkerAtomically(stagingPath, id, fileFaults)
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
            return StagedLocalManga(
                id = id,
                stagingPath = stagingPath,
                finalPath = finalPath,
                manifest = manifest,
                stagingRoot = stagingRoot,
                mangaRoot = mangaRoot,
                claimPath = claimPath,
                claimsRoot = claimsRoot,
                fileFaults = fileFaults,
            )
        } catch (crash: LocalImportCrashSimulation) {
            throw crash
        } catch (error: Throwable) {
            if (stagingCreated) {
                runCatching { deleteValidated(stagingRoot, stagingPath) }.onFailure(error::addSuppressed)
            }
            if (claimCreated) {
                runCatching { deleteValidClaim(claimsRoot, claimPath, id) }.onFailure(error::addSuppressed)
            }
            throw error
        }
    }

    fun cleanupOrphans(localLibraryRoot: Path, retainedFinalPaths: Set<Path>) {
        val root = localLibraryRoot.toAbsolutePath().normalize()
        val stagingRoot = root.resolve(STAGING_DIRECTORY)
        val claimsRoot = root.resolve(CLAIMS_DIRECTORY)
        val mangaRoot = root.resolve(MANGA_DIRECTORY)
        safeEnsureDirectory(root)
        safeEnsurePrivateDirectory(stagingRoot)
        safeEnsurePrivateDirectory(claimsRoot)
        safeEnsureDirectory(mangaRoot)
        val retained = retainedFinalPaths.mapTo(mutableSetOf()) { retainedPath ->
            val normalized = retainedPath.toAbsolutePath().normalize()
            requireImmediateChild(mangaRoot, normalized, "database local manga path")
            normalized
        }
        readValidClaims(claimsRoot).values.forEach { claim ->
            val stagingPath = stagingRoot.resolve(claim.id)
            val finalPath = mangaRoot.resolve(claim.id)
            var deletedDirectory = false
            if (isSafeImportDirectory(stagingPath)) {
                deleteValidated(stagingRoot, stagingPath)
                deletedDirectory = true
            }
            if (finalPath !in retained && isSafeImportDirectory(finalPath)) {
                deleteValidated(mangaRoot, finalPath)
                deletedDirectory = true
            }
            val pathRemains = Files.exists(stagingPath, LinkOption.NOFOLLOW_LINKS) ||
                Files.exists(finalPath, LinkOption.NOFOLLOW_LINKS)
            if (!pathRemains && (deletedDirectory || isStaleClaim(claim.createdAt, clock()))) {
                deleteValidClaim(claimsRoot, claim.path, claim.id)
            }
        }
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
}

class StagedLocalManga internal constructor(
    private val id: String,
    val stagingPath: Path,
    val finalPath: Path,
    val manifest: LocalImportManifest,
    private val stagingRoot: Path,
    private val mangaRoot: Path,
    private val claimPath: Path,
    private val claimsRoot: Path,
    private val fileFaults: LocalFileFaults,
) : AutoCloseable {
    private var promoted = false
    private var promotionAmbiguous = false
    private var committed = false
    private var closed = false

    fun promote(): Path {
        check(!closed) { "staged local manga is closed" }
        check(!promoted) { "staged local manga was already promoted" }
        val before = requireOwnedImportDirectory(stagingPath, id, claimPath)
        if (Files.exists(finalPath, LinkOption.NOFOLLOW_LINKS)) {
            rejectStaging("promotion target already exists: $finalPath")
        }
        try {
            fileFaults.beforeAtomicMove(stagingPath, finalPath)
            if (Files.exists(finalPath, LinkOption.NOFOLLOW_LINKS)) {
                rejectStaging("promotion target already exists: $finalPath")
            }
            Files.move(stagingPath, finalPath, StandardCopyOption.ATOMIC_MOVE)
            promotionAmbiguous = true
            val after = requireOwnedImportDirectory(finalPath, id, claimPath)
            if (Files.exists(stagingPath, LinkOption.NOFOLLOW_LINKS) || !sameIdentityAndMetadata(before, after)) {
                rejectStaging("atomic promotion identity validation failed: $finalPath")
            }
            promoted = true
            promotionAmbiguous = false
            return finalPath
        } catch (error: LocalImportRejected) {
            throw error
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
        if (promotionAmbiguous) {
            // Never authorize later cleanup of a target whose post-move identity could not be proved.
            deleteValidClaim(claimsRoot, claimPath, id)
            deleteValidated(stagingRoot, stagingPath)
            return
        }
        if (promoted) deleteValidated(mangaRoot, finalPath)
        deleteValidated(stagingRoot, stagingPath)
        deleteValidClaim(claimsRoot, claimPath, id)
    }
}

private data class ValidClaim(val id: String, val createdAt: Long, val path: Path)

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

private fun safeEnsurePrivateDirectory(directory: Path) {
    safeEnsureDirectory(directory)
    restrictPrivatePath(directory, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
}

private fun createPrivateDirectory(directory: Path) {
    val parent = checkNotNull(directory.parent)
    val posixView = Files.getFileAttributeView(parent, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
    var created = false
    try {
        if (posixView == null) {
            Files.createDirectory(directory)
        } else {
            Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(DIRECTORY_OWNER_PERMISSIONS))
        }
        created = true
        restrictPrivatePath(directory, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
        validateNoReparseAncestors(directory, "private staging directory")
    } catch (error: Throwable) {
        if (created) runCatching { Files.deleteIfExists(directory) }.onFailure(error::addSuppressed)
        throw error
    }
}

private fun restrictPrivatePath(path: Path, permissions: Set<PosixFilePermission>, isDirectory: Boolean) {
    val posix = Files.getFileAttributeView(path, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
    if (posix != null) {
        try {
            posix.setPermissions(permissions)
            if (posix.readAttributes().permissions() != permissions) {
                rejectStaging("private local import permissions could not be validated: $path")
            }
        } catch (error: IOException) {
            rejectStaging("private local import permissions could not be applied: $path", error)
        }
        return
    }
    if (!isWindowsHost()) return
    val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
        ?: rejectStaging("Windows ACL view is unavailable for private local import path: $path")
    try {
        val owner = acl.owner
        val system = lookupWindowsPrincipal(path, "S-1-5-18", "NT AUTHORITY\\SYSTEM", "SYSTEM")
        val flags = if (isDirectory) {
            setOf(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT)
        } else {
            emptySet()
        }
        val allPermissions = AclEntryPermission.entries.toSet()
        acl.acl = listOf(
            allowAcl(owner, allPermissions, flags),
            allowAcl(system, allPermissions, flags),
        )
        validateRestrictedWindowsAcl(path, acl, owner, system)
    } catch (error: LocalImportRejected) {
        throw error
    } catch (error: IOException) {
        rejectStaging("private Windows ACL could not be applied: $path", error)
    } catch (error: SecurityException) {
        rejectStaging("private Windows ACL could not be applied: $path", error)
    }
}

private fun validateRestrictedWindowsAcl(
    path: Path,
    view: AclFileAttributeView,
    owner: UserPrincipal,
    system: UserPrincipal,
) {
    val allowed = setOf(owner.name.lowercase(), system.name.lowercase())
    val entries = view.acl
    if (
        entries.size != 2 ||
        entries.any { it.type() != AclEntryType.ALLOW || it.principal().name.lowercase() !in allowed } ||
        entries.none { it.principal().name.equals(owner.name, ignoreCase = true) } ||
        entries.none { it.principal().name.equals(system.name, ignoreCase = true) }
    ) {
        rejectStaging("private Windows ACL contains inherited or broad access: $path")
    }
}

private fun allowAcl(
    principal: UserPrincipal,
    permissions: Set<AclEntryPermission>,
    flags: Set<AclEntryFlag>,
): AclEntry = AclEntry.newBuilder()
    .setType(AclEntryType.ALLOW)
    .setPrincipal(principal)
    .setPermissions(permissions)
    .setFlags(flags)
    .build()

private fun lookupWindowsPrincipal(path: Path, vararg names: String): UserPrincipal =
    names.firstNotNullOfOrNull { name ->
        runCatching { path.fileSystem.userPrincipalLookupService.lookupPrincipalByName(name) }.getOrNull()
    } ?: rejectStaging("required Windows SYSTEM principal is unavailable")

private fun isWindowsHost(): Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

private fun createClaim(path: Path, id: String, createdAt: Long) {
    if (createdAt < 0) rejectStaging("claim timestamp must not be negative")
    var created = false
    try {
        Files.writeString(
            path,
            claimContent(id, createdAt),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            StandardOpenOption.SYNC,
        )
        created = true
        restrictPrivatePath(path, FILE_OWNER_PERMISSIONS, isDirectory = false)
    } catch (error: FileAlreadyExistsException) {
        rejectStaging("local import claim already exists: $path", error)
    } catch (error: Throwable) {
        if (created) {
            runCatching { deleteValidClaim(checkNotNull(path.parent), path, id) }.onFailure(error::addSuppressed)
        }
        if (error is LocalImportRejected) throw error
        if (error is IOException) rejectStaging("cannot create local import claim: $path", error)
        throw error
    }
    if (readValidClaim(path)?.id != id) {
        runCatching { deleteValidClaim(checkNotNull(path.parent), path, id) }
        rejectStaging("local import claim validation failed: $path")
    }
}

private fun createOwnershipMarkerAtomically(directory: Path, id: String, fileFaults: LocalFileFaults) {
    val marker = directory.resolve(OWNERSHIP_MARKER)
    val temporary = directory.resolve("$OWNERSHIP_MARKER.${UUID.randomUUID()}.tmp")
    var simulatedCrash = false
    try {
        Files.writeString(
            temporary,
            ownershipMarkerContent(id),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            StandardOpenOption.SYNC,
        )
        restrictPrivatePath(temporary, FILE_OWNER_PERMISSIONS, isDirectory = false)
        try {
            fileFaults.duringMarkerPublication(temporary, marker)
        } catch (crash: LocalImportCrashSimulation) {
            simulatedCrash = true
            throw crash
        }
        Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE)
    } catch (error: AtomicMoveNotSupportedException) {
        rejectStaging("atomic ownership marker creation is not supported", error)
    } catch (error: IOException) {
        rejectStaging("cannot create local import ownership marker", error)
    } finally {
        if (!simulatedCrash) runCatching { Files.deleteIfExists(temporary) }
    }
    if (!hasValidOwnershipMarker(directory, id)) rejectStaging("local import ownership marker validation failed")
}

private fun requireOwnedImportDirectory(directory: Path, id: String, claimPath: Path): BasicFileAttributes {
    val attributes = readAttributes(directory, "owned import directory")
    if (
        !attributes.isDirectory ||
        isLinkOrReparsePoint(directory, attributes) ||
        !hasValidOwnershipMarker(directory, id) ||
        readValidClaim(claimPath)?.id != id
    ) {
        rejectStaging("local import ownership validation failed: $directory")
    }
    return attributes
}

private fun hasValidOwnershipMarker(directory: Path, id: String): Boolean {
    if (!isValidImportId(id) || directory.fileName?.toString() != id) return false
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

private fun readValidClaims(claimsRoot: Path): Map<String, ValidClaim> = try {
    Files.newDirectoryStream(claimsRoot).use { children ->
        children.mapNotNull(::readValidClaim).associateBy(ValidClaim::id)
    }
} catch (error: IOException) {
    rejectStaging("failed to read local import claims: $claimsRoot", error)
}

private fun readValidClaim(path: Path): ValidClaim? {
    val fileName = path.fileName?.toString() ?: return null
    if (!fileName.endsWith(CLAIM_SUFFIX)) return null
    val id = fileName.removeSuffix(CLAIM_SUFFIX)
    if (!isValidImportId(id)) return null
    val attributes = runCatching { readAttributes(path, "local import claim") }.getOrNull() ?: return null
    if (
        !attributes.isRegularFile ||
        isLinkOrReparsePoint(path, attributes) ||
        attributes.size() > MAX_CLAIM_BYTES
    ) {
        return null
    }
    val content = runCatching { Files.readString(path, StandardCharsets.UTF_8) }.getOrNull() ?: return null
    val lines = content.split('\n')
    if (lines.size != 4 || lines[0] != CLAIM_FORMAT || lines[3].isNotEmpty()) return null
    if (lines[1] != "id=$id") return null
    val timestampText = lines[2].removePrefix("createdAt=")
    if (lines[2] != "createdAt=$timestampText") return null
    val createdAt = timestampText.toLongOrNull()?.takeIf { it >= 0 } ?: return null
    return ValidClaim(id, createdAt, path.toAbsolutePath().normalize())
}

private fun deleteValidClaim(claimsRoot: Path, claimPath: Path, id: String) {
    val normalized = claimPath.toAbsolutePath().normalize()
    requireImmediateChild(claimsRoot, normalized, "claim cleanup target")
    if (Files.notExists(normalized, LinkOption.NOFOLLOW_LINKS)) return
    val valid = readValidClaim(normalized) ?: return
    if (valid.id != id) return
    try {
        Files.delete(normalized)
    } catch (error: IOException) {
        rejectStaging("failed to clean local import claim: $normalized", error)
    }
}

private fun isStaleClaim(createdAt: Long, now: Long): Boolean =
    now >= createdAt && now - createdAt >= CLAIM_STALE_MILLIS

private fun isSafeImportDirectory(directory: Path): Boolean {
    if (!isValidImportId(directory.fileName?.toString() ?: return false)) return false
    val attributes = runCatching { readAttributes(directory, "claimed import directory") }.getOrNull() ?: return false
    return attributes.isDirectory && !isLinkOrReparsePoint(directory, attributes)
}

private fun ownershipMarkerContent(id: String): String = "$OWNERSHIP_MARKER_FORMAT\nid=$id\n"

private fun claimContent(id: String, createdAt: Long): String = "$CLAIM_FORMAT\nid=$id\ncreatedAt=$createdAt\n"

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
    if (normalizedChild.parent != normalizedParent) {
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
private const val CLAIMS_DIRECTORY = ".claims"
private const val MANGA_DIRECTORY = "manga"
private const val CLAIM_SUFFIX = ".claim"
private const val CLAIM_FORMAT = "mihon-desktop-local-import-claim-v1"
private const val OWNERSHIP_MARKER = ".mihon-local-import-owner"
private const val OWNERSHIP_MARKER_FORMAT = "mihon-desktop-local-import-v1"
private const val MAX_OWNERSHIP_MARKER_BYTES = 256L
private const val MAX_CLAIM_BYTES = 512L
private const val CLAIM_STALE_MILLIS = 5 * 60 * 1000L
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
