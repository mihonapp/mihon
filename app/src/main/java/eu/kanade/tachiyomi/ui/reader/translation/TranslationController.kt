package eu.kanade.tachiyomi.ui.reader.translation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.davemorrissey.labs.subscaleview.CropBorders
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import tachiyomi.translation.model.EntryState
import tachiyomi.translation.model.NormalizedRect
import tachiyomi.translation.model.PageKey
import tachiyomi.translation.model.SessionSnapshot
import tachiyomi.translation.model.SpreadKey
import tachiyomi.translation.model.TranslationEntry
import tachiyomi.translation.model.TranslationSession
import tachiyomi.translation.ocr.BitmapCropper
import tachiyomi.translation.ocr.DbDetector
import tachiyomi.translation.ocr.ColumnSplitter
import tachiyomi.translation.ocr.MangaOcrEngine
import tachiyomi.translation.ocr.MangaOcrStore
import tachiyomi.translation.ocr.OcrEngine
import tachiyomi.translation.ocr.OcrModelStore
import tachiyomi.translation.ocr.OcrPreset
import tachiyomi.translation.ocr.OcrPresetCatalog
import tachiyomi.translation.ocr.OnnxOcrEngine
import tachiyomi.translation.provider.ErrorKind
import tachiyomi.translation.provider.ProviderRegistry
import tachiyomi.translation.provider.TranslationOutcome
import tachiyomi.translation.provider.TranslationService
import tachiyomi.translation.provider.buildContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class TranslationController(
    private val context: Context,
    private val prefs: TranslationPreferences,
    private val scope: CoroutineScope,
    private val onRequireSetup: () -> Unit,
    private val cropBordersEnabled: () -> Boolean = { false },
) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    val session = TranslationSession()
    val cacheRoot = File(context.cacheDir, "translation_pages")
    private var pageCache = TranslationPageCache(File(cacheRoot, "boot"))
    private val queue = Channel<Long>(Channel.UNLIMITED)
    private val callFactory = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val modelStore = OcrModelStore(context, callFactory)
    private val mangaOcrStore by lazy { MangaOcrStore(context, callFactory) }
    private var spread: SpreadKey? = null
    var page: ReaderPage? = null
        private set
    private var chapterPages: List<ReaderPage>? = null
    private var pageSize: Pair<Int, Int>? = null
    private var engine: OcrEngine? = null
    private var engineKey: String? = null
    private var detector: DbDetector? = null
    private var detectorKey: String? = null
    private var lastChapterId = -1L
    private var scanGeneration = 0

    /** The one scan that is allowed to run; a newer page flip cancels the older one. */
    private var scanJob: Job? = null

    /** Region ids must be unique across pages: the merge/tap flows mark them by id globally. */
    private var nextRegionId = 0L

    /**
     * Bumped on every view geometry publication. A scan only starts once this stops moving:
     * opening the panel rescales the page and the turn animation relayouts holders, and a scan
     * that raced those produced boxes parked where the art USED to be.
     */
    private var geometryEpoch = 0
    private var debugCropStamp = 0
    private val scansInFlight = AtomicInteger(0)
    private var preparedKey: String? = null
    private var preparedBitmap: Bitmap? = null
    private val cropInsets = HashMap<Int, CropInsets>()
    private val regionsByPage = LinkedHashMap<Int, List<ScannedRegion>>()
    private var continuousMode = false
    private val PRIMARY_BIAS = 1.25f

    val panelVisible: Boolean get() = _state.value.panelVisible

    init {
        TranslationGeometry.onFramePublished = { geometryEpoch++ }
        _state.value = _state.value.copy(
            enabled = prefs.enabled.get(),
            panelFraction = prefs.panelHeightFraction.get(),
            panelWidthFraction = prefs.panelWidthFraction.get(),
            sourceLanguage = prefs.sourceLanguage.get(),
        )
        // The default detection model moved up a tier; users still on the old default follow it.
        if (prefs.detPresetId.get() == "ppocr-v6-small-det") {
            prefs.detPresetId.set(OcrPresetCatalog.defaultDet.id)
        }
        runCatching { cacheRoot.deleteRecursively() }
        pageCache = TranslationPageCache(File(cacheRoot, "boot"))
        TranslationGeometry.clear()
        scope.launch {
            for (entryId in queue) {
                runCatching { processEntry(entryId) }
            }
        }
    }

    /** Recognition is local, so it works with or without network consent. */
    fun isRecognizerReady(): Boolean = isDetModelReady() && isAnyRecModelReady()

    private fun isDetModelReady(): Boolean = modelStore.isModelInstalled(prefs.detPresetId.get())

    /** 检测模型加载且任一识别模型加载即可启用翻译；当前语言专用模型缺失只在识别时提示。 */
    private fun isAnyRecModelReady(): Boolean =
        mangaOcrStore.isInstalled() || OcrPresetCatalog.presets.any { modelStore.isInstalled(it.id) }

    /**
     * Recogniser that will actually run for the selected source language: the dedicated language
     * model when downloaded, otherwise the first installed multilingual fallback.
     */
    private fun effectivePreset(): OcrPreset? {
        val language = _state.value.sourceLanguage.code
        val requested = prefs.ocrPresetId.get()
        val candidates = OcrPresetCatalog.candidatesFor(language, requested)
        return candidates.firstOrNull { modelStore.isInstalled(it.id) } ?: candidates.firstOrNull()
    }

    /**
     * The recogniser engine follows the source language, not a manual setting: Japanese uses
     * Manga OCR (trained on manga lettering), Korean and English run the RapidOCR models.
     */
    private fun engineFor(language: TranslationSourceLanguage): String =
        if (language == TranslationSourceLanguage.JAPANESE) {
            TranslationPreferences.ENGINE_MANGA_OCR
        } else {
            TranslationPreferences.ENGINE_PADDLE
        }

    private fun usesMangaOcr(): Boolean =
        engineFor(_state.value.sourceLanguage) == TranslationPreferences.ENGINE_MANGA_OCR

    /** True when the dedicated model for the current language is the one being used. */
    private fun usingDedicatedLanguageModel(): Boolean {
        val language = _state.value.sourceLanguage.code
        val dedicated = OcrPresetCatalog.languagePreset(language) ?: return true
        return effectivePreset()?.id == dedicated.id
    }

    /**
     * Translation needs a usable provider *and* the explicit text-upload consent. Without either,
     * the panel keeps working in "source text only" mode instead of refusing to start.
     */
    fun canTranslate(): Boolean = prefs.networkConsent.get() && prefs.isProviderConfigured()

    fun isConfigured(): Boolean = isRecognizerReady() && canTranslate()

    fun open() {
        if (!prefs.enabled.get()) return
        val mangaOcr = usesMangaOcr()
        val preset = if (mangaOcr) null else effectivePreset()
        val detMissing = !isDetModelReady()
        val languageModelMissing = if (mangaOcr) {
            !mangaOcrStore.isInstalled()
        } else {
            preset == null || !modelStore.isInstalled(preset.id)
        }
        if (detMissing || (languageModelMissing && !isAnyRecModelReady())) {
            showNotice(
                when {
                    detMissing -> "请先下载整页检测模型"
                    mangaOcr -> "请先下载 Manga OCR 模型"
                    else -> preset?.let { "请先下载${it.displayName}" } ?: "请先下载识别模型"
                },
            )
            onRequireSetup()
            return
        }

        val translate = canTranslate()
        _state.update {
            it.copy(
                panelVisible = true,
                translationEnabled = translate,
            )
        }
        when {
            !translate -> showNotice(
                if (!prefs.networkConsent.get()) {
                    "未开启文字发送，仅显示识别原文"
                } else {
                    "翻译引擎未配置，仅显示识别原文"
                },
            )
            // 当前语言的专用模型缺失不再阻止打开面板，但明确提示，免得被当成识别效果差。
            languageModelMissing -> showNotice(
                if (mangaOcr) {
                    "未下载 Manga OCR 模型，可在设置中下载"
                } else {
                    "未下载${_state.value.sourceLanguage.label}识别模型，暂用可用模型"
                },
            )
            !mangaOcr && !usingDedicatedLanguageModel() -> showNotice(
                "未下载${_state.value.sourceLanguage.label}专用模型，暂用通用模型",
            )
        }
        refreshEntries()
        scanPage()
    }

    fun close() {
        TranslationGeometry.onFramePublished = null
        scanJob?.cancel()
        scanJob = null
        scanGeneration++
        session.clear()
        TranslationGeometry.clear()
        pageCache.clear()
        (engine as? AutoCloseable)?.close()
        engine = null
        engineKey = null
        runCatching { detector?.close() }
        detector = null
        detectorKey = null
        releasePrepared()
        cropInsets.clear()
        regionsByPage.clear()
        _state.update {
            it.copy(
                panelVisible = false,
                entries = emptyList(),
                regions = emptyList(),
                scanning = false,
                busy = false,
            )
        }
    }

    fun toggleExpanded(id: Long) {
        session.update(id) {
            when (it.state) {
                EntryState.EXPANDED -> it.copy(state = EntryState.READY)
                EntryState.READY -> it.copy(state = EntryState.EXPANDED)
                else -> it
            }
        }
        refreshEntries()
    }

    fun delete(id: Long) {
        session.remove(id)
        refreshEntries()
    }

    fun copy(text: String) {
        val clipboard = context.getSystemService("clipboard") as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("translation", text))
        _state.update { it.copy(notice = "已复制") }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun showNotice(message: String) {
        _state.update { it.copy(notice = message) }
    }

    fun pageImageSize(): Pair<Int, Int>? = pageSize

    fun currentPageIndex(): Int = page?.index ?: -1

    fun scanPage() {
        val anchor = page ?: return
        if (!modelStore.isModelInstalled(prefs.detPresetId.get())) {
            showNotice("检测模型未下载，可手动框选")
            return
        }
        if (usesMangaOcr() || ensureEngine() != null) {
            // A stale scan keeps inferring whole pages (box by box, both orientations) before its
            // generation check fires; without cancelling it, rapid flips stacked up jobs that all
            // serialized on the engine locks and the panel read "scanning" for minutes.
            scanJob?.cancel()
            scanGeneration++
            val generation = scanGeneration
            _state.update { it.copy(scanning = true) }
            scansInFlight.incrementAndGet()
            scanJob = scope.launch {
                // The panel opening rescales the page and a turn animates holders: wait until
                // the view's geometry stops publishing before scanning, or the boxes land
                // where the art used to be.
                var epoch = geometryEpoch
                repeat(6) {
                    delay(GEOMETRY_SETTLE_MS)
                    if (geometryEpoch == epoch) return@repeat
                    epoch = geometryEpoch
                }
                runCatching { scanWindow(anchor.index, generation) }
                    .onFailure {
                        if (generation == scanGeneration) {
                            showNotice("整页识别失败：${it.message ?: "未知错误"}")
                        }
                    }
            }.also { job ->
                job.invokeOnCompletion {
                    scansInFlight.decrementAndGet()
                    // Only the current generation may clear the flag; a cancelled scan's
                    // completion must not switch off the scan the newer flip just started.
                    if (generation == scanGeneration) {
                        _state.update { it.copy(scanning = false) }
                    }
                }
            }
            return
        }
        showNotice("识别模型未下载，可手动框选")
    }

    /** The longest run of consecutive window pages that have no scan results yet. */
    private fun longestUnscannedRun(window: List<Int>): List<Int> {
        var best = emptyList<Int>()
        var current = ArrayList<Int>()
        for (index in window.sorted()) {
            if (regionsByPage.containsKey(index)) {
                if (current.size > best.size) best = current
                current = ArrayList()
            } else {
                current.add(index)
            }
        }
        if (current.size > best.size) best = current
        return best
    }

    /**
     * Stitches [pages] vertically into one tall bitmap and runs the whole detect/recognise
     * pipeline on it. A bubble crossing a page boundary is detected ONCE and read from the full
     * bubble; the resulting region is attached to the page holding its centre, normalised to
     * that page's share of the box. Windows with partially cached pages fall back to per-page
     * scanning.
     */
    private suspend fun scanStitched(
        pages: List<Int>,
        detector: DbDetector,
        recognizer: OcrEngine?,
        generation: Int,
    ): Boolean {
        val chapter = chapterPages ?: return false
        // 1) Decode every page of the strip to a common width.
        val slices = ArrayList<Triple<Int, Bitmap, Int>>() // pageIndex, bitmap, topOffset
        var width = Int.MAX_VALUE
        val decoded = ArrayList<Pair<Int, Bitmap>>()
        try {
            for (index in pages) {
                if (generation != scanGeneration) return false
                val readerPage = chapter.getOrNull(index) ?: return false
                // Same readiness wait as the per-page path: a just-loaded strip page's stream
                // may lag a beat behind the reader.
                val ready = awaitPage(readerPage, generation) ?: return false
                val file = ready.first
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapCropper.decodeWhole(file.absolutePath, 1800)
                } ?: run {
                    Log.w("Translation", "stitched scan: page $index decode failed")
                    return false
                }
                decoded += index to bitmap
                width = minOf(width, bitmap.width)
            }
            if (decoded.size < 2) {
                Log.w("Translation", "stitched scan: only ${decoded.size} pages decoded, falling back")
                return false
            }
            var top = 0
            for ((index, bitmap) in decoded) {
                val slice = if (bitmap.width != width) {
                    val height = (bitmap.height * width / bitmap.width).coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
                    if (scaled !== bitmap) bitmap.recycle()
                    scaled
                } else {
                    bitmap
                }
                slices += Triple(index, slice, top)
                top += slice.height
            }
            val total = top
            cropInsets[pages.first()] = CropInsets.NONE
            for (index in pages) cropInsets[index] = CropInsets.NONE

            // 2) Stitch.
            val stitched = Bitmap.createBitmap(width, total, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(stitched)
            for ((_, slice, offset) in slices) {
                canvas.drawBitmap(slice, 0f, offset.toFloat(), null)
            }
            slices.forEach { it.second.recycle() }

            Log.i(
                "Translation",
                "stitched scan pages=$pages size=${width}x${total}",
            )

            // 3) Tile detection over the strip; each box is read whole from the stitch.
            val perPage = HashMap<Int, MutableList<ScannedRegion>>()
            val tileHeight = 1536
            var tileTop = 0
            while (tileTop < total) {
                if (generation != scanGeneration) {
                    stitched.recycle()
                    return true
                }
                val tileBottom = minOf(tileTop + tileHeight, total)
                val tile = Bitmap.createBitmap(stitched, 0, tileTop, width, tileBottom - tileTop)
                val boxes = withContext(Dispatchers.Default) { detector.detect(tile) }
                tile.recycle()
                for (box in boxes) {
                    if (generation != scanGeneration) {
                        stitched.recycle()
                        return true
                    }
                    val left = box.rect.left.toInt().coerceIn(0, (width - 2).coerceAtLeast(0))
                    val top = (tileTop + box.rect.top.toInt()).coerceIn(0, (total - 2).coerceAtLeast(0))
                    val right = box.rect.right.toInt().coerceIn(left + 1, width)
                    val bottom = (tileTop + box.rect.bottom.toInt()).coerceIn(top + 1, total)

                    val crop = runCatching {
                        Bitmap.createBitmap(stitched, left, top, right - left, bottom - top)
                    }.getOrNull() ?: continue
                    val text = recognizer?.let { recognizeBox(crop, it) }.orEmpty().trim()
                    crop.recycle()
                    if (text.isEmpty()) continue

                    // The page holding the box's vertical centre owns the region.
                    val centerY = (top + bottom) / 2f
                    var owner = slices.first()
                    for (slice in slices) {
                        if (centerY >= slice.third) owner = slice
                    }
                    val (_, ownerBitmap, ownerTop) = owner
                    val pageHeight = ownerBitmap.height
                    val interTop = maxOf(top, ownerTop)
                    val interBottom = minOf(bottom, ownerTop + pageHeight)
                    if (interBottom <= interTop) continue
                    val region = ScannedRegion(
                        id = nextRegionId++,
                        pageIndex = owner.first,
                        rect = NormalizedRect(
                            left.toFloat() / width,
                            (interTop - ownerTop).toFloat() / pageHeight,
                            right.toFloat() / width,
                            (interBottom - ownerTop).toFloat() / pageHeight,
                        ).clampToUnit(),
                        text = text,
                        confidence = box.score,
                    )
                    val pageRegions = perPage.getOrPut(owner.first) { ArrayList() }
                    pageRegions.add(region)
                    updateRegions(owner.first, pageRegions.toList())
                }
                tileTop = tileBottom
            }
            stitched.recycle()
            Log.i("Translation", "stitched scan done pages=$pages perPage=${perPage.mapValues { it.value.size }}")
            // The merged-halves pass cannot find anything here (no halves exist), but it also
            // publishes; harmless and keeps the flow uniform.
            for (index in pages) mergeStraddlePartners(index)
            return true
        } finally {
            slices.forEach { if (!it.second.isRecycled) it.second.recycle() }
            decoded.forEach { if (!it.second.isRecycled) it.second.recycle() }
        }
    }

    private suspend fun scanWindow(anchorIndex: Int, generation: Int) {
        val pages = chapterPages ?: return
        val detector = ensureDetector() ?: throw IllegalStateException("检测模型不可用")
        val recognizer = ensureEngine()

        // Webtoon: the ±1 window is processed as one unit — the unscanned pages are stitched
        // into a single tall strip, so a bubble straddling a boundary stays WHOLE for both
        // detection and recognition. Per-page scanning cut every straddling bubble into halves
        // whose re-alignment no edge heuristic could recover.
        if (continuousMode) {
            val stripe = longestUnscannedRun(orderedWindow(anchorIndex))
            if (stripe.size >= 2) {
                Log.i("Translation", "stitched window pages=$stripe")
                if (scanStitched(stripe, detector, recognizer, generation)) {
                    return
                }
                Log.w("Translation", "stitched scan fell back to per-page")
            }
        }

        for (index in orderedWindow(anchorIndex)) {
            if (generation != scanGeneration) return
            if (regionsByPage.containsKey(index)) continue
            val readerPage = pages.getOrNull(index) ?: continue
            val ready = awaitPage(readerPage, generation) ?: continue
            val file = ready.first
            val bounds = ready.second
            val cropBorders = cropBordersEnabled()
            if (bounds.second > bounds.first * TALL_PAGE_RATIO) {
                cropInsets[index] = CropInsets.NONE
                val regions = detectOnTiles(file, bounds, detector, recognizer, generation, index) { updateRegions(index, it) }
                Log.i("Translation", "scan page $index tiles done regions=${regions.size}")
                mergeStraddlePartners(index)
            } else {
                val bitmap = withContext(Dispatchers.IO) {
                    prepareDisplayBitmap(readerPage, cropBorders)
                }
                if (bitmap == null) {
                    Log.w("Translation", "scan page $index: display bitmap null")
                } else {
                    val regions = detectOnBitmap(
                        bitmap,
                        file,
                        bounds,
                        detector,
                        recognizer,
                        generation,
                        index,
                    ) { updateRegions(index, it) }
                    Log.i("Translation", "scan page $index bitmap=${bitmap.width}x${bitmap.height} regions=${regions.size}")
                    mergeStraddlePartners(index)
                }
            }
        }
    }

    private suspend fun awaitPage(readerPage: ReaderPage, generation: Int): Pair<File, Pair<Int, Int>>? {
        var ready: Pair<File, Pair<Int, Int>>? = null
        repeat(PAGE_READY_ATTEMPTS) {
            if (generation != scanGeneration) return null
            ready = pageCache.fileFor(readerPage)?.let { file ->
                pageCache.sizeOf(readerPage)?.let { size -> file to size }
            }
            if (ready == null) delay(PAGE_READY_DELAY_MS)
        }
        if (ready == null) {
            ready = pageCache.fileFor(readerPage)?.let { file ->
                pageCache.sizeOf(readerPage)?.let { size -> file to size }
            }
        }
        return ready
    }

    private suspend fun orderedWindow(anchorIndex: Int): List<Int> {
        val pages = chapterPages ?: return listOf(anchorIndex)
        // Always include the neighbours: the webtoon reader shows parts of three pages at once,
        // and even the pager benefits because flipping to a cached page shows boxes instantly.
        // The reading-mode flag is not trusted here — a manga left on "default" reports pager
        // while actually scrolling, which once left a strip's edges unscanned.
        val all = listOf(anchorIndex - 1, anchorIndex, anchorIndex + 1).filter { it in 0 until pages.size }
        // Anchor first: its bounds decide whether the window is a strip at all.
        return all.sortedBy { kotlin.math.abs(it - anchorIndex) }
    }

    private fun updateRegions(pageIndex: Int, regions: List<ScannedRegion>) {
        // Neighbours are only scanned in strip mode; keep them regardless of the anchor's bounds
        // not being known yet on the very first scan.
        if (kotlin.math.abs(pageIndex - currentPageIndex()) > 1) {
            Log.w("Translation", "scan page $pageIndex dropped, anchor=${currentPageIndex()}")
            return
        }
        // A scan publishes progressively; if the user merged boxes while it was still running,
        // this final list would resurrect everything they consumed. Carry the consumed flags
        // over to the fresh detections at the same spot.
        val previous = regionsByPage[pageIndex]
        val adjusted = if (previous.isNullOrEmpty()) {
            regions
        } else {
            regions.map { fresh ->
                if (previous.any { it.consumed && sameSpot(it.rect, fresh.rect) }) {
                    fresh.copy(consumed = true)
                } else {
                    fresh
                }
            }
        }
        regionsByPage[pageIndex] = adjusted
        while (regionsByPage.size > REGION_PAGE_CACHE) {
            regionsByPage.remove(regionsByPage.keys.first())
        }
        publishRegions()
    }

    /** The detector is deterministic, so a re-detected box lands on the same spot. */
    private fun sameSpot(a: NormalizedRect, b: NormalizedRect): Boolean {
        val smaller = minOf(a.area, b.area)
        return smaller > 0f && a.intersectionArea(b) >= smaller * 0.7f
    }

    /**
     * The webtoon counterpart of whole-page scanning: strip pages are detected one by one, so a
     * bubble straddling a boundary comes out as two half boxes (a single line each). Once a page
     * has been scanned, pair every edge-touching box with its continuation on the neighbouring
     * page — the ±1 window is processed as one unit. The larger half survives as the single
     * region carrying the FULL text; the other half is dropped. Unconsumed halves only, and
     * idempotent, so rescans re-merge the same pairs.
     */
    private fun mergeStraddlePartners(pageIndex: Int) {
        val regions = regionsByPage[pageIndex] ?: return
        var changed = false
        val merged = ArrayList<ScannedRegion>(regions.size)
        for (region in regions) {
            if (region.consumed) {
                merged += region
                continue
            }
            val touchesBottom = region.rect.bottom >= 0.92f
            val touchesTop = region.rect.top <= 0.08f
            if (!touchesBottom && !touchesTop) {
                merged += region
                continue
            }
            val adjacent = if (touchesBottom) pageIndex + 1 else pageIndex - 1
            var partner: ScannedRegion? = null
            var bestOverlap = 0f
            for (candidate in regionsByPage[adjacent].orEmpty()) {
                if (candidate.consumed) continue
                val partnerTouches = if (touchesBottom) {
                    candidate.rect.top <= 0.08f
                } else {
                    candidate.rect.bottom >= 0.92f
                }
                if (!partnerTouches) continue
                val overlap = minOf(region.rect.right, candidate.rect.right) -
                    maxOf(region.rect.left, candidate.rect.left)
                val threshold = minOf(region.rect.width, candidate.rect.width) * 0.35f
                if (overlap >= threshold && overlap > bestOverlap) {
                    bestOverlap = overlap
                    partner = candidate
                }
            }
            if (partner == null) {
                merged += region
                continue
            }
            // Reading order is top-down: the upper page's half goes first.
            // The boundary line is sometimes detected on BOTH pages: identical halves collapse.
            val combined = if (touchesTop) {
                listOf(partner.text, region.text)
            } else {
                listOf(region.text, partner.text)
            }.distinct().joinToString("\n")
            val keeper = if (region.rect.area >= partner.rect.area) region else {
                // The partner wins the box; move it into this page's list.
                partner.copy(pageIndex = pageIndex)
            }
            merged += keeper.copy(text = combined)
            regionsByPage[adjacent] = regionsByPage[adjacent].orEmpty().filterNot { it.id == partner.id }
            Log.i(
                "Translation",
                "merged straddle halves page=$pageIndex+${adjacent} text=${combined.take(36)}",
            )
            changed = true
        }
        if (changed) {
            regionsByPage[pageIndex] = merged
            publishRegions()
        }
    }

    private fun publishRegions() {
        val anchor = page?.index ?: return
        val allowed = windowIndices(anchor).toSet()
        val merged = allowed.flatMap { regionsByPage[it].orEmpty() }
        Log.i(
            "Translation",
            "publish anchor=$anchor allowed=$allowed stored=${regionsByPage.keys} regions=${merged.size}",
        )
        _state.update { it.copy(regions = merged) }
    }

    /** The reader tells us the mode; inferring it from page bounds failed while a page loaded. */
    fun setContinuous(value: Boolean) {
        if (continuousMode == value) return
        continuousMode = value
        refreshEntries()
        publishRegions()
        if (_state.value.panelVisible) scanPage()
    }

    private fun windowIndices(anchor: Int): List<Int> {
        val size = chapterPages?.size ?: return listOf(anchor)
        val candidates = if (continuousMode) {
            listOf(anchor - 1, anchor, anchor + 1)
        } else {
            listOf(anchor)
        }
        return candidates.filter { it in 0 until size }
    }

    /** Detects and recognises a page that fits in a single bitmap. */
    private suspend fun detectOnBitmap(
        bitmap: Bitmap,
        file: File,
        rawBounds: Pair<Int, Int>,
        detector: DbDetector,
        recognizer: OcrEngine?,
        generation: Int,
        pageIndex: Int,
        onProgress: (List<ScannedRegion>) -> Unit,
    ): List<ScannedRegion> {
        val boxes = withContext(Dispatchers.Default) { detector.detect(bitmap) }
        Log.i("Translation", "det page=$pageIndex boxes=${boxes.size}")
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val collected = ArrayList<ScannedRegion>()
        val insets = cropInsets[pageIndex] ?: CropInsets.NONE
        val rawWidth = rawBounds.first.toFloat()
        val rawHeight = rawBounds.second.toFloat()

        boxes.forEachIndexed { index, box ->
            if (generation != scanGeneration) return collected
            val left = box.rect.left.toInt().coerceIn(0, (bitmap.width - 2).coerceAtLeast(0))
            val top = box.rect.top.toInt().coerceIn(0, (bitmap.height - 2).coerceAtLeast(0))
            val right = box.rect.right.toInt().coerceIn(left + 1, bitmap.width)
            val bottom = box.rect.bottom.toInt().coerceIn(top + 1, bitmap.height)

            // Recognise from the original file at full resolution: the detection bitmap is
            // downscaled to 1800px, and the shrunken text measurably hurt Korean and English.
            val rawLeft = ((insets.left + left / width * insets.scaleX) * rawWidth).toInt()
            val rawTop = ((insets.top + top / height * insets.scaleY) * rawHeight).toInt()
            val rawRight = ((insets.left + right / width * insets.scaleX) * rawWidth).toInt()
            val rawBottom = ((insets.top + bottom / height * insets.scaleY) * rawHeight).toInt()
            val crop = withContext(Dispatchers.IO) {
                BitmapCropper.decodeRegion(
                    filePath = file.absolutePath,
                    left = rawLeft,
                    top = rawTop,
                    right = rawRight,
                    bottom = rawBottom,
                )
            } ?: runCatching {
                Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            }.getOrNull() ?: return@forEachIndexed

            // A wide box can hold several vertical columns, so the reading direction is not
            // decidable from the box shape alone: try both orientations, keep the stronger read.
            val text = recognizer?.let { recognizeBox(crop, it) }.orEmpty().trim()
            crop.recycle()

            if (text.isNotEmpty()) {
                collected += ScannedRegion(
                    id = nextRegionId++,
                    pageIndex = pageIndex,
                    rect = NormalizedRect(
                        left / width,
                        top / height,
                        right / width,
                        bottom / height,
                    ),
                    text = text,
                    confidence = box.score,
                )
                if (generation == scanGeneration) onProgress(collected.toList())
            }
        }
        return collected
    }

    /** Scans a long strip tile by tile so text keeps enough pixels for the detector. */
    private suspend fun detectOnTiles(
        file: File,
        bounds: Pair<Int, Int>,
        detector: DbDetector,
        recognizer: OcrEngine?,
        generation: Int,
        pageIndex: Int,
        onProgress: (List<ScannedRegion>) -> Unit,
    ): List<ScannedRegion> {
        val pageWidth = bounds.first.toFloat()
        val pageHeight = bounds.second.toFloat()
        val collected = ArrayList<ScannedRegion>()
        var tileTop = 0
        // ids come from the controller-wide counter: a page-local one collides across pages.

        while (tileTop < bounds.second) {
            if (generation != scanGeneration) return collected
            val guessScale = (TILE_WIDTH / pageWidth).coerceAtMost(1f)
            val sourceHeight = (TILE_HEIGHT / guessScale).toInt().coerceAtLeast(64)
            val tileBottom = min(tileTop + sourceHeight, bounds.second)

            val region = withContext(Dispatchers.IO) {
                BitmapCropper.decodeRegion(
                    filePath = file.absolutePath,
                    left = 0,
                    top = tileTop,
                    right = bounds.first,
                    bottom = tileBottom,
                    maxSide = (TILE_WIDTH * 2).toInt(),
                )
            } ?: run {
                tileTop = tileBottom
                null
            } ?: continue

            val tile = if (region.width > TILE_WIDTH) {
                val scaled = Bitmap.createScaledBitmap(
                    region,
                    TILE_WIDTH.toInt(),
                    (region.height * TILE_WIDTH / region.width).roundToInt().coerceAtLeast(1),
                    true,
                )
                if (scaled !== region) region.recycle()
                scaled
            } else {
                region
            }

            val scale = tile.width / pageWidth
            val boxes = withContext(Dispatchers.Default) { detector.detect(tile) }

            boxes.forEach { box ->
                if (generation != scanGeneration) return collected
                val left = box.rect.left.toInt().coerceIn(0, (tile.width - 2).coerceAtLeast(0))
                val top = box.rect.top.toInt().coerceIn(0, (tile.height - 2).coerceAtLeast(0))
                val right = box.rect.right.toInt().coerceIn(left + 1, tile.width)
                val bottom = box.rect.bottom.toInt().coerceIn(top + 1, tile.height)

                val crop = runCatching {
                    Bitmap.createBitmap(tile, left, top, right - left, bottom - top)
                }.getOrNull() ?: return@forEach
                val text = recognizer?.let { recognizeBox(crop, it) }.orEmpty().trim()
                crop.recycle()
                if (text.isEmpty()) return@forEach

                // Undo the tile offset and the downscale to get page-normalized coordinates.
                val pageLeft = (left / scale).coerceIn(0f, pageWidth)
                val pageTop = (tileTop + top / scale).coerceIn(0f, pageHeight)
                val pageRight = (right / scale).coerceIn(pageLeft, pageWidth)
                val pageBottom = (tileTop + bottom / scale).coerceIn(pageTop, pageHeight)
                collected += ScannedRegion(
                    id = nextRegionId++,
                    pageIndex = pageIndex,
                    rect = NormalizedRect(
                        pageLeft / pageWidth,
                        pageTop / pageHeight,
                        pageRight / pageWidth,
                        pageBottom / pageHeight,
                    ),
                    text = text,
                    confidence = box.score,
                )
                if (generation == scanGeneration) onProgress(collected.toList())
            }

            tile.recycle()
            tileTop = tileBottom
        }
        Log.i("Translation", "scan page $pageIndex tiles done regions=${collected.size}")
        return collected
    }

    fun tapRegion(x: Float, y: Float, pageIndex: Int = currentPageIndex()): Boolean {
        val key = spread ?: return false
        val region = _state.value.regions
            .filter { it.pageIndex == pageIndex && !it.consumed && it.rect.contains(x, y) }
            .minByOrNull { it.rect.area } ?: return false

        // A bubble straddling the strip boundary is detected as two half boxes, one per page.
        // Tapping one half pulls in the matching half from the neighbouring page (overlapping
        // column, touching the opposite edge) so the entry carries the whole line.
        val touchesBottom = region.rect.bottom >= 0.92f
        val touchesTop = region.rect.top <= 0.08f
        var partner: ScannedRegion? = null
        var partnerPageIndex = -1
        if (touchesBottom || touchesTop) {
            val adjacent = if (touchesBottom) pageIndex + 1 else pageIndex - 1
            var bestOverlap = 0f
            for (candidate in _state.value.regions) {
                if (candidate.pageIndex != adjacent || candidate.consumed) continue
                val edgesTouch = if (touchesBottom) candidate.rect.top <= 0.08f else candidate.rect.bottom >= 0.92f
                if (!edgesTouch) continue
                val overlap = minOf(region.rect.right, candidate.rect.right) -
                    maxOf(region.rect.left, candidate.rect.left)
                val threshold = minOf(region.rect.width, candidate.rect.width) * 0.35f
                if (overlap >= threshold && overlap > bestOverlap) {
                    bestOverlap = overlap
                    partner = candidate
                    partnerPageIndex = adjacent
                }
            }
        }

        val readerPage = chapterPages?.getOrNull(pageIndex) ?: page ?: return false
        val pageKey = PageKey(chapterId = key.chapterId, pageIndex = readerPage.index)
        val entry = session.add(key, pageKey, region.rect)
        val combinedText = if (partner != null) {
            // Top of the dialogue first: the partner sits above when the tap hit a bottom-edge
            // box on the lower page, and below when it hit a top-edge box.
            if (touchesTop) {
                listOf(partner.text, region.text)
            } else {
                listOf(region.text, partner.text)
            }.distinct().joinToString("\n")
        } else {
            region.text
        }
        session.update(entry.id) {
            it.copy(state = EntryState.MT, sourceText = combinedText, error = null)
        }
        val consumedIds = buildSet {
            add(region.id)
            partner?.let { add(it.id) }
        }
        for (page in setOf(pageIndex, partnerPageIndex)) {
            if (page < 0) continue
            regionsByPage[page] = regionsByPage[page].orEmpty().map {
                if (it.id in consumedIds) it.copy(consumed = true) else it
            }
        }
        _state.update { state ->
            state.copy(regions = state.regions.map { if (it.id in consumedIds) it.copy(consumed = true) else it })
        }
        if (partner != null) {
            Log.i("Translation", "tap merged straddle halves pages=$pageIndex+$partnerPageIndex")
        }
        refreshEntries()
        queue.trySend(entry.id)
        return true
    }

    private fun ensureDetector(): DbDetector? {
        val presetId = prefs.detPresetId.get()
        detector?.let { if (detectorKey == presetId) return it }
        runCatching { detector?.close() }
        detector = null
        val model = modelStore.resolveModel(presetId) ?: return null
        return DbDetector(model).also {
            detector = it
            detectorKey = presetId
        }
    }

    fun moveBox(id: Long, dx: Float, dy: Float) {
        session.update(id) { entry ->
            val r = entry.rect
            var shiftX = dx
            var shiftY = dy
            if (r.left + shiftX < 0f) shiftX = -r.left
            if (r.right + shiftX > 1f) shiftX = 1f - r.right
            if (r.top + shiftY < 0f) shiftY = -r.top
            if (r.bottom + shiftY > 1f) shiftY = 1f - r.bottom
            entry.copy(
                rect = NormalizedRect(
                    r.left + shiftX,
                    r.top + shiftY,
                    r.right + shiftX,
                    r.bottom + shiftY,
                ),
            )
        }
        refreshEntries()
    }

    fun setSourceLanguage(language: TranslationSourceLanguage) {
        if (language == _state.value.sourceLanguage) return
        prefs.sourceLanguage.set(language)
        _state.update { it.copy(sourceLanguage = language) }
        resetRecognition()
        if (!_state.value.panelVisible) return

        if (usesMangaOcr()) {
            if (mangaOcrStore.isInstalled()) {
                showNotice("已切换到${language.label}（Manga OCR），重新识别中")
                scanPage()
            } else {
                showNotice("未下载 Manga OCR 模型，可在设置中下载")
            }
            return
        }

        val preset = effectivePreset()
        if (preset == null || !modelStore.isInstalled(preset.id)) {
            showNotice("未下载${language.label}识别模型，可在设置中下载")
            return
        }
        val dedicated = OcrPresetCatalog.languagePreset(language.code)
        val dedicatedReady = dedicated != null && modelStore.isInstalled(dedicated.id)
        showNotice(
            if (dedicatedReady) {
                "已切换到${language.label}，重新识别中"
            } else {
                "已切换到${language.label}，暂用通用模型重新识别"
            },
        )
        scanPage()
    }

    /** Drops everything recognised with the previous language and rebuilds the recogniser. */
    private fun resetRecognition() {
        // Bumping the generation makes any in-flight scan stop writing results.
        scanGeneration++
        regionsByPage.clear()
        (engine as? AutoCloseable)?.close()
        engine = null
        engineKey = null
        // Entries carry text read with the previous language's dictionary, so they go too.
        session.clear()
        refreshEntries()
        _state.update { it.copy(regions = emptyList(), scanning = false) }
    }

    fun setPanelWidthFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0.22f, 0.55f)
        _state.update { it.copy(panelWidthFraction = clamped) }
        prefs.panelWidthFraction.set(clamped)
    }

    /** Live width while the landscape rail is dragged; preference commits on release. */
    fun setPanelWidthFractionLive(fraction: Float) {
        val clamped = fraction.coerceIn(0.22f, 0.55f)
        _state.update { it.copy(panelWidthFraction = clamped) }
    }

    fun setPanelFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0.25f, 0.45f)
        _state.update { it.copy(panelFraction = clamped) }
        prefs.panelHeightFraction.set(clamped)
    }

    /**
     * Live height while the user drags the handle: the reader's bottom padding follows this
     * value, so the panel never covers art mid-drag. The preference is only committed on
     * release ([setPanelFraction]).
     */
    fun setPanelFractionLive(fraction: Float) {
        val clamped = fraction.coerceIn(0.25f, 0.45f)
        _state.update { it.copy(panelFraction = clamped) }
    }

    fun setActivePage(index: Int) {
        if (index == page?.index) return
        Log.w("Translation", "setActivePage $index (was ${page?.index})")
        val target = chapterPages?.getOrNull(index) ?: return
        onPageChanged(target)
    }

    fun onPageChanged(readerPage: ReaderPage) {
        val chapterId = runCatching { readerPage.chapter.chapter.id }.getOrDefault(-1L)
        if (chapterId != lastChapterId) {
            lastChapterId = chapterId
            session.clear()
            TranslationGeometry.clear()
            releasePrepared()
            cropInsets.clear()
            // Page 5 of one chapter is not page 5 of the next; folder per chapter keeps them apart.
            pageCache.clear()
            pageCache = TranslationPageCache(File(cacheRoot, chapterId.toString()))
            regionsByPage.clear()
            // Belt and braces: anything published for the previous book leaves the screen at
            // once, even if a teardown callback races this clear.
            _state.update { it.copy(regions = emptyList()) }
        }
        page = readerPage
        chapterPages = runCatching { readerPage.chapter.pages }.getOrNull()
        spread = SpreadKey(chapterId, readerPage.index)
        // Pages far outside the window had their holders recycled; their leftover screen rects
        // once came back as "live" geometry and republished old boxes over the new page.
        TranslationGeometry.retain((readerPage.index - 3)..(readerPage.index + 3))
        // Keep the neighbouring pages' regions; only this window is shown.
        _state.update { it.copy(scanning = false) }
        publishRegions()
        if (_state.value.panelVisible) scanPage()
        // Reading the page header copies the stream on first use; keep that off the main thread.
        scope.launch(Dispatchers.IO) {
            val size = pageCache.sizeOf(readerPage)
            withContext(Dispatchers.Main) {
                if (page?.index == readerPage.index) pageSize = size
                if (size != null) {
                    refreshEntries()
                    publishRegions()
                }
            }
        }
        refreshEntries()
    }

    fun addBox(rect: NormalizedRect, pageIndex: Int = currentPageIndex()) {
        val key = spread ?: return
        val readerPage = chapterPages?.getOrNull(pageIndex) ?: page ?: return
        val pageKey = PageKey(
            chapterId = key.chapterId,
            pageIndex = readerPage.index,
        )

        // A box drawn over detected regions means "translate these together": reuse the text the
        // scanner already recognised, keep the detector's reading order and skip a second OCR pass.
        // Regions belong to the active page only, so merging across pages would mix two images.
        // A region counts as picked when most of it falls inside the box, or when its centre
        // does — a box drawn slightly smaller than a column still means "take this column".
        val picked = _state.value.regions.filter { region ->
            region.pageIndex == pageIndex &&
                !region.consumed &&
                (
                    region.rect.intersectionArea(rect) >= region.rect.area * REGION_PICK_RATIO ||
                        rect.contains(region.rect.centerX, region.rect.centerY)
                    )
        }

        if (picked.isEmpty()) {
            val entry = session.add(key, pageKey, rect)
            refreshEntries()
            queue.trySend(entry.id)
            return
        }

        val mergedRect = picked.fold(rect) { acc, region -> acc.union(region.rect) }.clampToUnit()
        // Vertical manga columns are read right-to-left: tall boxes side by side are columns,
        // everything else is stacked lines read top-down.
        val columnsRightToLeft = picked.size > 1 && picked.all { it.rect.height >= it.rect.width }
        val mergedText = picked
            .sortedWith(
                if (columnsRightToLeft) {
                    compareByDescending { it.rect.left }
                } else {
                    compareBy({ it.rect.top }, { it.rect.left })
                },
            )
            .joinToString("\n") { it.text }

        val entry = session.add(key, pageKey, mergedRect)
        session.update(entry.id) { it.copy(state = EntryState.MT, sourceText = mergedText, error = null) }
        val consumed = picked.mapTo(HashSet()) { it.id }
        // Mark the storage layer too: the in-flight scan's final publish reads regionsByPage,
        // and without this the fresh detections would resurrect the merged boxes.
        regionsByPage[pageIndex] = regionsByPage[pageIndex].orEmpty().map {
            if (it.id in consumed) it.copy(consumed = true) else it
        }
        _state.update { state ->
            state.copy(regions = state.regions.map { if (it.id in consumed) it.copy(consumed = true) else it })
        }
        refreshEntries()
        queue.trySend(entry.id)
    }

    /**
     * A bubble straddling a strip page boundary: the drawn box is split by the page areas it
     * covers, each page's half is OCR-read from its own file, and the texts join in reading
     * order (top page first) into ONE entry anchored on the largest half. Recognising a single
     * page's half alone used to lose the rest of the dialogue for good.
     */
    fun addStraddlingBox(parts: List<Pair<Int, NormalizedRect>>) {
        val key = spread ?: return
        if (parts.isEmpty()) return
        scope.launch {
            val engine = ensureEngine() ?: run {
                showNotice("识别模型未下载，可手动框选")
                return@launch
            }
            val texts = ArrayList<String>()
            var anchorPage = -1
            var anchorRect: NormalizedRect? = null
            var anchorArea = -1f
            for ((pageIndex, rect) in parts.sortedBy { it.first }) {
                val readerPage = chapterPages?.getOrNull(pageIndex) ?: continue
                val file = pageCache.fileFor(readerPage) ?: continue
                val size = pageCache.sizeOf(readerPage) ?: continue
                val pixels = toRawRect(rect, pageIndex).toPixels(size.first, size.second)
                val crop = withContext(Dispatchers.IO) {
                    BitmapCropper.decodeRegion(
                        filePath = file.absolutePath,
                        left = pixels.left,
                        top = pixels.top,
                        right = pixels.right,
                        bottom = pixels.bottom,
                    )
                } ?: continue
                val partText = try {
                    recognizeBox(crop, engine)
                } finally {
                    if (!crop.isRecycled) crop.recycle()
                }
                if (!partText.isNullOrBlank()) texts += partText
                if (rect.area > anchorArea) {
                    anchorArea = rect.area
                    anchorPage = pageIndex
                    anchorRect = rect
                }
            }
            val anchor = anchorRect ?: return@launch
            // The visual outline must cover BOTH halves: the anchor half is the entry rect, the
            // other half rides along as the extra rect on its own page.
            val extra = parts.firstOrNull { it.first != anchorPage }
            val combined = texts.joinToString(
                if (_state.value.sourceLanguage == TranslationSourceLanguage.JAPANESE) "" else " ",
            )
            val readerPage = chapterPages?.getOrNull(anchorPage) ?: return@launch
            val pageKey = PageKey(chapterId = key.chapterId, pageIndex = readerPage.index)
            val entry = session.add(key, pageKey, anchor)
            if (combined.isBlank()) {
                // Still create the entry: a silent nothing read as "the draw failed", and a
                // visible failed row can be retried or deleted by the user.
                Log.w("Translation", "straddle box pages=${parts.map { it.first }}: no text")
                session.update(entry.id) {
                    it.copy(
                        state = EntryState.FAILED,
                        error = "识别失败，点按重试",
                        extraPageIndex = extra?.first,
                        extraRect = extra?.second,
                    )
                }
                refreshEntries()
                return@launch
            }
            session.update(entry.id) {
                it.copy(
                    state = EntryState.MT,
                    sourceText = combined,
                    error = null,
                    extraPageIndex = extra?.first,
                    extraRect = extra?.second,
                )
            }
            Log.i("Translation", "straddle box pages=${parts.map { it.first }} text=${combined.take(40)}")
            refreshEntries()
            queue.trySend(entry.id)
        }
    }

    fun editSource(id: Long, newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) {
            delete(id)
            return
        }
        session.update(id) { it.copy(state = EntryState.MT, sourceText = trimmed, error = null) }
        refreshEntries()
        queue.trySend(id)
    }

    fun retry(id: Long) {
        session.update(id) { it.copy(state = EntryState.MT, sourceText = null, error = null) }
        refreshEntries()
        queue.trySend(id)
    }

    fun snapshot(): String = TranslationSession.toJson(session.snapshot())

    fun restore(raw: String) {
        val snapshot = TranslationSession.fromJson(raw) ?: return
        session.restore(snapshot)
        refreshEntries()
    }

    fun onOrientationChanged() {
        close()
    }

    private fun refreshEntries() {
        val key = spread ?: run {
            _state.update { it.copy(entries = emptyList(), itemCount = 0) }
            return
        }
        val keys = windowIndices(key.anchorIndex).map { SpreadKey(key.chapterId, it) }
        val entries = keys.flatMap { session.entriesOf(it) }
            .mapIndexed { index, entry -> entry.copy(seq = index + 1) }
        _state.update { it.copy(entries = entries, itemCount = entries.size) }
    }

    private suspend fun processEntry(entryId: Long) {
        try {
            val entry = session.find(entryId) ?: return
            _state.update { it.copy(busy = true) }
            val text = entry.sourceText ?: recognize(entry) ?: run {
                markFailed(entryId, "识别失败")
                refreshEntries()
                _state.update { state -> state.copy(busy = state.entries.any { it.isBusy }) }
                return
            }
            val translate = canTranslate()
            session.update(entryId) {
                it.copy(
                    state = if (translate) EntryState.MT else EntryState.READY,
                    sourceText = text,
                    error = null,
                )
            }
            refreshEntries()

            if (!translate) {
                _state.update { state -> state.copy(busy = state.entries.any { it.isBusy }) }
                return
            }

            val provider = ProviderRegistry.create(prefs.providerId.get(), prefs.providerConfig(), callFactory)
            if (provider == null) {
                markFailed(entryId, "翻译引擎未配置")
                refreshEntries()
                _state.update { state -> state.copy(busy = state.entries.any { it.isBusy }) }
                return
            }

            val key = spread ?: return
            val texts = session.entriesOf(key).map { it.sourceText.orEmpty() }
            val anchorIndex = session.entriesOf(key).indexOfFirst { it.id == entryId }
            val context = buildContext(texts, anchorIndex)
            val service = TranslationService(provider, prefs.useContext.get())
            val outcome = service.translate(
                texts = listOf(text),
                from = _state.value.sourceLanguage.code,
                to = prefs.targetLanguage.get(),
                context = context,
            ).firstOrNull() ?: TranslationOutcome.Failure(ErrorKind.BAD_RESPONSE)

            when (outcome) {
                is TranslationOutcome.Success -> {
                    val translated = outcome.texts.firstOrNull().orEmpty()
                    session.update(entryId) {
                        it.copy(state = EntryState.READY, translation = translated, error = null)
                    }
                }
                is TranslationOutcome.Failure -> {
                    val probeKind: ErrorKind = outcome.kind
                    markFailed(entryId, probeKind.toMessage())
                }
            }
        } finally {
            refreshEntries()
            _state.update { state -> state.copy(busy = state.entries.any { it.isBusy }) }
        }
    }

    private suspend fun recognize(entry: TranslationEntry): String? {
        // Resolve the entry's own page: the OCR queue may run after the reader moved on, and
        // cropping from whatever page is current produced empty results.
        val readerPage = pageFor(entry) ?: return logRecognizeSkip(entry, "no reader page")
        val file = pageCache.fileFor(readerPage) ?: return logRecognizeSkip(entry, "no cached file")
        val size = pageCache.sizeOf(readerPage) ?: return logRecognizeSkip(entry, "no page size")
        val activeEngine = ensureEngine() ?: return logRecognizeSkip(entry, "no engine")

        val pixels = toRawRect(entry.rect, entry.pageIndex).toPixels(size.first, size.second)
        val crop = withContext(Dispatchers.IO) {
            BitmapCropper.decodeRegion(
                filePath = file.absolutePath,
                left = pixels.left,
                top = pixels.top,
                right = pixels.right,
                bottom = pixels.bottom,
            )
        } ?: return logRecognizeSkip(entry, "region decode failed rect=$pixels size=$size")
        Log.i("Translation", "ocr crop ${crop.width}x${crop.height} entry=${entry.id}")

        return try {
            recognizeBox(crop, activeEngine)
        } finally {
            if (!crop.isRecycled) crop.recycle()
        }
    }

    /**
     * Reads a box whose writing direction is unknown. Vertical Japanese columns are read
     * right-to-left and a wide box can hold several of them, so the aspect ratio is a poor hint.
     * Structural evidence decides first; only ambiguous shapes fall back to comparing scores,
     * with a bias toward the primary read because it follows the language's writing direction.
     */
    private suspend fun recognizeBox(crop: Bitmap, engine: OcrEngine): String? {
        val ratio = crop.width.toFloat() / crop.height.toFloat()
        // The primary orientation follows the language: Japanese manga text is vertical
        // (right-to-left columns), Korean and English manhwa/comics run horizontal lines.
        // Geometry alone cannot settle the direction — slicing a grid of text either way
        // yields elongated pieces, so the language decides and structure only confirms.
        val verticalPrimary = _state.value.sourceLanguage == TranslationSourceLanguage.JAPANESE ||
            ratio <= 0.5f
        val primary = readCrop(crop, engine, vertical = verticalPrimary)
        // Accept the primary read on structural grounds: either the splitter found several
        // matching units (clear multi-column / multi-line layout) or it found ONE unit, which
        // means the whole crop was read in the language's own direction — a single short line of
        // Korean would otherwise be re-scored against a rotated read whose garbage text is
        // frequently longer and won. Only a genuinely ambiguous split falls through.
        if (primary != null && (primary.units == 1 || (primary.units >= 2 && primary.unitsMatchOrientation))) {
            Log.i(
                "PaddleOcr",
                "box primary(${if (verticalPrimary) "v" else "h"}) units=${primary.units} conf=${primary.confidence} text=${primary.text.take(32)}",
            )
            return primary.text
        }
        val secondary = if (verticalPrimary && ratio > 0.5f || !verticalPrimary && ratio < 3f) {
            readCrop(crop, engine, vertical = !verticalPrimary)
        } else {
            null
        }
        val chosen = when {
            primary == null -> secondary?.text
            secondary == null -> primary.text
            secondary.units >= 2 && secondary.unitsMatchOrientation && primary.units < 2 -> secondary.text
            scoreOf(primary) > scoreOf(secondary) * PRIMARY_BIAS -> primary.text
            else -> secondary.text
        }
        Log.i(
            "PaddleOcr",
            "box dual(${if (verticalPrimary) "v" else "h"}+${if (verticalPrimary) "h" else "v"}) " +
                "primary=${primary?.let { "${it.units}u/${it.text.take(16)}" }} " +
                "secondary=${secondary?.let { "${it.units}u/${it.text.take(16)}" }} chosen=${chosen?.take(24)}",
        )
        return chosen
    }

    private fun scoreOf(read: OcrRead): Float =
        read.text.length * read.confidence.coerceAtLeast(0.05f)

    /**
     * One orientation attempt: split into columns/lines, read each, join in reading order.
     * [units] and [unitsMatchOrientation] carry the structural evidence: N units whose shapes all
     * match the reading direction (tall columns / wide rows) is proof of that direction, which a
     * score alone cannot give — misreading vertical columns as rows scores about the same.
     */
    private data class OcrRead(
        val text: String,
        val confidence: Float,
        val units: Int,
        val unitsMatchOrientation: Boolean,
    )

    private suspend fun readCrop(
        crop: Bitmap,
        engine: OcrEngine,
        vertical: Boolean,
    ): OcrRead? {
        val units = ColumnSplitter.split(crop, vertical)
        val ordered = if (vertical) units.reversed() else units
        val texts = ArrayList<String>(ordered.size)
        var confidenceSum = 0f
        var matchedUnits = 0

        try {
            for (unit in ordered) {
                val bitmap = unit.bitmap
                // A unit that matches the direction (tall column / wide row) supports it.
                val matches = if (vertical) bitmap.height >= bitmap.width else bitmap.width >= bitmap.height
                val result = engine.recognize(bitmap, vertical)
                val text = result.text.trim()
                if (text.isNotEmpty()) {
                    texts += text
                    confidenceSum += result.confidence
                    matchedUnits += if (matches) 1 else 0
                }
            }
        } finally {
            // The splitter may hand back the crop itself as the single unit.
            units.forEach { unit ->
                if (unit.bitmap !== crop && !unit.bitmap.isRecycled) unit.bitmap.recycle()
            }
        }

        if (texts.isEmpty()) return null
        val text = texts.joinToString(if (vertical) "" else " ")
        return OcrRead(
            text = text,
            confidence = confidenceSum / texts.size,
            units = ordered.size,
            // Majority rule: a stray sliver unit must not invalidate an otherwise clear split.
            unitsMatchOrientation = matchedUnits >= 2 && matchedUnits * 2 >= ordered.size,
        )
    }

    private fun logRecognizeSkip(entry: TranslationEntry, reason: String): String? {
        Log.w("Translation", "ocr skipped entry=${entry.id}: $reason")
        return null
    }

    /**
     * Decodes the page into the image the viewer actually shows.
     *
     * The reader crops white/black borders inside SubsamplingScaleImageView while decoding, so
     * OCR on the raw file saw a different picture than the one on screen and every box came out
     * shifted. [CropBorders.findCropBorders] is the same native helper that view uses, which lets
     * us crop identically.
     */
    private fun prepareDisplayBitmap(page: ReaderPage, cropBorders: Boolean): Bitmap? {
        val file = pageCache.fileFor(page) ?: return null
        val key = file.absolutePath + ":" + cropBorders
        preparedBitmap?.let { if (preparedKey == key && !it.isRecycled) return it }
        releasePrepared()

        val decoded = BitmapCropper.decodeWhole(file.absolutePath, 1800) ?: return null
        val rawWidth = decoded.width
        val rawHeight = decoded.height

        val crop = if (cropBorders) detectCrop(decoded) else null
        if (crop == null) {
            preparedBitmap = decoded
            preparedKey = key
            cropInsets[page.index] = CropInsets.NONE
            return decoded
        }

        val cropped = runCatching {
            Bitmap.createBitmap(decoded, crop.left, crop.top, crop.width(), crop.height())
        }.getOrNull()
        if (cropped == null || cropped === decoded) {
            preparedBitmap = decoded
            preparedKey = key
            cropInsets[page.index] = CropInsets.NONE
            return decoded
        }
        decoded.recycle()

        preparedBitmap = cropped
        preparedKey = key
        cropInsets[page.index] = CropInsets(
            crop.left / rawWidth.toFloat(),
            crop.top / rawHeight.toFloat(),
            (rawWidth - crop.right) / rawWidth.toFloat(),
            (rawHeight - crop.bottom) / rawHeight.toFloat(),
        )
        return cropped
    }

    private fun detectCrop(bitmap: Bitmap): Rect? = runCatching {
        val width = bitmap.width
        val height = bitmap.height
        val buffer = java.nio.ByteBuffer.allocate(width * height * 4)
        bitmap.copyPixelsToBuffer(buffer)
        val borders = CropBorders.findCropBorders(buffer.array(), width, height)
        if (borders.size < 4) return null
        val left = borders[0]
        val top = borders[1]
        val right = borders[2]
        val bottom = borders[3]
        if (left < 0 || top < 0 || right <= 0 || bottom <= 0) return null
        if (left + right > width || top + bottom > height) return null
        if (right < width * 0.5f || bottom < height * 0.5f) return null
        if (right == width && bottom == height) return null
        Rect(left, top, left + right, top + bottom)
    }.getOrNull()

    private fun toRawRect(rect: NormalizedRect, pageIndex: Int): NormalizedRect {
        val insets = cropInsets[pageIndex] ?: CropInsets.NONE
        return NormalizedRect(
            insets.left + rect.left * insets.scaleX,
            insets.top + rect.top * insets.scaleY,
            insets.left + rect.right * insets.scaleX,
            insets.top + rect.bottom * insets.scaleY,
        )
    }

    /** Temporary: shows the crop sent to OCR for a few seconds. Debug builds only. */
    private fun showDebugCrop(crop: Bitmap) {
        if (!BuildConfig.DEBUG) return
        val preview = runCatching {
            val scale = min(DEBUG_CROP_MAX_SIDE / max(crop.width, crop.height), 1f)
            val w = (crop.width * scale).roundToInt().coerceAtLeast(1)
            val h = (crop.height * scale).roundToInt().coerceAtLeast(1)
            if (w == crop.width && h == crop.height) {
                crop.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                Bitmap.createScaledBitmap(crop, w, h, true)
            }
        }.getOrNull()
        if (preview == null || preview.isRecycled) return
        debugCropStamp++
        val stamp = debugCropStamp
        _state.update { it.copy(debugCrop = preview) }
        scope.launch {
            delay(DEBUG_CROP_DURATION_MS)
            if (debugCropStamp == stamp) _state.update { it.copy(debugCrop = null) }
        }
    }

    private fun releasePrepared() {
        val bitmap = preparedBitmap
        preparedBitmap = null
        preparedKey = null
        if (scansInFlight.get() == 0 && bitmap != null) {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun pageFor(entry: TranslationEntry): ReaderPage? {
        chapterPages?.getOrNull(entry.pageIndex)?.let { return it }
        return page?.takeIf { it.index == entry.pageIndex }
    }

    private fun ensureEngine(): OcrEngine? {
        if (usesMangaOcr()) {
            val key = "mangaocr"
            engine?.let { if (engineKey == key) return it }
            (engine as? AutoCloseable)?.close()
            engine = null
            val encoder = mangaOcrStore.encoder() ?: run {
                Log.w("Translation", "mangaocr encoder missing")
                return null
            }
            val decoder = mangaOcrStore.decoder() ?: run {
                Log.w("Translation", "mangaocr decoder missing")
                return null
            }
            val vocab = mangaOcrStore.vocab() ?: run {
                Log.w("Translation", "mangaocr vocab missing")
                return null
            }
            val created = MangaOcrEngine(encoder, decoder, vocab)
            engine = created
            engineKey = key
            return created
        }

        val preset = effectivePreset() ?: return null
        val key = preset.id
        engine?.let { if (engineKey == key) return it }
        (engine as? AutoCloseable)?.close()
        engine = null
        val installed = modelStore.resolve(key) ?: return null
        val created = OnnxOcrEngine(installed.modelFile, installed.charset)
        engine = created
        engineKey = key
        return created
    }

    private fun markFailed(entryId: Long, message: String) {
        Log.w("Translation", "entry $entryId failed: $message")
        session.update(entryId) { it.copy(state = EntryState.FAILED, error = message) }
        refreshEntries()
    }


    data class UiState(
        val enabled: Boolean = true,
        val panelVisible: Boolean = false,
        val panelFraction: Float = 0.3f,
        val panelWidthFraction: Float = 0.3f,
        val sourceLanguage: TranslationSourceLanguage = TranslationSourceLanguage.JAPANESE,
        val busy: Boolean = false,
        val notice: String? = null,
        val entries: List<TranslationEntry> = emptyList(),
        val itemCount: Int = 0,
        val regions: List<ScannedRegion> = emptyList(),
        val scanning: Boolean = false,
        val debugCrop: Bitmap? = null,
        val translationEnabled: Boolean = true,
    )

    /** Fractions of the raw page that the viewer's border cropping removes. */
    private data class CropInsets(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val scaleX: Float get() = 1f - left - right
        val scaleY: Float get() = 1f - top - bottom

        companion object {
            val NONE = CropInsets(0f, 0f, 0f, 0f)
        }
    }
}

/** Long-strip pages are scanned in tiles of roughly this size (in output pixels). */
private const val TILE_WIDTH = 1200f
private const val TILE_HEIGHT = 1600f
private const val TALL_PAGE_RATIO = 2f

/** How many pages of detected regions are kept around the current window. */
private const val REGION_PAGE_CACHE = 10

/** Quiet period after the last view relayout before a scan may start. */
private const val GEOMETRY_SETTLE_MS = 400L

/** A detected region counts as picked when this much of it falls inside the drawn box. */
private const val REGION_PICK_RATIO = 0.35f

/** Temporary: shows the crop sent to OCR for a few seconds. Debug builds only. */
private const val DEBUG_CROP_MAX_SIDE = 360f
private const val DEBUG_CROP_DURATION_MS = 3000L

/** How long a scan waits for a page that is still downloading. */
private const val PAGE_READY_ATTEMPTS = 8
private const val PAGE_READY_DELAY_MS = 250L

/** A bubble the auto scan found and recognised, in display-normalized coordinates. */
data class ScannedRegion(
    val id: Long,
    val pageIndex: Int,
    val rect: NormalizedRect,
    val text: String,
    val confidence: Float,
    val consumed: Boolean = false,
)

private fun ErrorKind.toMessage(): String = when (this) {
    ErrorKind.NETWORK -> "网络不可用"
    ErrorKind.AUTH -> "凭据无效"
    ErrorKind.RATE_LIMIT -> "请求过于频繁"
    ErrorKind.BAD_RESPONSE -> "引擎返回异常"
    ErrorKind.UNSUPPORTED -> "不支持的请求"
    ErrorKind.CANCELLED -> "已取消"
}
