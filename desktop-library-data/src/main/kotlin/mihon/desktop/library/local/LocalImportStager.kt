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
import java.nio.file.NoSuchFileException
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
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

internal fun interface LocalStagingCheckpoint {
    fun afterStagingCopy(stagingPath: Path)

    companion object {
        val NONE = LocalStagingCheckpoint {}
    }
}

internal interface LocalFileFaults {
    fun afterCopyChunk(source: Path, target: Path, copiedBytes: Long) = Unit
    fun beforeAtomicMove(source: Path, target: Path) = Unit
    fun afterAtomicMove(source: Path, target: Path) = Unit
    fun afterClaimCreated(claim: Path) = Unit
    fun afterStagingDirectoryCreated(directory: Path) = Unit
    fun duringMarkerPublication(temporary: Path, marker: Path) = Unit

    companion object {
        val NONE = object : LocalFileFaults {}
    }
}

/** Test-only seam that models process death by intentionally bypassing synchronous compensation. */
internal class LocalImportCrashSimulation : Error("simulated local import process crash")

internal class LocalImportStager private constructor(
    private val scanner: LocalImportScanner,
    private val idFactory: () -> String,
    private val checkpoint: LocalStagingCheckpoint,
    private val fileFaults: LocalFileFaults,
    private val clock: () -> Long,
) {
    internal constructor(
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
        safeEnsurePrivateDirectory(mangaRoot)
        val id = idFactory()
        if (!isValidImportId(id)) rejectStaging("invalid staging identifier")
        val ownershipNonce = newOwnershipNonce()
        val claimPath = claimsRoot.resolve("$id$CLAIM_SUFFIX").normalize()
        val stagingPath = stagingRoot.resolve(id).normalize()
        val finalPath = mangaRoot.resolve(id).normalize()
        requireImmediateChild(claimsRoot, claimPath, "claim path")
        requireImmediateChild(stagingRoot, stagingPath, "staging path")
        requireImmediateChild(mangaRoot, finalPath, "promotion path")
        var claimCreated = false
        var stagingCreated = false
        var createdIdentity: OwnedDirectoryIdentity? = null
        try {
            createClaim(claimPath, id, ownershipNonce, clock())
            claimCreated = true
            fileFaults.afterClaimCreated(claimPath)
            validateClaimAndNamespaces(
                stagingRoot,
                claimsRoot,
                mangaRoot,
                claimPath,
                id,
                ownershipNonce,
            )
            try {
                createPrivateDirectory(stagingPath)
                stagingCreated = true
            } catch (error: FileAlreadyExistsException) {
                rejectStaging("staging target already exists: $stagingPath", error)
            }
            val identity = retainCreatedDirectoryIdentity(stagingPath, id, ownershipNonce, claimPath)
            createdIdentity = identity
            fileFaults.afterStagingDirectoryCreated(stagingPath)
            validateClaimAndNamespaces(
                stagingRoot,
                claimsRoot,
                mangaRoot,
                claimPath,
                id,
                ownershipNonce,
            )
            requireCreatedDirectoryMatches(stagingPath, identity, claimPath, requireEmpty = true)
            createOwnershipMarkerAtomically(
                stagingPath,
                identity,
                claimPath,
                stagingRoot,
                claimsRoot,
                mangaRoot,
                fileFaults,
            )
            requireOwnedDirectoryMatches(stagingPath, identity, claimPath)
            val copyContext = StagingCopyContext(
                stagingPath,
                identity,
                claimPath,
                stagingRoot,
                claimsRoot,
                mangaRoot,
            )
            manifest.chapters.forEach { chapter ->
                val source = resolveManifestEntry(manifest.sourceRoot, chapter.relativePath)
                val target = resolveManifestEntry(stagingPath, chapter.relativePath)
                copyEntryNoFollow(source, target, copyContext)
            }
            verifyStagedState(manifest, stagingPath, identity, claimPath, stagingRoot, claimsRoot, mangaRoot)
            checkpoint.afterStagingCopy(stagingPath)
            verifyStagedState(manifest, stagingPath, identity, claimPath, stagingRoot, claimsRoot, mangaRoot)
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
                scanner = scanner,
                createdIdentity = identity,
            )
        } catch (crash: LocalImportCrashSimulation) {
            throw crash
        } catch (error: Throwable) {
            var stagingRemoved = Files.notExists(stagingPath, LinkOption.NOFOLLOW_LINKS)
            if (stagingCreated || !stagingRemoved) {
                runCatching {
                    createdIdentity?.let { identity ->
                        deleteOwnedDirectory(stagingRoot, stagingPath, identity, claimPath) ||
                            deleteEmptyClaimedDirectory(stagingRoot, stagingPath, identity, claimPath)
                    } ?: false
                }.onSuccess { removed -> stagingRemoved = removed }
                    .onFailure(error::addSuppressed)
            }
            if (claimCreated && stagingRemoved) {
                runCatching { deleteValidClaim(claimsRoot, claimPath, id, ownershipNonce) }
                    .onFailure(error::addSuppressed)
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
        safeEnsurePrivateDirectory(mangaRoot)
        val retained = retainedFinalPaths.mapTo(mutableSetOf()) { retainedPath ->
            val normalized = retainedPath.toAbsolutePath().normalize()
            requireImmediateChild(mangaRoot, normalized, "database local manga path")
            normalized
        }
        readValidClaims(claimsRoot).values.forEach { claim ->
            val stagingPath = stagingRoot.resolve(claim.id)
            val finalPath = mangaRoot.resolve(claim.id)
            var deletedDirectory = false
            if (isSafeImportDirectory(stagingPath, claim, allowTemporaryMarker = true)) {
                deleteValidated(stagingRoot, stagingPath)
                deletedDirectory = true
            }
            if (finalPath !in retained && isSafeImportDirectory(finalPath, claim, allowTemporaryMarker = false)) {
                deleteValidated(mangaRoot, finalPath)
                deletedDirectory = true
            }
            val pathRemains = Files.exists(stagingPath, LinkOption.NOFOLLOW_LINKS) ||
                Files.exists(finalPath, LinkOption.NOFOLLOW_LINKS)
            if (!pathRemains && (deletedDirectory || isStaleClaim(claim.createdAt, clock()))) {
                deleteValidClaim(claimsRoot, claim.path, claim.id, claim.nonce)
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

    private fun verifyStagedState(
        manifest: LocalImportManifest,
        stagingPath: Path,
        identity: OwnedDirectoryIdentity,
        claimPath: Path,
        stagingRoot: Path,
        claimsRoot: Path,
        mangaRoot: Path,
    ) {
        validatePrivateNamespace(stagingRoot, "staging namespace")
        validatePrivateNamespace(claimsRoot, "claim namespace")
        validatePrivateNamespace(mangaRoot, "manga namespace")
        requireOwnedDirectoryMatches(stagingPath, identity, claimPath)
        requireUnchangedManifest(manifest, scanner.scan(manifest.sourceRoot), "source changed during staging")
        validatePrivateTree(stagingPath)
        val stagedManifest = scanner.scanOwnedImportDirectory(
            stagingPath,
            setOf(ownershipMarkerPath(stagingPath, identity.nonce)),
        )
        if (stagedManifest.chapters != manifest.chapters || stagedManifest.sha256 != manifest.sha256) {
            rejectStaging("staged manifest verification failed")
        }
    }

    private fun copyEntryNoFollow(source: Path, target: Path, context: StagingCopyContext) {
        val before = safeSourceAttributes(source)
        when {
            before.isDirectory -> copyDirectoryNoFollow(source, target, before, context)
            before.isRegularFile -> copyFileNoFollow(source, target, before, context)
            else -> rejectStaging("source entry is not a regular file or directory: $source")
        }
    }

    private fun copyDirectoryNoFollow(
        source: Path,
        target: Path,
        before: BasicFileAttributes,
        context: StagingCopyContext,
    ) {
        try {
            Files.createDirectory(target)
            restrictPrivatePath(target, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
            withDirectoryIterationRejection("failed to enumerate source directory: $source") {
                Files.newDirectoryStream(source).use { children ->
                    children.forEach { child ->
                        copyEntryNoFollow(child, target.resolve(child.fileName.toString()), context)
                    }
                }
            }
            verifySourceUnchanged(source, before)
            Files.setLastModifiedTime(target, before.lastModifiedTime())
            validatePrivatePath(target, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
        } catch (error: LocalImportRejected) {
            throw error
        } catch (error: IOException) {
            rejectStaging("failed to copy source directory without following links: $source", error)
        }
    }

    private fun copyFileNoFollow(
        source: Path,
        target: Path,
        before: BasicFileAttributes,
        context: StagingCopyContext,
    ) {
        try {
            val parent = checkNotNull(target.parent)
            val parentIdentity = readAttributes(parent, "staged target parent")
            FileChannel.open(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { input ->
                FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                    restrictPrivatePath(target, FILE_OWNER_PERMISSIONS, isDirectory = false)
                    val targetIdentity = readAttributes(target, "staged target file")
                    val buffer = ByteBuffer.allocateDirect(COPY_BUFFER_SIZE)
                    var copiedBytes = 0L
                    while (input.read(buffer) != -1) {
                        buffer.flip()
                        while (buffer.hasRemaining()) copiedBytes += output.write(buffer)
                        fileFaults.afterCopyChunk(source, target, copiedBytes)
                        verifyAfterCopyCallback(
                            source,
                            before,
                            target,
                            targetIdentity,
                            parent,
                            parentIdentity,
                            copiedBytes,
                            output,
                            context,
                        )
                        buffer.clear()
                    }
                }
            }
            verifySourceUnchanged(source, before)
            Files.setLastModifiedTime(target, before.lastModifiedTime())
            restrictPrivatePath(target, FILE_OWNER_PERMISSIONS, isDirectory = false)
        } catch (error: LocalImportRejected) {
            throw error
        } catch (error: IOException) {
            rejectStaging("failed to copy source file without following links: $source", error)
        }
    }

    private fun verifyAfterCopyCallback(
        source: Path,
        sourceIdentity: BasicFileAttributes,
        target: Path,
        targetIdentity: BasicFileAttributes,
        parent: Path,
        parentIdentity: BasicFileAttributes,
        copiedBytes: Long,
        output: FileChannel,
        context: StagingCopyContext,
    ) {
        verifySourceUnchanged(source, sourceIdentity)
        context.validate()
        validateNoReparseAncestors(parent, "staged target parent")
        validatePrivatePath(parent, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
        val currentParent = readAttributes(parent, "staged target parent")
        if (!samePathIdentity(parentIdentity, currentParent)) {
            rejectStaging("staged target parent identity changed: $parent")
        }
        validateNoReparseAncestors(target, "staged target file")
        validatePrivatePath(target, FILE_OWNER_PERMISSIONS, isDirectory = false)
        val currentTarget = readAttributes(target, "staged target file")
        if (
            !samePathIdentity(targetIdentity, currentTarget) ||
            currentTarget.size() != copiedBytes ||
            output.size() != copiedBytes
        ) {
            rejectStaging("staged target file identity changed: $target")
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

internal class StagedLocalManga internal constructor(
    private val id: String,
    internal val stagingPath: Path,
    internal val finalPath: Path,
    internal val manifest: LocalImportManifest,
    private val stagingRoot: Path,
    private val mangaRoot: Path,
    private val claimPath: Path,
    private val claimsRoot: Path,
    private val fileFaults: LocalFileFaults,
    private val scanner: LocalImportScanner,
    private val createdIdentity: OwnedDirectoryIdentity,
) : AutoCloseable {
    private var promoted = false
    private var promotionAmbiguous = false
    private var committed = false
    private var closed = false

    fun promote(): Path {
        check(!closed) { "staged local manga is closed" }
        check(!promoted) { "staged local manga was already promoted" }
        validatePromotionNamespaces()
        requireOwnedDirectoryMatches(stagingPath, createdIdentity, claimPath)
        if (Files.exists(finalPath, LinkOption.NOFOLLOW_LINKS)) {
            rejectStaging("promotion target already exists: $finalPath")
        }
        try {
            fileFaults.beforeAtomicMove(stagingPath, finalPath)
            if (Files.exists(finalPath, LinkOption.NOFOLLOW_LINKS)) {
                rejectStaging("promotion target already exists: $finalPath")
            }
            validatePromotionNamespaces()
            requireOwnedDirectoryMatches(stagingPath, createdIdentity, claimPath)
            promotionAmbiguous = true
            Files.move(stagingPath, finalPath, StandardCopyOption.ATOMIC_MOVE)
            fileFaults.afterAtomicMove(stagingPath, finalPath)
            validatePromotionNamespaces()
            requireOwnedDirectoryMatches(finalPath, createdIdentity, claimPath)
            if (Files.exists(stagingPath, LinkOption.NOFOLLOW_LINKS)) {
                rejectStaging("atomic promotion identity validation failed: $finalPath")
            }
            promoted = true
            promotionAmbiguous = false
            reverifyPromoted()
            return finalPath
        } catch (error: LocalImportRejected) {
            throw error
        } catch (error: AtomicMoveNotSupportedException) {
            rejectStaging("atomic local manga promotion is not supported", error)
        } catch (error: FileAlreadyExistsException) {
            rejectStaging("promotion target already exists: $finalPath", error)
        } catch (error: IOException) {
            rejectStaging("atomic local manga promotion failed", error)
        } finally {
            if (promotionAmbiguous) reconcileObservablePromotionState()
        }
    }

    fun markCommitted() {
        check(promoted) { "cannot commit local manga before promotion" }
        check(!closed) { "staged local manga is closed" }
        committed = true
    }

    internal fun reverifyPromoted() {
        check(promoted) { "cannot verify local manga before promotion" }
        validatePromotionNamespaces()
        requireOwnedDirectoryMatches(finalPath, createdIdentity, claimPath)
        validatePrivateTree(finalPath)
        val promotedManifest = scanner.scanOwnedImportDirectory(
            finalPath,
            setOf(ownershipMarkerPath(finalPath, createdIdentity.nonce)),
        )
        if (promotedManifest.chapters != manifest.chapters || promotedManifest.sha256 != manifest.sha256) {
            rejectStaging("promoted manifest verification failed")
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (committed) return
        compensateObservablePromotionState(reconcileObservablePromotionState())
    }

    private fun validatePromotionNamespaces() {
        validatePrivateNamespace(stagingRoot, "staging namespace")
        validatePrivateNamespace(claimsRoot, "claim namespace")
        validatePrivateNamespace(mangaRoot, "manga namespace")
        validateNoReparseAncestors(claimPath, "local import claim")
    }

    private fun reconcileObservablePromotionState(): ObservablePromotionState {
        if (runCatching { validatePromotionNamespaces() }.isFailure) {
            return ObservablePromotionState.unknown()
        }
        val observable = ObservablePromotionState(
            staging = observeOwnedPath(stagingPath),
            final = observeOwnedPath(finalPath),
        )
        when {
            observable.final.state == OwnedPathState.OWNED &&
                observable.staging.state == OwnedPathState.ABSENT -> {
                promoted = true
                promotionAmbiguous = false
            }
            observable.staging.state == OwnedPathState.OWNED &&
                observable.final.state == OwnedPathState.ABSENT -> {
                promoted = false
                promotionAmbiguous = false
            }
            observable.staging.state == OwnedPathState.ABSENT &&
                observable.final.state == OwnedPathState.ABSENT -> {
                promoted = false
                promotionAmbiguous = false
            }
        }
        return observable
    }

    private fun observeOwnedPath(path: Path): OwnedPathObservation = when (observePathPresence(path)) {
        PathPresence.ABSENT -> OwnedPathObservation(OwnedPathState.ABSENT)
        PathPresence.UNKNOWN -> OwnedPathObservation(OwnedPathState.OTHER_OR_UNKNOWN, needsProvenance = true)
        PathPresence.PRESENT -> when (observePathPresence(ownershipMarkerPath(path, createdIdentity.nonce))) {
            PathPresence.ABSENT -> OwnedPathObservation(OwnedPathState.OTHER_OR_UNKNOWN)
            PathPresence.UNKNOWN -> OwnedPathObservation(OwnedPathState.OTHER_OR_UNKNOWN, needsProvenance = true)
            PathPresence.PRESENT -> {
                val matches = runCatching {
                    requireOwnedDirectoryMatches(path, createdIdentity, claimPath)
                }.isSuccess
                OwnedPathObservation(
                    state = if (matches) OwnedPathState.OWNED else OwnedPathState.OTHER_OR_UNKNOWN,
                    needsProvenance = !matches,
                )
            }
        }
    }

    private fun compensateObservablePromotionState(observable: ObservablePromotionState) {
        var allOwnedPathsRemoved = true
        if (observable.staging.state == OwnedPathState.OWNED) {
            allOwnedPathsRemoved = deleteOwnedDirectory(
                stagingRoot,
                stagingPath,
                createdIdentity,
                claimPath,
            ) && allOwnedPathsRemoved
        }
        if (observable.final.state == OwnedPathState.OWNED) {
            allOwnedPathsRemoved = deleteOwnedDirectory(
                mangaRoot,
                finalPath,
                createdIdentity,
                claimPath,
            ) && allOwnedPathsRemoved
        }

        val afterCleanup = reconcileObservablePromotionState()
        val ownedPathRemains = afterCleanup.staging.state == OwnedPathState.OWNED ||
            afterCleanup.final.state == OwnedPathState.OWNED
        if (
            allOwnedPathsRemoved &&
            !ownedPathRemains &&
            !observable.needsProvenance &&
            !afterCleanup.needsProvenance
        ) {
            deleteValidClaim(claimsRoot, claimPath, id, createdIdentity.nonce)
        }
    }
}

internal data class OwnedDirectoryIdentity(val id: String, val nonce: String, val fileKey: Any?)

private data class OwnedMarkerIdentity(val id: String, val nonce: String, val fileKey: Any?)

private enum class PathPresence {
    ABSENT,
    PRESENT,
    UNKNOWN,
}

private enum class OwnedPathState {
    ABSENT,
    OWNED,
    OTHER_OR_UNKNOWN,
}

private data class OwnedPathObservation(
    val state: OwnedPathState,
    val needsProvenance: Boolean = false,
)

private data class ObservablePromotionState(
    val staging: OwnedPathObservation,
    val final: OwnedPathObservation,
) {
    val needsProvenance: Boolean = staging.needsProvenance || final.needsProvenance

    companion object {
        fun unknown(): ObservablePromotionState = ObservablePromotionState(
            staging = OwnedPathObservation(OwnedPathState.OTHER_OR_UNKNOWN, needsProvenance = true),
            final = OwnedPathObservation(OwnedPathState.OTHER_OR_UNKNOWN, needsProvenance = true),
        )
    }
}

private data class StagingCopyContext(
    val stagingPath: Path,
    val identity: OwnedDirectoryIdentity,
    val claimPath: Path,
    val stagingRoot: Path,
    val claimsRoot: Path,
    val mangaRoot: Path,
) {
    fun validate() {
        validateClaimAndNamespaces(
            stagingRoot,
            claimsRoot,
            mangaRoot,
            claimPath,
            identity.id,
            identity.nonce,
        )
        requireOwnedDirectoryMatches(stagingPath, identity, claimPath)
    }
}

private data class ValidClaim(val id: String, val nonce: String, val createdAt: Long, val path: Path)

internal fun validateCommittedLocalMangaSecurity(localLibraryRoot: Path, finalPath: Path): Path {
    val root = localLibraryRoot.toAbsolutePath().normalize()
    val claimsRoot = root.resolve(CLAIMS_DIRECTORY)
    val mangaRoot = root.resolve(MANGA_DIRECTORY)
    val normalizedFinal = finalPath.toAbsolutePath().normalize()
    requireImmediateChild(mangaRoot, normalizedFinal, "committed local manga path")
    validatePrivateNamespace(claimsRoot, "claim namespace")
    validatePrivateNamespace(mangaRoot, "manga namespace")
    val id = normalizedFinal.fileName?.toString() ?: rejectStaging("committed local manga has no identifier")
    if (!isValidImportId(id)) rejectStaging("committed local manga identifier is invalid")
    val claimPath = claimsRoot.resolve("$id$CLAIM_SUFFIX")
    validateNoReparseAncestors(claimPath, "local import claim")
    val claim = readValidClaim(claimPath) ?: rejectStaging("committed local manga claim is invalid")
    requireOwnedImportDirectory(normalizedFinal, id, claim.nonce, claimPath)
    validatePrivateTree(normalizedFinal)
    return ownershipMarkerPath(normalizedFinal, claim.nonce)
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

private fun safeEnsurePrivateDirectory(directory: Path) {
    safeEnsureDirectory(directory)
    restrictPrivatePath(directory, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
}

private fun validatePrivateNamespace(directory: Path, label: String) {
    validateNoReparseAncestors(directory, label)
    validatePrivatePath(directory, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
}

private fun validateClaimAndNamespaces(
    stagingRoot: Path,
    claimsRoot: Path,
    mangaRoot: Path,
    claimPath: Path,
    id: String,
    nonce: String,
) {
    validatePrivateNamespace(stagingRoot, "staging namespace")
    validatePrivateNamespace(claimsRoot, "claim namespace")
    validatePrivateNamespace(mangaRoot, "manga namespace")
    requireImmediateChild(claimsRoot, claimPath, "local import claim")
    validateNoReparseAncestors(claimPath, "local import claim")
    val claim = readValidClaim(claimPath)
    if (claim?.id != id || claim.nonce != nonce) {
        rejectStaging("local import claim validation failed: $claimPath")
    }
}

private fun validatePrivateTree(root: Path) {
    try {
        Files.walkFileTree(
            root,
            setOf(),
            Int.MAX_VALUE,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    directory: Path,
                    attributes: BasicFileAttributes,
                ): FileVisitResult {
                    validatePrivatePath(directory, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                    validatePrivatePath(file, FILE_OWNER_PERMISSIONS, isDirectory = false)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, error: IOException): FileVisitResult {
                    throw error
                }
            },
        )
    } catch (error: LocalImportRejected) {
        throw error
    } catch (error: IOException) {
        rejectStaging("private local import tree could not be validated: $root", error)
    }
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
        } catch (error: IOException) {
            rejectStaging("private local import permissions could not be applied: $path", error)
        }
        validatePrivatePath(path, permissions, isDirectory)
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
        validateRestrictedWindowsAcl(path, acl, owner, system, isDirectory)
    } catch (error: LocalImportRejected) {
        throw error
    } catch (error: IOException) {
        rejectStaging("private Windows ACL could not be applied: $path", error)
    } catch (error: SecurityException) {
        rejectStaging("private Windows ACL could not be applied: $path", error)
    }
}

private fun validatePrivatePath(path: Path, permissions: Set<PosixFilePermission>, isDirectory: Boolean) {
    val attributes = readAttributes(path, "private local import path")
    if (
        isLinkOrReparsePoint(path, attributes) ||
        (isDirectory && !attributes.isDirectory) ||
        (!isDirectory && !attributes.isRegularFile)
    ) {
        rejectStaging("private local import path is not a safe ${if (isDirectory) "directory" else "file"}: $path")
    }
    val posix = Files.getFileAttributeView(path, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
    if (posix != null) {
        try {
            if (posix.readAttributes().permissions() != permissions) {
                rejectStaging("private local import permissions could not be validated: $path")
            }
        } catch (error: IOException) {
            rejectStaging("private local import permissions could not be validated: $path", error)
        }
        return
    }
    if (!isWindowsHost()) return
    val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
        ?: rejectStaging("Windows ACL view is unavailable for private local import path: $path")
    try {
        val owner = acl.owner
        val system = lookupWindowsPrincipal(path, "S-1-5-18", "NT AUTHORITY\\SYSTEM", "SYSTEM")
        validateRestrictedWindowsAcl(path, acl, owner, system, isDirectory)
    } catch (error: LocalImportRejected) {
        throw error
    } catch (error: IOException) {
        rejectStaging("private Windows ACL could not be validated: $path", error)
    } catch (error: SecurityException) {
        rejectStaging("private Windows ACL could not be validated: $path", error)
    }
}

private fun validateRestrictedWindowsAcl(
    path: Path,
    view: AclFileAttributeView,
    owner: UserPrincipal,
    system: UserPrincipal,
    isDirectory: Boolean,
) {
    val ownerName = owner.name.lowercase(Locale.ROOT)
    val systemName = system.name.lowercase(Locale.ROOT)
    val entries = view.acl
    val expectedFlags = if (isDirectory) {
        setOf(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT)
    } else {
        emptySet()
    }
    val expectedPermissions = AclEntryPermission.entries.toSet()
    if (
        entries.size != 2 ||
        entries.count { it.principal().name.lowercase(Locale.ROOT) == ownerName } != 1 ||
        entries.count { it.principal().name.lowercase(Locale.ROOT) == systemName } != 1 ||
        entries.any {
            it.type() != AclEntryType.ALLOW ||
                it.permissions() != expectedPermissions ||
                it.flags() != expectedFlags
        }
    ) {
        rejectStaging("private Windows ACL is not the exact owner and SYSTEM allow-list: $path")
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

private fun createClaim(path: Path, id: String, nonce: String, createdAt: Long) {
    if (createdAt < 0) rejectStaging("claim timestamp must not be negative")
    if (!isValidOwnershipNonce(nonce)) rejectStaging("ownership nonce is invalid")
    var created = false
    try {
        // CREATE_NEW + SYNC establishes claim ordering for process-crash recovery on a running filesystem. Pure Java
        // has no portable parent-directory flush, so this is deliberately not a sudden-power-loss durability claim.
        Files.writeString(
            path,
            claimContent(id, nonce, createdAt),
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
            runCatching { deleteValidClaim(checkNotNull(path.parent), path, id, nonce) }
                .onFailure(error::addSuppressed)
        }
        if (error is LocalImportRejected) throw error
        if (error is IOException) rejectStaging("cannot create local import claim: $path", error)
        throw error
    }
    val claim = readValidClaim(path)
    if (claim?.id != id || claim.nonce != nonce) {
        runCatching { deleteValidClaim(checkNotNull(path.parent), path, id, nonce) }
        rejectStaging("local import claim validation failed: $path")
    }
}

private fun createOwnershipMarkerAtomically(
    directory: Path,
    identity: OwnedDirectoryIdentity,
    claimPath: Path,
    stagingRoot: Path,
    claimsRoot: Path,
    mangaRoot: Path,
    fileFaults: LocalFileFaults,
) {
    val id = identity.id
    val nonce = identity.nonce
    val marker = ownershipMarkerPath(directory, nonce)
    val temporary = directory.resolve("${marker.fileName}.${UUID.randomUUID()}.tmp")
    var simulatedCrash = false
    var temporaryIdentity: OwnedMarkerIdentity? = null
    try {
        Files.writeString(
            temporary,
            ownershipMarkerContent(id, nonce),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            StandardOpenOption.SYNC,
        )
        restrictPrivatePath(temporary, FILE_OWNER_PERMISSIONS, isDirectory = false)
        temporaryIdentity = retainOwnedMarkerIdentity(temporary, id, nonce)
        try {
            fileFaults.duringMarkerPublication(temporary, marker)
        } catch (crash: LocalImportCrashSimulation) {
            simulatedCrash = true
            throw crash
        }
        validateClaimAndNamespaces(stagingRoot, claimsRoot, mangaRoot, claimPath, id, nonce)
        requireCreatedDirectoryMatches(directory, identity, claimPath, requireEmpty = false)
        requireImmediateChild(directory, temporary, "temporary ownership marker")
        validateNoReparseAncestors(temporary, "temporary ownership marker")
        if (!hasValidOwnershipMarkerFile(temporary, id, nonce)) {
            rejectStaging("temporary local import ownership marker validation failed")
        }
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            rejectStaging("local import ownership marker destination already exists")
        }
        Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE)
    } catch (error: AtomicMoveNotSupportedException) {
        rejectStaging("atomic ownership marker creation is not supported", error)
    } catch (error: IOException) {
        rejectStaging("cannot create local import ownership marker", error)
    } finally {
        if (!simulatedCrash) {
            temporaryIdentity?.let { identity ->
                runCatching { deleteOwnedMarkerFile(temporary, identity) }
            }
        }
    }
    validatePrivatePath(marker, FILE_OWNER_PERMISSIONS, isDirectory = false)
    if (!hasValidOwnershipMarker(directory, id, nonce)) {
        rejectStaging("local import ownership marker validation failed")
    }
}

private fun retainOwnedMarkerIdentity(marker: Path, id: String, nonce: String): OwnedMarkerIdentity {
    if (!hasValidOwnershipMarkerFile(marker, id, nonce)) {
        rejectStaging("temporary local import ownership marker validation failed")
    }
    val attributes = readAttributes(marker, "temporary ownership marker")
    return OwnedMarkerIdentity(id, nonce, attributes.fileKey())
}

private fun deleteOwnedMarkerFile(marker: Path, identity: OwnedMarkerIdentity): Boolean {
    if (Files.notExists(marker, LinkOption.NOFOLLOW_LINKS)) return true
    if (!hasValidOwnershipMarkerFile(marker, identity.id, identity.nonce)) return false
    val attributes = readAttributes(marker, "temporary ownership marker cleanup")
    if (identity.fileKey != null && attributes.fileKey() != identity.fileKey) return false
    return try {
        Files.delete(marker)
        true
    } catch (_: IOException) {
        false
    }
}

private fun retainCreatedDirectoryIdentity(
    directory: Path,
    id: String,
    nonce: String,
    claimPath: Path,
): OwnedDirectoryIdentity {
    if (directory.fileName?.toString() != id) {
        rejectStaging("created local import directory identifier is invalid: $directory")
    }
    validateNoReparseAncestors(directory, "created import directory")
    val attributes = readAttributes(directory, "created import directory")
    if (!attributes.isDirectory || isLinkOrReparsePoint(directory, attributes)) {
        rejectStaging("created local import directory is not safe: $directory")
    }
    validatePrivatePath(directory, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
    val claim = readValidClaim(claimPath)
    if (claim?.id != id || claim.nonce != nonce) {
        rejectStaging("created local import directory claim is invalid: $directory")
    }
    return OwnedDirectoryIdentity(id, nonce, attributes.fileKey())
}

private fun requireCreatedDirectoryMatches(
    directory: Path,
    identity: OwnedDirectoryIdentity,
    claimPath: Path,
    requireEmpty: Boolean,
): BasicFileAttributes {
    if (directory.fileName?.toString() != identity.id) {
        rejectStaging("created local import directory identifier changed: $directory")
    }
    validateNoReparseAncestors(directory, "created import directory")
    val attributes = readAttributes(directory, "created import directory")
    val claim = readValidClaim(claimPath)
    if (
        !attributes.isDirectory ||
        isLinkOrReparsePoint(directory, attributes) ||
        claim?.id != identity.id ||
        claim.nonce != identity.nonce ||
        (identity.fileKey != null && attributes.fileKey() != identity.fileKey)
    ) {
        rejectStaging("created local import directory identity changed: $directory")
    }
    validatePrivatePath(directory, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
    if (requireEmpty && !isEmptyClaimedDirectory(directory)) {
        rejectStaging("created local import directory is not empty: $directory")
    }
    return attributes
}

private fun requireOwnedDirectoryMatches(
    directory: Path,
    identity: OwnedDirectoryIdentity,
    claimPath: Path,
): BasicFileAttributes {
    val attributes = requireOwnedImportDirectory(directory, identity.id, identity.nonce, claimPath)
    if (identity.fileKey != null && attributes.fileKey() != identity.fileKey) {
        rejectStaging("local import directory identity changed: $directory")
    }
    return attributes
}

private fun requireOwnedImportDirectory(
    directory: Path,
    id: String,
    nonce: String,
    claimPath: Path,
): BasicFileAttributes {
    validateNoReparseAncestors(directory, "owned import directory")
    val attributes = readAttributes(directory, "owned import directory")
    if (
        !attributes.isDirectory ||
        isLinkOrReparsePoint(directory, attributes) ||
        !hasValidOwnershipMarker(directory, id, nonce) ||
        readValidClaim(claimPath)?.let { it.id == id && it.nonce == nonce } != true
    ) {
        rejectStaging("local import ownership validation failed: $directory")
    }
    validatePrivatePath(directory, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
    return attributes
}

private fun deleteOwnedDirectory(
    parent: Path,
    target: Path,
    identity: OwnedDirectoryIdentity,
    claimPath: Path,
): Boolean {
    val normalizedTarget = target.toAbsolutePath().normalize()
    requireImmediateChild(parent, normalizedTarget, "owned cleanup target")
    if (Files.notExists(normalizedTarget, LinkOption.NOFOLLOW_LINKS)) return true
    val matches = runCatching {
        requireOwnedDirectoryMatches(normalizedTarget, identity, claimPath)
        true
    }.getOrDefault(false)
    if (!matches) return false
    deleteValidated(parent, normalizedTarget)
    return true
}

private fun deleteEmptyClaimedDirectory(
    parent: Path,
    target: Path,
    identity: OwnedDirectoryIdentity,
    claimPath: Path,
): Boolean {
    val normalizedTarget = target.toAbsolutePath().normalize()
    requireImmediateChild(parent, normalizedTarget, "empty claimed cleanup target")
    if (Files.notExists(normalizedTarget, LinkOption.NOFOLLOW_LINKS)) return true
    val matches = runCatching {
        requireCreatedDirectoryMatches(normalizedTarget, identity, claimPath, requireEmpty = true)
        true
    }.getOrDefault(false)
    if (!matches) return false
    return try {
        Files.delete(normalizedTarget)
        true
    } catch (_: IOException) {
        false
    }
}

private fun hasValidOwnershipMarker(directory: Path, id: String, nonce: String): Boolean {
    if (!isValidImportId(id) || directory.fileName?.toString() != id) return false
    val marker = ownershipMarkerPath(directory, nonce)
    return hasValidOwnershipMarkerFile(marker, id, nonce)
}

private fun hasValidOwnershipMarkerFile(marker: Path, id: String, nonce: String): Boolean {
    if (!isValidImportId(id) || !isValidOwnershipNonce(nonce)) return false
    val markerAttributes = runCatching { readAttributes(marker, "ownership marker") }.getOrNull() ?: return false
    if (
        !markerAttributes.isRegularFile ||
        isLinkOrReparsePoint(marker, markerAttributes) ||
        markerAttributes.size() > MAX_OWNERSHIP_MARKER_BYTES
    ) {
        return false
    }
    if (runCatching { validatePrivatePath(marker, FILE_OWNER_PERMISSIONS, isDirectory = false) }.isFailure) {
        return false
    }
    return runCatching { Files.readString(marker, StandardCharsets.UTF_8) == ownershipMarkerContent(id, nonce) }
        .getOrDefault(false)
}

private fun readValidClaims(claimsRoot: Path): Map<String, ValidClaim> = try {
    withDirectoryIterationRejection("failed to read local import claims: $claimsRoot") {
        Files.newDirectoryStream(claimsRoot).use { children ->
            children.mapNotNull(::readValidClaim).associateBy(ValidClaim::id)
        }
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
    if (runCatching { validatePrivatePath(path, FILE_OWNER_PERMISSIONS, isDirectory = false) }.isFailure) return null
    val content = runCatching { Files.readString(path, StandardCharsets.UTF_8) }.getOrNull() ?: return null
    val lines = content.split('\n')
    if (lines.size != 5 || lines[0] != CLAIM_FORMAT || lines[4].isNotEmpty()) return null
    if (lines[1] != "id=$id") return null
    val nonce = lines[2].removePrefix("nonce=")
    if (lines[2] != "nonce=$nonce" || !isValidOwnershipNonce(nonce)) return null
    val timestampText = lines[3].removePrefix("createdAt=")
    if (lines[3] != "createdAt=$timestampText") return null
    val createdAt = timestampText.toLongOrNull()?.takeIf { it >= 0 } ?: return null
    return ValidClaim(id, nonce, createdAt, path.toAbsolutePath().normalize())
}

private fun deleteValidClaim(claimsRoot: Path, claimPath: Path, id: String, nonce: String) {
    val normalized = claimPath.toAbsolutePath().normalize()
    requireImmediateChild(claimsRoot, normalized, "claim cleanup target")
    if (Files.notExists(normalized, LinkOption.NOFOLLOW_LINKS)) return
    val valid = readValidClaim(normalized) ?: return
    if (valid.id != id || valid.nonce != nonce) return
    try {
        Files.delete(normalized)
    } catch (error: IOException) {
        rejectStaging("failed to clean local import claim: $normalized", error)
    }
}

private fun isStaleClaim(createdAt: Long, now: Long): Boolean =
    now >= createdAt && now - createdAt >= CLAIM_STALE_MILLIS

private fun isSafeImportDirectory(
    directory: Path,
    claim: ValidClaim,
    allowTemporaryMarker: Boolean,
): Boolean {
    if (directory.fileName?.toString() != claim.id) return false
    val attributes = runCatching { readAttributes(directory, "claimed import directory") }.getOrNull() ?: return false
    if (!attributes.isDirectory || isLinkOrReparsePoint(directory, attributes)) return false
    if (runCatching { validateNoReparseAncestors(directory, "claimed import directory") }.isFailure) return false
    if (runCatching { validatePrivatePath(directory, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true) }.isFailure) {
        return false
    }
    if (hasValidOwnershipMarker(directory, claim.id, claim.nonce)) return true
    if (!allowTemporaryMarker) return false
    if (hasValidTemporaryOwnershipMarker(directory, claim)) return true
    return isEmptyClaimedDirectory(directory)
}

private fun isEmptyClaimedDirectory(directory: Path): Boolean = try {
    withDirectoryIterationRejection("failed to enumerate claimed import directory: $directory") {
        Files.newDirectoryStream(directory).use { entries -> !entries.iterator().hasNext() }
    }
} catch (_: IOException) {
    false
}

private fun hasValidTemporaryOwnershipMarker(directory: Path, claim: ValidClaim): Boolean = try {
    val markerName = ownershipMarkerPath(directory, claim.nonce).fileName.toString()
    withDirectoryIterationRejection("failed to enumerate temporary ownership markers: $directory") {
        Files.newDirectoryStream(directory, "$markerName.*.tmp").use { candidates ->
            val matching = candidates.filter { marker ->
                hasValidOwnershipMarkerFile(marker, claim.id, claim.nonce)
            }
            matching.size == 1
        }
    }
} catch (_: IOException) {
    false
}

private fun ownershipMarkerContent(id: String, nonce: String): String =
    "$OWNERSHIP_MARKER_FORMAT\nid=$id\nnonce=$nonce\n"

private fun claimContent(id: String, nonce: String, createdAt: Long): String =
    "$CLAIM_FORMAT\nid=$id\nnonce=$nonce\ncreatedAt=$createdAt\n"

internal fun ownershipMarkerPath(directory: Path, nonce: String): Path {
    if (!isValidOwnershipNonce(nonce)) rejectStaging("ownership marker nonce is invalid")
    return directory.resolve("$LOCAL_IMPORT_OWNERSHIP_MARKER_PREFIX$nonce")
}

private fun newOwnershipNonce(): String {
    val bytes = ByteArray(OWNERSHIP_NONCE_BYTES)
    OWNERSHIP_RANDOM.nextBytes(bytes)
    return bytes.joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }
}

private fun isValidOwnershipNonce(nonce: String): Boolean =
    nonce.length == OWNERSHIP_NONCE_BYTES * 2 && nonce.all { it in '0'..'9' || it in 'a'..'f' }

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

private fun samePathIdentity(before: BasicFileAttributes, after: BasicFileAttributes): Boolean =
    before.isDirectory == after.isDirectory &&
        before.isRegularFile == after.isRegularFile &&
        before.isSymbolicLink == after.isSymbolicLink &&
        before.isOther == after.isOther &&
        (before.fileKey() == null || before.fileKey() == after.fileKey())

private fun observePathPresence(path: Path): PathPresence = try {
    Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    PathPresence.PRESENT
} catch (_: NoSuchFileException) {
    PathPresence.ABSENT
} catch (_: IOException) {
    PathPresence.UNKNOWN
} catch (_: SecurityException) {
    PathPresence.UNKNOWN
}

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
internal const val LOCAL_IMPORT_OWNERSHIP_MARKER_PREFIX = ".mihon-local-import-owner."
private const val OWNERSHIP_MARKER_FORMAT = "mihon-desktop-local-import-v1"
private const val MAX_OWNERSHIP_MARKER_BYTES = 256L
private const val MAX_CLAIM_BYTES = 512L
private const val OWNERSHIP_NONCE_BYTES = 16
private const val CLAIM_STALE_MILLIS = 5 * 60 * 1000L
private const val COPY_BUFFER_SIZE = 64 * 1024
private val OWNERSHIP_RANDOM = SecureRandom()
private val DIRECTORY_OWNER_PERMISSIONS = setOf(
    PosixFilePermission.OWNER_READ,
    PosixFilePermission.OWNER_WRITE,
    PosixFilePermission.OWNER_EXECUTE,
)
private val FILE_OWNER_PERMISSIONS = setOf(
    PosixFilePermission.OWNER_READ,
    PosixFilePermission.OWNER_WRITE,
)
