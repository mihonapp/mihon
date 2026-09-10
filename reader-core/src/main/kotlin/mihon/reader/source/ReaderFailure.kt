package mihon.reader.source

import java.io.IOException

sealed class ReaderFailure(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    class UnsafePath(path: String, reason: String) : ReaderFailure("Unsafe path '$path': $reason")

    class ResourceChanged(path: String) : ReaderFailure("Resource changed while opening '$path'")

    class UnsupportedFormat(format: String) : ReaderFailure("Unsupported chapter format: $format")

    class CorruptContainer(format: String, cause: Throwable? = null) :
        ReaderFailure("Corrupt $format container", cause)

    class EncryptedContainer(format: String) : ReaderFailure("Encrypted $format containers are not supported")

    class LimitExceeded(
        val limitName: String,
        val limitBytes: Long,
        val actualBytes: Long,
    ) : ReaderFailure("$limitName limit exceeded: $actualBytes > $limitBytes")

    class TooManyEntries(limit: Int) : ReaderFailure("Chapter contains more than $limit entries")

    class DuplicateEntry(name: String) : ReaderFailure("Duplicate normalized entry '$name'")

    class PageNotFound(name: String) : ReaderFailure("Page '$name' does not exist in this chapter")

    class EmptyChapter : ReaderFailure("Chapter contains no readable pages")

    class XmlRejected(document: String, cause: Throwable? = null) :
        ReaderFailure("Unsafe or invalid XML document '$document'", cause)

    class SourceClosed : ReaderFailure("Chapter source is closed")

    class MemoryBudgetClosed : ReaderFailure("Reader memory budget is closed")

    class UnsupportedImage(reason: String) : ReaderFailure("Unsupported image: $reason")

    class RemoteImage(reason: String, cause: Throwable? = null) :
        ReaderFailure("Unable to load remote image: $reason", cause)

    class CorruptImage(cause: Throwable? = null) : ReaderFailure("Corrupt or unreadable image", cause)

    class RegionUnavailable(reason: String) : ReaderFailure("Region decode unavailable: $reason")
}
