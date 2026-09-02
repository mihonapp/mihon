package mihon.reader.source

import com.github.junrar.Archive
import com.github.junrar.ArchiveOptions
import com.github.junrar.exception.RarException
import com.github.junrar.exception.WrongPasswordException
import com.github.junrar.io.SeekableReadOnlyByteChannel
import com.github.junrar.rarfile.FileHeader
import com.github.junrar.rarfile.HostSystem
import com.github.junrar.volume.Volume
import com.github.junrar.volume.VolumeManager
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class RarChapterSource(
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
        val archive = openArchive()
        try {
            rejectEncryption(archive)
            val header = archive.fileHeaders.singleOrNull { it.fileName == wanted.rawName }
                ?: throw ReaderFailure.PageNotFound(wanted.logicalName)
            validateRarEntry(header)
            val input = archive.getInputStream(header)
            return boundedInput(input, header.fullUnpackSize) { archive.close() }
        } catch (error: Throwable) {
            archive.close()
            throw classifyRarFailure(error)
        }
    }

    private fun enumerate(): List<SourceEntry> {
        val archive = openArchive()
        return try {
            rejectEncryption(archive)
            val found = mutableListOf<SourceEntry>()
            val duplicateKeys = mutableSetOf<String>()
            var count = 0
            archive.fileHeaders.forEach { header ->
                count += 1
                if (count > ReaderLimits.MAX_ENTRIES) throw ReaderFailure.TooManyEntries(ReaderLimits.MAX_ENTRIES)
                validateRarEntry(header)
                val normalized = ImageEntryPolicy.normalize(header.fileName)
                val key = ImageEntryPolicy.duplicateKey(normalized)
                if (!duplicateKeys.add(key)) throw ReaderFailure.DuplicateEntry(normalized)
                if (!header.isDirectory) {
                    if (ImageEntryPolicy.isSupportedImage(normalized)) {
                        found += SourceEntry(normalized, header.fileName, header.fullUnpackSize)
                    }
                }
            }
            found
        } catch (error: Throwable) {
            throw classifyRarFailure(error)
        } finally {
            archive.close()
        }
    }

    private fun openArchive(): Archive {
        val channel = securePath.openRegularFile(asset.relativePath)
        return try {
            Archive(
                SingleChannelVolumeManager(channel),
                ArchiveOptions.builder().maxDictionarySize(ReaderLimits.SEVEN_Z_MEMORY_KIB * 1024L).build(),
            )
        } catch (error: Throwable) {
            channel.close()
            throw classifyRarFailure(error)
        }
    }
}

private class SingleChannelVolumeManager(
    private val fileChannel: FileChannel,
) : VolumeManager {
    override fun nextVolume(archive: Archive, lastVolume: Volume?): Volume? =
        if (lastVolume != null) {
            null
        } else {
            object : Volume {
                override fun getChannel(): SeekableReadOnlyByteChannel = FileChannelAdapter(fileChannel)

                override fun getLength(): Long = fileChannel.size()

                override fun getArchive(): Archive = archive
            }
        }
}

private class FileChannelAdapter(
    private val channel: FileChannel,
) : SeekableReadOnlyByteChannel {
    override fun getPosition(): Long = channel.position()

    override fun setPosition(pos: Long) {
        channel.position(pos)
    }

    override fun read(): Int {
        val one = ByteBuffer.allocate(1)
        return if (channel.read(one) < 0) -1 else one.array()[0].toInt() and 0xFF
    }

    override fun read(buffer: ByteArray, off: Int, count: Int): Int = channel.read(ByteBuffer.wrap(buffer, off, count))

    override fun readFully(buffer: ByteArray, count: Int): Int {
        var offset = 0
        while (offset < count) {
            val read = read(buffer, offset, count - offset)
            if (read < 0) throw EOFException("Unexpected end of RAR channel")
            offset += read
        }
        return count
    }

    override fun close() = channel.close()
}

private fun rejectEncryption(archive: Archive) {
    if (archive.isEncrypted || archive.isPasswordProtected) throw ReaderFailure.EncryptedContainer("RAR")
    if (archive.mainHeader?.isMultiVolume == true) throw ReaderFailure.UnsupportedFormat("multi-volume RAR")
}

private fun validateRarEntry(header: FileHeader) {
    if (header.isEncrypted) throw ReaderFailure.EncryptedContainer("RAR")
    if (header.redirection != null) throw ReaderFailure.UnsafePath(header.fileName, "archive link entry")
    if (header.hostOS == HostSystem.unix) {
        val unixType = header.fileAttr and 0xF000
        if (unixType != 0 && unixType != 0x8000 && unixType != 0x4000) {
            throw ReaderFailure.UnsafePath(header.fileName, "archive special entry")
        }
    }
}

private fun classifyRarFailure(error: Throwable): ReaderFailure = when (error) {
    is ReaderFailure -> error
    is WrongPasswordException -> ReaderFailure.EncryptedContainer("RAR")
    is RarException -> ReaderFailure.CorruptContainer("RAR", error)
    else -> ReaderFailure.CorruptContainer("RAR", error)
}
