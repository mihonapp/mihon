package mihon.reader.source

import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import java.nio.channels.Channels
import java.nio.file.Path

class DirectoryChapterSource(
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
        val entry = requireEntry(pageId, entries)
        val channel = securePath.openRegularFile(asset.relativePath.resolve(entry.logicalName))
        return boundedInput(Channels.newInputStream(channel), channel.size())
    }

    private fun enumerate(): List<SourceEntry> {
        val found = mutableListOf<SourceEntry>()
        val duplicateKeys = mutableSetOf<String>()
        var count = 0

        fun visit(relativeDirectory: Path, logicalPrefix: String) {
            securePath.listDirectory(relativeDirectory, ReaderLimits.MAX_ENTRIES - count).forEach { child ->
                synchronousScanCheckpoint()
                count += 1
                if (count > ReaderLimits.MAX_ENTRIES) throw ReaderFailure.TooManyEntries(ReaderLimits.MAX_ENTRIES)
                val attributes = securePath.secureAttributes(child)
                val rawLogical = if (logicalPrefix.isEmpty()) {
                    child.fileName.toString()
                } else {
                    "$logicalPrefix/${child.fileName}"
                }
                val logical = ImageEntryPolicy.normalize(rawLogical)
                val key = ImageEntryPolicy.duplicateKey(logical)
                if (!duplicateKeys.add(key)) throw ReaderFailure.DuplicateEntry(logical)
                when {
                    attributes.isDirectory -> visit(relativeDirectory.resolve(child.fileName), logical)
                    attributes.isRegularFile && ImageEntryPolicy.isSupportedImage(logical) ->
                        found += SourceEntry(logical, logical, attributes.size())
                    attributes.isRegularFile -> Unit
                    else -> throw ReaderFailure.UnsafePath(logical, "non-regular directory entry")
                }
            }
        }
        visit(asset.relativePath, "")
        return found
    }
}
