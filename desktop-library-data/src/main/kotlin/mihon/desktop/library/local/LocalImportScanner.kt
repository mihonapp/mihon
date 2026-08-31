package mihon.desktop.library.local

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributes
import java.security.MessageDigest
import java.util.Locale

enum class LocalChapterKind {
    DIRECTORY,
    ARCHIVE,
}

data class LocalChapterCandidate(
    val name: String,
    val relativePath: Path,
    val kind: LocalChapterKind,
    val sizeBytes: Long,
    val modifiedAt: Long,
)

data class LocalImportManifest(
    val title: String,
    val sourceRoot: Path,
    val chapters: List<LocalChapterCandidate>,
    val sha256: String,
)

data class LocalImportLimits(
    val maxChapters: Int = 100_000,
    val maxEntries: Long = 2_000_000,
    val maxBytes: Long = 1_073_741_824,
) {
    init {
        require(maxChapters > 0)
        require(maxEntries > 0)
        require(maxBytes >= 0)
    }
}

class LocalImportRejected(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

class LocalImportScanner(
    private val limits: LocalImportLimits = LocalImportLimits(),
) {
    fun scan(sourceDirectory: Path): LocalImportManifest {
        val root = sourceDirectory.toAbsolutePath().normalize()
        if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) {
            reject("source root is not a readable directory: $root")
        }
        validateNoReparseAncestors(root, "source root")
        val rootAttributes = readAttributes(root, "source root")
        if (isLinkOrReparsePoint(root, rootAttributes) || !rootAttributes.isDirectory || !Files.isReadable(root)) {
            reject("source root is not a readable directory: $root")
        }
        val title = root.fileName?.toString()?.takeIf(String::isNotEmpty)
            ?: reject("source root must have a directory name: $root")
        val state = ScanState(root, limits)
        val chapters = try {
            Files.newDirectoryStream(root).use { children ->
                children.mapNotNull { child -> scanTopLevel(child, state) }
            }
        } catch (error: LocalImportRejected) {
            throw error
        } catch (error: IOException) {
            reject("cannot enumerate source root: $root", error)
        }
        if (chapters.isEmpty()) reject("local manga contains no chapters: $root")
        val sorted = chapters.sortedWith(compareBy(LOCAL_CHAPTER_PATH_COMPARATOR) { portablePath(it.relativePath) })
        validateNoReparseAncestors(root, "source root")
        return LocalImportManifest(title, root, sorted, manifestSha256(sorted))
    }

    internal fun validateCandidatePaths(root: Path, candidates: List<Path>) {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val seen = mutableSetOf<String>()
        candidates.forEach { relative ->
            if (relative.isAbsolute) reject("candidate path must not be absolute: $relative")
            val resolved = normalizedRoot.resolve(relative).normalize()
            if (!resolved.startsWith(normalizedRoot)) reject("candidate path escapes source root: $relative")
            val normalizedRelative = normalizedRoot.relativize(resolved)
            if (normalizedRelative.nameCount == 0) reject("candidate path resolves to source root: $relative")
            val key = portablePath(normalizedRelative).lowercase(Locale.ROOT)
            if (!seen.add(key)) reject("case-folded duplicate path: $relative")
        }
    }

    private fun scanTopLevel(child: Path, state: ScanState): LocalChapterCandidate? {
        val preliminaryAttributes = readAttributes(child, "top-level entry")
        if (isLinkOrReparsePoint(child, preliminaryAttributes)) {
            reject("link or reparse point is not allowed: ${child.fileName}")
        }
        if (!Files.isReadable(child)) reject("entry is unreadable: ${child.fileName}")
        if (isHidden(child, preliminaryAttributes)) return null
        val entry = state.inspect(child)
        val relative = entry.relativePath
        return when {
            entry.attributes.isDirectory -> {
                val directorySize = scanChapterDirectory(child, state)
                LocalChapterCandidate(
                    name = child.fileName.toString(),
                    relativePath = relative,
                    kind = LocalChapterKind.DIRECTORY,
                    sizeBytes = directorySize,
                    modifiedAt = entry.attributes.lastModifiedTime().toMillis(),
                )
            }
            entry.attributes.isRegularFile && isSupportedArchive(child.fileName.toString()) -> {
                state.addBytes(entry.attributes.size(), child)
                LocalChapterCandidate(
                    name = child.fileName.toString(),
                    relativePath = relative,
                    kind = LocalChapterKind.ARCHIVE,
                    sizeBytes = entry.attributes.size(),
                    modifiedAt = entry.attributes.lastModifiedTime().toMillis(),
                )
            }
            entry.attributes.isRegularFile -> reject("unsupported top-level file: $relative")
            else -> reject("top-level entry is not a regular file or directory: $relative")
        }
    }

    private fun scanChapterDirectory(directory: Path, state: ScanState): Long {
        var size = 0L
        try {
            Files.walkFileTree(
                directory,
                setOf(),
                Int.MAX_VALUE,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attributes: BasicFileAttributes): FileVisitResult {
                        if (dir != directory) state.inspect(dir)
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                        val inspected = state.inspect(file)
                        if (!inspected.attributes.isRegularFile) {
                            reject("chapter asset is not a regular file: ${inspected.relativePath}")
                        }
                        size = checkedAdd(size, inspected.attributes.size(), "chapter byte size")
                        state.addBytes(inspected.attributes.size(), file)
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, error: IOException): FileVisitResult =
                        reject("chapter asset is unreadable: $file", error)
                },
            )
        } catch (error: LocalImportRejected) {
            throw error
        } catch (error: IOException) {
            reject("cannot scan chapter directory: $directory", error)
        }
        return size
    }
}

