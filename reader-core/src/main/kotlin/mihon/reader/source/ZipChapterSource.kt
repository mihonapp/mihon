package mihon.reader.source

import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile

class ZipChapterSource(
    asset: ReaderChapterAsset,
    private val securePath: SecureLocalPath = SecureLocalPath(asset.storageRoot),
    expansionBudget: ChapterExpansionBudget = ChapterExpansionBudget(),
) : ManagedChapterSource(asset, expansionBudget) {
    private val entries: List<SourceEntry> = enumerate().sortedNaturally()

    override suspend fun pages(): List<PageDescriptor> {
        checkOpenAndCancellation()
        return entries.map { it.descriptor(asset) }
    }

    override suspend fun open(pageId: PageId): BoundedPageInput {
        checkOpenAndCancellation()
        val wanted = requireEntry(pageId, entries)
        val channel = securePath.openRegularFile(asset.relativePath)
        val archive = try {
            ZipFile.builder().setSeekableByteChannel(channel).get()
        } catch (error: Throwable) {
            channel.close()
            throw classifyZipFailure(error)
        }
        try {
            val entry = archive.entries.asSequence().singleOrNull { it.name == wanted.rawName }
                ?: throw ReaderFailure.PageNotFound(wanted.logicalName)
            validateZipEntry(entry)
            if (!archive.canReadEntryData(entry)) throw ReaderFailure.UnsupportedFormat("ZIP entry compression")
            val input = archive.getInputStream(entry)
            return boundedInput(input, entry.size) { archive.close() }
        } catch (error: Throwable) {
            archive.close()
            throw classifyZipFailure(error)
        }
    }

    private fun enumerate(): List<SourceEntry> {
        val channel = securePath.openRegularFile(asset.relativePath)
        val archive = try {
            ZipFile.builder().setSeekableByteChannel(channel).get()
        } catch (error: Throwable) {
            channel.close()
            throw classifyZipFailure(error)
        }
        return try {
            val found = mutableListOf<SourceEntry>()
            val duplicateKeys = mutableSetOf<String>()
            var count = 0
            archive.entries.asSequence().forEach { entry ->
                count += 1
                if (count > ReaderLimits.MAX_ENTRIES) throw ReaderFailure.TooManyEntries(ReaderLimits.MAX_ENTRIES)
                validateZipEntry(entry)
                if (!archive.canReadEntryData(entry)) throw ReaderFailure.UnsupportedFormat("ZIP entry compression")
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
            throw classifyZipFailure(error)
        } finally {
            archive.close()
        }
    }
}

internal fun validateZipEntry(entry: ZipArchiveEntry) {
    if (entry.generalPurposeBit.usesEncryption() || entry.generalPurposeBit.usesStrongEncryption()) {
        throw ReaderFailure.EncryptedContainer("ZIP")
    }
    if (!entry.isDirectory && entry.isUnixSymlink) throw ReaderFailure.UnsafePath(entry.name, "archive symbolic link")
    val mode = entry.unixMode and 0xF000
    if (mode != 0 && mode != 0x8000 && mode != 0x4000) {
        throw ReaderFailure.UnsafePath(entry.name, "archive special-device entry")
    }
}

internal fun classifyZipFailure(error: Throwable): ReaderFailure = when (error) {
    is ReaderFailure -> error
    else -> ReaderFailure.CorruptContainer("ZIP", error)
}
