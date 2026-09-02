package mihon.reader.source

import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import java.nio.channels.Channels

class StandaloneImageChapterSource(
    asset: ReaderChapterAsset,
    private val securePath: SecureLocalPath = SecureLocalPath(asset.storageRoot),
    expansionBudget: ChapterExpansionBudget = ChapterExpansionBudget(),
) : ManagedChapterSource(asset, expansionBudget) {
    private val entry: SourceEntry

    init {
        val normalized = ImageEntryPolicy.normalize(asset.relativePath.fileName.toString())
        if (!ImageEntryPolicy.isSupportedImage(normalized)) throw ReaderFailure.UnsupportedFormat("image")
        securePath.openRegularFile(asset.relativePath).use { channel ->
            if (channel.size() > ReaderLimits.MAX_STANDALONE_BYTES) {
                throw ReaderFailure.LimitExceeded(
                    "standalone encoded bytes",
                    ReaderLimits.MAX_STANDALONE_BYTES,
                    channel.size(),
                )
            }
            entry = SourceEntry(normalized, normalized, channel.size())
        }
    }

    override suspend fun pages(): List<PageDescriptor> {
        checkOpenAndCancellation()
        return listOf(entry.descriptor(asset))
    }

    override suspend fun open(pageId: PageId): BoundedPageInput {
        checkOpenAndCancellation()
        requireEntry(pageId, listOf(entry))
        val channel = securePath.openRegularFile(asset.relativePath)
        if (channel.size() > ReaderLimits.MAX_STANDALONE_BYTES) {
            val size = channel.size()
            channel.close()
            throw ReaderFailure.LimitExceeded("standalone encoded bytes", ReaderLimits.MAX_STANDALONE_BYTES, size)
        }
        return boundedInput(Channels.newInputStream(channel), channel.size())
    }
}