private data class InspectedEntry(
    val relativePath: Path,
    val attributes: BasicFileAttributes,
)

private class ScanState(
    private val root: Path,
    private val limits: LocalImportLimits,
) {
    private val caseFoldedPaths = mutableSetOf<String>()
    private var entries = 0L
    private var bytes = 0L
    private var chapters = 0

    fun inspect(candidate: Path): InspectedEntry {
        val absolute = candidate.toAbsolutePath().normalize()
        if (!absolute.startsWith(root)) reject("candidate path escapes source root: $candidate")
        val relative = root.relativize(absolute)
        if (relative.nameCount == 0) reject("candidate path resolves to source root: $candidate")
        val key = portablePath(relative).lowercase(Locale.ROOT)
        if (!caseFoldedPaths.add(key)) reject("case-folded duplicate path: $relative")
        entries++
        if (entries > limits.maxEntries) reject("local import entry limit exceeded: ${limits.maxEntries}")
        val attributes = readAttributes(absolute, relative.toString())
        if (isLinkOrReparsePoint(absolute, attributes)) reject("link or reparse point is not allowed: $relative")
        if (!Files.isReadable(absolute)) reject("entry is unreadable: $relative")
        if (relative.nameCount == 1) {
            chapters++
            if (chapters > limits.maxChapters) reject("local import chapter limit exceeded: ${limits.maxChapters}")
        }
        return InspectedEntry(relative, attributes)
    }

    fun addBytes(amount: Long, path: Path) {
        if (amount < 0) reject("negative entry size: $path")
        bytes = checkedAdd(bytes, amount, "local import byte size")
        if (bytes > limits.maxBytes) reject("local import byte limit exceeded: ${limits.maxBytes}")
    }
}

private val SUPPORTED_ARCHIVE_EXTENSIONS = setOf("cbz", "zip", "rar", "cbr", "7z", "cb7", "tar", "cbt", "epub")

private fun isSupportedArchive(name: String): Boolean =
    name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT) in SUPPORTED_ARCHIVE_EXTENSIONS

private fun isHidden(path: Path, attributes: BasicFileAttributes): Boolean {
    if (path.fileName.toString().startsWith('.')) return true
    if (attributes is DosFileAttributes && attributes.isHidden) return true
    return runCatching { Files.isHidden(path) }.getOrDefault(false)
}

internal fun readAttributes(path: Path, label: String): BasicFileAttributes = try {
    Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
} catch (error: IOException) {
    reject("cannot read attributes for $label: $path", error)
} catch (error: SecurityException) {
    reject("cannot read attributes for $label: $path", error)
}

internal fun validateNoReparseAncestors(path: Path, label: String) {
    val absolute = path.toAbsolutePath().normalize()
    var current = absolute.root ?: reject("$label has no filesystem root: $absolute")
    validateSafePathComponent(current, label)
    for (index in 0 until absolute.nameCount) {
        current = current.resolve(absolute.getName(index))
        validateSafePathComponent(current, label)
    }
}

private fun validateSafePathComponent(path: Path, label: String) {
    val attributes = readAttributes(path, "$label ancestor")
    if (isLinkOrReparsePoint(path, attributes)) {
        reject("$label ancestor is a link or reparse point: $path")
    }
}

internal fun isLinkOrReparsePoint(path: Path, attributes: BasicFileAttributes): Boolean {
    if (Files.isSymbolicLink(path) || attributes.isSymbolicLink || attributes.isOther) return true
    if (attributes is DosFileAttributes && attributes.isOther) return true
    return runCatching {
        Files.getAttribute(path, "dos:reparsePoint", LinkOption.NOFOLLOW_LINKS) as? Boolean == true
    }.getOrDefault(false)
}

internal fun portablePath(path: Path): String =
    (0 until path.nameCount).joinToString("/") { path.getName(it).toString() }

private val LOCAL_CHAPTER_PATH_COMPARATOR = Comparator<String>(::compareCodePoints)

private fun compareCodePoints(left: String, right: String): Int {
    var leftIndex = 0
    var rightIndex = 0
    while (leftIndex < left.length && rightIndex < right.length) {
        val leftPoint = left.codePointAt(leftIndex)
        val rightPoint = right.codePointAt(rightIndex)
        if (leftPoint != rightPoint) return leftPoint.compareTo(rightPoint)
        leftIndex += Character.charCount(leftPoint)
        rightIndex += Character.charCount(rightPoint)
    }
    return (left.length - leftIndex).compareTo(right.length - rightIndex)
}

private fun manifestSha256(chapters: List<LocalChapterCandidate>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    chapters.forEach { chapter ->
        digest.updateLengthPrefixed(portablePath(chapter.relativePath))
        digest.updateLengthPrefixed(chapter.kind.name)
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(chapter.sizeBytes).array())
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(chapter.modifiedAt).array())
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun MessageDigest.updateLengthPrefixed(value: String) {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
    update(bytes)
}

private fun checkedAdd(left: Long, right: Long, label: String): Long = try {
    Math.addExact(left, right)
} catch (error: ArithmeticException) {
    reject("$label overflow", error)
}

private fun reject(message: String, cause: Throwable? = null): Nothing = throw LocalImportRejected(message, cause)
