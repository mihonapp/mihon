package mihon.reader.source

import mihon.reader.memory.MemoryKind
import mihon.reader.memory.MemoryLease
import mihon.reader.memory.ReaderMemoryBudget
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import org.apache.commons.compress.MemoryLimitException
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod

class SevenZipChapterSource(
    asset: ReaderChapterAsset,
    private val memoryBudget: ReaderMemoryBudget,
    private val securePath: SecureLocalPath = SecureLocalPath(asset.storageRoot),
    expansionBudget: ChapterExpansionBudget = ChapterExpansionBudget(),
) : ManagedChapterSource(asset, expansionBudget) {
    private val decoderAllowance = ReaderLimits.SEVEN_Z_MEMORY_KIB * 1024L
    private val entries: List<SourceEntry> = enumerate().sortedNaturally()

    override suspend fun pages(): List<PageDescriptor> {
        checkOpenAndCancellation()
        return entries.map { it.descriptor(asset) }
    }

    override suspend fun open(pageId: PageId): BoundedPageInput {
        checkOpenAndCancellation()
        val wanted = requireEntry(pageId, entries)
        val lease = reserveDecoderMemory()
        val archive = try {
            openArchive()
        } catch (error: Throwable) {
            lease.close()
            throw error
        }
        try {
            val entry = archive.entries.singleOrNull { it.name == wanted.rawName }
                ?: throw ReaderFailure.PageNotFound(wanted.logicalName)
            validateSevenZEntry(entry)
            val input = archive.getInputStream(entry)
            return boundedInput(input, entry.size) {
                try {
                    archive.close()
                } finally {
                    lease.close()
                }
            }
        } catch (error: Throwable) {
            try {
                archive.close()
            } finally {
                lease.close()
            }
            throw classifySevenZFailure(error)
        }
    }

    private fun enumerate(): List<SourceEntry> {
        val lease = reserveDecoderMemory()
        val archive = try {
            openArchive()
        } catch (error: Throwable) {
            lease.close()
            throw error
        }
        return try {
            val found = mutableListOf<SourceEntry>()
            val duplicateKeys = mutableSetOf<String>()
            var count = 0
            archive.entries.forEach { entry ->
                count += 1
                if (count > ReaderLimits.MAX_ENTRIES) throw ReaderFailure.TooManyEntries(ReaderLimits.MAX_ENTRIES)
                validateSevenZEntry(entry)
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
        } catch (error: Throwable) {
            throw classifySevenZFailure(error)
        } finally {
            try {
                archive.close()
            } finally {
                lease.close()
            }
        }
    }

    private fun reserveDecoderMemory(): MemoryLease =
        memoryBudget.tryReserve(MemoryKind.DECODED_OUTPUT, decoderAllowance)
            ?: throw ReaderFailure.LimitExceeded("7z decoder memory", decoderAllowance, decoderAllowance)

    private fun openArchive(): SevenZFile {
        val channel = securePath.openRegularFile(asset.relativePath)
        return try {
            SevenZFile.builder()
                .setSeekableByteChannel(channel)
                .setMaxMemoryLimitKiB(ReaderLimits.SEVEN_Z_MEMORY_KIB)
                .get()
        } catch (error: Throwable) {
            channel.close()
            throw classifySevenZFailure(error)
        }
    }
}

private fun validateSevenZEntry(entry: SevenZArchiveEntry) {
    if (entry.contentMethods?.any { it.method == SevenZMethod.AES256SHA256 } == true) {
        throw ReaderFailure.EncryptedContainer("7z")
    }
    if (entry.hasWindowsAttributes) {
        val attributes = entry.windowsAttributes
        val isReparsePoint = attributes and 0x400 != 0
        val unixType = attributes ushr 16 and 0xF000
        if (isReparsePoint || (unixType != 0 && unixType != 0x8000 && unixType != 0x4000)) {
            throw ReaderFailure.UnsafePath(entry.name, "archive link or special entry")
        }
    }
}

private fun classifySevenZFailure(error: Throwable): ReaderFailure = when (error) {
    is ReaderFailure -> error
    is PasswordRequiredException -> ReaderFailure.EncryptedContainer("7z")
    is MemoryLimitException -> ReaderFailure.LimitExceeded(
        "7z decoder memory KiB",
        ReaderLimits.SEVEN_Z_MEMORY_KIB.toLong(),
        error.memoryNeededInKb,
    )
    else -> ReaderFailure.CorruptContainer("7z", error)
}
