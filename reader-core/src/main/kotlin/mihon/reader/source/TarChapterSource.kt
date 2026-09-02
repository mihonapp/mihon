package mihon.reader.source

import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import org.apache.commons.compress.MemoryLimitException
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.channels.Channels

class TarChapterSource(
    asset: ReaderChapterAsset,
    private val securePath: SecureLocalPath = SecureLocalPath(asset.storageRoot),
    expansionBudget: ChapterExpansionBudget = ChapterExpansionBudget(),
    private val compression: TarCompression = TarCompression.NONE,
) : ManagedChapterSource(asset, expansionBudget) {
    private val entries: List<SourceEntry> = enumerate().sortedNaturally()

    override suspend fun pages(): List<PageDescriptor> {
        checkOpenAndCancellation()
        return entries.map { it.descriptor(asset) }
    }

    override suspend fun open(pageId: PageId): BoundedPageInput {
        checkOpenAndCancellation()
        val wanted = requireEntry(pageId, entries)
        val archive = openArchive()
        try {
            while (true) {
                val entry = archive.nextEntry ?: break
                validateTarEntry(entry)
                if (entry.name == wanted.rawName) {
                    return boundedInput(archive, entry.size, chargeChapterBudget = false)
                }
            }
            throw ReaderFailure.PageNotFound(wanted.logicalName)
        } catch (error: Throwable) {
            archive.close()
            if (error is ReaderFailure) throw error
            throw classifyTarFailure(error)
        }
    }

    private fun enumerate(): List<SourceEntry> {
        val archive = openArchive()
        return try {
            val found = mutableListOf<SourceEntry>()
            val duplicateKeys = mutableSetOf<String>()
            var count = 0
            while (true) {
                val entry = archive.nextEntry ?: break
                count += 1
                if (count > ReaderLimits.MAX_ENTRIES) throw ReaderFailure.TooManyEntries(ReaderLimits.MAX_ENTRIES)
                validateTarEntry(entry)
                val normalized = ImageEntryPolicy.normalize(entry.name)
                val key = ImageEntryPolicy.duplicateKey(normalized)
                if (!duplicateKeys.add(key)) throw ReaderFailure.DuplicateEntry(normalized)
                if (!entry.isDirectory) {
                    if (ImageEntryPolicy.isSupportedImage(normalized)) {
                        found += SourceEntry(normalized, entry.name, entry.size)
                    }
                }
            }
            found
        } catch (error: ReaderFailure) {
            throw error
        } catch (error: Throwable) {
            throw classifyTarFailure(error)
        } finally {
            archive.close()
        }
    }

    private fun openArchive(): TarArchiveInputStream {
        val channel = securePath.openRegularFile(asset.relativePath)
        return try {
            val raw: InputStream = BufferedInputStream(Channels.newInputStream(channel))
            val content = openTarContent(raw, compression)
            TarArchiveInputStream(ChapterBudgetInputStream(content, expansionBudget))
        } catch (error: Throwable) {
            channel.close()
            if (error is ReaderFailure) throw error
            throw classifyTarFailure(error)
        }
    }

    companion object {
        fun hasTarMagic(
            asset: ReaderChapterAsset,
            securePath: SecureLocalPath,
            compression: TarCompression,
        ): Boolean {
            val channel = securePath.openRegularFile(asset.relativePath)
            return try {
                val raw: InputStream = BufferedInputStream(Channels.newInputStream(channel))
                openTarContent(raw, compression).use { content ->
                    val signature = content.readNBytes(512)
                    TarArchiveInputStream.matches(signature, signature.size)
                }
            } catch (error: Throwable) {
                channel.close()
                throw classifyTarFailure(error)
            }
        }
    }
}

enum class TarCompression {
    NONE,
    GZIP,
    BZIP2,
    XZ,
}

private fun validateTarEntry(entry: TarArchiveEntry) {
    if (
        entry.isSymbolicLink || entry.isLink || entry.isBlockDevice || entry.isCharacterDevice ||
        entry.isFIFO || entry.isSparse
    ) {
        throw ReaderFailure.UnsafePath(entry.name, "archive link, device, or sparse entry")
    }
    if (!entry.isDirectory && !entry.isFile) throw ReaderFailure.UnsafePath(entry.name, "unsupported TAR entry type")
}

private fun openTarContent(raw: InputStream, compression: TarCompression): InputStream = when (compression) {
    TarCompression.NONE -> raw
    TarCompression.GZIP -> GzipCompressorInputStream(raw)
    TarCompression.BZIP2 -> BZip2CompressorInputStream(raw)
    TarCompression.XZ -> XZCompressorInputStream.builder()
        .setInputStream(raw)
        .setDecompressConcatenated(false)
        .setMemoryLimitKiB(ReaderLimits.SEVEN_Z_MEMORY_KIB)
        .get()
}

private fun classifyTarFailure(error: Throwable): ReaderFailure = when (error) {
    is ReaderFailure -> error
    is MemoryLimitException -> ReaderFailure.LimitExceeded(
        "XZ decoder memory KiB",
        ReaderLimits.SEVEN_Z_MEMORY_KIB.toLong(),
        error.memoryNeededInKb,
    )
    else -> ReaderFailure.CorruptContainer("TAR", error)
}
