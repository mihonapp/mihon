package eu.kanade.tachiyomi.ui.reader.cast

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import ca.mpreg.imagedecoder.ImageDecoder
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/**
 * Gives the repository access to the reader's pages.
 */
interface CastPageSource {
    fun findPage(chapterId: Long, index: Int): ReaderPage?

    /** Makes sure the page image is downloaded/available. Returns false on failure or timeout. */
    suspend fun awaitReady(page: ReaderPage): Boolean
}

/**
 * A decoded page ready to be drawn on a cast display. Tall images are split in [tiles] stacked
 * vertically so no single bitmap exceeds the GPU texture limits. [sourceWidth]/[sourceHeight] are
 * the dimensions of the original file; the bitmaps may be sub-sampled.
 *
 * Bitmaps are never recycled explicitly: a renderer may still be drawing an image that was just
 * evicted from the cache, so their memory is reclaimed by the garbage collector instead.
 */
class CastImage(
    val width: Int,
    val height: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val tiles: List<Tile>,
) {
    class Tile(val top: Int, val bitmap: Bitmap)

    val byteCount: Int
        get() = tiles.sumOf { it.bitmap.byteCount }
}

/** An image encoded for the web receiver. */
class CastEncodedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int,
)

data class CastImageDimensions(val width: Int, val height: Int)

/**
 * Loads, decodes and caches page images for the cast targets.
 *
 * - [decoded] / [getDecodedOrLoad] / [decodedAny]: bitmaps (tiled) for the secondary display.
 * - [encoded]: bytes for the web receiver, optionally transcoded to a browser friendly JPEG.
 * - [dimensions] / [probeDimensions]: cheap size probing used to lay out continuous strips.
 *
 * Decodes run on a small dedicated pool and belong to the current session: [clear] cancels them
 * and forgets everything. Pages in the [setPinned] window are never evicted by the byte budget.
 */
