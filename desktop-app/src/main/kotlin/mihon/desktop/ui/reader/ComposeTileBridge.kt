package mihon.desktop.ui.reader

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import mihon.reader.image.TileKey
import mihon.reader.memory.BoundedReaderMemoryBudget
import mihon.reader.memory.MemoryKind
import mihon.reader.memory.MemoryLease
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.awt.image.BufferedImage
import java.io.Closeable

data class ComposeBridgeMetrics(
    val limitBytes: Long,
    val retainedBytes: Long,
    val highWaterBytes: Long,
    val recordCount: Int,
    val conversionCount: Long,
    val waitingCount: Int,
)

class ComposeBridgeImage(
    val image: ImageBitmap,
    private val onDispose: () -> Unit = {},
) : Closeable {
    private val lock = Any()
    private var closed = false

    override fun close() {
        val dispose = synchronized(lock) {
            if (closed) return
            closed = true
            onDispose
        }
        dispose()
    }
}

fun interface ComposeImageConverter {
    suspend fun convert(image: BufferedImage): ComposeBridgeImage
}

/**
 * Owns Compose/Skia copies independently from reader-core's decoded cache. Visible callers hold
 * reference-counted [BridgeTile]s; the final close releases both the native image and its bridge
 * reservation. Static replacement releases first so pressure cannot deadlock, while animated
 * replacement keeps the current frame until its fully converted successor is ready.
 */
