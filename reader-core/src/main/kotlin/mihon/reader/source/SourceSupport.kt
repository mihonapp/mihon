package mihon.reader.source

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import mihon.reader.layout.NaturalPageComparator
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

data class SourceEntry(
    val logicalName: String,
    val rawName: String,
    val declaredSize: Long,
)

internal fun List<SourceEntry>.sortedNaturally(): List<SourceEntry> =
    sortedWith { left, right -> NaturalPageComparator.compare(left.logicalName, right.logicalName) }

internal fun SourceEntry.descriptor(asset: ReaderChapterAsset): PageDescriptor = PageDescriptor(
    id = PageId(asset.chapterId.toString(), logicalName),
    width = 1,
    height = 1,
)

abstract class ManagedChapterSource(
    final override val asset: ReaderChapterAsset,
    protected val expansionBudget: ChapterExpansionBudget = ChapterExpansionBudget(),
    private val pageLimitBytes: Long = ReaderLimits.MAX_PAGE_BYTES,
) : ChapterSource {
    init {
        require(pageLimitBytes >= 0) { "pageLimitBytes must not be negative" }
    }
    private val lock = Any()
    private val activeInputs = mutableSetOf<InputStream>()
    private var closed = false

    protected fun checkOpen() {
        synchronized(lock) {
            if (closed) throw ReaderFailure.SourceClosed()
        }
    }

    protected suspend fun checkOpenAndCancellation() {
        currentCoroutineContext().ensureActive()
        checkOpen()
    }

    protected fun requireEntry(pageId: PageId, entries: List<SourceEntry>): SourceEntry {
        checkOpen()
        if (pageId.chapterId != asset.chapterId.toString()) throw ReaderFailure.PageNotFound(pageId.entryName)
        return entries.singleOrNull { it.logicalName == pageId.entryName }
            ?: throw ReaderFailure.PageNotFound(pageId.entryName)
    }

    protected fun boundedInput(
        input: InputStream,
        declaredSize: Long,
        chargeChapterBudget: Boolean = true,
        onClose: () -> Unit = {},
    ): BoundedPageInput {
        checkOpen()
        lateinit var managed: InputStream
        managed = object : FilterInputStream(input) {
            private val inputClosed = AtomicBoolean(false)
            private var pageBytes = 0L

            override fun read(): Int {
                val value = try {
                    super.read()
                } catch (error: Throwable) {
                    close()
                    throw error
                }
                if (value >= 0) charge(1)
                return value
            }

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                val count = try {
                    super.read(bytes, offset, length)
                } catch (error: Throwable) {
                    close()
                    throw error
                }
                if (count > 0) charge(count.toLong())
                return count
            }

            override fun skip(byteCount: Long): Long {
                if (byteCount <= 0) return 0
                val buffer = ByteArray(minOf(8192L, byteCount).toInt())
                var remaining = byteCount
                while (remaining > 0) {
                    val count = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (count < 0) break
                    remaining -= count
                }
                return byteCount - remaining
            }

            private fun charge(byteCount: Long) {
                if (chargeChapterBudget) expansionBudget.charge(byteCount)
                if (byteCount > pageLimitBytes - pageBytes) {
                    val actual = if (Long.MAX_VALUE - pageBytes < byteCount) Long.MAX_VALUE else pageBytes + byteCount
                    pageBytes = pageLimitBytes
                    close()
                    throw ReaderFailure.LimitExceeded("expanded page bytes", pageLimitBytes, actual)
                }
                pageBytes += byteCount
            }

            override fun close() {
                if (!inputClosed.compareAndSet(false, true)) return
                try {
                    super.close()
                } finally {
                    synchronized(lock) { activeInputs.remove(managed) }
                    onClose()
                }
            }
        }
        synchronized(lock) {
            if (closed) {
                managed.close()
                throw ReaderFailure.SourceClosed()
            }
            activeInputs += managed
        }
        return BoundedPageInput(managed, declaredSize.coerceAtLeast(0))
    }

    override fun close() {
        val inputs = synchronized(lock) {
            if (closed) return
            closed = true
            activeInputs.toList().also { activeInputs.clear() }
        }
        var failure: Throwable? = null
        inputs.forEach {
            try {
                it.close()
            } catch (error: Throwable) {
                val primary = failure
                if (primary == null) failure = error else primary.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
}

internal class ChapterBudgetInputStream(
    input: InputStream,
    private val budget: ChapterExpansionBudget,
) : FilterInputStream(input) {
    override fun read(): Int = super.read().also { if (it >= 0) budget.charge(1) }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
        super.read(bytes, offset, length).also { if (it > 0) budget.charge(it.toLong()) }

    override fun skip(byteCount: Long): Long {
        if (byteCount <= 0) return 0
        val buffer = ByteArray(minOf(8192L, byteCount).toInt())
        var remaining = byteCount
        while (remaining > 0) {
            val count = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) break
            remaining -= count
        }
        return byteCount - remaining
    }
}

internal fun MutableList<SourceEntry>.addChecked(
    rawName: String,
    declaredSize: Long,
    duplicateKeys: MutableSet<String>,
): SourceEntry {
    val normalized = ImageEntryPolicy.normalize(rawName)
    val key = ImageEntryPolicy.duplicateKey(normalized)
    if (!duplicateKeys.add(key)) throw ReaderFailure.DuplicateEntry(normalized)
    if (size >= ReaderLimits.MAX_ENTRIES) throw ReaderFailure.TooManyEntries(ReaderLimits.MAX_ENTRIES)
    return SourceEntry(normalized, rawName, declaredSize).also(::add)
}