class CastImageRepository(
    private val pageSource: CastPageSource,
) {

    private val sessionJob = SupervisorJob()
    private val scope = CoroutineScope(sessionJob + Dispatchers.IO.limitedParallelism(DECODE_PARALLELISM))

    private val lock = Any()

    private val dimensionCache = HashMap<String, CastImageDimensions>()

    // Access-ordered so iteration order is LRU.
    private val decodedCache = LinkedHashMap<String, CastImage>(16, 0.75f, true)
    private var decodedBytes = 0L
    private val decodedInFlight = HashMap<String, Deferred<CastImage?>>()

    /** Decode keys that failed recently, with the uptime until which they should not be retried. */
    private val failedUntil = HashMap<String, Long>()

    /** Page keys the renderer is about to draw; eviction skips them. */
    private var pinnedPages: Set<String> = emptySet()

    /** Bumped by [clear] so decodes started in a previous session don't fill the cache. */
    private var generation = 0

    private val encodedCache = LinkedHashMap<String, CastEncodedImage>(8, 0.75f, true)
    private val rawCache = LinkedHashMap<String, ByteArray>(4, 0.75f, true)

    private val maxDecodedBytes: Long = min(Runtime.getRuntime().maxMemory() / 4, MAX_DECODED_BYTES)

    /** No single decoded page may take more than this, so a pinned window always fits. */
    private val maxImageBytes: Long = maxDecodedBytes / (PIN_WINDOW_SIZE + 1)

    /** Invoked (on a background thread) whenever a decoded image becomes available. */
    @Volatile
    var onImageDecoded: ((CastPageInfo) -> Unit)? = null

    fun dimensions(page: CastPageInfo): CastImageDimensions? {
        if (page.hasDimensions) return CastImageDimensions(page.width, page.height)
        synchronized(lock) { return dimensionCache[page.key] }
    }

    /**
     * Returns the image dimensions, downloading the page if needed. Cheap for formats
     * BitmapFactory understands; falls back to a full decode for exotic formats.
     */
    suspend fun probeDimensions(page: CastPageInfo): CastImageDimensions? {
        dimensions(page)?.let { return it }
        val bytes = readBytes(page) ?: return null
        val probed = withContext(Dispatchers.IO) {
            probe(bytes) ?: runCatching {
                ImageDecoder.new(ByteArrayInputStream(bytes)).use { decoder ->
                    val result = decoder.decode()
                    CastImageDimensions(result.width, result.height)
                }
            }.getOrNull()
        } ?: return null
        rememberDimensions(page.key, probed)
        return probed
    }

    /** Returns the cached decoded image for [page] at the given width bucket, if any. */
    fun decoded(page: CastPageInfo, targetWidth: Int): CastImage? {
        val key = decodedKey(page, bucket(targetWidth))
        synchronized(lock) { return decodedCache[key] }
    }

    /** Returns any cached decode of [page] (the widest one), regardless of the width bucket. */
    fun decodedAny(page: CastPageInfo): CastImage? {
        val prefix = page.key + '@'
        synchronized(lock) {
            var best: CastImage? = null
            for ((key, image) in decodedCache) {
                if (key.startsWith(prefix) && (best == null || image.width > best.width)) best = image
            }
            return best
        }
    }

    /**
     * Returns the cached decoded image or starts decoding it in the background. [onImageDecoded]
     * is called once it is available. Failed decodes are not retried for a while.
     */
    fun getDecodedOrLoad(page: CastPageInfo, targetWidth: Int): CastImage? {
        val width = bucket(targetWidth)
        val key = decodedKey(page, width)
        synchronized(lock) {
            decodedCache[key]?.let { return it }
            if (decodedInFlight.containsKey(key)) return null
            if ((failedUntil[key] ?: 0L) > SystemClock.uptimeMillis()) return null
            launchDecode(page, width, key)
        }
        return null
    }

    /** Suspends until [page] is decoded at the given width bucket. */
    suspend fun decode(page: CastPageInfo, targetWidth: Int): CastImage? {
        val width = bucket(targetWidth)
        val key = decodedKey(page, width)
        val deferred = synchronized(lock) {
            decodedCache[key]?.let { return it }
            decodedInFlight[key] ?: launchDecode(page, width, key)
        }
        return deferred.await()
    }

    /**
     * Declares the pages the renderer needs right now (by [CastPageInfo.key]). They are exempt
     * from eviction and pending decodes of other pages are cancelled.
     */
    fun setPinned(pageKeys: Set<String>) {
        val cancelled = ArrayList<Deferred<CastImage?>>()
        synchronized(lock) {
            if (pageKeys == pinnedPages) return
            pinnedPages = pageKeys
            val iterator = decodedInFlight.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (pageOf(entry.key) !in pageKeys) {
                    cancelled += entry.value
                    iterator.remove()
                }
            }
        }
        cancelled.forEach { it.cancel() }
    }

    /**
     * Returns the page bytes for the web receiver. With [CastImageQuality.OPTIMIZED] large or
     * exotic images are transcoded to a JPEG no wider than [maxWidth].
     */
    suspend fun encoded(
        page: CastPageInfo,
        quality: CastImageQuality,
        maxWidth: Int = WEB_MAX_WIDTH,
    ): CastEncodedImage? {
        val key = "${page.key}@${quality.name}@$maxWidth"
        synchronized(lock) { encodedCache[key]?.let { return it } }
        val bytes = readBytes(page) ?: return null
        val (result, sourceDimensions) = withContext(Dispatchers.IO) {
            encodeInternal(bytes, quality, maxWidth) to probe(bytes)
        }
        if (result == null) return null
        // The encoded image may be downscaled; only the source size is meaningful for layout.
        if (sourceDimensions != null) rememberDimensions(page.key, sourceDimensions)
        synchronized(lock) {
            encodedCache[key] = result
            while (encodedCache.size > MAX_ENCODED_ENTRIES) {
                encodedCache.remove(encodedCache.keys.first())
            }
        }
        return result
    }

    /**
     * Ends the current session: cancels pending decodes and drops everything cached. With [join]
     * the call waits (briefly) for running decodes so the reader can safely recycle its pages.
     */
    fun clear(join: Boolean = false) {
        synchronized(lock) {
            generation++
            decodedCache.clear()
            decodedBytes = 0
            decodedInFlight.clear()
            failedUntil.clear()
            pinnedPages = emptySet()
            encodedCache.clear()
            rawCache.clear()
            if (dimensionCache.size > MAX_DIMENSION_ENTRIES) dimensionCache.clear()
        }
        sessionJob.cancelChildren()
        if (join) {
            runBlocking {
                withTimeoutOrNull(CLEAR_JOIN_TIMEOUT_MS) {
                    sessionJob.children.forEach { it.join() }
                }
            }
        }
    }

    /** Frees decoded images that are not within [keep] (page keys). */
    fun trimDecoded(keep: Set<String>) {
        synchronized(lock) {
            val iterator = decodedCache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (pageOf(entry.key) !in keep) {
                    decodedBytes -= entry.value.byteCount
                    iterator.remove()
                }
            }
        }
    }

    // region internals

    /** Must be called with [lock] held. */
    private fun launchDecode(page: CastPageInfo, width: Int, key: String): Deferred<CastImage?> {
        val startedGeneration = generation
        val deferred = scope.async {
            var result: CastImage? = null
            try {
                result = decodeInternal(page, width, key, startedGeneration)
                result
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e) { "Failed to decode cast page ${page.key}" }
                null
            } finally {
                val self = coroutineContext[Job]
                synchronized(lock) {
                    if (decodedInFlight[key] === self) decodedInFlight.remove(key)
                    if (result == null && startedGeneration == generation && self?.isCancelled != true) {
                        failedUntil[key] = SystemClock.uptimeMillis() + FAILURE_COOLDOWN_MS
                    }
                }
            }
        }
        decodedInFlight[key] = deferred
        return deferred
    }

    private fun rememberDimensions(pageKey: String, dimensions: CastImageDimensions) {
        synchronized(lock) { dimensionCache.putIfAbsent(pageKey, dimensions) }
    }

    private suspend fun readBytes(page: CastPageInfo): ByteArray? {
        synchronized(lock) { rawCache[page.key]?.let { return it } }
        val readerPage = pageSource.findPage(page.chapterId, page.index) ?: return null
        if (!pageSource.awaitReady(readerPage)) return null
        val streamFn = readerPage.stream ?: return null
        val bytes = withContext(Dispatchers.IO) {
            try {
                streamFn().use { it.readBytes() }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e) { "Failed to read cast page ${page.key}" }
                null
            }
        } ?: return null
        synchronized(lock) {
            rawCache[page.key] = bytes
            while (rawCache.size > MAX_RAW_ENTRIES) {
                rawCache.remove(rawCache.keys.first())
            }
        }
        return bytes
    }

    private suspend fun decodeInternal(
        page: CastPageInfo,
        width: Int,
        key: String,
        startedGeneration: Int,
    ): CastImage? {
        val bytes = readBytes(page) ?: return null
        val image = withContext(Dispatchers.IO) { decodeTiles(bytes, width) } ?: return null
        coroutineContext.ensureActive()
        synchronized(lock) {
            // The session ended while decoding: don't resurrect the cache.
            if (startedGeneration != generation) return null
            rememberDimensionsLocked(page.key, image)
            decodedCache[key] = image
            decodedBytes += image.byteCount
            val iterator = decodedCache.entries.iterator()
            while (decodedBytes > maxDecodedBytes && iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key == key || pageOf(entry.key) in pinnedPages) continue
                decodedBytes -= entry.value.byteCount
                iterator.remove()
            }
        }
        onImageDecoded?.invoke(page)
        return image
    }

    /** Must be called with [lock] held. */
    private fun rememberDimensionsLocked(pageKey: String, image: CastImage) {
        if (image.sourceWidth > 0 && image.sourceHeight > 0) {
            dimensionCache.putIfAbsent(pageKey, CastImageDimensions(image.sourceWidth, image.sourceHeight))
        }
    }

    private fun probe(bytes: ByteArray): CastImageDimensions? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return CastImageDimensions(options.outWidth, options.outHeight)
    }

    private fun probeMimeType(bytes: ByteArray): String? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outMimeType
    }

    /**
     * Power-of-two sample size so the decoded width stays at least [targetWidth] when possible
     * while the decoded image respects the per-image byte budget.
     */
    private fun sampleSizeFor(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, bytesPerPixel: Int): Int {
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= 1) {
            sample *= 2
        }
        while (
            (sourceWidth / sample).toLong() * (sourceHeight / sample) * bytesPerPixel > maxImageBytes &&
            sourceWidth / (sample * 2) >= 1 &&
            sourceHeight / (sample * 2) >= 1
        ) {
            sample *= 2
        }
        return sample
    }

    /**
     * Decodes [bytes] sub-sampled so that the width is at least [targetWidth] when possible,
     * split into tiles of at most [MAX_TILE_HEIGHT] rows.
     */
    private fun decodeTiles(bytes: ByteArray, targetWidth: Int): CastImage? {
        val probed = probe(bytes) ?: return decodeWithImageDecoder(bytes, targetWidth)
        val srcWidth = probed.width
        val srcHeight = probed.height
        val sample = sampleSizeFor(srcWidth, srcHeight, targetWidth, ARGB_BYTES_PER_PIXEL)
        val outHeight = srcHeight / sample
        if (outHeight <= MAX_TILE_HEIGHT) {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: return decodeWithImageDecoder(bytes, targetWidth)
            return CastImage(bitmap.width, bitmap.height, srcWidth, srcHeight, listOf(CastImage.Tile(0, bitmap)))
        }

        val decoder = newRegionDecoder(bytes) ?: return decodeWithImageDecoder(bytes, targetWidth)
        val tiles = ArrayList<CastImage.Tile>()
        try {
            val tileSourceHeight = MAX_TILE_HEIGHT * sample
            var top = 0
            var outTop = 0
            while (top < srcHeight) {
                val bottom = min(srcHeight, top + tileSourceHeight)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = decoder.decodeRegion(Rect(0, top, srcWidth, bottom), options) ?: break
                tiles += CastImage.Tile(outTop, bitmap)
                outTop += bitmap.height
                top = bottom
            }
            if (tiles.isEmpty()) return decodeWithImageDecoder(bytes, targetWidth)
            return CastImage(tiles.first().bitmap.width, outTop, srcWidth, srcHeight, tiles)
        } finally {
            decoder.recycle()
        }
    }

    private fun newRegionDecoder(bytes: ByteArray): BitmapRegionDecoder? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(bytes, 0, bytes.size)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            null
        }
    }

    /** Fallback for formats BitmapFactory can't handle (JXL, AVIF, JP2, ...). */
    private fun decodeWithImageDecoder(bytes: ByteArray, targetWidth: Int): CastImage? {
        val full = decodeFullBitmap(bytes) ?: return null
        val sourceWidth = full.width
        val sourceHeight = full.height
        val sample = sampleSizeFor(sourceWidth, sourceHeight, targetWidth, ARGB_BYTES_PER_PIXEL)
        val width = min(targetWidth, sourceWidth / sample).coerceAtLeast(1)
        val scaled = if (width < sourceWidth) {
            val height = max(1, sourceHeight.toLong() * width / sourceWidth).toInt()
            Bitmap.createScaledBitmap(full, width, height, true)
        } else {
            full
        }
        return tileBitmap(scaled, sourceWidth, sourceHeight)
    }

    private fun decodeFullBitmap(bytes: ByteArray): Bitmap? {
        return try {
            ImageDecoder.new(ByteArrayInputStream(bytes)).use { decoder ->
                val result = decoder.decode()
                val config = if (result.isHdr) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
                Bitmap.createBitmap(result.width, result.height, config).also { bitmap ->
                    result.image.rewind()
                    bitmap.copyPixelsFromBuffer(result.image)
                }
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e) { "ImageDecoder failed" }
            null
        }
    }

    private fun tileBitmap(bitmap: Bitmap, sourceWidth: Int, sourceHeight: Int): CastImage {
        if (bitmap.height <= MAX_TILE_HEIGHT) {
            return CastImage(bitmap.width, bitmap.height, sourceWidth, sourceHeight, listOf(CastImage.Tile(0, bitmap)))
        }
        val tiles = ArrayList<CastImage.Tile>()
        var top = 0
        while (top < bitmap.height) {
            val height = min(MAX_TILE_HEIGHT, bitmap.height - top)
            tiles += CastImage.Tile(top, Bitmap.createBitmap(bitmap, 0, top, bitmap.width, height))
            top += height
        }
        return CastImage(tiles.first().bitmap.width, top, sourceWidth, sourceHeight, tiles)
    }

    private fun encodeInternal(bytes: ByteArray, quality: CastImageQuality, maxWidth: Int): CastEncodedImage? {
        val probed = probe(bytes)
        val mime = probeMimeType(bytes)
        val browserFriendly = mime != null && mime in BROWSER_MIME_TYPES
        if (quality == CastImageQuality.ORIGINAL) {
            return CastEncodedImage(bytes, mime ?: "application/octet-stream", probed?.width ?: 0, probed?.height ?: 0)
        }
        if (browserFriendly && probed != null && probed.width <= maxWidth && bytes.size <= WEB_MAX_RAW_BYTES) {
            return CastEncodedImage(bytes, mime, probed.width, probed.height)
        }

        val bitmap: Bitmap = if (probed != null) {
            // Sample for the width first; tall strips keep their width unless the pixel budget
            // (RGB_565, half the memory of ARGB) is exceeded.
            var sample = 1
            while (probed.width / (sample * 2) >= maxWidth) sample *= 2
            while ((probed.width.toLong() / sample) * (probed.height.toLong() / sample) > WEB_MAX_PIXELS) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: decodeFullBitmap(bytes)
                ?: return null
        } else {
            decodeFullBitmap(bytes) ?: return null
        }
        val scaled = if (bitmap.width > maxWidth) {
            val height = max(1, bitmap.height.toLong() * maxWidth / bitmap.width).toInt()
            Bitmap.createScaledBitmap(bitmap, maxWidth, height, true)
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream(scaled.width * scaled.height / 4)
        scaled.compress(Bitmap.CompressFormat.JPEG, WEB_JPEG_QUALITY, out)
        return CastEncodedImage(out.toByteArray(), "image/jpeg", scaled.width, scaled.height)
    }

    private fun bucket(targetWidth: Int): Int {
        val clamped = targetWidth.coerceIn(MIN_DECODE_WIDTH, MAX_DECODE_WIDTH)
        return ((clamped + WIDTH_BUCKET - 1) / WIDTH_BUCKET) * WIDTH_BUCKET
    }

    private fun decodedKey(page: CastPageInfo, width: Int) = "${page.key}@$width"

    private fun pageOf(decodedKey: String) = decodedKey.substringBefore('@')

    // endregion

    companion object {
        const val MAX_TILE_HEIGHT = 2048
        const val MIN_DECODE_WIDTH = 256
        const val MAX_DECODE_WIDTH = 4096
        const val WIDTH_BUCKET = 256
        const val WEB_MAX_WIDTH = 2160

        /** Number of pages a renderer pins at once; the byte budget is sized for it. */
        const val PIN_WINDOW_SIZE = 3

        private const val DECODE_PARALLELISM = 2
        private const val ARGB_BYTES_PER_PIXEL = 4
        private const val WEB_MAX_RAW_BYTES = 6L * 1024 * 1024
        private const val WEB_MAX_PIXELS = 24_000_000L
        private const val WEB_JPEG_QUALITY = 85
        private const val MAX_DECODED_BYTES = 320L * 1024 * 1024
        private const val MAX_ENCODED_ENTRIES = 8
        private const val MAX_RAW_ENTRIES = 4
        private const val MAX_DIMENSION_ENTRIES = 2000
        private const val FAILURE_COOLDOWN_MS = 20_000L
        private const val CLEAR_JOIN_TIMEOUT_MS = 400L
        private val BROWSER_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    }
}