class ComposeTileBridge(
    private val limitBytes: Long = CAPACITY_BYTES,
    private val converter: ComposeImageConverter = DefaultComposeImageConverter,
    private val byteCount: (TileKey, BufferedImage) -> Long = { _, image -> image.rgbaByteCount() },
) : Closeable {
    init {
        require(limitBytes >= 0L) { "limitBytes must not be negative" }
    }

    private val lock = Any()
    private val budget = BoundedReaderMemoryBudget(limitBytes)
    private val records = mutableMapOf<TileKey, Record>()
    private val flights = mutableMapOf<TileKey, kotlinx.coroutines.CompletableDeferred<Record>>()
    private var highWaterBytes = 0L
    private var conversionCount = 0L
    private var waitingCount = 0
    private var closed = false

    val metrics: ComposeBridgeMetrics
        get() {
            val memory = budget.metrics
            return synchronized(lock) {
                ComposeBridgeMetrics(
                    limitBytes = limitBytes,
                    retainedBytes = memory.cacheBytes,
                    highWaterBytes = highWaterBytes,
                    recordCount = records.size,
                    conversionCount = conversionCount,
                    waitingCount = waitingCount,
                )
            }
        }

    suspend fun acquire(key: TileKey, source: BufferedImage): BridgeTile {
        val requiredBytes = byteCount(key, source)
        require(requiredBytes >= 0L) { "bridge byte count must not be negative" }
        while (true) {
            when (val lookup = synchronized(lock) { lookupLocked(key) }) {
                is Lookup.Hit -> return BridgeTile(lookup.record)
                is Lookup.Join -> {
                    val record = try {
                        lookup.flight.await()
                    } catch (cancelled: CancellationException) {
                        currentCoroutineContext().ensureActive()
                        continue
                    }
                    val retained = synchronized(lock) {
                        if (records[key] === record) {
                            record.references++
                            true
                        } else {
                            false
                        }
                    }
                    if (retained) return BridgeTile(record)
                }
                is Lookup.Lead -> return convertAndPublish(key, source, requiredBytes, lookup.flight)
            }
        }
    }

    suspend fun replaceStatic(
        previous: BridgeTile?,
        key: TileKey,
        source: BufferedImage,
    ): BridgeTile {
        previous?.close()
        return acquire(key, source)
    }

    suspend fun replaceAnimated(
        previous: BridgeTile?,
        key: TileKey,
        source: BufferedImage,
    ): BridgeTile {
        val replacement = acquire(key, source)
        previous?.close()
        return replacement
    }

    override fun close() {
        val (retained, pending) = synchronized(lock) {
            if (closed) return
            closed = true
            val allRecords = records.values.toList()
            records.clear()
            val allFlights = flights.values.toList()
            flights.clear()
            allRecords to allFlights
        }
        pending.forEach { it.completeExceptionally(IllegalStateException(CLOSED_MESSAGE)) }
        retained.forEach(::disposeRecord)
        budget.close()
    }

    private fun lookupLocked(key: TileKey): Lookup {
        check(!closed) { CLOSED_MESSAGE }
        records[key]?.let {
            it.references++
            return Lookup.Hit(it)
        }
        flights[key]?.let { return Lookup.Join(it) }
        val flight = kotlinx.coroutines.CompletableDeferred<Record>()
        flights[key] = flight
        return Lookup.Lead(flight)
    }

    private suspend fun convertAndPublish(
        key: TileKey,
        source: BufferedImage,
        requiredBytes: Long,
        flight: kotlinx.coroutines.CompletableDeferred<Record>,
    ): BridgeTile {
        var reservation: MemoryLease? = null
        var converted: ComposeBridgeImage? = null
        try {
            reservation = reserve(requiredBytes)
            currentCoroutineContext().ensureActive()
            converted = converter.convert(source)
            currentCoroutineContext().ensureActive()
            val record = Record(key, converted, reservation, references = 1)
            synchronized(lock) {
                check(!closed) { CLOSED_MESSAGE }
                flights.remove(key)
                records[key] = record
                conversionCount++
            }
            flight.complete(record)
            converted = null
            reservation = null
            return BridgeTile(record)
        } catch (error: Throwable) {
            synchronized(lock) { flights.remove(key) }
            flight.completeExceptionally(error)
            converted?.close()
            reservation?.close()
            throw error
        }
    }

    private suspend fun reserve(byteCount: Long): MemoryLease {
        budget.tryReserve(MemoryKind.CACHE_RESIDENT, byteCount)?.let { lease ->
            updateHighWater()
            return lease
        }
        synchronized(lock) { waitingCount++ }
        return try {
            budget.reserve(MemoryKind.CACHE_RESIDENT, byteCount).also { updateHighWater() }
        } finally {
            synchronized(lock) { waitingCount-- }
        }
    }

    private fun updateHighWater() {
        val retained = budget.metrics.cacheBytes
        synchronized(lock) {
            if (retained > highWaterBytes) highWaterBytes = retained
        }
    }

    private fun release(record: Record) {
        val dispose = synchronized(lock) {
            if (record.disposed) return
            record.references--
            check(record.references >= 0) { "bridge tile reference count underflow" }
            if (record.references == 0) {
                record.disposed = true
                records.remove(record.key, record)
                true
            } else {
                false
            }
        }
        if (dispose) disposeRecord(record)
    }

    private fun disposeRecord(record: Record) {
        try {
            record.converted.close()
        } finally {
            record.reservation.close()
        }
    }

    inner class BridgeTile internal constructor(private val record: Record) : Closeable {
        private val tileLock = Any()
        private var active = true

        val key: TileKey get() = record.key
        val image: ImageBitmap get() = record.converted.image
        val retainedBytes: Long get() = record.reservation.byteCount

        override fun close() {
            val shouldRelease = synchronized(tileLock) {
                if (!active) return
                active = false
                true
            }
            if (shouldRelease) release(record)
        }
    }

    internal class Record(
        val key: TileKey,
        val converted: ComposeBridgeImage,
        val reservation: MemoryLease,
        var references: Int,
        var disposed: Boolean = false,
    )

    private sealed interface Lookup {
        data class Hit(val record: Record) : Lookup
        data class Join(val flight: kotlinx.coroutines.CompletableDeferred<Record>) : Lookup
        data class Lead(val flight: kotlinx.coroutines.CompletableDeferred<Record>) : Lookup
    }

    companion object {
        const val CAPACITY_BYTES = 96L * 1024L * 1024L
        private const val CLOSED_MESSAGE = "Compose tile bridge is closed"
    }
}

private object DefaultComposeImageConverter : ComposeImageConverter {
    override suspend fun convert(image: BufferedImage): ComposeBridgeImage = withContext(Dispatchers.Default) {
        val width = image.width
        val height = image.height
        val rowBytes = Math.multiplyExact(width, 4)
        val pixels = ByteArray(Math.multiplyExact(rowBytes, height))
        val row = IntArray(width)
        repeat(height) { y ->
            image.getRGB(0, y, width, 1, row, 0, width)
            repeat(width) { x ->
                val argb = row[x]
                val offset = y * rowBytes + x * 4
                pixels[offset] = (argb and 0xff).toByte()
                pixels[offset + 1] = (argb ushr 8 and 0xff).toByte()
                pixels[offset + 2] = (argb ushr 16 and 0xff).toByte()
                pixels[offset + 3] = (argb ushr 24 and 0xff).toByte()
            }
        }
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        val skiaImage = Image.makeRaster(info, pixels, rowBytes)
        ComposeBridgeImage(skiaImage.toComposeImageBitmap()) { skiaImage.close() }
    }
}

private fun BufferedImage.rgbaByteCount(): Long =
    Math.multiplyExact(Math.multiplyExact(width.toLong(), height.toLong()), 4L)
